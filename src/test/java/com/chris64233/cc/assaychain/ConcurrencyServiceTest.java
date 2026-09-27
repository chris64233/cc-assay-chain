package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.domain.ApprovalDecision;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.AssayStatus;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.ReviewService;
import com.chris64233.cc.assaychain.service.SplitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyServiceTest {

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
    private SampleRepository sampleRepository;
    @Autowired
    private AssayEventRepository assayEventRepository;

    private record RunOutcome(int success, int failure) {
    }

    private RunOutcome runConcurrently(int threads, Runnable action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    action.run();
                    success.incrementAndGet();
                } catch (Exception ex) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        return new RunOutcome(success.get(), failure.get());
    }

    @Test
    void onlyOneConcurrentSplitSucceeds() throws Exception {
        receptionService.receive("CC-SP", "矿区", new BigDecimal("100.0000"), "地勘院");

        RunOutcome outcome = runConcurrently(8, () -> {
            String thread = Thread.currentThread().getName().replace(' ', '_');
            splitService.split(
                    "EV-CC-SP-" + thread + "-" + System.nanoTime(),
                    "CC-SP",
                    new BigDecimal("10.0000"),
                    List.of(
                            new ChildSampleRequest(
                                    "CC-SP-A-" + thread + "-" + System.nanoTime(),
                                    new BigDecimal("60.0000"), "地勘院"),
                            new ChildSampleRequest(
                                    "CC-SP-B-" + thread + "-" + System.nanoTime(),
                                    new BigDecimal("30.0000"), "地勘院")));
        });

        assertThat(outcome.success()).isEqualTo(1);
        assertThat(outcome.failure()).isEqualTo(7);

        Sample parent = sampleRepository.findByExternalNo("CC-SP").orElseThrow();
        assertThat(parent.isLeaf()).isFalse();
        assertThat(sampleRepository.findByParentId(parent.getId())).hasSize(2);
    }

    @Test
    void onlyOnePendingCustodyInitiationSucceeds() throws Exception {
        receptionService.receive("CC-CU", "矿区", new BigDecimal("100.0000"), "地勘院");

        RunOutcome outcome = runConcurrently(8, () -> {
            String thread = Thread.currentThread().getName().replace(' ', '_');
            custodyService.initiate(
                    "EV-CC-CU-" + thread + "-" + System.nanoTime(),
                    "CC-CU", "地勘院", "中心实验室-" + thread);
        });

        assertThat(outcome.success()).isEqualTo(1);
        assertThat(outcome.failure()).isEqualTo(7);

        Sample sample = sampleRepository.findByExternalNo("CC-CU").orElseThrow();
        assertThat(sample.getCustodian()).isEqualTo("地勘院");
        assertThat(sample.getPendingCustodyEventId()).isNotNull();
    }

    @Test
    void onlyOneConcurrentAssayResultPerItem() throws Exception {
        receptionService.receive("CC-AS", "矿区", new BigDecimal("10.0000"), "中心实验室");

        RunOutcome outcome = runConcurrently(8, () -> {
            String thread = Thread.currentThread().getName().replace(' ', '_');
            assayService.submit(
                    "EV-CC-AS-" + thread + "-" + System.nanoTime(),
                    "CC-AS", "AU_GRADE", new BigDecimal("2.350000"), "g/t", "中心实验室");
        });

        assertThat(outcome.success()).isEqualTo(1);
        assertThat(outcome.failure()).isEqualTo(7);
        Long sampleId = sampleRepository.findByExternalNo("CC-AS").orElseThrow().getId();
        List<AssayEvent> chain = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(sampleId, "AU_GRADE");
        assertThat(chain).hasSize(1);
        assertThat(chain.getFirst().getResultValue()).isEqualByComparingTo("2.350000");
    }

    @Test
    void concurrentResultSubmitAndSplitNeverPublishOnNonLeaf() throws Exception {
        receptionService.receive("CC-RS", "矿区", new BigDecimal("100.0000"), "中心实验室");

        Runnable submitJob = () -> assayService.submit(
                "EV-CC-RS-A-" + System.nanoTime(),
                "CC-RS", "AU_GRADE", new BigDecimal("2.350000"), "g/t", "中心实验室");
        Runnable splitJob = () -> splitService.split(
                "EV-CC-RS-S-" + System.nanoTime(),
                "CC-RS", new BigDecimal("10.0000"),
                List.of(
                        new ChildSampleRequest(
                                "CC-RS-A-" + System.nanoTime(),
                                new BigDecimal("60.0000"), "中心实验室"),
                        new ChildSampleRequest(
                                "CC-RS-B-" + System.nanoTime(),
                                new BigDecimal("30.0000"), "中心实验室")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(8);
        AtomicInteger submitSuccess = new AtomicInteger();
        AtomicInteger splitSuccess = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    submitJob.run();
                    submitSuccess.incrementAndGet();
                } catch (Exception ex) {
                    // 预期：非叶子/已有待复核结果
                } finally {
                    done.countDown();
                }
            });
            pool.submit(() -> {
                try {
                    start.await();
                    splitJob.run();
                    splitSuccess.incrementAndGet();
                } catch (Exception ex) {
                    // 预期：样本已被分样
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        Sample parent = sampleRepository.findByExternalNo("CC-RS").orElseThrow();
        assertThat(parent.isLeaf()).isFalse();
        assertThat(splitSuccess.get()).isEqualTo(1);
        // 分样一旦先行，任何结果提交都必须失败；结果最多 1 条且（若存在）属于分样前的窗口
        Long sampleId = parent.getId();
        List<AssayEvent> chain = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(sampleId, "AU_GRADE");
        if (submitSuccess.get() == 1) {
            assertThat(chain).hasSize(1);
        } else {
            assertThat(chain).isEmpty();
        }
        assertThat(submitSuccess.get()).isLessThanOrEqualTo(1);
    }

    @Test
    void concurrentSplitAndCustodyNeverLeaveParentChildBothActive() throws Exception {
        receptionService.receive("CC-MIX", "矿区", new BigDecimal("100.0000"), "地勘院");

        Runnable splitJob = () -> splitService.split(
                "EV-CC-MIX-S-" + System.nanoTime(),
                "CC-MIX", new BigDecimal("10.0000"),
                List.of(
                        new ChildSampleRequest(
                                "CC-MIX-A-" + System.nanoTime(),
                                new BigDecimal("60.0000"), "地勘院"),
                        new ChildSampleRequest(
                                "CC-MIX-B-" + System.nanoTime(),
                                new BigDecimal("30.0000"), "地勘院")));
        Runnable custodyJob = () -> {
            String eventNo = "EV-CC-MIX-C-" + System.nanoTime();
            custodyService.initiate(eventNo, "CC-MIX", "地勘院", "中心实验室");
            custodyService.confirm(eventNo, "中心实验室");
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        for (Runnable job : List.of(splitJob, custodyJob)) {
            pool.submit(() -> {
                try {
                    start.await();
                    job.run();
                    success.incrementAndGet();
                } catch (Exception ex) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get() + failure.get()).isEqualTo(2);
        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(1);

        Sample parent = sampleRepository.findByExternalNo("CC-MIX").orElseThrow();
        if (!parent.isLeaf()) {
            assertThat(sampleRepository.findByParentId(parent.getId())).hasSize(2);
            assertThat(parent.getCustodian()).isEqualTo("地勘院");
            assertThat(parent.getPendingCustodyEventId()).isNull();
        } else {
            assertThat(sampleRepository.findByParentId(parent.getId())).isEmpty();
        }
    }

    private void seedEffectiveResult(String sampleNo, String resultNo) {
        receptionService.receive(sampleNo, "矿区", new BigDecimal("100.0000"), "中心实验室");
        assayService.submit(resultNo, sampleNo, "AU_GRADE",
                new BigDecimal("2.350000"), "g/t", "中心实验室");
        reviewService.reviewSubmission(
                "AP-" + resultNo, resultNo, ApprovalDecision.APPROVE, "质量负责人", null);
    }

    @Test
    void concurrentDuplicateCorrectionApprovalsYieldSingleNewVersion() throws Exception {
        seedEffectiveResult("CC-CR", "CC-CR-R");
        reviewService.requestCorrection(
                "CC-CR-C1", "CC-CR-R", new BigDecimal("3.250000"), "g/t",
                "仪器标定错误", "复检报告", "中心实验室");

        RunOutcome outcome = runConcurrently(8, () -> {
            String thread = Thread.currentThread().getName().replace(' ', '_');
            reviewService.decideCorrection(
                    "CC-CR-AP-" + thread + "-" + System.nanoTime(),
                    "CC-CR-C1", ApprovalDecision.APPROVE, "技术负责人", null);
        });

        assertThat(outcome.success()).isEqualTo(1);
        assertThat(outcome.failure()).isEqualTo(7);
        List<AssayEvent> chain = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(
                        sampleRepository.findByExternalNo("CC-CR").orElseThrow().getId(), "AU_GRADE");
        assertThat(chain).filteredOn(event -> event.getStatus() == AssayStatus.EFFECTIVE)
                .hasSize(1);
        assertThat(chain).hasSize(2);
        assertThat(chain.getLast().getEventNo()).isEqualTo("COR-CC-CR-C1");
    }

    @Test
    void concurrentCorrectionRequestAndApprovalNeverCorruptVersionChain() throws Exception {
        seedEffectiveResult("CC-CQ", "CC-CQ-R");
        reviewService.requestCorrection(
                "CC-CQ-C1", "CC-CQ-R", new BigDecimal("3.250000"), "g/t",
                "仪器错误", "证据 A", "中心实验室");

        Runnable approve = () -> reviewService.decideCorrection(
                "CC-CQ-AP1", "CC-CQ-C1", ApprovalDecision.APPROVE, "技术负责人", null);
        Runnable requestSecond = () -> reviewService.requestCorrection(
                "CC-CQ-C2", "CC-CQ-R", new BigDecimal("4.000000"), "g/t",
                "单位错误", "证据 B", "中心实验室");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        for (Runnable job : List.of(approve, requestSecond)) {
            pool.submit(() -> {
                try {
                    start.await();
                    job.run();
                    success.incrementAndGet();
                } catch (Exception ex) {
                    // 批准先提交则第二次申请必失败（原版本已被取代），反之亦然
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // 两种交错下第二份申请都必失败：批准先行则原版本已被取代；
        // 申请先行则与待审批更正冲突。批准恰好成功一次。
        assertThat(success.get()).isEqualTo(1);

        List<AssayEvent> chain = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(
                        sampleRepository.findByExternalNo("CC-CQ").orElseThrow().getId(), "AU_GRADE");
        assertThat(chain).hasSize(2);
        assertThat(chain).filteredOn(event -> event.getStatus() == AssayStatus.EFFECTIVE)
                .hasSize(1);
        assertThat(chain.getLast().getEventNo()).isEqualTo("COR-CC-CQ-C1");
    }

    @Test
    void concurrentReviewAndSplitAreMutuallyExclusive() throws Exception {
        receptionService.receive("CC-RV", "矿区", new BigDecimal("100.0000"), "中心实验室");
        assayService.submit("CC-RV-R", "CC-RV", "AU_GRADE",
                new BigDecimal("2.350000"), "g/t", "中心实验室");

        Runnable reviewJob = () -> reviewService.reviewSubmission(
                "CC-RV-AP", "CC-RV-R", ApprovalDecision.APPROVE, "质量负责人", null);
        Runnable splitJob = () -> splitService.split(
                "CC-RV-S", "CC-RV", new BigDecimal("10.0000"),
                List.of(
                        new ChildSampleRequest(
                                "CC-RV-A", new BigDecimal("60.0000"), "中心实验室"),
                        new ChildSampleRequest(
                                "CC-RV-B", new BigDecimal("30.0000"), "中心实验室")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        for (Runnable job : List.of(reviewJob, splitJob)) {
            pool.submit(() -> {
                try {
                    start.await();
                    job.run();
                    success.incrementAndGet();
                } catch (Exception ex) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(1);

        Sample sample = sampleRepository.findByExternalNo("CC-RV").orElseThrow();
        AssayEvent result = assayEventRepository.findByEventNo("CC-RV-R").orElseThrow();
        if (sample.isLeaf()) {
            // 复核先赢：结果生效，样本未被分样
            assertThat(result.getStatus()).isEqualTo(AssayStatus.EFFECTIVE);
            assertThat(sampleRepository.findByParentId(sample.getId())).isEmpty();
        } else {
            // 分样先赢：结果永远停留在待复核，未对非叶子样本生效
            assertThat(result.getStatus()).isEqualTo(AssayStatus.PENDING_REVIEW);
            assertThat(sampleRepository.findByParentId(sample.getId())).hasSize(2);
        }
    }
}
