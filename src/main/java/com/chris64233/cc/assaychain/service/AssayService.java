package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.ResultStatus;
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
     * 提交检测结果，结果进入 PENDING 待复核状态；复核通过后才成为对外有效结果。
     *
     * <p>只有当前持有叶子样本、且无待确认交接（保管链完整）的实验室可提交。
     * 每个（样本，项目）同时只允许一个待复核/生效版本；生效结果不得再直接提交覆盖，
     * 必须走更正流程；被复核驳回后允许重新提交（产生新版本号）。
     * 外部结果号幂等：同号同内容重放返回原事件，同号异内容冲突。</p>
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
        // 悲观锁锁定样本行，与分样/交接/复核/更正式串行
        Sample sample = sampleRepository.findByExternalNoForUpdate(sampleExternalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + sampleExternalNo));

        if (existingByNo != null) {
            verifyIdempotent(existingByNo, sample, itemCode, normalizedValue, unit, submittedBy);
            return existingByNo;
        }

        if (!sample.isLeaf()) {
            throw new BusinessRuleException("样本已分样，不能提交检测结果: " + sampleExternalNo);
        }
        if (sample.getPendingCustodyEventId() != null) {
            throw new BusinessRuleException("样本存在待确认交接、保管链不完整，不能提交检测结果: "
                    + sampleExternalNo);
        }
        if (!sample.getCustodian().equals(submittedBy)) {
            throw new BusinessRuleException(
                    "只有当前持有样本的实验室可以提交结果，当前持有方为: " + sample.getCustodian());
        }

        AssayEvent latest = assayEventRepository
                .findTopBySampleIdAndItemCodeOrderByVersionNoDesc(sample.getId(), itemCode)
                .orElse(null);
        if (latest != null) {
            if (latest.getStatus() == ResultStatus.EFFECTIVE
                    || latest.getStatus() == ResultStatus.SUPERSEDED) {
                throw new ConflictException("检测项目已有生效结果，不可直接覆盖，请发起更正申请: "
                        + itemCode + "（结果号 " + latest.getEventNo() + "）");
            }
            if (latest.getStatus() == ResultStatus.PENDING) {
                throw new ConflictException("检测项目已有待复核结果，不能重复提交: " + itemCode
                        + "（结果号 " + latest.getEventNo() + "）");
            }
            // 最新版本为 REJECTED：允许重新提交，版本号在其基础上递增
        }

        AssayEvent event = new AssayEvent();
        event.setEventNo(eventNo);
        event.setSample(sample);
        event.setItemCode(itemCode);
        event.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        if (latest != null) {
            event.setPrevVersion(latest);
        }
        event.setResultValue(normalizedValue);
        event.setUnit(unit);
        event.setSubmittedBy(submittedBy);
        event.setStatus(ResultStatus.PENDING);
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
