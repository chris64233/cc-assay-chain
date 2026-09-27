package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.ApprovalDecision;
import com.chris64233.cc.assaychain.domain.ApprovalEvent;
import com.chris64233.cc.assaychain.domain.ApprovalKind;
import com.chris64233.cc.assaychain.domain.AssayCorrection;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.AssayStatus;
import com.chris64233.cc.assaychain.domain.CorrectionStatus;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.ApprovalEventRepository;
import com.chris64233.cc.assaychain.repo.AssayCorrectionRepository;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 检测结果复核与更正流程。
 *
 * <p>所有决定动作先对样本行加悲观写锁，与分样、保管方变更互斥：
 * <ul>
 *   <li>决定时样本必须仍是叶子、保管链完整（无待确认交接），否则拒绝；</li>
 *   <li>更正批准时再次确认被更正版本仍是当前生效版本，
 *       防止基于旧版本的审批覆盖后来已经生效的新版本。</li>
 * </ul>
 */
@Service
public class ReviewService {

    /** 更正批准生成新版本时使用的结果号前缀。 */
    public static final String CORRECTION_EVENT_PREFIX = "COR-";

    private final AssayEventRepository assayEventRepository;
    private final AssayCorrectionRepository correctionRepository;
    private final ApprovalEventRepository approvalEventRepository;
    private final SampleRepository sampleRepository;

    public ReviewService(AssayEventRepository assayEventRepository,
                         AssayCorrectionRepository correctionRepository,
                         ApprovalEventRepository approvalEventRepository,
                         SampleRepository sampleRepository) {
        this.assayEventRepository = assayEventRepository;
        this.correctionRepository = correctionRepository;
        this.approvalEventRepository = approvalEventRepository;
        this.sampleRepository = sampleRepository;
    }

    /**
     * 对实验室提交的待复核结果作出复核决定。
     * 复核人必须与原提交人不同；通过后结果才成为对外有效结果。
     */
    @Transactional
    public ApprovalEvent reviewSubmission(String approvalNo,
                                          String resultEventNo,
                                          ApprovalDecision decision,
                                          String decidedBy,
                                          String comment) {
        ReceptionService.requireText(approvalNo, "审批事件号");
        ReceptionService.requireText(resultEventNo, "结果号");
        ReceptionService.requireText(decidedBy, "复核人");
        if (decision == null) {
            throw new BusinessRuleException("复核决定不能为空");
        }

        AssayEvent event = assayEventRepository.findByEventNo(resultEventNo)
                .orElseThrow(() -> new NotFoundException("结果不存在: " + resultEventNo));
        Sample sample = lockSample(event);

        ApprovalEvent existing = approvalEventRepository.findByApprovalNo(approvalNo).orElse(null);
        if (existing != null) {
            verifyApprovalIdempotent(
                    existing, ApprovalKind.SUBMISSION_REVIEW, event, null, decision, decidedBy);
            return existing;
        }

        if (event.getStatus() != AssayStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("结果不在待复核状态，当前状态为 "
                    + event.getStatus() + "（结果号 " + resultEventNo + "）");
        }
        if (event.getSubmittedBy().equals(decidedBy)) {
            throw new BusinessRuleException("复核人必须与原提交人不同，原提交人为: "
                    + event.getSubmittedBy());
        }
        requireDecidableLeaf(sample, "复核");

        Instant now = Instant.now();
        event.setReviewedBy(decidedBy);
        event.setReviewedAt(now);
        if (decision == ApprovalDecision.APPROVE) {
            event.setStatus(AssayStatus.EFFECTIVE);
            event.setEffectiveAt(now);
        } else {
            event.setStatus(AssayStatus.REJECTED);
        }
        assayEventRepository.save(event);

        return recordApproval(
                approvalNo, ApprovalKind.SUBMISSION_REVIEW, event, null,
                decision, decidedBy, comment, null, now);
    }

