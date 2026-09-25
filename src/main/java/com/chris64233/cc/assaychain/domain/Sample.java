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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "sample",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sample_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_sample_external_no", columnNames = "external_sample_no")
        }
)
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entity_seq_gen")
    @SequenceGenerator(name = "entity_seq_gen", sequenceName = "entity_seq", allocationSize = 1)
    private Long id;

    /** 系统内部样本编号，全局唯一。 */
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    /** 外部样本号：仅原始收样样本有值，数据库唯一约束保护；分样子样本为空。 */
    @Column(name = "external_sample_no", length = 64)
    private String externalSampleNo;

    @Column(name = "mining_area", nullable = false, length = 128)
    private String miningArea;

    /** 质量，固定精度 scale=3（克，最小刻度 0.001g）。 */
    @Column(name = "mass", nullable = false, precision = 24, scale = 3)
    private BigDecimal mass;

    @Column(name = "current_custodian", nullable = false, length = 64)
    private String currentCustodian;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SampleStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Sample parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_event_id")
    private SplitEvent splitEvent;

    /** 物化谱系路径，形如 /1/4/9/，用于向下查询整棵子树。 */
    @Column(name = "material_path", nullable = false, length = 512)
    private String materialPath;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getExternalSampleNo() {
        return externalSampleNo;
    }

    public void setExternalSampleNo(String externalSampleNo) {
        this.externalSampleNo = externalSampleNo;
    }

    public String getMiningArea() {
        return miningArea;
    }

    public void setMiningArea(String miningArea) {
        this.miningArea = miningArea;
    }

    public BigDecimal getMass() {
        return mass;
    }

    public void setMass(BigDecimal mass) {
        this.mass = mass;
    }

    public String getCurrentCustodian() {
        return currentCustodian;
    }

    public void setCurrentCustodian(String currentCustodian) {
        this.currentCustodian = currentCustodian;
    }

    public SampleStatus getStatus() {
        return status;
    }

    public void setStatus(SampleStatus status) {
        this.status = status;
    }

    public Sample getParent() {
        return parent;
    }

    public void setParent(Sample parent) {
        this.parent = parent;
    }

    public SplitEvent getSplitEvent() {
        return splitEvent;
    }

    public void setSplitEvent(SplitEvent splitEvent) {
        this.splitEvent = splitEvent;
    }

    public String getMaterialPath() {
        return materialPath;
    }

    public void setMaterialPath(String materialPath) {
        this.materialPath = materialPath;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
