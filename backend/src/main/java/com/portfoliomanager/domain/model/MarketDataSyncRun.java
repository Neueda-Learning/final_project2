package com.portfoliomanager.domain.model;

import com.portfoliomanager.domain.SyncStatus;
import com.portfoliomanager.domain.SyncStage;
import com.portfoliomanager.domain.SyncTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "market_data_sync_run")
public class MarketDataSyncRun {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status = SyncStatus.RUNNING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStage stage = SyncStage.QUEUED;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false)
    private SyncTrigger triggeredBy = SyncTrigger.SCHEDULE;

    protected MarketDataSyncRun() {}
}
