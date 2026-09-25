package com.chris64233.cc.assaychain.api.dto;

/** 确认交接请求：由指定接收实验室发起确认。 */
public record CustodyConfirmRequest(
        String eventNo,
        String confirmedBy) {
}
