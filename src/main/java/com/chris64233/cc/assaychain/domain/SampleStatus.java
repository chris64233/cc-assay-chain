package com.chris64233.cc.assaychain.domain;

/**
 * 样本生命周期状态：ACTIVE 表示叶子样本（可继续分样/交接/检测），
 * SPLIT 表示已被分样消耗，不能再次交接或检测。
 */
public enum SampleStatus {
    ACTIVE,
    SPLIT
}
