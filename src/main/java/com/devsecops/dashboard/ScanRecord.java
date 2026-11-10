package com.devsecops.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One row of the scan history (table scan_runs). Unlike ScanRun, which only lives in memory
 * while a scan runs, this survives restarts: inserted as RUNNING when a scan starts and
 * completed with its final status and new-finding counts when it ends.
 */
@Entity
@Table(name = "scan_runs")
public class ScanRecord {

    static final String RUNNING = "RUNNING";

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Column(name = "risk_levels", length = 40)
    private String riskLevels;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "new_high", nullable = false)
    private int newHigh;

    @Column(name = "new_medium", nullable = false)
    private int newMedium;

    @Column(name = "new_low", nullable = false)
    private int newLow;

    @Column(name = "new_informational", nullable = false)
    private int newInformational;

    protected ScanRecord() {
    }

    static ScanRecord startedFrom(ScanRun run) {
        ScanRecord record = new ScanRecord();
        record.id = run.getScanId();
        record.targetUrl = run.getTargetUrl();
        // Empty = no filter (every active-scan rule ran), same meaning as the riskLevels param.
        record.riskLevels = run.getRiskLevels().stream()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        record.status = RUNNING;
        record.startedAt = LocalDateTime.ofInstant(run.getStartedAt(), ZoneId.systemDefault());
        return record;
    }

    /** Final status (DONE, STOPPED or FAILED) plus the findings this scan saved for the first time. */
    void finish(ScanPhase phase, List<ScanFinding> newFindings, String errorMessage, LocalDateTime finishedAt) {
        this.status = phase.name();
        this.errorMessage = errorMessage;
        this.finishedAt = finishedAt;
        this.newHigh = 0;
        this.newMedium = 0;
        this.newLow = 0;
        this.newInformational = 0;
        for (ScanFinding finding : newFindings) {
            switch (ReportRisk.normalizeRisk(finding.getRiskLevel())) {
                case ReportRisk.HIGH -> newHigh++;
                case ReportRisk.MEDIUM -> newMedium++;
                case ReportRisk.LOW -> newLow++;
                default -> newInformational++;
            }
        }
    }

    /** A scan left RUNNING by a backend that stopped mid-scan can never finish. */
    void markInterrupted(LocalDateTime now) {
        this.status = ScanPhase.FAILED.name();
        this.errorMessage = "The backend restarted while this scan was running.";
        this.finishedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getRiskLevels() {
        return riskLevels;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getNewHigh() {
        return newHigh;
    }

    public int getNewMedium() {
        return newMedium;
    }

    public int getNewLow() {
        return newLow;
    }

    public int getNewInformational() {
        return newInformational;
    }
}
