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
 * 检测结果事件：只能由当前持有叶子样本的实验室提交。
 * (样本, 检测项目) 数据库唯一约束保证每个项目最多一个有效结果，且不可覆盖。
 */
@Entity
@Table(
        name = "assay_result",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_result_event_no", columnNames = "event_no"),
                @UniqueConstraint(name = "uk_result_sample_item", columnNames = {"sample_id", "test_item"})
        }
)
public class AssayResult {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entity_seq_gen")
    @SequenceGenerator(name = "entity_seq_gen", sequenceName = "entity_seq", allocationSize = 1)
    private Long id;

    @Column(name = "event_no", nullable = false, length = 64)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_id", nullable = false)
    private Sample sample;

    @Column(name = "test_item", nullable = false, length = 64)
    private String testItem;

    @Column(name = "value", nullable = false, precision = 24, scale = 6)
    private BigDecimal value;

    @Column(name = "unit", nullable = false, length = 32)
    private String unit;

    @Column(name = "submitted_lab", nullable = false, length = 64)
    private String submittedLab;

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

    public Sample getSample() {
        return sample;
    }

    public void setSample(Sample sample) {
        this.sample = sample;
    }

    public String getTestItem() {
        return testItem;
    }

    public void setTestItem(String testItem) {
        this.testItem = testItem;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getSubmittedLab() {
        return submittedLab;
    }

    public void setSubmittedLab(String submittedLab) {
        this.submittedLab = submittedLab;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
