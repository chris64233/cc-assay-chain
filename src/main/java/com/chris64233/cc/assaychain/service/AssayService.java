package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.AssayStatus;
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

    /**
     * 提交检测结果。只有当前持有该叶子样本的实验室可提交；
     * 结果进入待复核状态，复核通过后才对外有效。
     * 对样本行加悲观写锁，与分样/保管方变更互斥，保证不会对已不再是
     * 叶子节点的样本发布结果。
     */
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
        Sample sample = sampleRepository.findLockedByExternalNo(sampleExternalNo)
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

        AssayEvent openItem = assayEventRepository
                .findBySampleIdAndItemCodeOrderByVersionNoAsc(sample.getId(), itemCode)
                .stream()
                .filter(event -> event.getStatus() == AssayStatus.EFFECTIVE
                        || event.getStatus() == AssayStatus.PENDING_REVIEW)
                .reduce((first, second) -> second)
                .orElse(null);
        if (openItem != null) {
            if (openItem.getStatus() == AssayStatus.EFFECTIVE) {
                throw new ConflictException("检测项目已有生效结果，不得直接覆盖，请发起更正申请: "
                        + itemCode + "（结果号 " + openItem.getEventNo() + "）");
            }
            throw new ConflictException("检测项目已有待复核结果，请等待复核结论: " + itemCode
                    + "（结果号 " + openItem.getEventNo() + "）");
        }

        AssayEvent event = new AssayEvent();
        event.setEventNo(eventNo);
        event.setSample(sample);
        event.setItemCode(itemCode);
        event.setResultValue(normalizedValue);
        event.setUnit(unit);
        event.setSubmittedBy(submittedBy);
        event.setStatus(AssayStatus.PENDING_REVIEW);
        event.setVersionNo(1);
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
