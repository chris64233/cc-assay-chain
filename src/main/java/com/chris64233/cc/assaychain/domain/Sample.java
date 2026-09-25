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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 矿样。原始矿样的 parent 为空；分样产生的子样本指向父样本。
 * 只有 leaf=true 的样本可以继续分样、交接与提交检测结果。
 */
@Entity
@Table(
        name = "sample",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sample_external_no",
                columnNames = "external_no"))
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一外部样本号，由数据库唯一约束保护。 */
    @Column(name = "external_no", nullable = false, length = 64, updatable = false)
    private String externalNo;

    @Column(name = "mining_area", nullable = false, length = 128)
    private String miningArea;

    /** 当前质量，BigDecimal 固定精度 19,4，单位克。 */
    @Column(name = "mass", nullable = false, precision = 19, scale = 4)
    private BigDecimal mass;

    /** 当前保管方（机构/实验室名称）。 */
    @Column(name = "custodian", nullable = false, length = 128)
    private String custodian;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Sample parent;

    /** 是否为叶子样本：分样后父样本置为 false。 */
    @Column(name = "is_leaf", nullable = false)
    private boolean leaf = true;

    /** 未确认交接事件的 id；存在待确认交接时不允许再次分样/发起交接。 */
    @Column(name = "pending_custody_event_id")
    private Long pendingCustodyEventId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

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

    public String getExternalNo() {
        return externalNo;
    }

    public void setExternalNo(String externalNo) {
        this.externalNo = externalNo;
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

    public String getCustodian() {
        return custodian;
    }

    public void setCustodian(String custodian) {
        this.custodian = custodian;
    }

    public Sample getParent() {
        return parent;
    }

    public void setParent(Sample parent) {
        this.parent = parent;
    }

    public boolean isLeaf() {
        return leaf;
    }

    public void setLeaf(boolean leaf) {
        this.leaf = leaf;
    }

    public Long getPendingCustodyEventId() {
        return pendingCustodyEventId;
    }

    public void setPendingCustodyEventId(Long pendingCustodyEventId) {
        this.pendingCustodyEventId = pendingCustodyEventId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
