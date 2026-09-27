package com.chris64233.cc.assaychain.service;

import java.math.BigDecimal;
import java.time.Instant;

/** 结果版本链上的一个版本视图。 */
public record ResultVersionView(
        String resultEventNo,
        String sampleExternalNo,
        String itemCode,
        int versionNo,
        String prevResultEventNo,
        BigDecimal resultValue,
        String unit,
        String submittedBy,
        String status,
        Instant eventTime,
        Instant effectiveAt,
        Instant supersededAt) {
}
