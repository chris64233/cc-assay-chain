package com.chris64233.cc.assaychain.domain;

/**
 * 检测结果版本状态。
 * PENDING：实验室已提交，等待复核；
 * EFFECTIVE：复核/更正审批通过，是对外有效版本；
 * REJECTED：复核未通过；
 * SUPERSEDED：曾生效，后被更正批准产生的新版本取代。
 */
public enum ResultStatus {
    PENDING,
    EFFECTIVE,
    REJECTED,
    SUPERSEDED
}
