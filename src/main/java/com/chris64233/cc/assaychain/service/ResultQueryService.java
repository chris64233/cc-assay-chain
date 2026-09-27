package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.ApprovalEvent;
import com.chris64233.cc.assaychain.domain.AssayCorrection;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.AssayStatus;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.ApprovalEventRepository;
import com.chris64233.cc.assaychain.repo.AssayCorrectionRepository;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结果版本链、当前有效结果、复核记录及历史时点有效结果查询。
 */
@Service
public class ResultQueryService {

    private final AssayEventRepository assayEventRepository;
    private final AssayCorrectionRepository correctionRepository;
    private final ApprovalEventRepository approvalEventRepository;
    private final SampleRepository sampleRepository;
    private final LineageService lineageService;

    public ResultQueryService(AssayEventRepository assayEventRepository,
                              AssayCorrectionRepository correctionRepository,
                              ApprovalEventRepository approvalEventRepository,
                              SampleRepository sampleRepository,
                              LineageService lineageService) {
        this.assayEventRepository = assayEventRepository;
        this.correctionRepository = correctionRepository;
        this.approvalEventRepository = approvalEventRepository;
        this.sampleRepository = sampleRepository;
        this.lineageService = lineageService;
    }

    /**
     * 联合查询：（样本，检测项目）的当前有效结果、待复核结果、完整版本链、
     * 全部复核/审批记录，并按 asOf 时点还原当时的有效结果。
     */
    @Transactional(readOnly = true)
    public ResultHistoryView getHistory(String sampleExternalNo, String itemCode, Instant asOf) {
        Sample sample = sampleRepository.findByExternalNo(sampleExternalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + sampleExternalNo));
        ReceptionService.requireText(itemCode, "检测项目");

        List<AssayEvent> chain = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(sample.getId(), itemCode);
        if (chain.isEmpty()) {
            throw new NotFoundException("检测项目无结果记录: " + sampleExternalNo + "/" + itemCode);
        }

        List<ApprovalEvent> approvals = approvalEventRepository
                .findByAssayEventSampleIdInOrderByDecisionTimeAsc(List.of(sample.getId())).stream()
                .filter(approval -> approval.getAssayEvent().getItemCode().equals(itemCode))
                .sorted(Comparator.comparing(ApprovalEvent::getDecisionTime)
                        .thenComparing(ApprovalEvent::getApprovalNo))
                .toList();

        Map<String, String> correctionNoByNewEvent = new LinkedHashMap<>();
        List<AssayCorrection> corrections = correctionRepository
                .findByAssayEventSampleIdInOrderByCreatedAtAsc(List.of(sample.getId()));
        for (AssayCorrection correction : corrections) {
            if (!correction.getAssayEvent().getItemCode().equals(itemCode)) {
                continue;
            }
            approvals.stream()
                    .filter(approval -> approval.getCorrection() != null
                            && approval.getCorrection().getId().equals(correction.getId())
                            && approval.getNewEventNo() != null)
                    .findFirst()
                    .ifPresent(approval ->
                            correctionNoByNewEvent.put(approval.getNewEventNo(),
                                    correction.getCorrectionNo()));
        }

        List<ResultVersionView> versions = chain.stream()
                .map(event -> toVersionView(event,
                        sample.getExternalNo(),
                        correctionNoByNewEvent.get(event.getEventNo())))
                .toList();
        List<ReviewRecordView> reviews = approvals.stream().map(this::toReviewView).toList();

        AssayEvent effective = chain.stream()
                .filter(event -> event.getStatus() == AssayStatus.EFFECTIVE)
                .reduce((first, second) -> second)
                .orElse(null);
        AssayEvent pending = chain.stream()
                .filter(event -> event.getStatus() == AssayStatus.PENDING_REVIEW)
                .reduce((first, second) -> second)
                .orElse(null);

        String effectiveAsOf = null;
        if (asOf != null) {
            effectiveAsOf = chain.stream()
                    .filter(event -> event.getEffectiveAt() != null
                            && !event.getEffectiveAt().isAfter(asOf))
                    .max(Comparator.comparing(AssayEvent::getEffectiveAt))
                    .map(AssayEvent::getEventNo)
                    .orElse(null);
        }

        return new ResultHistoryView(
                sample.getExternalNo(),
                itemCode,
                effective == null ? null : toCurrentView(effective, sample.getExternalNo()),
                pending == null ? null : toCurrentView(pending, sample.getExternalNo()),
                versions,
                reviews,
                effectiveAsOf);
    }

    /** 查询样本某检测项目当前对外有效结果；无生效版本返回空。 */
    @Transactional(readOnly = true)
    public CurrentResultView getEffective(String sampleExternalNo, String itemCode) {
        Sample sample = sampleRepository.findByExternalNo(sampleExternalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + sampleExternalNo));
        ReceptionService.requireText(itemCode, "检测项目");
        return assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(sample.getId(), itemCode).stream()
                .filter(event -> event.getStatus() == AssayStatus.EFFECTIVE)
                .reduce((first, second) -> second)
                .map(event -> toCurrentView(event, sample.getExternalNo()))
                .orElse(null);
    }

    /**
     * 联合查询：样本完整谱系 + 该样本每个检测项目的
     * 当前有效结果、版本链与复核记录。
     */
    @Transactional(readOnly = true)
    public SampleResultsLineageView getSampleResultsLineage(String sampleExternalNo) {
        LineageView lineage = lineageService.getLineage(sampleExternalNo);
        Sample sample = sampleRepository.findByExternalNo(sampleExternalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + sampleExternalNo));
        List<String> itemCodes = assayEventRepository
                .findBySampleIdInOrderByEventTimeAsc(List.of(sample.getId())).stream()
                .map(AssayEvent::getItemCode)
                .distinct()
                .sorted()
                .toList();
        List<ResultHistoryView> results = itemCodes.stream()
                .map(itemCode -> getHistory(sampleExternalNo, itemCode, null))
                .toList();
        return new SampleResultsLineageView(lineage, results);
    }

    private ResultVersionView toVersionView(AssayEvent event,
                                            String sampleExternalNo,
                                            String sourceCorrectionNo) {
        return new ResultVersionView(
                event.getEventNo(),
                sampleExternalNo,
                event.getItemCode(),
                event.getVersionNo(),
                event.getStatus(),
                event.getResultValue(),
                event.getUnit(),
                event.getSubmittedBy(),
                event.getEventTime(),
                event.getReviewedBy(),
                event.getReviewedAt(),
                event.getEffectiveAt(),
                sourceCorrectionNo);
    }

    private CurrentResultView toCurrentView(AssayEvent event, String sampleExternalNo) {
        return new CurrentResultView(
                sampleExternalNo,
                event.getItemCode(),
                event.getStatus().name(),
                event.getEventNo(),
                event.getVersionNo(),
                event.getResultValue(),
                event.getUnit(),
                event.getSubmittedBy(),
                event.getReviewedBy(),
                event.getEffectiveAt());
    }

    private ReviewRecordView toReviewView(ApprovalEvent approval) {
        AssayCorrection correction = approval.getCorrection();
        return new ReviewRecordView(
                approval.getApprovalNo(),
                approval.getKind(),
                approval.getAssayEvent().getEventNo(),
                correction == null ? null : correction.getCorrectionNo(),
                approval.getDecision(),
                approval.getDecidedBy(),
                approval.getComment(),
                approval.getNewEventNo(),
                approval.getDecisionTime());
    }
}
