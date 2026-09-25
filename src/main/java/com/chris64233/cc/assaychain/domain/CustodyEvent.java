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
 * 实验室交接事件。发起后 PENDING，指定实验室确认后 CONFIRMED。
 * 事件一经确认不可变更。
 */
@Entity
@Table(
        name = "custody_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_custody_event_no",
                columnNames = "event_no"))
public class CustodyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, length = 64, updatable = false)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_id", nullable = false, updatable = false)
    private Sample sample;

    @Column(name = "from_custodian", nullable = false, length = 128, updatable = false)
    private String fromCustodian;

    @Column(name = "to_lab", nullable = false, length = 128, updatable = false)
    private String toLab;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CustodyStatus status = CustodyStatus.PENDING;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @PrePersist
    void onCreate() {
        if (initiatedAt == null) {
            initiatedAt = Instant.now();
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

    public String getFromCustodian() {
        return fromCustodian;
    }

    public void setFromCustodian(String fromCustodian) {
        this.fromCustodian = fromCustodian;
    }

    public String getToLab() {
        return toLab;
    }

    public void setToLab(String toLab) {
        this.toLab = toLab;
    }

    public CustodyStatus getStatus() {
        return status;
    }

    public void setStatus(CustodyStatus status) {
        this.status = status;
    }

    public Instant getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(Instant initiatedAt) {
        this.initiatedAt = initiatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
