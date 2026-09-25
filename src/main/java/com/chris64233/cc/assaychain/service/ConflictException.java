package com.chris64233.cc.assaychain.service;

/**
 * 事件冲突：同事件号内容不一致、并发竞争、重复检测、双重保管等（HTTP 409）。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
