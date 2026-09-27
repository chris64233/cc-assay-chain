package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.AssayStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** 结果版本链上的单个版本视图。 */
public record ResultVersionView(
        String eventNo,
        String sampleExternalNo,
        String itemCode,
        int versionNo,
        AssayStatus status,
        BigDecimal resultValue,
        String unit,
        String submittedBy,
        Instant eventTime,
        String reviewedBy,
        Instant reviewedAt,
        Instant effectiveAt,
        /** 产生该版本的更正号（实验室原始提交为空）。 */
        String sourceCorrectionNo) {
}
