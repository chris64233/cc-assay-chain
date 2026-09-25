package com.chris64233.cc.assaychain.service;

import java.math.BigDecimal;
import java.time.Instant;

/** 单个样本的当前状态视图。 */
public record SampleView(
        String externalNo,
        String miningArea,
        BigDecimal mass,
        String custodian,
        boolean leaf,
        String parentExternalNo,
        Instant createdAt) {
}
