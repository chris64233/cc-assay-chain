package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.domain.SplitEvent;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.BusinessRuleException;
import com.chris64233.cc.assaychain.service.ConflictException;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.SplitService;
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
class SplitServiceTest {

    @Autowired
    private ReceptionService receptionService;
    @Autowired
    private SplitService splitService;
    @Autowired
    private CustodyService custodyService;
    @Autowired
    private AssayService assayService;
    @Autowired
    private SampleRepository sampleRepository;

    private Sample seedParent(String no, String mass) {
        return receptionService.receive(no, "矿区", new BigDecimal(mass), "地勘院");
    }

    private ChildSampleRequest child(String no, String mass) {
        return new ChildSampleRequest(no, new BigDecimal(mass), "地勘院");
    }

    @Test
    void splitConservesMassAndMarksParentNonLeaf() {
        seedParent("SP-01", "100.0000");

        SplitEvent event = splitService.split("EV-SP-01", "SP-01", new BigDecimal("10.0000"),
                List.of(child("SP-01-A", "60.0000"), child("SP-01-B", "30.0000")));

        assertThat(event.getLossMass()).isEqualByComparingTo("10.0000");
        assertThat(event.getChildMassSum()).isEqualByComparingTo("90.0000");

        Sample parent = sampleRepository.findByExternalNo("SP-01").orElseThrow();
        assertThat(parent.isLeaf()).isFalse();
        Sample childA = sampleRepository.findByExternalNo("SP-01-A").orElseThrow();
        Sample childB = sampleRepository.findByExternalNo("SP-01-B").orElseThrow();
        assertThat(childA.getParent().getId()).isEqualTo(parent.getId());
        assertThat(childB.getParent().getId()).isEqualTo(parent.getId());
        assertThat(childA.getMiningArea()).isEqualTo("矿区");
    }

    @Test
    void splitAcceptsRoundingAtExactToleranceBoundary() {
        seedParent("SP-02", "100.0000");

        SplitEvent event = splitService.split("EV-SP-02", "SP-02", new BigDecimal("10.0001"),
                List.of(child("SP-02-A", "60.0000"), child("SP-02-B", "30.0000")));

        assertThat(event.getLossMass()).isEqualByComparingTo("10.0000");
        assertThat(event.getDeclaredLossMass()).isEqualByComparingTo("10.0001");
    }

    @Test
    void splitRejectsDeviationBeyondTolerance() {
        seedParent("SP-03", "100.0000");

        assertThatThrownBy(() -> splitService.split(
                "EV-SP-03", "SP-03", new BigDecimal("10.0002"),
                List.of(child("SP-03-A", "60.0000"), child("SP-03-B", "30.0000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("质量不守恒");

        assertThat(sampleRepository.existsByExternalNo("SP-03-A")).isFalse();
        assertThat(sampleRepository.findByExternalNo("SP-03").orElseThrow().isLeaf()).isTrue();
    }

    @Test
    void splitRejectsChildSumExceedingParent() {
        seedParent("SP-04", "50.0000");

        assertThatThrownBy(() -> splitService.split(
                "EV-SP-04", "SP-04", new BigDecimal("0.0000"),
                List.of(child("SP-04-A", "30.0000"), child("SP-04-B", "21.0000"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void splitRequiresAtLeastTwoChildren() {
        seedParent("SP-05", "10.0000");

        assertThatThrownBy(() -> splitService.split(
                "EV-SP-05", "SP-05", BigDecimal.ZERO,
                List.of(child("SP-05-A", "10.0000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("至少产生 2 个子样本");
    }

    @Test
    void splitParentCannotBeSplitAgain() {
        seedParent("SP-06", "100.0000");
        splitService.split("EV-SP-06", "SP-06", new BigDecimal("10.0000"),
                List.of(child("SP-06-A", "60.0000"), child("SP-06-B", "30.0000")));

        assertThatThrownBy(() -> splitService.split(
                "EV-SP-06-2", "SP-06", new BigDecimal("0.0000"),
                List.of(child("SP-06-C", "50.0000"), child("SP-06-D", "50.0000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已分样");
    }

    @Test
    void splitParentCannotHandOverOrBeAssayed() {
        seedParent("SP-07", "100.0000");
        splitService.split("EV-SP-07", "SP-07", new BigDecimal("10.0000"),
                List.of(child("SP-07-A", "60.0000"), child("SP-07-B", "30.0000")));

        assertThatThrownBy(() -> custodyService.initiate(
                "EV-SP-07-CUST", "SP-07", "地勘院", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已分样");

        assertThatThrownBy(() -> assayService.submit(
                "EV-SP-07-ASSAY", "SP-07", "AU", new BigDecimal("1.0"), "g/t", "地勘院"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已分样");
    }

    @Test
    void splitRejectsDuplicateChildExternalNoAndLeavesNoPartialLineage() {
        seedParent("SP-08", "100.0000");

        assertThatThrownBy(() -> splitService.split(
                "EV-SP-08", "SP-08", new BigDecimal("10.0000"),
                List.of(child("SP-08-DUP", "45.0000"), child("SP-08-DUP", "45.0000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("子样本外部样本号重复");

        assertThat(sampleRepository.existsByExternalNo("SP-08-DUP")).isFalse();
    }

    @Test
    void splitIdempotentForSameEventAndContent() {
        seedParent("SP-09", "100.0000");
        var children = List.of(child("SP-09-A", "60.0000"), child("SP-09-B", "30.0000"));

        SplitEvent first = splitService.split("EV-SP-09", "SP-09", new BigDecimal("10.0000"), children);
        SplitEvent replay = splitService.split("EV-SP-09", "SP-09", new BigDecimal("10.0000"),
                List.of(child("SP-09-A", "60.0000"), child("SP-09-B", "30.0000")));

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(sampleRepository.findByParentId(
                sampleRepository.findByExternalNo("SP-09").orElseThrow().getId())).hasSize(2);
    }

    @Test
    void splitConflictsWhenSameEventNoDifferentContent() {
        seedParent("SP-10", "100.0000");
        splitService.split("EV-SP-10", "SP-10", new BigDecimal("10.0000"),
                List.of(child("SP-10-A", "60.0000"), child("SP-10-B", "30.0000")));

        assertThatThrownBy(() -> splitService.split(
                "EV-SP-10", "SP-10", new BigDecimal("10.0000"),
                List.of(child("SP-10-A", "50.0000"), child("SP-10-B", "40.0000"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");
    }
}
