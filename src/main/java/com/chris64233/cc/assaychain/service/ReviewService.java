package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.ResultStatus;
import com.chris64233.cc.assaychain.domain.ReviewDecision;
import com.chris64233.cc.assaychain.domain.ReviewEvent;
import com.chris64233.cc.assaychain.domain.ReviewKind;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.ReviewEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ReviewService {

    private final ReviewEventRepository reviewEventRepository;
    private final AssayEventRepository assayEventRepository;
    private final SampleRepository sampleRepository;

    public ReviewService(ReviewEventRepository reviewEventRepository,
                         AssayEventRepository assayEventRepository,
                         SampleRepository sampleRepository) {
        this.reviewEventRepository = reviewEventRepository;
        this.assayEventRepository = assayEventRepository;
        this.sampleRepository = sampleRepository;
    }

    /**
     * 对待复核结果作出复核决定。
     *
     * <p>复核人必须与原提交人不同；只能对保管链完整（无待确认交接）、尚未作废的叶子样本
     * 作出决定。通过后结果成为 EFFECTIVE 对外有效版本；驳回置 REJECTED，允许重新提交。
     * 审批事件号全局幂等：同号同内容重放返回原记录，同号异内容冲突。</p>
     */
    @Transactional
    public ReviewEvent review(String eventNo,
                              String resultEventNo,
                              String reviewer,
                              ReviewDecision decision,
                              String comment) {
        ReceptionService.requireText(eventNo, "审批事件号");
        ReceptionService.requireText(resultEventNo, "结果号");
        ReceptionService.requireText(reviewer, "复核人");
        if (decision == null) {
            throw new BusinessRuleException("复核决定不能为空（APPROVED/REJECTED）");
        }

        ReviewEvent existing = reviewEventRepository.findByEventNo(eventNo).orElse(null);
        // 标量投影取 id，避免实体先进入持久化上下文导致后续 FOR UPDATE 命中一级缓存而失效
        Long versionId = assayEventRepository.findIdByEventNo(resultEventNo)
                .orElseThrow(() -> new NotFoundException("检测结果不存在: " + resultEventNo));
        Long sampleId = assayEventRepository.findSampleIdById(versionId);

        // 先锁样本行再锁结果版本行，与提交/分样/交接/更正式串行，锁顺序全局一致
        Sample sample = sampleRepository.findByIdForUpdate(sampleId)
                .orElseThrow(() -> new NotFoundException("样本不存在，id=" + sampleId));
        AssayEvent version = assayEventRepository.findByIdForUpdate(versionId).orElseThrow();

        if (existing != null) {
            verifyIdempotent(existing, version, decision, reviewer, comment);
            return existing;
        }

        if (version.getStatus() != ResultStatus.PENDING) {
            throw new ConflictException("结果已复核，不能重复决定: " + resultEventNo
                    + "（当前状态 " + version.getStatus() + "）");
        }
        if (!sample.isLeaf()) {
            throw new BusinessRuleException("样本已分样（不再是叶子节点），不能对其结果作出复核决定: "
                    + sample.getExternalNo());
        }
        if (sample.getPendingCustodyEventId() != null) {
            throw new BusinessRuleException("样本存在待确认交接、保管链不完整，不能复核: "
                    + sample.getExternalNo());
        }
        if (version.getSubmittedBy().equals(reviewer)) {
            throw new BusinessRuleException("复核人必须与结果提交人不同，提交人为: "
                    + version.getSubmittedBy());
        }

        Instant now = Instant.now();
        if (decision == ReviewDecision.APPROVED) {
            version.setStatus(ResultStatus.EFFECTIVE);
            version.setEffectiveAt(now);
        } else {
            version.setStatus(ResultStatus.REJECTED);
        }
        assayEventRepository.save(version);

        ReviewEvent review = new ReviewEvent();
        review.setEventNo(eventNo);
        review.setKind(ReviewKind.RESULT_REVIEW);
        review.setResultVersion(version);
        review.setDecision(decision);
        review.setReviewedBy(reviewer);
        review.setComment(comment);
        return reviewEventRepository.save(review);
    }

    private void verifyIdempotent(ReviewEvent existing,
                                  AssayEvent version,
                                  ReviewDecision decision,
                                  String reviewer,
                                  String comment) {
        boolean sameComment = existing.getComment() == null
                ? comment == null
                : existing.getComment().equals(comment);
        boolean same = existing.getKind() == ReviewKind.RESULT_REVIEW
                && existing.getResultVersion().getId().equals(version.getId())
                && existing.getDecision() == decision
                && existing.getReviewedBy().equals(reviewer)
                && sameComment;
        if (!same) {
            throw new ConflictException("审批事件号 " + existing.getEventNo()
                    + " 已存在但内容不同（冲突）");
        }
    }
}
