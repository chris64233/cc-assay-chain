package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.CorrectionRequest;
import com.chris64233.cc.assaychain.domain.CorrectionStatus;
import com.chris64233.cc.assaychain.domain.ResultStatus;
import com.chris64233.cc.assaychain.domain.ReviewDecision;
import com.chris64233.cc.assaychain.domain.ReviewEvent;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.BusinessRuleException;
import com.chris64233.cc.assaychain.service.ConflictException;
import com.chris64233.cc.assaychain.service.CorrectionService;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.ReviewService;
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
class CorrectionServiceTest {

    @Autowired
    private ReceptionService receptionService;
    @Autowired
    private CustodyService custodyService;
    @Autowired
    private AssayService assayService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private CorrectionService correctionService;
    @Autowired
    private SplitService splitService;
    @Autowired
    private AssayEventRepository assayEventRepository;

    private void prepareEffective(String sampleNo, String resultNo, String value, String unit) {
        receptionService.receive(sampleNo, "矿区", new BigDecimal("40.0000"), "地勘院");
        custodyService.initiate(resultNo + "-I", sampleNo, "地勘院", "中心实验室");
        custodyService.confirm(resultNo + "-I", "中心实验室");
        assayService.submit(resultNo, sampleNo, "AU_GRADE",
                new BigDecimal(value), unit, "中心实验室");
        reviewService.review(resultNo + "-RV", resultNo, "质量主管",
                ReviewDecision.APPROVED, null);
    }

