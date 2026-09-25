package com.chris64233.cc.assaychain.api.dto;

import java.math.BigDecimal;

/** 收样请求。 */
public record ReceiveRequest(
        String externalNo,
        String miningArea,
        BigDecimal mass,
        String custodian) {
}
