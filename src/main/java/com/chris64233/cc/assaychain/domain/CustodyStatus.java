package com.chris64233.cc.assaychain.domain;

/** 交接事件状态。 */
public enum CustodyStatus {
    /** 保管方已发起，等待指定实验室确认。 */
    PENDING,
    /** 指定实验室已确认，保管方已变更。 */
    CONFIRMED
}
