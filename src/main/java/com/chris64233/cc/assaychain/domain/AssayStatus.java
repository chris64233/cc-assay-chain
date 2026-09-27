package com.chris64233.cc.assaychain.domain;

/** 检测结果版本状态。 */
public enum AssayStatus {
    /** 实验室已提交，等待复核。 */
    PENDING_REVIEW,
    /** 复核通过，对外有效。 */
    EFFECTIVE,
    /** 曾被更正产生的新版本取代，仅用于历史还原。 */
    SUPERSEDED,
    /** 复核驳回，从未生效。 */
    REJECTED
}
