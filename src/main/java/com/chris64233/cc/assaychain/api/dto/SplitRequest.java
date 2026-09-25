package com.chris64233.cc.assaychain.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** 分样请求：从一个叶子样本产生多个子样本并声明处理损耗。 */
public record SplitRequest(
        String eventNo,
        String parentExternalNo,
        BigDecimal declaredLossMass,
        List<ChildSampleRequest> children) {
}
