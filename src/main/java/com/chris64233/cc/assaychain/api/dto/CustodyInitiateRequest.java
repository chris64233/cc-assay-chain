package com.chris64233.cc.assaychain.api.dto;

/** 发起交接请求：当前保管方 -> 指定实验室。 */
public record CustodyInitiateRequest(
        String eventNo,
        String sampleExternalNo,
        String fromCustodian,
        String toLab) {
}
