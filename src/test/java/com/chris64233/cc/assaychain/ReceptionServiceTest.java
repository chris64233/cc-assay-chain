package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import com.chris64233.cc.assaychain.service.BusinessRuleException;
import com.chris64233.cc.assaychain.service.ConflictException;
import com.chris64233.cc.assaychain.service.ReceptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ReceptionServiceTest {

    @Autowired
    private ReceptionService receptionService;

    @Autowired
    private SampleRepository sampleRepository;

    @Test
    void receiveRecordsAllFieldsWithFixedScale() {
        Sample sample = receptionService.receive(
                "EXT-001", "甲玛矿区", new BigDecimal("120.5"), "地勘院");

        Sample loaded = sampleRepository.findByExternalNo("EXT-001").orElseThrow();
        assertThat(loaded.getMiningArea()).isEqualTo("甲玛矿区");
        assertThat(loaded.getMass()).isEqualByComparingTo("120.5000");
        assertThat(loaded.getMass().scale()).isEqualTo(4);
        assertThat(loaded.getCustodian()).isEqualTo("地勘院");
        assertThat(loaded.isLeaf()).isTrue();
        assertThat(loaded.getParent()).isNull();
        assertThat(sample.getId()).isNotNull();
    }

    @Test
    void rejectsNonPositiveMass() {
        assertThatThrownBy(() ->
                receptionService.receive("EXT-002", "矿区", BigDecimal.ZERO, "地勘院"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("必须为正数");

        assertThatThrownBy(() ->
                receptionService.receive("EXT-003", "矿区", new BigDecimal("-1.0"), "地勘院"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("必须为正数");
    }

    @Test
    void rejectsMassBeyondFixedPrecision() {
        assertThatThrownBy(() -> receptionService.receive(
                "EXT-004", "矿区", new BigDecimal("1.00001"), "地勘院"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("精度不能超过");
    }

    @Test
    void rejectsBlankFields() {
        assertThatThrownBy(() -> receptionService.receive(
                "  ", "矿区", new BigDecimal("1.0"), "地勘院"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> receptionService.receive(
                "EXT-005", "矿区", new BigDecimal("1.0"), " "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void duplicateExternalNoConflicts() {
        receptionService.receive("EXT-DUP", "矿区", new BigDecimal("10.0000"), "地勘院");

        assertThatThrownBy(() -> receptionService.receive(
                "EXT-DUP", "其他矿区", new BigDecimal("20.0000"), "中心实验室"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("外部样本号已存在");
    }
}
