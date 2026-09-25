package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
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
class AssayServiceTest {

    @Autowired
    private ReceptionService receptionService;
    @Autowired
    private CustodyService custodyService;
    @Autowired
    private AssayService assayService;
    @Autowired
    private SplitService splitService;
    @Autowired
    private AssayEventRepository assayEventRepository;
    @Autowired
    private SampleRepository sampleRepository;

    private Long sampleId(String externalNo) {
        return sampleRepository.findByExternalNo(externalNo).orElseThrow().getId();
    }

    private void handToLab(String sampleNo, String eventNo, String lab) {
        receptionService.receive(sampleNo, "矿区", new BigDecimal("40.0000"), "地勘院");
        custodyService.initiate(eventNo + "-I", sampleNo, "地勘院", lab);
        custodyService.confirm(eventNo + "-I", lab);
    }

    @Test
    void onlyCurrentHoldingLabSubmitsResult() {
        handToLab("AS-01", "EV-AS-01", "中心实验室");

        AssayEvent event = assayService.submit(
                "EV-AS-01", "AS-01", "AU_GRADE", new BigDecimal("2.35"), "g/t", "中心实验室");

        assertThat(event.getResultValue().scale()).isEqualTo(6);
        assertThat(event.getSubmittedBy()).isEqualTo("中心实验室");
        assertThat(assayEventRepository.findBySampleIdAndItemCode(
                sampleId("AS-01"), "AU_GRADE")).isPresent();
    }

    @Test
    void nonHolderCannotSubmit() {
        handToLab("AS-02", "EV-AS-02", "中心实验室");

        assertThatThrownBy(() -> assayService.submit(
                "EV-AS-02-X", "AS-02", "AU_GRADE", new BigDecimal("2.35"), "g/t", "南方实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("只有当前持有样本的实验室");
    }

    @Test
    void oneValidResultPerItemAndResultCannotBeOverwritten() {
        handToLab("AS-03", "EV-AS-03", "中心实验室");
        assayService.submit(
                "EV-AS-03", "AS-03", "AU_GRADE", new BigDecimal("2.35"), "g/t", "中心实验室");

        assertThatThrownBy(() -> assayService.submit(
                "EV-AS-03-2", "AS-03", "AU_GRADE", new BigDecimal("9.99"), "g/t", "中心实验室"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已有有效结果");

        AssayEvent persisted = assayEventRepository.findAll().getFirst();
        assertThat(persisted.getResultValue()).isEqualByComparingTo("2.350000");
    }

    @Test
    void differentItemsCanEachHaveOneResult() {
        handToLab("AS-04", "EV-AS-04", "中心实验室");
        assayService.submit(
                "EV-AS-04-A", "AS-04", "AU_GRADE", new BigDecimal("2.35"), "g/t", "中心实验室");
        assayService.submit(
                "EV-AS-04-B", "AS-04", "CU_GRADE", new BigDecimal("0.12"), "%", "中心实验室");

        assertThat(assayEventRepository.findBySampleIdAndItemCode(
                sampleId("AS-04"), "AU_GRADE")).isPresent();
        assertThat(assayEventRepository.findBySampleIdAndItemCode(
                sampleId("AS-04"), "CU_GRADE")).isPresent();
    }

    @Test
    void submitIdempotentSameContentConflictOnDifference() {
        handToLab("AS-05", "EV-AS-05", "中心实验室");
        AssayEvent first = assayService.submit(
                "EV-AS-05", "AS-05", "AU_GRADE", new BigDecimal("2.35"), "g/t", "中心实验室");
        AssayEvent replay = assayService.submit(
                "EV-AS-05", "AS-05", "AU_GRADE", new BigDecimal("2.350000"), "g/t", "中心实验室");
        assertThat(replay.getId()).isEqualTo(first.getId());

        assertThatThrownBy(() -> assayService.submit(
                "EV-AS-05", "AS-05", "AU_GRADE", new BigDecimal("2.36"), "g/t", "中心实验室"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");
    }

    @Test
    void cannotSubmitForSplitParent() {
        handToLab("AS-06", "EV-AS-06", "中心实验室");
        splitService.split("EV-AS-06-S", "AS-06", new BigDecimal("0.0000"),
                List.of(new ChildSampleRequest("AS-06-A", new BigDecimal("20.0000"), "中心实验室"),
                        new ChildSampleRequest("AS-06-B", new BigDecimal("20.0000"), "中心实验室")));

        assertThatThrownBy(() -> assayService.submit(
                "EV-AS-06-X", "AS-06", "AU_GRADE", new BigDecimal("1.0"), "g/t", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已分样");
    }
}
