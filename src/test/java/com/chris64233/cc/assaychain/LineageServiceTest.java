package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.LineageService;
import com.chris64233.cc.assaychain.service.LineageView;
import com.chris64233.cc.assaychain.service.NotFoundException;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.SampleView;
import com.chris64233.cc.assaychain.service.SplitService;
import com.chris64233.cc.assaychain.service.TimelineEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LineageServiceTest {

    @Autowired
    private ReceptionService receptionService;
    @Autowired
    private SplitService splitService;
    @Autowired
    private CustodyService custodyService;
    @Autowired
    private AssayService assayService;
    @Autowired
    private LineageService lineageService;

    private ChildSampleRequest child(String no, String mass, String custodian) {
        return new ChildSampleRequest(no, new BigDecimal(mass), custodian);
    }

    @Test
    void queriesFullAncestorAndDescendantLineageAndTimeline() {
        receptionService.receive("LG-ROOT", "巨龙矿区", new BigDecimal("100.0000"), "地勘院");
        splitService.split("LG-S1", "LG-ROOT", new BigDecimal("10.0000"),
                List.of(child("LG-A", "60.0000", "地勘院"),
                        child("LG-B", "30.0000", "地勘院")));
        custodyService.initiate("LG-C1", "LG-A", "地勘院", "中心实验室");
        custodyService.confirm("LG-C1", "中心实验室");
        assayService.submit(
                "LG-R1", "LG-A", "AU_GRADE", new BigDecimal("3.1"), "g/t", "中心实验室");
        splitService.split("LG-S2", "LG-B", new BigDecimal("5.0000"),
                List.of(child("LG-B1", "12.5000", "地勘院"),
                        child("LG-B2", "12.5000", "地勘院")));

        LineageView fromLeaf = lineageService.getLineage("LG-B2");
        assertThat(fromLeaf.origin().externalNo()).isEqualTo("LG-B2");
        assertThat(fromLeaf.ancestors()).extracting(SampleView::externalNo)
                .containsExactly("LG-B", "LG-ROOT");
        assertThat(fromLeaf.descendants()).isEmpty();

        LineageView fromRoot = lineageService.getLineage("LG-ROOT");
        assertThat(fromRoot.ancestors()).isEmpty();
        assertThat(fromRoot.descendants()).extracting(SampleView::externalNo)
                .containsExactlyInAnyOrder("LG-A", "LG-B", "LG-B1", "LG-B2");

        List<String> timelineTypes = fromRoot.timeline().stream()
                .map(TimelineEntry::type).toList();
        assertThat(timelineTypes).contains("RECEIVE", "SPLIT", "CUSTODY", "CUSTODY_CONFIRM", "ASSAY");

        TimelineEntry splitEntry = fromRoot.timeline().stream()
                .filter(entry -> entry.type().equals("SPLIT") && entry.eventNo().equals("LG-S2"))
                .findFirst().orElseThrow();
        assertThat(splitEntry.summary()).contains("LG-B1", "LG-B2", "损耗 5.0000");

        TimelineEntry assayEntry = fromRoot.timeline().stream()
                .filter(entry -> entry.type().equals("ASSAY")).findFirst().orElseThrow();
        assertThat(assayEntry.sampleExternalNo()).isEqualTo("LG-A");
        assertThat(assayEntry.summary()).contains("AU_GRADE", "中心实验室");

        assertThat(lineageService.getSample("LG-A").custodian()).isEqualTo("中心实验室");
        assertThat(lineageService.getSample("LG-ROOT").leaf()).isFalse();
    }

    @Test
    void unknownSampleIsNotFound() {
        assertThatThrownBy(() -> lineageService.getLineage("MISSING"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void timelineIsChronologicallyOrdered() {
        receptionService.receive("LG-T", "矿区", new BigDecimal("10.0000"), "地勘院");

        LineageView view = lineageService.getLineage("LG-T");
        assertThat(view.timeline()).hasSize(1);
        assertThat(view.timeline()).isSortedAccordingTo(
                java.util.Comparator.comparing(TimelineEntry::eventTime));
    }
}
