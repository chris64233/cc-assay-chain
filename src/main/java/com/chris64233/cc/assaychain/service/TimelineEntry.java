package com.chris64233.cc.assaychain.service;

import java.time.Instant;

/** 统一的事件时间线条目。 */
public record TimelineEntry(
        String type,
        String eventNo,
        String sampleExternalNo,
        String summary,
        Instant eventTime) {
}
