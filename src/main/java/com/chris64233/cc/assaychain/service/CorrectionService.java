package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.CorrectionRequest;
import com.chris64233.cc.assaychain.domain.CorrectionStatus;
import com.chris64233.cc.assaychain.domain.ResultStatus;
import com.chris64233.cc.assaychain.domain.ReviewDecision;
import com.chris64233.cc.assaychain.domain.ReviewEvent;
import com.chris64233.cc.assaychain.domain.ReviewKind;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.CorrectionRequestRepository;
import com.chris64233.cc.assaychain.repo.ReviewEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class CorrectionService {

    private final CorrectionRequestRepository correctionRepository;
    private final AssayEventRepository assayEventRepository;
    private final ReviewEventRepository reviewEventRepository;
    private final SampleRepository sampleRepository;

    public CorrectionService(CorrectionRequestRepository correctionRepository,
                             AssayEventRepository assayEventRepository,
                             ReviewEventRepository reviewEventRepository,
                             SampleRepository sampleRepository) {
        this.correctionRepository = correctionRepository;
        this.assayEventRepository = assayEventRepository;
        this.reviewEventRepository = reviewEventRepository;
        this.sampleRepository = sampleRepository;
    }

    /**
     * 对已生效结果发起更正申请，保存旧值、新值、原因和证据。
     *
     * <p>只能引用当前 EFFECTIVE 的版本；旧值/旧单位由服务端从原版本快照，不信任调用方。
     * 新值或新单位必须与旧值至少有一项不同。同一版本同时只允许一个待审批更正。
     * 更正号幂等：同号同内容重放返回原申请，同号异内容冲突。</p>
     */
    @Transactional
    public CorrectionRequest request(String correctionNo,
                                     String resultEventNo,
                                     String newResultEventNo,
                                     BigDecimal newResultValue,
                                     String newUnit,
                                     String reason,
                                     String evidence,
                                     String requestedBy) {
        ReceptionService.requireText(correctionNo, "更正号");
        ReceptionService.requireText(resultEventNo, "原结果号");
        ReceptionService.requireText(newResultEventNo, "新版本结果号");
        ReceptionService.requireText(newUnit, "新计量单位");
        ReceptionService.requireText(reason, "更正原因");
        ReceptionService.requireText(evidence, "更正证据");
        ReceptionService.requireText(requestedBy, "申请人");
        BigDecimal normalizedNewValue = MassRules.requireResultScale(newResultValue);

        CorrectionRequest existing = correctionRepository.findByCorrectionNo(correctionNo)
                .orElse(null);
        // 标量投影取 id，避免实体先进入持久化上下文导致后续 FOR UPDATE 命中一级缓存
        Long originalId = assayEventRepository.findIdByEventNo(resultEventNo)
                .orElseThrow(() -> new NotFoundException("检测结果不存在: " + resultEventNo));

        if (existing != null) {
            AssayEvent originalForCompare = assayEventRepository.findById(originalId).orElseThrow();
            verifyIdempotent(existing, originalForCompare, newResultEventNo, normalizedNewValue,
                    newUnit, reason, evidence, requestedBy);
            return existing;
        }

        if (assayEventRepository.existsByEventNo(newResultEventNo)) {
            throw new ConflictException("新版本结果号已存在: " + newResultEventNo);
        }
        if (correctionRepository.existsByReservedEventNo(newResultEventNo)) {
            throw new ConflictException("新版本结果号已被其他待审批更正占用: " + newResultEventNo);
        }

        // 锁定原版本行，串行同一结果上的并发更正申请
        AssayEvent original = assayEventRepository.findByIdForUpdate(originalId).orElseThrow();
        if (original.getStatus() != ResultStatus.EFFECTIVE) {
            throw new BusinessRuleException("只能更正当前生效的结果，原结果状态为 "
                    + original.getStatus() + ": " + resultEventNo);
        }
        if (correctionRepository.existsByOriginalVersionIdAndStatus(
                original.getId(), CorrectionStatus.PENDING)) {
            throw new ConflictException("该结果已有待审批的更正申请: " + resultEventNo);
        }
        if (original.getResultValue().compareTo(normalizedNewValue) == 0
                && original.getUnit().equals(newUnit)) {
            throw new BusinessRuleException("更正后的结果值与单位与原结果完全相同，无需更正");
        }

        CorrectionRequest request = new CorrectionRequest();
        request.setCorrectionNo(correctionNo);
        request.setOriginalVersion(original);
        request.setNewResultEventNo(newResultEventNo);
        request.setOldValue(original.getResultValue());
        request.setOldUnit(original.getUnit());
        request.setNewValue(normalizedNewValue);
        request.setNewUnit(newUnit);
        request.setReason(reason);
        request.setEvidence(evidence);
        request.setRequestedBy(requestedBy);
        request.setStatus(CorrectionStatus.PENDING);
        return correctionRepository.save(request);
    }

    /**
     * 审批更正申请。批准时原子地把旧生效版本置 SUPERSEDED 并发布 EFFECTIVE 新版本，
     * 形成结果版本链；驳回仅关闭申请，原结果继续有效。
     *
     * <p>临界区内依次锁样本行、申请行、原结果版本行（与复核/提交/分样保持「样本优先」的
     * 锁顺序），因此：样本一旦不再是叶子或处于待确认交接，不能发布新版本；基于旧版本的
     * 审批若发现原版本已被另一笔更正取代，直接冲突，不会覆盖后来生效的版本。
     * 审批事件号全局幂等。</p>
     */
    @Transactional
    public ReviewEvent decide(String reviewEventNo,
                              String correctionNo,
                              String reviewer,
                              ReviewDecision decision,
                              String comment) {
        ReceptionService.requireText(reviewEventNo, "审批事件号");
        ReceptionService.requireText(correctionNo, "更正号");
        ReceptionService.requireText(reviewer, "审批人");
        if (decision == null) {
            throw new BusinessRuleException("审批决定不能为空（APPROVED/REJECTED）");
        }

        ReviewEvent existingReview = reviewEventRepository.findByEventNo(reviewEventNo).orElse(null);
        // 标量投影取申请 id，避免实体先进入持久化上下文导致后续 FOR UPDATE 命中一级缓存
        Long requestId = correctionRepository.findIdByCorrectionNo(correctionNo)
                .orElseThrow(() -> new NotFoundException("更正申请不存在: " + correctionNo));

        if (existingReview != null) {
            CorrectionRequest requestForCompare =
                    correctionRepository.findById(requestId).orElseThrow();
            verifyReviewIdempotent(existingReview, requestForCompare, decision, reviewer, comment);
            return existingReview;
        }

        // 锁顺序：样本行 -> 更正申请行 -> 原结果版本行（与复核/提交/分样保持「样本优先」）
        Long sampleId = correctionRepository.findSampleIdById(requestId);
        Sample sample = sampleRepository.findByIdForUpdate(sampleId)
                .orElseThrow(() -> new NotFoundException("样本不存在，id=" + sampleId));
        CorrectionRequest request = correctionRepository.findByIdForUpdate(requestId).orElseThrow();
        Long originalId = correctionRepository.findOriginalVersionIdById(requestId);
        AssayEvent original = assayEventRepository.findByIdForUpdate(originalId).orElseThrow();

        if (request.getStatus() != CorrectionStatus.PENDING) {
            throw new ConflictException("更正申请已审批，不能重复决定: " + correctionNo
                    + "（当前状态 " + request.getStatus() + "）");
        }
        if (reviewer.equals(original.getSubmittedBy())) {
            throw new BusinessRuleException("审批人必须与原结果提交人不同，提交人为: "
                    + original.getSubmittedBy());
        }
        if (reviewer.equals(request.getRequestedBy())) {
            throw new BusinessRuleException("审批人不能是更正申请人本人: " + reviewer);
        }

        Instant now = Instant.now();
        if (decision == ReviewDecision.APPROVED) {
            if (original.getStatus() != ResultStatus.EFFECTIVE) {
                // 并发：原版本已被另一笔先批准的更正取代
                throw new ConflictException(
                        "原结果版本已被后来生效的版本取代，本次基于旧版本的审批不能生效: "
                                + original.getEventNo());
            }
            if (!sample.isLeaf()) {
                throw new BusinessRuleException(
                        "样本已分样（不再是叶子节点），不能发布更正结果: "
                                + sample.getExternalNo());
            }
            if (sample.getPendingCustodyEventId() != null) {
                throw new BusinessRuleException("样本存在待确认交接、保管链不完整，不能批准更正: "
                        + sample.getExternalNo());
            }
            if (assayEventRepository.findByEventNo(request.getNewResultEventNo()).isPresent()) {
                throw new ConflictException(
                        "新版本结果号已被占用: " + request.getNewResultEventNo());
            }

            // 先把旧版本移出唯一生效位并立即刷库，再插入新版本，避免唯一约束误伤
            original.setStatus(ResultStatus.SUPERSEDED);
            original.setSupersededAt(now);
            assayEventRepository.save(original);
            assayEventRepository.flush();

            AssayEvent newVersion = new AssayEvent();
            newVersion.setEventNo(request.getNewResultEventNo());
            newVersion.setSample(sample);
            newVersion.setItemCode(original.getItemCode());
            newVersion.setVersionNo(original.getVersionNo() + 1);
            newVersion.setPrevVersion(original);
            newVersion.setResultValue(request.getNewValue());
            newVersion.setUnit(request.getNewUnit());
            newVersion.setSubmittedBy(request.getRequestedBy());
            newVersion.setStatus(ResultStatus.EFFECTIVE);
            newVersion.setEffectiveAt(now);
            assayEventRepository.save(newVersion);

            request.setNewVersion(newVersion);
            request.setStatus(CorrectionStatus.APPROVED);
            request.setDecidedAt(now);
            correctionRepository.save(request);

            return saveReview(reviewEventNo, ReviewKind.CORRECTION_REVIEW, newVersion,
                    request, decision, reviewer, comment);
        }

        request.setStatus(CorrectionStatus.REJECTED);
        request.setDecidedAt(now);
        correctionRepository.save(request);
        return saveReview(reviewEventNo, ReviewKind.CORRECTION_REVIEW, original,
                request, decision, reviewer, comment);
    }

    private ReviewEvent saveReview(String eventNo,
                                   ReviewKind kind,
                                   AssayEvent version,
                                   CorrectionRequest request,
                                   ReviewDecision decision,
                                   String reviewer,
                                   String comment) {
        ReviewEvent review = new ReviewEvent();
        review.setEventNo(eventNo);
        review.setKind(kind);
        review.setResultVersion(version);
        review.setCorrectionRequest(request);
        review.setDecision(decision);
        review.setReviewedBy(reviewer);
        review.setComment(comment);
        return reviewEventRepository.save(review);
    }

    private void verifyIdempotent(CorrectionRequest existing,
                                  AssayEvent original,
                                  String newResultEventNo,
                                  BigDecimal newResultValue,
                                  String newUnit,
                                  String reason,
                                  String evidence,
                                  String requestedBy) {
        boolean same = existing.getOriginalVersion().getId().equals(original.getId())
                && existing.getNewResultEventNo().equals(newResultEventNo)
                && existing.getNewValue().compareTo(newResultValue) == 0
                && existing.getNewUnit().equals(newUnit)
                && existing.getReason().equals(reason)
                && existing.getEvidence().equals(evidence)
                && existing.getRequestedBy().equals(requestedBy);
        if (!same) {
            throw new ConflictException("更正号 " + existing.getCorrectionNo()
                    + " 已存在但内容不同（冲突）");
        }
    }

    private void verifyReviewIdempotent(ReviewEvent existing,
                                        CorrectionRequest request,
                                        ReviewDecision decision,
                                        String reviewer,
                                        String comment) {
        boolean sameComment = existing.getComment() == null
                ? comment == null
                : existing.getComment().equals(comment);
        boolean same = existing.getKind() == ReviewKind.CORRECTION_REVIEW
                && existing.getCorrectionRequest() != null
                && existing.getCorrectionRequest().getId().equals(request.getId())
                && existing.getDecision() == decision
                && existing.getReviewedBy().equals(reviewer)
                && sameComment;
        if (!same) {
            throw new ConflictException("审批事件号 " + existing.getEventNo()
                    + " 已存在但内容不同（冲突）");
        }
    }
}
