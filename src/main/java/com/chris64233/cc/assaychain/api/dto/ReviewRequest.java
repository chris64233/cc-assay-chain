package com.chris64233.cc.assaychain.api.dto;

/** 检测结果复核请求。decision 取值 APPROVED/REJECTED。 */
public record ReviewRequest(
        String eventNo,
        String resultEventNo,
        String decision,
        String reviewedBy,
        String comment) {
}
