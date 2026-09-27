package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.ResultStatus;
import com.chris64233.cc.assaychain.domain.ReviewEvent;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.ReviewEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 检测结果查询：当前有效结果、完整结果版本链、复核记录，以及任意时点有效结果还原。
 */
@Service
public class ResultQueryService {

    private final SampleRepository sampleRepository;
    private final AssayEventRepository assayEventRepository;
    private final ReviewEventRepository reviewEventRepository;

    public ResultQueryService(SampleRepository sampleRepository,
                              AssayEventRepository assayEventRepository,
                              ReviewEventRepository reviewEventRepository) {
        this.sampleRepository = sampleRepository;
        this.assayEventRepository = assayEventRepository;
        this.reviewEventRepository = reviewEventRepository;
    }

    /** 查询（样本，项目）的当前对外有效结果；不存在生效结果时 404。 */
    @Transactional(readOnly = true)
    public ResultVersionView getCurrent(String sampleExternalNo, String itemCode) {
        Sample sample = requireSample(sampleExternalNo);
        AssayEvent current = assayEventRepository
                .findBySampleIdAndItemCodeAndStatus(sample.getId(), itemCode, ResultStatus.EFFECTIVE)
                .orElseThrow(() -> new NotFoundException(
                        "该检测项目当前无有效结果: " + sampleExternalNo + "/" + itemCode));
        return toVersionView(current);
    }

    /** 联合查询：当前有效结果 + 完整版本链 + 每个版本的复核/审批记录。 */
    @Transactional(readOnly = true)
    public ResultHistoryView getHistory(String sampleExternalNo, String itemCode) {
        Sample sample = requireSample(sampleExternalNo);
        List<AssayEvent> chain = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(sample.getId(), itemCode);
        if (chain.isEmpty()) {
            throw new NotFoundException(
                    "该检测项目没有任何结果记录: " + sampleExternalNo + "/" + itemCode);
        }

        List<Long> versionIds = chain.stream().map(AssayEvent::getId).toList();
        List<ReviewEvent> reviews = reviewEventRepository
                .findByResultVersionIdInOrderByReviewedAtAsc(versionIds);

        ResultVersionView current = chain.stream()
                .filter(v -> v.getStatus() == ResultStatus.EFFECTIVE)
                .findFirst()
                .map(this::toVersionView)
                .orElse(null);

        return new ResultHistoryView(
                sampleExternalNo,
                itemCode,
                current,
                chain.stream().map(this::toVersionView).toList(),
                reviews.stream().map(this::toReviewView).toList());
    }

    /**
     * 还原任意时点的有效结果：版本生效时间 &lt;= at 且未在 at 之前失效。
     * 版本切换的同一时刻归属新版本（旧版本失效时间等于新版本生效时间）。
     */
    @Transactional(readOnly = true)
    public ResultVersionView getEffectiveAt(String sampleExternalNo, String itemCode, Instant at) {
        if (at == null) {
            throw new BusinessRuleException("查询时点不能为空");
        }
        Sample sample = requireSample(sampleExternalNo);
        List<AssayEvent> chain = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(sample.getId(), itemCode);
        return chain.stream()
                .filter(v -> v.getEffectiveAt() != null
                        && !v.getEffectiveAt().isAfter(at)
                        && (v.getSupersededAt() == null || v.getSupersededAt().isAfter(at)))
                .reduce((first, second) -> second) // 理论上至多一个，取最高版本兜底
                .map(this::toVersionView)
                .orElseThrow(() -> new NotFoundException(
                        "该时点没有有效结果: " + sampleExternalNo + "/" + itemCode + " @ " + at));
    }

    private Sample requireSample(String sampleExternalNo) {
        ReceptionService.requireText(sampleExternalNo, "样本号");
        return sampleRepository.findByExternalNo(sampleExternalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + sampleExternalNo));
    }

    private ResultVersionView toVersionView(AssayEvent v) {
        AssayEvent prev = v.getPrevVersion();
        return new ResultVersionView(
                v.getEventNo(),
                v.getSample().getExternalNo(),
                v.getItemCode(),
                v.getVersionNo(),
                prev == null ? null : prev.getEventNo(),
                v.getResultValue(),
                v.getUnit(),
                v.getSubmittedBy(),
                v.getStatus().name(),
                v.getEventTime(),
                v.getEffectiveAt(),
                v.getSupersededAt());
    }

    private ReviewRecordView toReviewView(ReviewEvent r) {
        return new ReviewRecordView(
                r.getEventNo(),
                r.getKind().name(),
                r.getResultVersion().getEventNo(),
                r.getCorrectionRequest() == null ? null : r.getCorrectionRequest().getCorrectionNo(),
                r.getDecision().name(),
                r.getReviewedBy(),
                r.getComment(),
                r.getReviewedAt());
    }
}