    @Test
    void correctionApprovalCreatesNewVersionAndSupersedesOld() {
        prepareEffective("CR-01", "R-CR-01", "2.35", "g/t");

        CorrectionRequest correction = correctionService.request(
                "CO-CR-01", "R-CR-01", "R-CR-01-V2", new BigDecimal("3.25"), "g/t",
                "仪器校准错误", "校准记录 CERT-77", "中心实验室");
        assertThat(correction.getStatus()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(correction.getOldValue()).isEqualByComparingTo("2.350000");
        assertThat(correction.getOldUnit()).isEqualTo("g/t");

        ReviewEvent review = correctionService.decide(
                "AP-CR-01", "CO-CR-01", "技术负责人", ReviewDecision.APPROVED, "同意更正");

        AssayEvent v1 = assayEventRepository.findByEventNo("R-CR-01").orElseThrow();
        AssayEvent v2 = assayEventRepository.findByEventNo("R-CR-01-V2").orElseThrow();
        assertThat(v1.getStatus()).isEqualTo(ResultStatus.SUPERSEDED);
        assertThat(v1.getSupersededAt()).isNotNull();
        assertThat(v2.getStatus()).isEqualTo(ResultStatus.EFFECTIVE);
        assertThat(v2.getVersionNo()).isEqualTo(2);
        assertThat(v2.getPrevVersion().getId()).isEqualTo(v1.getId());
        assertThat(v2.getResultValue()).isEqualByComparingTo("3.250000");
        assertThat(v2.getEffectiveAt()).isEqualTo(v1.getSupersededAt());
        assertThat(review.getResultVersion().getId()).isEqualTo(v2.getId());
        assertThat(review.getCorrectionRequest().getId()).isEqualTo(correction.getId());

        // 唯一生效版本
        assertThat(assayEventRepository
                .findBySampleIdAndItemCodeAndStatus(v1.getSample().getId(), "AU_GRADE",
                        ResultStatus.EFFECTIVE)
                .orElseThrow().getId()).isEqualTo(v2.getId());
    }

    @Test
    void rejectKeepsOldResultEffective() {
        prepareEffective("CR-02", "R-CR-02", "2.35", "g/t");
        correctionService.request(
                "CO-CR-02", "R-CR-02", "R-CR-02-V2", new BigDecimal("9.99"), "g/t",
                "录入错误", "工单 WO-2", "中心实验室");

        correctionService.decide(
                "AP-CR-02", "CO-CR-02", "技术负责人", ReviewDecision.REJECTED, "证据不足");

        assertThat(assayEventRepository.findByEventNo("R-CR-02").orElseThrow().getStatus())
                .isEqualTo(ResultStatus.EFFECTIVE);
        assertThat(assayEventRepository.findAll()).hasSize(1);
    }

    @Test
    void cannotCorrectPendingResult() {
        receptionService.receive("CR-03", "矿区", new BigDecimal("40.0000"), "中心实验室");
        assayService.submit("R-CR-03", "CR-03", "AU_GRADE",
                new BigDecimal("2.35"), "g/t", "中心实验室");

        assertThatThrownBy(() -> correctionService.request(
                "CO-CR-03", "R-CR-03", "R-CR-03-V2", new BigDecimal("3.0"), "g/t",
                "录入错误", "证据", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("当前生效");
    }

    @Test
    void cannotCorrectWithIdenticalContent() {
        prepareEffective("CR-04", "R-CR-04", "2.35", "g/t");

        assertThatThrownBy(() -> correctionService.request(
                "CO-CR-04", "R-CR-04", "R-CR-04-V2", new BigDecimal("2.35"), "g/t",
                "无变化", "证据", "中心实验室"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("完全相同");
    }

    @Test
    void onlyOnePendingCorrectionAtATime() {
        prepareEffective("CR-05", "R-CR-05", "2.35", "g/t");
        correctionService.request(
                "CO-CR-05", "R-CR-05", "R-CR-05-V2", new BigDecimal("3.1"), "g/t",
                "仪器错误", "证据1", "中心实验室");

        assertThatThrownBy(() -> correctionService.request(
                "CO-CR-05-B", "R-CR-05", "R-CR-05-V2B", new BigDecimal("3.2"), "g/t",
                "单位错误", "证据2", "中心实验室"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("待审批");
    }

    @Test
    void approverMustDifferFromSubmitterAndRequester() {
        prepareEffective("CR-06", "R-CR-06", "2.35", "g/t");
        correctionService.request(
                "CO-CR-06", "R-CR-06", "R-CR-06-V2", new BigDecimal("3.25"), "g/t",
                "仪器错误", "证据", "中心实验室");

        assertThatThrownBy(() -> correctionService.decide(
                "AP-CR-06", "CO-CR-06", "中心实验室", ReviewDecision.APPROVED, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("必须与原结果提交人不同");
    }

    @Test
    void cannotApproveCorrectionAfterSampleSplit() {
        prepareEffective("CR-07", "R-CR-07", "2.35", "g/t");
        correctionService.request(
                "CO-CR-07", "R-CR-07", "R-CR-07-V2", new BigDecimal("3.25"), "g/t",
                "仪器错误", "证据", "中心实验室");
        splitService.split("SP-CR-07", "CR-07", new BigDecimal("0.0000"),
                List.of(new ChildSampleRequest("CR-07-A", new BigDecimal("20.0000"), "中心实验室"),
                        new ChildSampleRequest("CR-07-B", new BigDecimal("20.0000"), "中心实验室")));

        assertThatThrownBy(() -> correctionService.decide(
                "AP-CR-07", "CO-CR-07", "技术负责人", ReviewDecision.APPROVED, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("叶子");
        // 原结果仍生效，申请仍待审批（未产生孤立新版本）
        assertThat(assayEventRepository.findByEventNo("R-CR-07").orElseThrow().getStatus())
                .isEqualTo(ResultStatus.EFFECTIVE);
    }

    @Test
    void correctionChainsMultipleVersions() {
        prepareEffective("CR-08", "R-CR-08", "2.35", "g/t");
        correctionService.request("CO-CR-08-1", "R-CR-08", "R-CR-08-V2",
                new BigDecimal("3.25"), "g/t", "仪器错误", "证据1", "中心实验室");
        correctionService.decide("AP-CR-08-1", "CO-CR-08-1", "技术负责人",
                ReviewDecision.APPROVED, null);

        // 基于当前生效版本 v2 继续更正（改单位）
        correctionService.request("CO-CR-08-2", "R-CR-08-V2", "R-CR-08-V3",
                new BigDecimal("3250"), "mg/kg", "单位错误", "证据2", "中心实验室");
        correctionService.decide("AP-CR-08-2", "CO-CR-08-2", "技术负责人",
                ReviewDecision.APPROVED, null);

        List<AssayEvent> chain = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(
                        assayEventRepository.findByEventNo("R-CR-08").orElseThrow().getSample().getId(),
                        "AU_GRADE");
        assertThat(chain).hasSize(3);
        assertThat(chain).extracting(AssayEvent::getStatus)
                .containsExactly(ResultStatus.SUPERSEDED, ResultStatus.SUPERSEDED,
                        ResultStatus.EFFECTIVE);
        assertThat(chain.get(2).getPrevVersion().getEventNo()).isEqualTo("R-CR-08-V2");
    }

    @Test
    void rejectedCorrectionReleasesReservedResultNumber() {
        prepareEffective("CR-10", "R-CR-10", "2.35", "g/t");
        correctionService.request(
                "CO-CR-10-A", "R-CR-10", "R-CR-10-NEW", new BigDecimal("3.1"), "g/t",
                "录入错误", "证据", "中心实验室");
        correctionService.decide(
                "AP-CR-10-A", "CO-CR-10-A", "技术负责人", ReviewDecision.REJECTED, null);

        // 驳回后同一新版本结果号可被新的更正申请复用，并最终批准
        correctionService.request(
                "CO-CR-10-B", "R-CR-10", "R-CR-10-NEW", new BigDecimal("3.2"), "g/t",
                "仪器错误", "证据2", "中心实验室");
        correctionService.decide(
                "AP-CR-10-B", "CO-CR-10-B", "技术负责人", ReviewDecision.APPROVED, null);

        AssayEvent published = assayEventRepository.findByEventNo("R-CR-10-NEW").orElseThrow();
        assertThat(published.getStatus()).isEqualTo(ResultStatus.EFFECTIVE);
        assertThat(published.getResultValue()).isEqualByComparingTo("3.200000");
    }

    @Test
    void cannotApproveCorrectionWhileCustodyPending() {
        prepareEffective("CR-11", "R-CR-11", "2.35", "g/t");
        correctionService.request(
                "CO-CR-11", "R-CR-11", "R-CR-11-V2", new BigDecimal("3.25"), "g/t",
                "仪器错误", "证据", "中心实验室");
        custodyService.initiate("CU-CR-11", "CR-11", "中心实验室", "南方实验室");

        assertThatThrownBy(() -> correctionService.decide(
                "AP-CR-11", "CO-CR-11", "技术负责人", ReviewDecision.APPROVED, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("保管链不完整");
        assertThat(assayEventRepository.findByEventNo("R-CR-11").orElseThrow().getStatus())
                .isEqualTo(ResultStatus.EFFECTIVE);
    }

    @Test
    void correctionAndReviewNumbersIdempotentConflictOnDifference() {
        prepareEffective("CR-09", "R-CR-09", "2.35", "g/t");
        CorrectionRequest first = correctionService.request(
                "CO-CR-09", "R-CR-09", "R-CR-09-V2", new BigDecimal("3.25"), "g/t",
                "仪器错误", "证据", "中心实验室");
        CorrectionRequest replay = correctionService.request(
                "CO-CR-09", "R-CR-09", "R-CR-09-V2", new BigDecimal("3.25"), "g/t",
                "仪器错误", "证据", "中心实验室");
        assertThat(replay.getId()).isEqualTo(first.getId());

        assertThatThrownBy(() -> correctionService.request(
                "CO-CR-09", "R-CR-09", "R-CR-09-V2", new BigDecimal("3.26"), "g/t",
                "仪器错误", "证据", "中心实验室"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");

        correctionService.decide("AP-CR-09", "CO-CR-09", "技术负责人",
                ReviewDecision.APPROVED, null);
        ReviewEvent reviewReplay = correctionService.decide(
                "AP-CR-09", "CO-CR-09", "技术负责人", ReviewDecision.APPROVED, null);
        assertThat(reviewReplay.getEventNo()).isEqualTo("AP-CR-09");

        assertThatThrownBy(() -> correctionService.decide(
                "AP-CR-09", "CO-CR-09", "其他人", ReviewDecision.APPROVED, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");
    }
}
