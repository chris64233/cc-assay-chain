package com.chris64233.cc.assaychain.domain;

/**
 * 交接事件状态：PENDING 为保管方已发起、等待实验室确认；CONFIRMED 为实验室已确认，
 * 确认后事件内容不可再变更。
 */
public enum HandoverStatus {
    PENDING,
    CONFIRMED
}
