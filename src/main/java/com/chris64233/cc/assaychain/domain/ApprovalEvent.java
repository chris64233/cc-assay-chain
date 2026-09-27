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

import java.time.Instant;

/**
 * 审批事件（复核 / 更正审批的决定记录，不可变）。
 * 审批事件号全局唯一，是审批动作的幂等键：
 * 同号同内容重放返回原记录，同号异内容返回冲突。
 */
@Entity
@Table(
        name = "approval_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_approval_no",
                columnNames = "approval_no"))
public class ApprovalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 审批事件号，幂等键。 */
    @Column(name = "approval_no", nullable = false, length = 64, updatable = false)
    private String approvalNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24, updatable = false)
    private ApprovalKind kind;

    /** 被复核/被更正的结果版本（更正审批时指向被更正的原版本）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assay_event_id", nullable = false, updatable = false)
    private AssayEvent assayEvent;

    /** 更正审批时关联的更正申请；复核时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_id", updatable = false)
    private AssayCorrection correction;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16, updatable = false)
    private ApprovalDecision decision;

    /** 复核人/审批人，必须与原提交人（或更正申请人）不同。 */
    @Column(name = "decided_by", nullable = false, length = 128, updatable = false)
    private String decidedBy;

    @Column(name = "comment", length = 512, updatable = false)
    private String comment;

    /** 更正审批通过时生成的新版本结果号；其余情况为空。 */
    @Column(name = "new_event_no", length = 64, updatable = false)
    private String newEventNo;

    @Column(name = "decision_time", nullable = false, updatable = false)
    private Instant decisionTime;

    @PrePersist
    void onCreate() {
        if (decisionTime == null) {
            decisionTime = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getApprovalNo() {
        return approvalNo;
    }

    public void setApprovalNo(String approvalNo) {
        this.approvalNo = approvalNo;
    }

    public ApprovalKind getKind() {
        return kind;
    }

    public void setKind(ApprovalKind kind) {
        this.kind = kind;
    }

    public AssayEvent getAssayEvent() {
        return assayEvent;
    }

    public void setAssayEvent(AssayEvent assayEvent) {
        this.assayEvent = assayEvent;
    }

    public AssayCorrection getCorrection() {
        return correction;
    }

    public void setCorrection(AssayCorrection correction) {
        this.correction = correction;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public void setDecision(ApprovalDecision decision) {
        this.decision = decision;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getNewEventNo() {
        return newEventNo;
    }

    public void setNewEventNo(String newEventNo) {
        this.newEventNo = newEventNo;
    }

    public Instant getDecisionTime() {
        return decisionTime;
    }

    public void setDecisionTime(Instant decisionTime) {
        this.decisionTime = decisionTime;
    }
}
