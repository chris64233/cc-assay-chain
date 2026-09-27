package com.chris64233.cc.assaychain.domain;

/** 审批事件类型。 */
public enum ApprovalKind {
    /** 对实验室提交结果的复核。 */
    SUBMISSION_REVIEW,
    /** 对更正申请的审批。 */
    CORRECTION_DECISION
}
