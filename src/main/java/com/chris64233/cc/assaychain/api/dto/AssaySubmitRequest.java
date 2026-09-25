package com.chris64233.cc.assaychain.api.dto;

import java.math.BigDecimal;

/** 检测结果提交请求。 */
public record AssaySubmitRequest(
        String eventNo,
        String sampleExternalNo,
        String itemCode,
        BigDecimal resultValue,
        String unit,
        String submittedBy) {
}
