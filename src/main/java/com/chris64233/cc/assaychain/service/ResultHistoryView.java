package com.chris64233.cc.assaychain.service;

import java.util.List;

/**
 * （样本，检测项目）的结果版本链联合视图：当前有效版本、按版本号排列的完整版本链、
 * 与各版本关联的全部复核/审批记录。
 */
public record ResultHistoryView(
        String sampleExternalNo,
        String itemCode,
        ResultVersionView current,
        List<ResultVersionView> versions,
        List<ReviewRecordView> reviews) {
}
