package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ReceptionService {

    private final SampleRepository sampleRepository;

    public ReceptionService(SampleRepository sampleRepository) {
        this.sampleRepository = sampleRepository;
    }

    /** 接收原始矿样。外部样本号重复时抛出冲突。 */
    @Transactional
    public Sample receive(String externalNo, String miningArea, BigDecimal mass, String custodian) {
        requireText(externalNo, "外部样本号");
        requireText(miningArea, "矿区");
        requireText(custodian, "保管方");
        BigDecimal normalizedMass = MassRules.requirePositiveMass(mass, "质量");

        if (sampleRepository.existsByExternalNo(externalNo)) {
            throw new ConflictException("外部样本号已存在: " + externalNo);
        }

        Sample sample = new Sample();
        sample.setExternalNo(externalNo);
        sample.setMiningArea(miningArea);
        sample.setMass(normalizedMass);
        sample.setCustodian(custodian);
        sample.setLeaf(true);
        return sampleRepository.save(sample);
    }

    static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(name + "不能为空");
        }
    }
}
