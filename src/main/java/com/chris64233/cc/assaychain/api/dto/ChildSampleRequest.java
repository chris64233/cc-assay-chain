package com.chris64233.cc.assaychain.api.dto;

import java.math.BigDecimal;

/** 分样产生的单个子样本。 */
public record ChildSampleRequest(
        String externalNo,
        BigDecimal mass,
        String custodian) {
}
