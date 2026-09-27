package com.chris64233.cc.assaychain.api.dto;

import com.chris64233.cc.assaychain.domain.ApprovalDecision;

/** 更正审批请求。 */
public record CorrectionDecisionRequest(
        String approvalNo,
        String correctionNo,
        ApprovalDecision decision,
        String decidedBy,
        String comment) {
}