    /**
     * 创建更正申请，引用当前生效的原结果，保存旧值、新值、原因与证据。
     * 已生效结果不得直接覆盖，只能通过更正申请在批准后形成新版本。
     */
    @Transactional
    public AssayCorrection requestCorrection(String correctionNo,
                                             String resultEventNo,
                                             BigDecimal newValue,
                                             String newUnit,
                                             String reason,
                                             String evidence,
                                             String requestedBy) {
        ReceptionService.requireText(correctionNo, "更正号");
        ReceptionService.requireText(resultEventNo, "原结果号");
        ReceptionService.requireText(newUnit, "新计量单位");
        ReceptionService.requireText(reason, "更正原因");
        ReceptionService.requireText(evidence, "更正证据");
        ReceptionService.requireText(requestedBy, "更正申请人");
        BigDecimal normalizedNewValue = MassRules.requireResultScale(newValue);

        AssayEvent event = assayEventRepository.findByEventNo(resultEventNo)
                .orElseThrow(() -> new NotFoundException("结果不存在: " + resultEventNo));
        Sample sample = lockSample(event);

        AssayCorrection existing = correctionRepository.findByCorrectionNo(correctionNo).orElse(null);
        if (existing != null) {
            verifyCorrectionIdempotent(
                    existing, event, normalizedNewValue, newUnit, reason, evidence, requestedBy);
            return existing;
        }

        if (event.getStatus() != AssayStatus.EFFECTIVE) {
            throw new BusinessRuleException("只有生效结果可以发起更正，当前状态为 "
                    + event.getStatus() + "（结果号 " + resultEventNo + "）");
        }
        requireDecidableLeaf(sample, "更正");
        if (event.getResultValue().compareTo(normalizedNewValue) == 0
                && event.getUnit().equals(newUnit)) {
            throw new BusinessRuleException("更正后的数值与单位与原结果一致，无需更正");
        }

        if (correctionRepository.existsByAssayEventIdAndStatus(
                event.getId(), CorrectionStatus.PENDING)) {
            throw new ConflictException("该结果已有待审批的更正申请，请先完成审批: "
                    + resultEventNo);
        }

        AssayCorrection correction = new AssayCorrection();
        correction.setCorrectionNo(correctionNo);
        correction.setAssayEvent(event);
        correction.setOldValue(event.getResultValue());
        correction.setOldUnit(event.getUnit());
        correction.setNewValue(normalizedNewValue);
        correction.setNewUnit(newUnit);
        correction.setReason(reason);
        correction.setEvidence(evidence);
        correction.setRequestedBy(requestedBy);
        correction.setStatus(CorrectionStatus.PENDING);
        return correctionRepository.save(correction);
    }

    /**
     * 审批更正申请。批准时以原版本为基准生成新版本并将旧版本置为被取代；
     * 若审批期间基准版本已被其他更正取代，则拒绝本次覆盖。
     */
    @Transactional
    public ApprovalEvent decideCorrection(String approvalNo,
                                          String correctionNo,
                                          ApprovalDecision decision,
                                          String decidedBy,
                                          String comment) {
        ReceptionService.requireText(approvalNo, "审批事件号");
        ReceptionService.requireText(correctionNo, "更正号");
        ReceptionService.requireText(decidedBy, "审批人");
        if (decision == null) {
            throw new BusinessRuleException("审批决定不能为空");
        }

        AssayCorrection correction = correctionRepository.findByCorrectionNo(correctionNo)
                .orElseThrow(() -> new NotFoundException("更正申请不存在: " + correctionNo));
        AssayEvent original = correction.getAssayEvent();
        Sample sample = lockSample(original);

        ApprovalEvent existing = approvalEventRepository.findByApprovalNo(approvalNo).orElse(null);
        if (existing != null) {
            verifyApprovalIdempotent(
                    existing, ApprovalKind.CORRECTION_DECISION, original, correction,
                    decision, decidedBy);
            return existing;
        }

        if (correction.getStatus() != CorrectionStatus.PENDING) {
            throw new BusinessRuleException("更正申请不在待审批状态，当前状态为 "
                    + correction.getStatus() + "（更正号 " + correctionNo + "）");
        }
        if (correction.getRequestedBy().equals(decidedBy)) {
            throw new BusinessRuleException("审批人必须与更正申请人不同，申请人为: "
                    + correction.getRequestedBy());
        }
        requireDecidableLeaf(sample, "更正审批");

        Instant now = Instant.now();
        String newEventNo = null;

        if (decision == ApprovalDecision.APPROVE) {
            // 乐观并发：申请引用的版本必须仍是当前生效版本，
            // 否则说明已有另一个更正先生效，本次审批不得覆盖。
            if (original.getStatus() != AssayStatus.EFFECTIVE) {
                throw new ConflictException("原结果版本已不再是当前生效版本（状态 "
                        + original.getStatus() + "），本次更正审批作废: " + correctionNo);
            }

            newEventNo = CORRECTION_EVENT_PREFIX + correctionNo;
            if (assayEventRepository.findByEventNo(newEventNo).isPresent()) {
                throw new ConflictException("更正新版本结果号冲突: " + newEventNo);
            }

            int nextVersionNo = assayEventRepository
                    .findBySampleIdAndItemCodeOrderByVersionNoAsc(
                            sample.getId(), original.getItemCode())
                    .stream()
                    .mapToInt(AssayEvent::getVersionNo)
                    .max()
                    .orElse(original.getVersionNo()) + 1;

            AssayEvent newVersion = new AssayEvent();
            newVersion.setEventNo(newEventNo);
            newVersion.setSample(sample);
            newVersion.setItemCode(original.getItemCode());
            newVersion.setResultValue(correction.getNewValue());
            newVersion.setUnit(correction.getNewUnit());
            newVersion.setSubmittedBy(correction.getRequestedBy());
            newVersion.setStatus(AssayStatus.EFFECTIVE);
            newVersion.setVersionNo(nextVersionNo);
            newVersion.setReviewedBy(decidedBy);
            newVersion.setReviewedAt(now);
            newVersion.setEffectiveAt(now);
            assayEventRepository.save(newVersion);

            original.setStatus(AssayStatus.SUPERSEDED);
            assayEventRepository.save(original);

            correction.setStatus(CorrectionStatus.APPROVED);
        } else {
            correction.setStatus(CorrectionStatus.REJECTED);
        }
        correctionRepository.save(correction);

        return recordApproval(
                approvalNo, ApprovalKind.CORRECTION_DECISION, original, correction,
                decision, decidedBy, comment, newEventNo, now);
    }

