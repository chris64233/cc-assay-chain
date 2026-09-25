package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.domain.CustodyEvent;
import com.chris64233.cc.assaychain.domain.CustodyStatus;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import com.chris64233.cc.assaychain.service.BusinessRuleException;
import com.chris64233.cc.assaychain.service.ConflictException;
import com.chris64233.cc.assaychain.service.CustodyService;
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
class CustodyServiceTest {

    @Autowired
    private ReceptionService receptionService;
    @Autowired
    private CustodyService custodyService;
    @Autowired
    private SampleRepository sampleRepository;

    private String seed(String no) {
        receptionService.receive(no, "矿区", new BigDecimal("80.0000"), "地勘院");
        return no;
    }

    @Test
    void initiateThenConfirmUpdatesCustodianAndEvent() {
        seed("CU-01");

        CustodyEvent initiated = custodyService.initiate(
                "EV-CU-01", "CU-01", "地勘院", "中心实验室");
        assertThat(initiated.getStatus()).isEqualTo(CustodyStatus.PENDING);

        Sample pending = sampleRepository.findByExternalNo("CU-01").orElseThrow();
        assertThat(pending.getCustodian()).isEqualTo("地勘院");
        assertThat(pending.getPendingCustodyEventId()).isEqualTo(initiated.getId());

        CustodyEvent confirmed = custodyService.confirm("EV-CU-01", "中心实验室");
        assertThat(confirmed.getStatus()).isEqualTo(CustodyStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(confirmed.getId()).isEqualTo(initiated.getId());

        Sample after = sampleRepository.findByExternalNo("CU-01").orElseThrow();
        assertThat(after.getCustodian()).isEqualTo("中心实验室");
        assertThat(after.getPendingCustodyEventId()).isNull();
    }

    @Test
    void onlyCurrentCustodianMayInitiate() {
        seed("CU-02");

        assertThatThrownBy(() -> custodyService.initiate(
                "EV-CU-02", "CU-02", "第三方机构", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("只有当前保管方");
    }

    @Test
    void onlyDesignatedLabMayConfirm() {
        seed("CU-03");
        custodyService.initiate("EV-CU-03", "CU-03", "地勘院", "中心实验室");

        assertThatThrownBy(() -> custodyService.confirm("EV-CU-03", "其他实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("只有指定接收实验室");

        assertThat(sampleRepository.findByExternalNo("CU-03").orElseThrow().getCustodian())
                .isEqualTo("地勘院");
    }

    @Test
    void cannotInitiateSecondPendingHandover() {
        seed("CU-04");
        custodyService.initiate("EV-CU-04", "CU-04", "地勘院", "中心实验室");

        assertThatThrownBy(() -> custodyService.initiate(
                "EV-CU-04-B", "CU-04", "地勘院", "南方实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("待确认交接");
    }

    @Test
    void confirmIsIdempotentForSameLab() {
        seed("CU-05");
        custodyService.initiate("EV-CU-05", "CU-05", "地勘院", "中心实验室");

        CustodyEvent first = custodyService.confirm("EV-CU-05", "中心实验室");
        CustodyEvent second = custodyService.confirm("EV-CU-05", "中心实验室");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(CustodyStatus.CONFIRMED);
    }

    @Test
    void confirmConflictsWhenReplayedByDifferentLab() {
        seed("CU-06");
        custodyService.initiate("EV-CU-06", "CU-06", "地勘院", "中心实验室");
        custodyService.confirm("EV-CU-06", "中心实验室");

        assertThatThrownBy(() -> custodyService.confirm("EV-CU-06", "其他实验室"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void initiateIdempotentSameContentAndConflictOnDifference() {
        seed("CU-07");
        CustodyEvent first = custodyService.initiate(
                "EV-CU-07", "CU-07", "地勘院", "中心实验室");
        CustodyEvent replay = custodyService.initiate(
                "EV-CU-07", "CU-07", "地勘院", "中心实验室");
        assertThat(replay.getId()).isEqualTo(first.getId());

        assertThatThrownBy(() -> custodyService.initiate(
                "EV-CU-07", "CU-07", "地勘院", "南方实验室"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");
    }

    @Test
    void unknownEventNoOnConfirmIsNotFound() {
        assertThatThrownBy(() -> custodyService.confirm("NOPE", "中心实验室"))
                .isInstanceOf(com.chris64233.cc.assaychain.service.NotFoundException.class);
    }

    @Test
    void confirmedCustodyAllowsNextHandoverByNewLab() {
        seed("CU-08");
        custodyService.initiate("EV-CU-08", "CU-08", "地勘院", "中心实验室");
        custodyService.confirm("EV-CU-08", "中心实验室");

        CustodyEvent second = custodyService.initiate(
                "EV-CU-08-B", "CU-08", "中心实验室", "南方实验室");
        custodyService.confirm("EV-CU-08-B", "南方实验室");

        assertThat(second.getFromCustodian()).isEqualTo("中心实验室");
        assertThat(sampleRepository.findByExternalNo("CU-08").orElseThrow().getCustodian())
                .isEqualTo("南方实验室");
    }
}
