package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.ResultStatus;
import com.chris64233.cc.assaychain.domain.ReviewDecision;
import com.chris64233.cc.assaychain.domain.ReviewEvent;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.BusinessRuleException;
import com.chris64233.cc.assaychain.service.ConflictException;
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
class ReviewServiceTest {

    @Autowired
    private ReceptionService receptionService;
    @Autowired
    private CustodyService custodyService;
    @Autowired
    private AssayService assayService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private SplitService splitService;
    @Autowired
    private AssayEventRepository assayEventRepository;

    private void preparePending(String sampleNo, String eventNo) {
        receptionService.receive(sampleNo, "矿区", new BigDecimal("40.0000"), "地勘院");
        custodyService.initiate(eventNo + "-I", sampleNo, "地勘院", "中心实验室");
        custodyService.confirm(eventNo + "-I", "中心实验室");
        assayService.submit(eventNo, sampleNo, "AU_GRADE",
                new BigDecimal("2.35"), "g/t", "中心实验室");
    }

    @Test
    void approveMakesResultEffective() {
        preparePending("RV-01", "EV-RV-01");

        ReviewEvent review = reviewService.review(
                "AP-RV-01", "EV-RV-01", "质量主管", ReviewDecision.APPROVED, "合格");

        assertThat(review.getDecision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(review.getReviewedBy()).isEqualTo("质量主管");
        AssayEvent version = assayEventRepository.findByEventNo("EV-RV-01").orElseThrow();
        assertThat(version.getStatus()).isEqualTo(ResultStatus.EFFECTIVE);
        assertThat(version.getEffectiveAt()).isNotNull();
    }

    @Test
    void reviewerMustDifferFromSubmitter() {
        preparePending("RV-02", "EV-RV-02");

        assertThatThrownBy(() -> reviewService.review(
                "AP-RV-02", "EV-RV-02", "中心实验室", ReviewDecision.APPROVED, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("复核人必须与结果提交人不同");
    }

    @Test
    void cannotReviewNonLeafSample() {
        preparePending("RV-03", "EV-RV-03");
        splitService.split("SP-RV-03", "RV-03", new BigDecimal("0.0000"),
                List.of(new ChildSampleRequest("RV-03-A", new BigDecimal("20.0000"), "中心实验室"),
                        new ChildSampleRequest("RV-03-B", new BigDecimal("20.0000"), "中心实验室")));

        assertThatThrownBy(() -> reviewService.review(
                "AP-RV-03", "EV-RV-03", "质量主管", ReviewDecision.APPROVED, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("叶子");
    }

    @Test
    void cannotReviewWhileCustodyPending() {
        receptionService.receive("RV-04", "矿区", new BigDecimal("40.0000"), "中心实验室");
        assayService.submit("EV-RV-04", "RV-04", "AU_GRADE",
                new BigDecimal("2.35"), "g/t", "中心实验室");
        custodyService.initiate("CU-RV-04", "RV-04", "中心实验室", "南方实验室");

        assertThatThrownBy(() -> reviewService.review(
                "AP-RV-04", "EV-RV-04", "质量主管", ReviewDecision.APPROVED, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("保管链不完整");
    }

    @Test
    void cannotReviewTwice() {
        preparePending("RV-05", "EV-RV-05");
        reviewService.review("AP-RV-05", "EV-RV-05", "质量主管", ReviewDecision.APPROVED, null);

        assertThatThrownBy(() -> reviewService.review(
                "AP-RV-05-2", "EV-RV-05", "质量主管", ReviewDecision.REJECTED, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已复核");
    }

    @Test
    void rejectAllowsResubmitAsNewVersionThenApprove() {
        preparePending("RV-06", "EV-RV-06");
        reviewService.review("AP-RV-06", "EV-RV-06", "质量主管", ReviewDecision.REJECTED, "存疑");

        assertThat(assayEventRepository.findByEventNo("EV-RV-06").orElseThrow().getStatus())
                .isEqualTo(ResultStatus.REJECTED);

        AssayEvent v2 = assayService.submit(
                "EV-RV-06-V2", "RV-06", "AU_GRADE",
                new BigDecimal("2.40"), "g/t", "中心实验室");
        assertThat(v2.getVersionNo()).isEqualTo(2);
        assertThat(v2.getPrevVersion().getEventNo()).isEqualTo("EV-RV-06");

        reviewService.review("AP-RV-06-V2", "EV-RV-06-V2", "质量主管",
                ReviewDecision.APPROVED, null);
        assertThat(assayEventRepository.findByEventNo("EV-RV-06-V2").orElseThrow().getStatus())
                .isEqualTo(ResultStatus.EFFECTIVE);
        assertThat(assayEventRepository.findByEventNo("EV-RV-06").orElseThrow().getStatus())
                .isEqualTo(ResultStatus.REJECTED);
    }

    @Test
    void reviewIdempotentSameContentConflictOnDifference() {
        preparePending("RV-07", "EV-RV-07");
        ReviewEvent first = reviewService.review(
                "AP-RV-07", "EV-RV-07", "质量主管", ReviewDecision.APPROVED, "ok");
        ReviewEvent replay = reviewService.review(
                "AP-RV-07", "EV-RV-07", "质量主管", ReviewDecision.APPROVED, "ok");
        assertThat(replay.getId()).isEqualTo(first.getId());

        assertThatThrownBy(() -> reviewService.review(
                "AP-RV-07", "EV-RV-07", "质量主管", ReviewDecision.REJECTED, "ok"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容不同");
    }
}
