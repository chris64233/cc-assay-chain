package com.chris64233.cc.assaychain.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 检测结果版本（不可变内容，状态可流转）。
 * 同一（样本，检测项目）的历次提交与更正形成 version_no 递增的版本链；
 * 任意时刻链上最多一个 EFFECTIVE 版本。已生效结果不得直接覆盖，
 * 只能通过更正申请产生新版本，旧版本置为 SUPERSEDED 供历史还原。
 */
@Entity
@Table(
        name = "assay_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_assay_event_no",
                columnNames = "event_no"))
public class AssayEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部结果号（更正产生的新版本使用 COR-{更正号}）。 */
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

    /** 提交结果时样本的保管实验室（更正版本为更正申请人，冗余留痕，不可变）。 */
    @Column(name = "submitted_by", nullable = false, length = 128, updatable = false)
    private String submittedBy;

    /** 版本状态：待复核 / 生效 / 被取代 / 已驳回。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AssayStatus status = AssayStatus.PENDING_REVIEW;

    /** 版本号：同一（样本，检测项目）链上从 1 递增。 */
    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo = 1;

    /** 复核/审批人（生效或驳回后留痕）。 */
    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    /** 复核/审批时间。 */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** 生效时间；历史时点查询以此还原任意时点的有效结果。 */
    @Column(name = "effective_at")
    private Instant effectiveAt;

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

    public AssayStatus getStatus() {
        return status;
    }

    public void setStatus(AssayStatus status) {
        this.status = status;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }
}
