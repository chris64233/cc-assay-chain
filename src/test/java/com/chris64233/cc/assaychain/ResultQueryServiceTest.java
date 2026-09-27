package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.domain.ReviewDecision;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.CorrectionService;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.NotFoundException;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.ResultHistoryView;
import com.chris64233.cc.assaychain.service.ResultQueryService;
import com.chris64233.cc.assaychain.service.ResultVersionView;
import com.chris64233.cc.assaychain.service.ReviewRecordView;
import com.chris64233.cc.assaychain.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ResultQueryServiceTest {

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
    private ResultQueryService resultQueryService;

    private void prepareTwoCorrections() {
        receptionService.receive("RQ-01", "矿区", new BigDecimal("40.0000"), "地勘院");
        custodyService.initiate("RQ-01-I", "RQ-01", "地勘院", "中心实验室");
        custodyService.confirm("RQ-01-I", "中心实验室");
        assayService.submit("R-RQ-01-V1", "RQ-01", "AU_GRADE",
                new BigDecimal("2.35"), "g/t", "中心实验室");
        reviewService.review("AP-RQ-01-V1", "R-RQ-01-V1", "质量主管",
                ReviewDecision.APPROVED, null);
        correctionService.request("CO-RQ-01-1", "R-RQ-01-V1", "R-RQ-01-V2",
                new BigDecimal("3.25"), "g/t", "仪器错误", "证据1", "中心实验室");
        correctionService.decide("AP-RQ-01-1", "CO-RQ-01-1", "技术负责人",
                ReviewDecision.APPROVED, null);
        correctionService.request("CO-RQ-01-2", "R-RQ-01-V2", "R-RQ-01-V3",
                new BigDecimal("4.25"), "g/t", "录入错误", "证据2", "中心实验室");
        correctionService.decide("AP-RQ-01-2", "CO-RQ-01-2", "技术负责人",
                ReviewDecision.APPROVED, null);
    }

    @Test
    void currentResultIsLatestEffectiveVersion() {
        prepareTwoCorrections();

        ResultVersionView current = resultQueryService.getCurrent("RQ-01", "AU_GRADE");
        assertThat(current.resultEventNo()).isEqualTo("R-RQ-01-V3");
        assertThat(current.versionNo()).isEqualTo(3);
        assertThat(current.status()).isEqualTo("EFFECTIVE");
        assertThat(current.prevResultEventNo()).isEqualTo("R-RQ-01-V2");
    }

    @Test
    void historyJoinsVersionChainAndReviews() {
        prepareTwoCorrections();

        ResultHistoryView history = resultQueryService.getHistory("RQ-01", "AU_GRADE");
        assertThat(history.current().resultEventNo()).isEqualTo("R-RQ-01-V3");
        assertThat(history.versions()).extracting(ResultVersionView::resultEventNo)
                .containsExactly("R-RQ-01-V1", "R-RQ-01-V2", "R-RQ-01-V3");
        assertThat(history.versions()).extracting(ResultVersionView::status)
                .containsExactly("SUPERSEDED", "SUPERSEDED", "EFFECTIVE");

        assertThat(history.reviews()).extracting(ReviewRecordView::eventNo)
                .containsExactly("AP-RQ-01-V1", "AP-RQ-01-1", "AP-RQ-01-2");
        ReviewRecordView correctionReview = history.reviews().stream()
                .filter(r -> r.eventNo().equals("AP-RQ-01-1")).findFirst().orElseThrow();
        assertThat(correctionReview.kind()).isEqualTo("CORRECTION_REVIEW");
        assertThat(correctionReview.correctionNo()).isEqualTo("CO-RQ-01-1");
        assertThat(correctionReview.resultEventNo()).isEqualTo("R-RQ-01-V2");
    }

    @Test
    void effectiveAtReconstructsPointInTimeResult() {
        prepareTwoCorrections();

        ResultHistoryView history = resultQueryService.getHistory("RQ-01", "AU_GRADE");
        Instant v1From = history.versions().get(0).effectiveAt();
        Instant v2From = history.versions().get(1).effectiveAt();
        Instant v3From = history.versions().get(2).effectiveAt();
        Instant v1Until = history.versions().get(0).supersededAt();

        // v1 生效期间
        assertThat(resultQueryService.getEffectiveAt("RQ-01", "AU_GRADE",
                v1From.plus(1, ChronoUnit.MILLIS)).resultEventNo()).isEqualTo("R-RQ-01-V1");
        // 切换瞬间归属新版本（旧版本失效时间 = 新版本生效时间）
        assertThat(resultQueryService.getEffectiveAt("RQ-01", "AU_GRADE", v1Until)
                .resultEventNo()).isEqualTo("R-RQ-01-V2");
        // v2 生效期间
        assertThat(resultQueryService.getEffectiveAt("RQ-01", "AU_GRADE",
                v2From.plus(1, ChronoUnit.MILLIS)).resultEventNo()).isEqualTo("R-RQ-01-V2");
        // 当前
        assertThat(resultQueryService.getEffectiveAt("RQ-01", "AU_GRADE",
                v3From.plus(1, ChronoUnit.HOURS)).resultEventNo()).isEqualTo("R-RQ-01-V3");
        // v1 生效之前没有有效结果
        assertThatThrownBy(() -> resultQueryService.getEffectiveAt(
                "RQ-01", "AU_GRADE", v1From.minus(1, ChronoUnit.HOURS)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void currentMissingWhenOnlyPending() {
        receptionService.receive("RQ-02", "矿区", new BigDecimal("40.0000"), "中心实验室");
        assayService.submit("R-RQ-02", "RQ-02", "AU_GRADE",
                new BigDecimal("1.0"), "g/t", "中心实验室");

        assertThatThrownBy(() -> resultQueryService.getCurrent("RQ-02", "AU_GRADE"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("无有效结果");
    }
}
