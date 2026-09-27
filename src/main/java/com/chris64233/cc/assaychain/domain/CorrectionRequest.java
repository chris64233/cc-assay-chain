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
 * 检测结果更正申请（不可变申请记录 + 可变审批状态）。
 *
 * <p>只能引用当前 EFFECTIVE 的结果版本，完整保存旧值、新值、原因与证据。
 * 更正号全局唯一、幂等：同号同内容重放返回原申请，同号异内容返回冲突。
 * 批准时形成新版本（见 {@link AssayEvent}），旧版本置 SUPERSEDED；历史版本不被删除，
 * 任意时点的有效结果可通过版本链与生效/失效时间还原。</p>
 */
@Entity
@Table(
        name = "correction_request",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_correction_no", columnNames = "correction_no"),
                // reserved_event_no 仅在 PENDING 行上非空（见 setStatus），保证待审批期间
                // 新版本结果号不被多笔申请同时占用；审批结束后释放，驳回的号段可再次使用
                @UniqueConstraint(name = "uk_correction_reserved_event_no",
                        columnNames = "reserved_event_no")
        })
public class CorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部更正号，全局唯一、幂等。 */
    @Column(name = "correction_no", nullable = false, length = 64, updatable = false)
    private String correctionNo;

    /** 更正批准后新版本将使用的外部结果号（申请时声明，批准时发布，保证结果号幂等）。 */
    @Column(name = "new_event_no", nullable = false, length = 64, updatable = false)
    private String newResultEventNo;

    /**
     * 仅 PENDING 行保存预留的新版本结果号，审批结束后置空。配合唯一约束实现待审批期间
     * 号段唯一占用，同时让被驳回申请声明的号段在事后可以再次使用（H2 唯一约束忽略 null）。
     */
    @Column(name = "reserved_event_no", length = 64)
    private String reservedEventNo;

    /** 被更正的原结果版本（申请时必须为 EFFECTIVE）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_version_id", nullable = false, updatable = false)
    private AssayEvent originalVersion;

    /** 更正批准后产生的新版本；申请阶段为空，批准时写入一次。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_version_id")
    private AssayEvent newVersion;

    /** 旧结果值（申请时留痕，不可变）。 */
    @Column(name = "old_value", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal oldValue;

    /** 旧单位。 */
    @Column(name = "old_unit", nullable = false, length = 32, updatable = false)
    private String oldUnit;

    /** 新结果值。 */
    @Column(name = "new_value", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal newValue;

    /** 新单位。 */
    @Column(name = "new_unit", nullable = false, length = 32, updatable = false)
    private String newUnit;

    /** 更正原因，如仪器故障、单位错误、录入错误。 */
    @Column(name = "reason", nullable = false, length = 512, updatable = false)
    private String reason;

    /** 证据描述或证据材料编号。 */
    @Column(name = "evidence", length = 512, updatable = false)
    private String evidence;

    /** 申请人。 */
    @Column(name = "requested_by", nullable = false, length = 128, updatable = false)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CorrectionStatus status = CorrectionStatus.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
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

    public String getNewResultEventNo() {
        return newResultEventNo;
    }

    public void setNewResultEventNo(String newResultEventNo) {
        this.newResultEventNo = newResultEventNo;
    }

    public AssayEvent getOriginalVersion() {
        return originalVersion;
    }

    public void setOriginalVersion(AssayEvent originalVersion) {
        this.originalVersion = originalVersion;
    }

    public AssayEvent getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(AssayEvent newVersion) {
        this.newVersion = newVersion;
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

    /** 状态迁移时维护待审批期间的新版本结果号占用键。 */
    public void setStatus(CorrectionStatus status) {
        this.status = status;
        this.reservedEventNo = status == CorrectionStatus.PENDING ? newResultEventNo : null;
    }

    public String getReservedEventNo() {
        return reservedEventNo;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
