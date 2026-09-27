package com.chris64233.cc.assaychain.service;

import java.math.BigDecimal;
import java.time.Instant;

/** 样本在某检测项目上的当前状态：对外有效结果或待复核结果。 */
public record CurrentResultView(
        String sampleExternalNo,
        String itemCode,
        /** EFFECTIVE 或 PENDING_REVIEW。 */
        String status,
        String eventNo,
        int versionNo,
        BigDecimal resultValue,
        String unit,
        String submittedBy,
        String reviewedBy,
        Instant effectiveAt) {
}
