package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.ApprovalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalEventRepository extends JpaRepository<ApprovalEvent, Long> {

    Optional<ApprovalEvent> findByApprovalNo(String approvalNo);

    List<ApprovalEvent> findByAssayEventSampleIdInOrderByDecisionTimeAsc(List<Long> sampleIds);
}
