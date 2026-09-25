package com.chris64233.cc.assaychain;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.ReceptionService;
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
        assertThat(assayEventRepository.findBySampleIdAndItemCode(sampleId, "AU_GRADE"))
                .isPresent();
        assertThat(assayEventRepository.findBySampleIdAndItemCode(sampleId, "AU_GRADE").orElseThrow()
                .getResultValue()).isEqualByComparingTo("2.350000");
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
}
