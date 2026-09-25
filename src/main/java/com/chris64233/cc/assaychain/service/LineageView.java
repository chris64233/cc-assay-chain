package com.chris64233.cc.assaychain.service;

import java.util.List;

/** 从任意样本向上/向下查询得到的完整谱系。 */
public record LineageView(
        SampleView origin,
        List<SampleView> ancestors,
        List<SampleView> descendants,
        List<TimelineEntry> timeline) {
}