    private Sample lockSample(AssayEvent event) {
        return sampleRepository.findLockedById(event.getSample().getId())
                .orElseThrow(() -> new NotFoundException(
                        "样本不存在: " + event.getSample().getExternalNo()));
    }

    /** 决定时样本必须仍是叶子，且保管链完整（不存在悬而未决的交接）。 */
    private void requireDecidableLeaf(Sample sample, String action) {
        if (!sample.isLeaf()) {
            throw new BusinessRuleException("样本已分样，不能对非叶子样本作出" + action
                    + "决定: " + sample.getExternalNo());
        }
        if (sample.getPendingCustodyEventId() != null) {
            throw new BusinessRuleException("样本存在待确认交接，保管链不完整，不能作出" + action
                    + "决定: " + sample.getExternalNo());
        }
    }

    private ApprovalEvent recordApproval(String approvalNo,
                                         ApprovalKind kind,
                                         AssayEvent event,
                                         AssayCorrection correction,
                                         ApprovalDecision decision,
                                         String decidedBy,
                                         String comment,
                                         String newEventNo,
                                         Instant now) {
        ApprovalEvent approval = new ApprovalEvent();
        approval.setApprovalNo(approvalNo);
        approval.setKind(kind);
        approval.setAssayEvent(event);
        approval.setCorrection(correction);
        approval.setDecision(decision);
        approval.setDecidedBy(decidedBy);
        approval.setComment(comment);
        approval.setNewEventNo(newEventNo);
        approval.setDecisionTime(now);
        return approvalEventRepository.save(approval);
    }

    private void verifyApprovalIdempotent(ApprovalEvent existing,
                                          ApprovalKind kind,
                                          AssayEvent event,
                                          AssayCorrection correction,
                                          ApprovalDecision decision,
                                          String decidedBy) {
        boolean same = existing.getKind() == kind
                && existing.getAssayEvent().getId().equals(event.getId())
                && (correction == null
                        ? existing.getCorrection() == null
                        : existing.getCorrection() != null
                                && existing.getCorrection().getId().equals(correction.getId()))
                && existing.getDecision() == decision
                && existing.getDecidedBy().equals(decidedBy);
        if (!same) {
            throw new ConflictException("审批事件号 " + existing.getApprovalNo()
                    + " 已存在但内容不同（冲突）");
        }
    }

    private void verifyCorrectionIdempotent(AssayCorrection existing,
                                            AssayEvent event,
                                            BigDecimal newValue,
                                            String newUnit,
                                            String reason,
                                            String evidence,
                                            String requestedBy) {
        boolean same = existing.getAssayEvent().getId().equals(event.getId())
                && existing.getNewValue().compareTo(newValue) == 0
                && existing.getNewUnit().equals(newUnit)
                && existing.getReason().equals(reason)
                && existing.getEvidence().equals(evidence)
                && existing.getRequestedBy().equals(requestedBy);
        if (!same) {
            throw new ConflictException("更正号 " + existing.getCorrectionNo()
                    + " 已存在但内容不同（冲突）");
        }
    }
}
