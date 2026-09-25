package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.api.dto.ChildSampleRequest;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.domain.SplitEvent;
import com.chris64233.cc.assaychain.repo.SampleRepository;
import com.chris64233.cc.assaychain.repo.SplitEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SplitService {

    private final SampleRepository sampleRepository;
    private final SplitEventRepository splitEventRepository;

    public SplitService(SampleRepository sampleRepository,
                        SplitEventRepository splitEventRepository) {
        this.sampleRepository = sampleRepository;
        this.splitEventRepository = splitEventRepository;
    }

    /**
     * 从一个叶子样本分样产生多个子样本，并声明处理损耗。
     * 子样本质量与损耗之和必须等于分样前质量（容差 0.0001 克，严格闭差记账）。
     * 整个操作一个事务，失败不留部分谱系。
     */
    @Transactional
    public SplitEvent split(String eventNo,
                            String parentExternalNo,
                            BigDecimal declaredLossMass,
                            List<ChildSampleRequest> requestedChildren) {
        ReceptionService.requireText(eventNo, "分样事件号");
        ReceptionService.requireText(parentExternalNo, "父样本号");
        if (requestedChildren == null || requestedChildren.size() < 2) {
            throw new BusinessRuleException("一次分样至少产生 2 个子样本");
        }
        BigDecimal declaredLoss = MassRules.requireNonNegativeMass(declaredLossMass, "处理损耗");

        SplitEvent existing = splitEventRepository.findByEventNo(eventNo).orElse(null);
        Sample parent = sampleRepository.findByExternalNo(parentExternalNo)
                .orElseThrow(() -> new NotFoundException("样本不存在: " + parentExternalNo));

        if (existing != null) {
            verifyIdempotent(existing, parent, declaredLoss, requestedChildren);
            return existing;
        }

        if (!parent.isLeaf()) {
            throw new BusinessRuleException("样本已分样，不能再次分样: " + parentExternalNo);
        }
        if (parent.getPendingCustodyEventId() != null) {
            throw new BusinessRuleException("样本存在待确认交接，不能分样: " + parentExternalNo);
        }

        List<ValidatedChild> children = validateChildren(requestedChildren);

        BigDecimal childSum = BigDecimal.ZERO.setScale(MassRules.MASS_SCALE);
        for (ValidatedChild child : children) {
            childSum = childSum.add(child.mass());
        }

        BigDecimal parentMass = parent.getMass();
        BigDecimal bookLoss = parentMass.subtract(childSum);
        if (bookLoss.signum() < 0) {
            throw new BusinessRuleException("子样本质量之和超过分样前质量");
        }
        if (!MassRules.withinTolerance(bookLoss, declaredLoss)) {
            throw new BusinessRuleException(
                    "质量不守恒：子样本质量(" + childSum.toPlainString()
                            + ")+损耗(" + declaredLoss.toPlainString()
                            + ")与分样前质量(" + parentMass.toPlainString()
                            + ")之差超过允许误差 " + MassRules.MASS_TOLERANCE.toPlainString());
        }

        SplitEvent event = new SplitEvent();
        event.setEventNo(eventNo);
        event.setParentSample(parent);
        event.setParentMass(parentMass);
        event.setChildMassSum(childSum);
        event.setDeclaredLossMass(declaredLoss);
        event.setLossMass(bookLoss);
        event.setChildCount(children.size());

        List<Sample> childSamples = new ArrayList<>(children.size());
        for (ValidatedChild child : children) {
            Sample sample = new Sample();
            sample.setExternalNo(child.externalNo());
            sample.setMiningArea(parent.getMiningArea());
            sample.setMass(child.mass());
            sample.setCustodian(child.custodian());
            sample.setParent(parent);
            sample.setLeaf(true);
            childSamples.add(sample);
        }

        splitEventRepository.save(event);
        sampleRepository.saveAll(childSamples);

        parent.setLeaf(false);
        sampleRepository.save(parent);
        return event;
    }

    private List<ValidatedChild> validateChildren(List<ChildSampleRequest> requestedChildren) {
        List<ValidatedChild> children = new ArrayList<>(requestedChildren.size());
        Set<String> externalNos = new HashSet<>();
        for (ChildSampleRequest request : requestedChildren) {
            if (request == null) {
                throw new BusinessRuleException("子样本不能为空");
            }
            ReceptionService.requireText(request.externalNo(), "子样本外部样本号");
            ReceptionService.requireText(request.custodian(), "子样本保管方");
            BigDecimal mass = MassRules.requirePositiveMass(request.mass(), "子样本质量");
            if (!externalNos.add(request.externalNo())) {
                throw new BusinessRuleException("子样本外部样本号重复: " + request.externalNo());
            }
            if (sampleRepository.existsByExternalNo(request.externalNo())) {
                throw new ConflictException("子样本外部样本号已存在: " + request.externalNo());
            }
            children.add(new ValidatedChild(request.externalNo(), mass, request.custodian()));
        }
        return children;
    }

    private void verifyIdempotent(SplitEvent existing,
                                  Sample parent,
                                  BigDecimal declaredLoss,
                                  List<ChildSampleRequest> requestedChildren) {
        boolean same = existing.getParentSample().getId().equals(parent.getId())
                && existing.getDeclaredLossMass().compareTo(declaredLoss) == 0
                && existing.getChildCount() == requestedChildren.size();
        if (same) {
            List<Sample> persistedChildren = sampleRepository.findByParentId(parent.getId());
            if (persistedChildren.size() == requestedChildren.size()) {
                for (ChildSampleRequest request : requestedChildren) {
                    Sample match = persistedChildren.stream()
                            .filter(sample -> sample.getExternalNo().equals(request.externalNo()))
                            .findFirst()
                            .orElse(null);
                    if (match == null
                            || match.getMass().compareTo(
                                    MassRules.requirePositiveMass(request.mass(), "子样本质量")) != 0
                            || !match.getCustodian().equals(request.custodian())) {
                        same = false;
                        break;
                    }
                }
            } else {
                same = false;
            }
        }
        if (!same) {
            throw new ConflictException("分样事件号 " + existing.getEventNo()
                    + " 已存在但内容不同（冲突）");
        }
    }

    private record ValidatedChild(String externalNo, BigDecimal mass, String custodian) {
    }
}
