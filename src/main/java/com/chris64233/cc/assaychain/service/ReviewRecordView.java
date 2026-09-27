package com.chris64233.cc.assaychain.service;

import java.time.Instant;

/** 复核/审批记录视图。 */
public record ReviewRecordView(
        String eventNo,
        String kind,
        String resultEventNo,
        String correctionNo,
        String decision,
        String reviewedBy,
        String comment,
        Instant reviewedAt) {
}
