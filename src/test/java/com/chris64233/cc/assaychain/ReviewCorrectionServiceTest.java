package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.domain.ApprovalDecision;
import com.chris64233.cc.assaychain.domain.ApprovalEvent;
import com.chris64233.cc.assaychain.domain.ApprovalKind;
import com.chris64233.cc.assaychain.domain.AssayCorrection;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.AssayStatus;
import com.chris64233.cc.assaychain.domain.CorrectionStatus;
import com.chris64233.cc.assaychain.repo.ApprovalEventRepository;
import com.chris64233.cc.assaychain.repo.AssayCorrectionRepository;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.BusinessRuleException;
import com.chris64233.cc.assaychain.service.ConflictException;
import com.chris64233.cc.assaychain.service.CurrentResultView;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.ResultHistoryView;
import com.chris64233.cc.assaychain.service.ResultQueryService;
import com.chris64233.cc.assaychain.service.ReviewService;
import com.chris64233.cc.assaychain.service.SplitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ReviewCorrectionServiceTest {

    @Autowired
    private ReceptionService receptionService;
    @Autowired
    private SplitService splitService;
    @Autowired
    private CustodyService custodyService;
    @Autowired
    private AssayService assayService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ResultQueryService resultQueryService;
    @Autowired
    private AssayEventRepository assayEventRepository;
    @Autowired
    private AssayCorrectionRepository correctionRepository;
    @Autowired
    private ApprovalEventRepository approvalEventRepository;
    @Autowired
    private SampleRepository sampleRepository;

    private void effectiveResult(String sampleNo, String eventNo, String value, String unit) {
        receptionService.receive(sampleNo, "矿区", new BigDecimal("40.0000"), "地勘院");
        custodyService.initiate(eventNo + "-I", sampleNo, "地勘院", "中心实验室");
        custodyService.confirm(eventNo + "-I", "中心实验室");
        assayService.submit(eventNo, sampleNo, "AU_GRADE",
                new BigDecimal(value), unit, "中心实验室");
        reviewService.reviewSubmission(
                "AP-" + eventNo, eventNo, ApprovalDecision.APPROVE, "质量负责人", null);
    }

    @Test
    void onlyEffectiveResultCanBeCorrected() {
        effectiveResult("RC-01", "R-01", "2.35", "g/t");

        AssayCorrection correction = reviewService.requestCorrection(
                "CR-01", "R-01", new BigDecimal("3.25"), "g/t",
                "仪器标定错误", "复检报告 LAB-2026-001", "中心实验室");

        assertThat(correction.getOldValue()).isEqualByComparingTo("2.350000");
        assertThat(correction.getNewValue()).isEqualByComparingTo("3.250000");
        assertThat(correction.getStatus()).isEqualTo(CorrectionStatus.PENDING);
    }

    @Test
    void correctionRequiresReasonEvidenceAndChangedContent() {
        effectiveResult("RC-02", "R-02", "2.35", "g/t");

        assertThatThrownBy(() -> reviewService.requestCorrection(
                "CR-02-BAD", "R-02", new BigDecimal("2.35"), "g/t",
                "仪器错误", "报告 X", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("一致，无需更正");

        assertThatThrownBy(() -> reviewService.requestCorrection(
                "CR-02-BAD2", "R-02", new BigDecimal("2.50"), "g/t",
                "  ", "报告 X", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("更正原因");
    }

    @Test
    void cannotCorrectPendingOrRejectedResult() {
        receptionService.receive("RC-03", "矿区", new BigDecimal("40.0000"), "中心实验室");
        assayService.submit("R-03", "RC-03", "AU_GRADE",
                new BigDecimal("2.35"), "g/t", "中心实验室");

        assertThatThrownBy(() -> reviewService.requestCorrection(
                "CR-03", "R-03", new BigDecimal("3.0"), "g/t",
                "录入错误", "报告", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("只有生效结果可以发起更正");
    }

    @Test
    void approvedCorrectionCreatesNewVersionAndSupersedesOld() {
        effectiveResult("RC-04", "R-04", "2.35", "g/t");
        reviewService.requestCorrection(
                "CR-04", "R-04", new BigDecimal("3.25"), "g/t",
                "单位换算录入错误", "复检报告 LAB-2026-004", "中心实验室");

        ApprovalEvent approval = reviewService.decideCorrection(
                "AP-CR-04", "CR-04", ApprovalDecision.APPROVE, "技术负责人", null);

        assertThat(approval.getKind()).isEqualTo(ApprovalKind.CORRECTION_DECISION);
        assertThat(approval.getNewEventNo()).isEqualTo("COR-CR-04");

        AssayEvent old = assayEventRepository.findByEventNo("R-04").orElseThrow();
        AssayEvent newVersion = assayEventRepository.findByEventNo("COR-CR-04").orElseThrow();
        assertThat(old.getStatus()).isEqualTo(AssayStatus.SUPERSEDED);
        assertThat(newVersion.getStatus()).isEqualTo(AssayStatus.EFFECTIVE);
        assertThat(newVersion.getVersionNo()).isEqualTo(2);
        assertThat(newVersion.getResultValue()).isEqualByComparingTo("3.250000");
        assertThat(newVersion.getReviewedBy()).isEqualTo("技术负责人");

        CurrentResultView effective = resultQueryService.getEffective("RC-04", "AU_GRADE");
        assertThat(effective.eventNo()).isEqualTo("COR-CR-04");
        assertThat(effective.versionNo()).isEqualTo(2);

        AssayCorrection correction = correctionRepository.findByCorrectionNo("CR-04").orElseThrow();
        assertThat(correction.getStatus()).isEqualTo(CorrectionStatus.APPROVED);
    }

    @Test
    void rejectedCorrectionKeepsOriginalEffective() {
        effectiveResult("RC-05", "R-05", "2.35", "g/t");
        reviewService.requestCorrection(
                "CR-05", "R-05", new BigDecimal("9.99"), "g/t",
                "疑似仪器漂移", "证据不足", "中心实验室");

        reviewService.decideCorrection(
                "AP-CR-05", "CR-05", ApprovalDecision.REJECT, "技术负责人", "证据不充分");

        assertThat(assayEventRepository.findByEventNo("R-05").orElseThrow().getStatus())
                .isEqualTo(AssayStatus.EFFECTIVE);
        assertThat(correctionRepository.findByCorrectionNo("CR-05").orElseThrow().getStatus())
                .isEqualTo(CorrectionStatus.REJECTED);
        assertThat(resultQueryService.getEffective("RC-05", "AU_GRADE").eventNo())
                .isEqualTo("R-05");
    }

    @Test
    void correctionApproverMustDifferFromRequester() {
        effectiveResult("RC-06", "R-06", "2.35", "g/t");
        reviewService.requestCorrection(
                "CR-06", "R-06", new BigDecimal("3.1"), "g/t",
                "录入错误", "报告", "中心实验室");

        assertThatThrownBy(() -> reviewService.decideCorrection(
                "AP-CR-06", "CR-06", ApprovalDecision.APPROVE, "中心实验室", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("审批人必须与更正申请人不同");
    }

    @Test
    void approvalBasedOnStaleVersionCannotOverwriteLaterVersion() {
        effectiveResult("RC-07", "R-07", "2.35", "g/t");
        // 两份针对同一当前生效版本的更正申请（并发窗口的等价情形）
        reviewService.requestCorrection(
                "CR-07-A", "R-07", new BigDecimal("3.25"), "g/t",
                "仪器错误", "证据 A", "中心实验室");
        // 第二份不能直接对同一版本创建（已有待审批更正），先驳回第一份再申请第二份
        reviewService.decideCorrection(
                "AP-CR-07-A", "CR-07-A", ApprovalDecision.REJECT, "技术负责人", "驳回");

        reviewService.requestCorrection(
                "CR-07-B", "R-07", new BigDecimal("4.00"), "g/t",
                "单位错误", "证据 B", "中心实验室");
        reviewService.decideCorrection(
                "AP-CR-07-B", "CR-07-B", ApprovalDecision.APPROVE, "技术负责人", null);
        assertThat(resultQueryService.getEffective("RC-07", "AU_GRADE").eventNo())
                .isEqualTo("COR-CR-07-B");

        // 对已被取代的原版本再次创建更正必须失败，无法覆盖新版本
        assertThatThrownBy(() -> reviewService.requestCorrection(
                "CR-07-C", "R-07", new BigDecimal("5.0"), "g/t",
                "录入错误", "证据 C", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("只有生效结果可以发起更正");
    }

    @Test
    void versionChainAndHistoryAsOfAreQueryable() {
        effectiveResult("RC-08", "R-08", "2.35", "g/t");
        Instant afterV1 = Instant.now();
        reviewService.requestCorrection(
                "CR-08", "R-08", new BigDecimal("3.25"), "g/t",
                "仪器标定错误", "复检报告 LAB-2026-008", "中心实验室");
        reviewService.decideCorrection(
                "AP-CR-08", "CR-08", ApprovalDecision.APPROVE, "技术负责人", null);
        Instant afterV2 = Instant.now();

        ResultHistoryView history = resultQueryService.getHistory("RC-08", "AU_GRADE", null);
        assertThat(history.versions()).extracting(version -> version.eventNo() + version.status())
                .containsExactly("R-08SUPERSEDED", "COR-CR-08EFFECTIVE");
        assertThat(history.effective().eventNo()).isEqualTo("COR-CR-08");
        assertThat(history.reviews()).extracting("approvalNo")
                .containsExactly("AP-R-08", "AP-CR-08");
        assertThat(history.versions()).filteredOn(version -> version.versionNo() == 2)
                .singleElement()
                .extracting(version -> version.sourceCorrectionNo())
                .isEqualTo("CR-08");

        ResultHistoryView asOfV1 = resultQueryService.getHistory(
                "RC-08", "AU_GRADE", afterV1);
        assertThat(asOfV1.effectiveEventNoAsOf()).isEqualTo("R-08");
        ResultHistoryView asOfV2 = resultQueryService.getHistory(
                "RC-08", "AU_GRADE", afterV2);
        assertThat(asOfV2.effectiveEventNoAsOf()).isEqualTo("COR-CR-08");
        ResultHistoryView beforeAll = resultQueryService.getHistory(
                "RC-08", "AU_GRADE", Instant.parse("2000-01-01T00:00:00Z"));
        assertThat(beforeAll.effectiveEventNoAsOf()).isNull();
    }

    @Test
    void reviewAndCorrectionNumbersAreIdempotentAndConflictOnDifference() {
        effectiveResult("RC-09", "R-09", "2.35", "g/t");

        ApprovalEvent replay = reviewService.reviewSubmission(
                "AP-R-09", "R-09", ApprovalDecision.APPROVE, "质量负责人", null);
        assertThat(replay.getId())
                .isEqualTo(approvalEventRepository.findByApprovalNo("AP-R-09").orElseThrow().getId());
        assertThatThrownBy(() -> reviewService.reviewSubmission(
                "AP-R-09", "R-09", ApprovalDecision.REJECT, "质量负责人", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");

        reviewService.requestCorrection(
                "CR-09", "R-09", new BigDecimal("3.25"), "g/t",
                "录入错误", "报告", "中心实验室");
        AssayCorrection replayedCorrection = reviewService.requestCorrection(
                "CR-09", "R-09", new BigDecimal("3.250000"), "g/t",
                "录入错误", "报告", "中心实验室");
        assertThat(replayedCorrection.getCorrectionNo()).isEqualTo("CR-09");
        assertThatThrownBy(() -> reviewService.requestCorrection(
                "CR-09", "R-09", new BigDecimal("3.26"), "g/t",
                "录入错误", "报告", "中心实验室"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");

        ApprovalEvent decisionReplay = reviewService.decideCorrection(
                "AP-CR-09", "CR-09", ApprovalDecision.APPROVE, "技术负责人", null);
        assertThat(decisionReplay.getId()).isEqualTo(
                approvalEventRepository.findByApprovalNo("AP-CR-09").orElseThrow().getId());
        assertThatThrownBy(() -> reviewService.decideCorrection(
                "AP-CR-09", "CR-09", ApprovalDecision.APPROVE, "其他负责人", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");
    }

    @Test
    void decisionOnSplitSampleOrDuringPendingCustodyIsRejected() {
        effectiveResult("RC-10", "R-10", "2.35", "g/t");
        splitService.split("RC-10-S", "RC-10", new BigDecimal("0.0000"),
                List.of(new ChildSampleRequest("RC-10-A", new BigDecimal("20.0000"), "中心实验室"),
                        new ChildSampleRequest("RC-10-B", new BigDecimal("20.0000"), "中心实验室")));

        assertThatThrownBy(() -> reviewService.requestCorrection(
                "CR-10", "R-10", new BigDecimal("3.0"), "g/t",
                "录入错误", "报告", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("非叶子样本");

        // 待复核结果在保管链不完整（存在待确认交接）时不能被复核
        receptionService.receive("RC-11", "矿区", new BigDecimal("40.0000"), "地勘院");
        custodyService.initiate("RC-11-CI", "RC-11", "地勘院", "中心实验室");
        custodyService.confirm("RC-11-CI", "中心实验室");
        assayService.submit("R-11", "RC-11", "AU_GRADE",
                new BigDecimal("1.0"), "g/t", "中心实验室");
        custodyService.initiate("RC-11-CO", "RC-11", "中心实验室", "南方实验室");

        assertThatThrownBy(() -> reviewService.reviewSubmission(
                "AP-R-11", "R-11", ApprovalDecision.APPROVE, "质量负责人", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("保管链不完整");
    }
}
