package com.chris64233.cc.assaychain.domain;

/** 更正申请状态。 */
public enum CorrectionStatus {
    /** 已申请，等待审批。 */
    PENDING,
    /** 审批通过，已生成新版本。 */
    APPROVED,
    /** 审批驳回。 */
    REJECTED
}
