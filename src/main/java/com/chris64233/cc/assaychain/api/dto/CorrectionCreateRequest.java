package com.chris64233.cc.assaychain.api.dto;

import java.math.BigDecimal;

/** 检测结果更正申请请求。 */
public record CorrectionCreateRequest(
        String correctionNo,
        String resultEventNo,
        String newResultEventNo,
        BigDecimal newResultValue,
        String newUnit,
        String reason,
        String evidence,
        String requestedBy) {
}
