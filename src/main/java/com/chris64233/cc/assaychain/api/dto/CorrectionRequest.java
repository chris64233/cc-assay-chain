package com.chris64233.cc.assaychain.api.dto;

import java.math.BigDecimal;

/** 结果更正申请请求。 */
public record CorrectionRequest(
        String correctionNo,
        String resultEventNo,
        BigDecimal newValue,
        String newUnit,
        String reason,
        String evidence,
        String requestedBy) {
}
