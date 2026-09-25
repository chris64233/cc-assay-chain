package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.CustodyEvent;
import com.chris64233.cc.assaychain.domain.CustodyStatus;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.CustodyEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CustodyService {

    private final CustodyEventRepository custodyEventRepository;
    private final SampleRepository sampleRepository;

    public CustodyService(CustodyEventRepository custodyEventRepository,
                          SampleRepository sampleRepository) {
        this.custodyEventRepository = custodyEventRepository;
        this.sampleRepository = sampleRepository;
    }

    /** 当前保管方发起交接给指定实验室。 */
    @Transactional
    public CustodyEvent initiate(String eventNo,
                                 String sampleExternalNo,
                                 String fromCustodian,
                                 String toLab) {
        ReceptionService.requireText(eventNo, "交接事件号");
        ReceptionService.requireText(sampleExternalNo, "样本号");
        ReceptionService.requireText(fromCustodian, "发起保管方");
        ReceptionService.requireText(toLab, "接收实验室");
        if (fromCustodian.equals(toLab)) {
            throw new BusinessRuleException("接收实验室必须与当前保管方不同");
        }

        CustodyEvent existing = custodyEventRepository.findByEventNo(eventNo).orElse(null);
        Sample sample = sampleRepository.findByExternalNo(sampleExternalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + sampleExternalNo));

        if (existing != null) {
            verifyInitiateIdempotent(existing, sample, fromCustodian, toLab);
            return existing;
        }

        if (!sample.isLeaf()) {
            throw new BusinessRuleException("样本已分样，不能交接: " + sampleExternalNo);
        }
        if (!sample.getCustodian().equals(fromCustodian)) {
            throw new BusinessRuleException(
                    "只有当前保管方可以发起交接，当前保管方为: " + sample.getCustodian());
        }
        if (sample.getPendingCustodyEventId() != null) {
            throw new BusinessRuleException("样本已有待确认交接，不能重复发起: " + sampleExternalNo);
        }

        CustodyEvent event = new CustodyEvent();
        event.setEventNo(eventNo);
        event.setSample(sample);
        event.setFromCustodian(fromCustodian);
        event.setToLab(toLab);
        event.setStatus(CustodyStatus.PENDING);

        custodyEventRepository.save(event);
        custodyEventRepository.flush();

        sample.setPendingCustodyEventId(event.getId());
        sampleRepository.save(sample);
        return event;
    }

    /** 指定接收实验室确认交接；确认后保管方变更，事件不可变。 */
    @Transactional
    public CustodyEvent confirm(String eventNo, String confirmedBy) {
        ReceptionService.requireText(eventNo, "交接事件号");
        ReceptionService.requireText(confirmedBy, "确认方");

        CustodyEvent event = custodyEventRepository.findByEventNo(eventNo)
                .orElseThrow(() -> new NotFoundException("交接事件不存在: " + eventNo));

        if (event.getStatus() == CustodyStatus.CONFIRMED) {
            if (!event.getToLab().equals(confirmedBy)) {
                throw new ConflictException("交接事件已确认，确认方不一致（冲突）");
            }
            return event;
        }

        if (!event.getToLab().equals(confirmedBy)) {
            throw new BusinessRuleException(
                    "只有指定接收实验室可以确认交接，指定接收方为: " + event.getToLab());
        }

        Sample sample = event.getSample();
        if (!sample.isLeaf()) {
            throw new BusinessRuleException("样本已分样，不能确认交接: " + sample.getExternalNo());
        }
        if (!sample.getCustodian().equals(event.getFromCustodian())
                || !event.getId().equals(sample.getPendingCustodyEventId())) {
            throw new ConflictException("样本保管状态与待确认交接不一致，交接作废");
        }

        event.setStatus(CustodyStatus.CONFIRMED);
        event.setConfirmedAt(Instant.now());
        custodyEventRepository.save(event);

        sample.setCustodian(event.getToLab());
        sample.setPendingCustodyEventId(null);
        sampleRepository.save(sample);
        return event;
    }

    private void verifyInitiateIdempotent(CustodyEvent existing,
                                          Sample sample,
                                          String fromCustodian,
                                          String toLab) {
        boolean same = existing.getSample().getId().equals(sample.getId())
                && existing.getFromCustodian().equals(fromCustodian)
                && existing.getToLab().equals(toLab);
        if (!same) {
            throw new ConflictException("交接事件号 " + existing.getEventNo()
                    + " 已存在但内容不同（冲突）");
        }
    }
}
