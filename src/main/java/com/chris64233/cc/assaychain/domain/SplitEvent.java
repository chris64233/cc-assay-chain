package com.chris64233.cc.assaychain.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 分样事件：一次分样把一个叶子样本变成多个子样本并声明处理损耗。
 * 事件写入后不可变，eventNo 全局唯一并承担幂等键职责。
 */
@Entity
@Table(
        name = "split_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_split_event_no", columnNames = "event_no")
)
public class SplitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entity_seq_gen")
    @SequenceGenerator(name = "entity_seq_gen", sequenceName = "entity_seq", allocationSize = 1)
    private Long id;

    @Column(name = "event_no", nullable = false, length = 64)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_sample_id", nullable = false)
    private Sample parentSample;

    @Column(name = "pre_split_mass", nullable = false, precision = 24, scale = 3)
    private BigDecimal preSplitMass;

    @Column(name = "loss", nullable = false, precision = 24, scale = 3)
    private BigDecimal loss;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getPreSplitMass() {
        return preSplitMass;
    }

    public void setPreSplitMass(BigDecimal preSplitMass) {
        this.preSplitMass = preSplitMass;
    }

    public BigDecimal getLoss() {
        return loss;
    }

    public void setLoss(BigDecimal loss) {
        this.loss = loss;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
