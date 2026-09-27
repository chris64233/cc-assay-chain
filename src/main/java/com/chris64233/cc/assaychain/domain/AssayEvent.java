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
 * 检测结果版本（不可变版本链上的一个节点）。
 *
 * <p>实验室提交后为 {@link ResultStatus#PENDING}，复核通过成为 {@link ResultStatus#EFFECTIVE}；
 * 更正批准时旧生效版本置为 {@link ResultStatus#SUPERSEDED} 并新增一个 EFFECTIVE 版本。
 * 每个（样本，检测项目）最多一个 EFFECTIVE 版本，由唯一约束 {@code uk_assay_effective}
 * （仅对 EFFECTIVE 行非空的 current_item_key）兜底。</p>
 */
@Entity
@Table(
        name = "assay_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_assay_event_no", columnNames = "event_no"),
                @UniqueConstraint(name = "uk_assay_version",
                        columnNames = {"sample_id", "item_code", "version_no"}),
                // current_item_key 仅在 EFFECTIVE 行上非空（见 setStatus），保证每项目至多一个生效版本
                @UniqueConstraint(name = "uk_assay_effective", columnNames = "current_item_key")
        })
public class AssayEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部结果号：提交/更正发布时由调用方提供，全局唯一、幂等。 */
    @Column(name = "event_no", nullable = false, length = 64, updatable = false)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_id", nullable = false, updatable = false)
    private Sample sample;

    /** 检测项目编码，如 AU_GRADE。 */
    @Column(name = "item_code", nullable = false, length = 64, updatable = false)
    private String itemCode;

    /** （样本，项目）内的版本序号，首版为 1，更正批准产生的新版本递增。 */
    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    /** 上一版本；首版为空，构成结果版本链。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_version_id", updatable = false)
    private AssayEvent prevVersion;

    /** 检测结果数值，固定精度 19,6。 */
    @Column(name = "result_value", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal resultValue;

    /** 结果计量单位，如 g/t。 */
    @Column(name = "unit", nullable = false, length = 32, updatable = false)
    private String unit;

    /** 提交结果时样本的保管实验室（冗余留痕，不可变）。 */
    @Column(name = "submitted_by", nullable = false, length = 128, updatable = false)
    private String submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ResultStatus status = ResultStatus.PENDING;

    /** 生效（复核/更正批准通过）时间。 */
    @Column(name = "effective_at")
    private Instant effectiveAt;

    /** 被后续更正版本取代的时间。 */
    @Column(name = "superseded_at")
    private Instant supersededAt;

    /**
     * 仅 EFFECTIVE 行保存 {@code sampleId + "#" + itemCode}，其余状态为 null。
     * 配合唯一约束实现每个（样本，项目）至多一个生效版本（H2/标准 SQL 唯一约束忽略 null）。
     */
    @Column(name = "current_item_key", length = 200)
    private String currentItemKey;

    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant eventTime;

    @PrePersist
    void onCreate() {
        if (eventTime == null) {
            eventTime = Instant.now();
        }
    }

    /** 状态迁移时维护生效版本唯一键。 */
    public void setStatus(ResultStatus status) {
        this.status = status;
        if (status == ResultStatus.EFFECTIVE) {
            this.currentItemKey = sample.getId() + "#" + itemCode;
        } else {
            this.currentItemKey = null;
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

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public AssayEvent getPrevVersion() {
        return prevVersion;
    }

    public void setPrevVersion(AssayEvent prevVersion) {
        this.prevVersion = prevVersion;
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

    public ResultStatus getStatus() {
        return status;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public void setSupersededAt(Instant supersededAt) {
        this.supersededAt = supersededAt;
    }

    public String getCurrentItemKey() {
        return currentItemKey;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }
}
