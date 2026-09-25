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
 * 检测结果事件（不可变、不可覆盖）。同一样本的同一检测项目最多一个有效结果。
 */
@Entity
@Table(
        name = "assay_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_assay_event_no", columnNames = "event_no"),
                @UniqueConstraint(name = "uk_assay_sample_item", columnNames = {"sample_id", "item_code"})
        })
public class AssayEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, length = 64, updatable = false)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_id", nullable = false, updatable = false)
    private Sample sample;

    /** 检测项目编码，如 AU_GRADE。 */
    @Column(name = "item_code", nullable = false, length = 64, updatable = false)
    private String itemCode;

    /** 检测结果数值，固定精度 19,6。 */
    @Column(name = "result_value", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal resultValue;

    /** 结果计量单位，如 g/t。 */
    @Column(name = "unit", nullable = false, length = 32, updatable = false)
    private String unit;

    /** 提交结果时样本的保管实验室（冗余留痕，不可变）。 */
    @Column(name = "submitted_by", nullable = false, length = 128, updatable = false)
    private String submittedBy;

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

    public Sample getSample() {
        return sample;
    }

    public void setSample(Sample sample) {
        this.sample = sample;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getResultValue() {
        return resultValue;
    }

    public void setResultValue(BigDecimal resultValue) {
        this.resultValue = resultValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }
}
