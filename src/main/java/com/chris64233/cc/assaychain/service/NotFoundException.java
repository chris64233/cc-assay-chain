package com.chris64233.cc.assaychain.service;

/** 引用的样本或事件不存在（HTTP 404）。 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
