package com.chris64233.cc.assaychain.api.dto;

import com.chris64233.cc.assaychain.domain.ApprovalDecision;

/** 检测结果复核请求。 */
public record ReviewRequest(
        String approvalNo,
        String resultEventNo,
        ApprovalDecision decision,
        String decidedBy,
        String comment) {
}
