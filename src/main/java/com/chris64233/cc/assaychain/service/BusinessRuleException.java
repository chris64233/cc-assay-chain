package com.chris64233.cc.assaychain.service;

/**
 * 业务规则校验失败：参数非法、状态不允许、质量不守恒等（HTTP 400）。
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
