package com.chris64233.cc.assaychain.service;

import java.util.List;

/**
 * 单个（样本，检测项目）的结果联合视图：
 * 当前有效结果、完整版本链、全部复核/审批记录，
 * 以及指定历史时点还原出的有效版本。
 */
public record ResultHistoryView(
        String sampleExternalNo,
        String itemCode,
        /** 当前对外有效结果；无生效版本时为空（如仅待复核或已驳回）。 */
        CurrentResultView effective,
        /** 当前待复核结果；无待复核版本时为空。 */
        CurrentResultView pendingReview,
        List<ResultVersionView> versions,
        List<ReviewRecordView> reviews,
        /** 按 asOf 时点还原的有效结果版本号；未指定时点时为空。 */
        String effectiveEventNoAsOf) {
}
