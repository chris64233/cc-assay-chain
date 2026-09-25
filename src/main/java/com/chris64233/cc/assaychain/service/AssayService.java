package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.repo.AssayEventRepository;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AssayService {

    private final AssayEventRepository assayEventRepository;
    private final SampleRepository sampleRepository;

    public AssayService(AssayEventRepository assayEventRepository,
                        SampleRepository sampleRepository) {
        this.assayEventRepository = assayEventRepository;
        this.sampleRepository = sampleRepository;
    }

    /** 提交检测结果。只有当前持有该叶子样本的实验室可提交，结果事件不可覆盖。 */
    @Transactional
    public AssayEvent submit(String eventNo,
                             String sampleExternalNo,
                             String itemCode,
                             BigDecimal resultValue,
                             String unit,
                             String submittedBy) {
        ReceptionService.requireText(eventNo, "结果事件号");
        ReceptionService.requireText(sampleExternalNo, "样本号");
        ReceptionService.requireText(itemCode, "检测项目");
        ReceptionService.requireText(unit, "计量单位");
        ReceptionService.requireText(submittedBy, "提交实验室");
        BigDecimal normalizedValue = MassRules.requireResultScale(resultValue);

        AssayEvent existingByNo = assayEventRepository.findByEventNo(eventNo).orElse(null);
        Sample sample = sampleRepository.findByExternalNo(sampleExternalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + sampleExternalNo));

        if (existingByNo != null) {
            verifyIdempotent(existingByNo, sample, itemCode, normalizedValue, unit, submittedBy);
            return existingByNo;
        }

        if (!sample.isLeaf()) {
            throw new BusinessRuleException("样本已分样，不能提交检测结果: " + sampleExternalNo);
        }
        if (!sample.getCustodian().equals(submittedBy)) {
            throw new BusinessRuleException(
                    "只有当前持有样本的实验室可以提交结果，当前持有方为: " + sample.getCustodian());
        }

        AssayEvent existingItem = assayEventRepository
                .findBySampleIdAndItemCode(sample.getId(), itemCode)
                .orElse(null);
        if (existingItem != null) {
            throw new ConflictException("检测项目已有有效结果，不可覆盖: " + itemCode
                    + "（事件号 " + existingItem.getEventNo() + "）");
        }

        AssayEvent event = new AssayEvent();
        event.setEventNo(eventNo);
        event.setSample(sample);
        event.setItemCode(itemCode);
        event.setResultValue(normalizedValue);
        event.setUnit(unit);
        event.setSubmittedBy(submittedBy);
        return assayEventRepository.save(event);
    }

    private void verifyIdempotent(AssayEvent existing,
                                  Sample sample,
                                  String itemCode,
                                  BigDecimal resultValue,
                                  String unit,
                                  String submittedBy) {
        boolean same = existing.getSample().getId().equals(sample.getId())
                && existing.getItemCode().equals(itemCode)
                && existing.getResultValue().compareTo(resultValue) == 0
                && existing.getUnit().equals(unit)
                && existing.getSubmittedBy().equals(submittedBy);
        if (!same) {
            throw new ConflictException("结果事件号 " + existing.getEventNo()
                    + " 已存在但内容不同（冲突）");
        }
    }
}
