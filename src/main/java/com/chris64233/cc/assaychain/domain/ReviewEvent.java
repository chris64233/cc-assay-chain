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
 * 复核/审批事件（不可变留痕）。
 *
 * <p>两类：{@link ReviewKind#RESULT_REVIEW} 针对待复核结果版本；
 * {@link ReviewKind#CORRECTION_REVIEW} 针对更正申请。审批事件号全局唯一、幂等，
 * 同号异内容返回冲突。复核人必须与结果提交人不同。</p>
 */
@Entity
@Table(
        name = "review_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_event_no",
                columnNames = "event_no"))
public class ReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部审批事件号，全局唯一、幂等。 */
    @Column(name = "event_no", nullable = false, length = 64, updatable = false)
    private String eventNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24, updatable = false)
    private ReviewKind kind;

    /** 被决定的结果版本（结果复核即该版本；更正审批即更正产生的新版本审批对象）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_version_id", nullable = false, updatable = false)
    private AssayEvent resultVersion;

    /** 更正审批时关联的更正申请；结果复核时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_request_id", updatable = false)
    private CorrectionRequest correctionRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16, updatable = false)
    private ReviewDecision decision;

    /** 复核/审批人，必须与结果提交人不同。 */
    @Column(name = "reviewed_by", nullable = false, length = 128, updatable = false)
    private String reviewedBy;

    /** 审批意见，可选。 */
    @Column(name = "comment", length = 512, updatable = false)
    private String comment;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        if (reviewedAt == null) {
            reviewedAt = Instant.now();
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

    public ReviewKind getKind() {
        return kind;
    }

    public void setKind(ReviewKind kind) {
        this.kind = kind;
    }

    public AssayEvent getResultVersion() {
        return resultVersion;
    }

    public void setResultVersion(AssayEvent resultVersion) {
        this.resultVersion = resultVersion;
    }

    public CorrectionRequest getCorrectionRequest() {
        return correctionRequest;
    }

    public void setCorrectionRequest(CorrectionRequest correctionRequest) {
        this.correctionRequest = correctionRequest;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public void setDecision(ReviewDecision decision) {
        this.decision = decision;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
