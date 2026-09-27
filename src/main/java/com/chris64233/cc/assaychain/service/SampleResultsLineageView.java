package com.chris64233.cc.assaychain.service;

import java.util.List;

/**
 * 样本谱系与检测结果的联合查询视图：
 * 完整谱系（祖先/后代/事件时间线）加上该样本每个检测项目的
 * 当前有效结果、版本链与复核记录。
 */
public record SampleResultsLineageView(
        LineageView lineage,
        List<ResultHistoryView> results) {
}
