package com.chris64233.cc.assaychain.service;

import com.chris64233.cc.assaychain.domain.ApprovalDecision;
import com.chris64233.cc.assaychain.domain.ApprovalKind;

import java.time.Instant;

/** 复核/更正审批记录视图。 */
public record ReviewRecordView(
        String approvalNo,
        ApprovalKind kind,
        /** 被决定的结果版本号（外部结果号）。 */
        String resultEventNo,
        /** 更正审批时的更正号；复核时为空。 */
        String correctionNo,
        ApprovalDecision decision,
        String decidedBy,
        String comment,
        /** 更正批准生成的新版本结果号。 */
        String newEventNo,
        Instant decisionTime) {
}
