package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.CustodyEvent;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.domain.SplitEvent;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.CustodyEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import com.chris64233.cc.assaychain.repo.SplitEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LineageService {

    private final SampleRepository sampleRepository;
    private final SplitEventRepository splitEventRepository;
    private final CustodyEventRepository custodyEventRepository;
    private final AssayEventRepository assayEventRepository;

    public LineageService(SampleRepository sampleRepository,
                          SplitEventRepository splitEventRepository,
                          CustodyEventRepository custodyEventRepository,
                          AssayEventRepository assayEventRepository) {
        this.sampleRepository = sampleRepository;
        this.splitEventRepository = splitEventRepository;
        this.custodyEventRepository = custodyEventRepository;
        this.assayEventRepository = assayEventRepository;
    }

    @Transactional(readOnly = true)
    public SampleView getSample(String externalNo) {
        Sample sample = sampleRepository.findByExternalNo(externalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + externalNo));
        return toView(sample);
    }

    /**
     * 从任意样本出发，查询完整向上祖先链与向下后代树，
     * 并汇总谱系内所有样本的接收/分样/交接/检测事件时间线。
     */
    @Transactional(readOnly = true)
    public LineageView getLineage(String externalNo) {
        Sample origin = sampleRepository.findByExternalNo(externalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + externalNo));

        List<Sample> ancestors = new ArrayList<>();
        Sample cursor = origin.getParent();
        while (cursor != null) {
            ancestors.add(cursor);
            cursor = cursor.getParent();
        }

        List<Sample> descendants = new ArrayList<>();
        List<Sample> frontier = new ArrayList<>();
        frontier.add(origin);
        while (!frontier.isEmpty()) {
            List<Long> parentIds = frontier.stream().map(Sample::getId).toList();
            List<Sample> children = sampleRepository.findByParentIdIn(parentIds);
            descendants.addAll(children);
            frontier = children;
        }

        Map<Long, Sample> lineage = new LinkedHashMap<>();
        lineage.put(origin.getId(), origin);
        ancestors.forEach(sample -> lineage.put(sample.getId(), sample));
        descendants.forEach(sample -> lineage.put(sample.getId(), sample));
        List<Long> sampleIds = new ArrayList<>(lineage.keySet());

        List<TimelineEntry> timeline = new ArrayList<>();

        for (Sample sample : lineage.values()) {
            timeline.add(new TimelineEntry(
                    "RECEIVE",
                    "RECEIVE-" + sample.getExternalNo(),
                    sample.getExternalNo(),
                    "接收矿样，质量 " + sample.getMass().toPlainString()
                            + "，保管方 " + sample.getCustodian(),
                    sample.getCreatedAt()));
        }

        List<SplitEvent> splitEvents =
                splitEventRepository.findByParentSampleIdInOrderByEventTimeAsc(sampleIds);
        Map<Long, List<Sample>> childrenByParent = new LinkedHashMap<>();
        sampleRepository.findByParentIdIn(sampleIds)
                .forEach(child -> childrenByParent
                        .computeIfAbsent(child.getParent().getId(), key -> new ArrayList<>())
                        .add(child));
        for (SplitEvent event : splitEvents) {
            List<Sample> children = childrenByParent
                    .getOrDefault(event.getParentSample().getId(), List.of());
            String childNos = children.stream().map(Sample::getExternalNo)
                    .reduce((a, b) -> a + "," + b).orElse("");
            timeline.add(new TimelineEntry(
                    "SPLIT",
                    event.getEventNo(),
                    event.getParentSample().getExternalNo(),
                    "分样为 " + event.getChildCount() + " 个子样本[" + childNos
                            + "]，子样合计 " + event.getChildMassSum().toPlainString()
                            + "，损耗 " + event.getLossMass().toPlainString(),
                    event.getEventTime()));
        }

        for (CustodyEvent event :
                custodyEventRepository.findBySampleIdInOrderByInitiatedAtAsc(sampleIds)) {
            timeline.add(new TimelineEntry(
                    "CUSTODY",
                    event.getEventNo(),
                    event.getSample().getExternalNo(),
                    "交接 " + event.getFromCustodian() + " -> " + event.getToLab()
                            + "，状态 " + event.getStatus(),
                    event.getInitiatedAt()));
            if (event.getConfirmedAt() != null) {
                timeline.add(new TimelineEntry(
                        "CUSTODY_CONFIRM",
                        event.getEventNo(),
                        event.getSample().getExternalNo(),
                        event.getToLab() + " 确认接收",
                        event.getConfirmedAt()));
            }
        }

        for (AssayEvent event :
                assayEventRepository.findBySampleIdInOrderByEventTimeAsc(sampleIds)) {
            timeline.add(new TimelineEntry(
                    "ASSAY",
                    event.getEventNo(),
                    event.getSample().getExternalNo(),
                    "检测项目 " + event.getItemCode() + " = "
                            + event.getResultValue().toPlainString() + " " + event.getUnit()
                            + "，提交方 " + event.getSubmittedBy(),
                    event.getEventTime()));
        }

        timeline.sort(Comparator.comparing(TimelineEntry::eventTime)
                .thenComparing(TimelineEntry::type)
                .thenComparing(TimelineEntry::eventNo));

        return new LineageView(
                toView(origin),
                ancestors.stream().map(this::toView).toList(),
                descendants.stream().map(this::toView).toList(),
                List.copyOf(timeline));
    }

    private SampleView toView(Sample sample) {
        Sample parent = sample.getParent();
        return new SampleView(
                sample.getExternalNo(),
                sample.getMiningArea(),
                sample.getMass(),
                sample.getCustodian(),
                sample.isLeaf(),
                parent == null ? null : parent.getExternalNo(),
                sample.getCreatedAt());
    }
}
