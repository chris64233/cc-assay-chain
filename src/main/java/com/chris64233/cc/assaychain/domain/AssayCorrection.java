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
 * 结果更正申请。引用原结果版本，完整保存旧值、新值、原因与证据。
 * 审批通过后以原版本为基础生成新版本，原版本置为 SUPERSEDED。
 */
@Entity
@Table(
        name = "assay_correction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_correction_no",
                columnNames = "correction_no"))
public class AssayCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 更正号，幂等键：同号同内容重放，同号异内容冲突。 */
    @Column(name = "correction_no", nullable = false, length = 64, updatable = false)
    private String correctionNo;

    /** 被更正的原结果版本。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assay_event_id", nullable = false, updatable = false)
    private AssayEvent assayEvent;

    /** 原结果值快照（申请时留痕，不随后续版本变化）。 */
    @Column(name = "old_value", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal oldValue;

    @Column(name = "old_unit", nullable = false, length = 32, updatable = false)
    private String oldUnit;

    /** 更正后的新值。 */
    @Column(name = "new_value", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal newValue;

    @Column(name = "new_unit", nullable = false, length = 32, updatable = false)
    private String newUnit;

    /** 更正原因（如仪器故障、单位错误、录入错误）。 */
    @Column(name = "reason", nullable = false, length = 512, updatable = false)
    private String reason;

    /** 支撑证据（如复检报告编号、影像链接）。 */
    @Column(name = "evidence", nullable = false, length = 512, updatable = false)
    private String evidence;

    @Column(name = "requested_by", nullable = false, length = 128, updatable = false)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CorrectionStatus status = CorrectionStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getCorrectionNo() {
        return correctionNo;
    }

    public void setCorrectionNo(String correctionNo) {
        this.correctionNo = correctionNo;
    }

    public AssayEvent getAssayEvent() {
        return assayEvent;
    }

    public void setAssayEvent(AssayEvent assayEvent) {
        this.assayEvent = assayEvent;
    }

    public BigDecimal getOldValue() {
        return oldValue;
    }

    public void setOldValue(BigDecimal oldValue) {
        this.oldValue = oldValue;
    }

    public String getOldUnit() {
        return oldUnit;
    }

    public void setOldUnit(String oldUnit) {
        this.oldUnit = oldUnit;
    }

    public BigDecimal getNewValue() {
        return newValue;
    }

    public void setNewValue(BigDecimal newValue) {
        this.newValue = newValue;
    }

    public String getNewUnit() {
        return newUnit;
    }

    public void setNewUnit(String newUnit) {
        this.newUnit = newUnit;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public CorrectionStatus getStatus() {
        return status;
    }

    public void setStatus(CorrectionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
