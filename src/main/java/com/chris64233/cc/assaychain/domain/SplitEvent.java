package com.chris64233.cc.assaychain.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 分样事件（不可变）。一次分样把一个叶子样本拆成多个子样本并登记处理损耗。
 */
@Entity
@Table(
        name = "split_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_split_event_no",
                columnNames = "event_no"))
public class SplitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, length = 64, updatable = false)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_sample_id", nullable = false, updatable = false)
    private Sample parentSample;

    @Column(name = "parent_mass", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal parentMass;

    /** 子样本质量之和。 */
    @Column(name = "child_mass_sum", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal childMassSum;

    /** 委托方声明的处理损耗。 */
    @Column(name = "declared_loss_mass", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal declaredLossMass;

    /** 按质量守恒闭差记账的实际损耗 = 分样前质量 - 子样本质量之和。 */
    @Column(name = "loss_mass", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal lossMass;

    @Column(name = "child_count", nullable = false, updatable = false)
    private int childCount;

    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant eventTime;

    @PrePersist
    void onCreate() {
        if (eventTime == null) {
            eventTime = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
    }

    public Sample getParentSample() {
        return parentSample;
    }

    public void setParentSample(Sample parentSample) {
        this.parentSample = parentSample;
    }

    public BigDecimal getParentMass() {
        return parentMass;
    }

    public void setParentMass(BigDecimal parentMass) {
        this.parentMass = parentMass;
    }

    public BigDecimal getChildMassSum() {
        return childMassSum;
    }

    public void setChildMassSum(BigDecimal childMassSum) {
        this.childMassSum = childMassSum;
    }

    public BigDecimal getDeclaredLossMass() {
        return declaredLossMass;
    }

    public void setDeclaredLossMass(BigDecimal declaredLossMass) {
        this.declaredLossMass = declaredLossMass;
    }

    public BigDecimal getLossMass() {
        return lossMass;
    }

    public void setLossMass(BigDecimal lossMass) {
        this.lossMass = lossMass;
    }

    public int getChildCount() {
        return childCount;
    }

    public void setChildCount(int childCount) {
        this.childCount = childCount;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }
}
