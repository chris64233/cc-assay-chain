package com.chris64233.cc.assaychain.api.dto;

/** 更正申请审批请求。decision 取值 APPROVED/REJECTED。 */
public record CorrectionDecisionRequest(
        String eventNo,
        String correctionNo,
        String decision,
        String reviewedBy,
        String comment) {
}
