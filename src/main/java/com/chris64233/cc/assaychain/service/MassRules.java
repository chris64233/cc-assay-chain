package com.chris64233.cc.assaychain.service;

import java.math.BigDecimal;

/** 质量精度与守恒常量及校验工具。 */
public final class MassRules {

    private MassRules() {
    }

    /** 质量固定小数位（克）。 */
    public static final int MASS_SCALE = 4;

    /** 检测结果固定小数位。 */
    public static final int RESULT_SCALE = 6;

    /**
     * 允许的严格舍入误差：子样本质量与损耗之和与分样前质量之差的绝对值
     * 不得超过 0.0001（质量精度的一个最小刻度，单位克）。
     */
    public static final BigDecimal MASS_TOLERANCE = new BigDecimal("0.0001");

    /** 校验质量为正数且不超过固定精度，返回规范化后的值。 */
    public static BigDecimal requirePositiveMass(BigDecimal value, String name) {
        if (value == null) {
            throw new BusinessRuleException(name + "不能为空");
        }
        if (value.signum() <= 0) {
            throw new BusinessRuleException(name + "必须为正数: " + value.toPlainString());
        }
        return requireScale(value, MASS_SCALE, name);
    }

    /** 校验非负且不超过固定精度，返回规范化后的值。 */
    public static BigDecimal requireNonNegativeMass(BigDecimal value, String name) {
        if (value == null) {
            throw new BusinessRuleException(name + "不能为空");
        }
        if (value.signum() < 0) {
            throw new BusinessRuleException(name + "不能为负: " + value.toPlainString());
        }
        return requireScale(value, MASS_SCALE, name);
    }

    /** 校验检测结果精度。 */
    public static BigDecimal requireResultScale(BigDecimal value) {
        if (value == null) {
            throw new BusinessRuleException("检测结果不能为空");
        }
        return requireScale(value, RESULT_SCALE, "检测结果");
    }

    private static BigDecimal requireScale(BigDecimal value, int maxScale, String name) {
        if (value.stripTrailingZeros().scale() > maxScale) {
            throw new BusinessRuleException(
                    name + "精度不能超过小数点后 " + maxScale + " 位: " + value.toPlainString());
        }
        return value.setScale(maxScale, java.math.RoundingMode.UNNECESSARY);
    }

    /** 绝对差是否在允许舍入误差内。 */
    public static boolean withinTolerance(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(MASS_TOLERANCE) <= 0;
    }
}
