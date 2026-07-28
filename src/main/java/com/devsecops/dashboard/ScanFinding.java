package com.devsecops.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "scan_findings")
public class ScanFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Column(name = "scan_type", nullable = false, length = 20)
    private String scanType;

    @Column(name = "alert_name", nullable = false)
    private String alertName;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "cwe_id")
    private Integer cweId;

    @Column(name = "affected_url")
    private String affectedUrl;

    @Column(name = "description")
    private String description;

    @Column(name = "solution")
    private String solution;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    protected ScanFinding() {
    }

    public ScanFinding(String targetUrl, String scanType, String alertName, String riskLevel,
                        Integer cweId, String affectedUrl, String description, String solution) {
        this.targetUrl = targetUrl;
        this.scanType = scanType;
        this.alertName = alertName;
        this.riskLevel = riskLevel;
        this.cweId = cweId;
        this.affectedUrl = affectedUrl;
        this.description = description;
        this.solution = solution;
    }

    public Integer getId() {
        return id;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getScanType() {
        return scanType;
    }

    public void setScanType(String scanType) {
        this.scanType = scanType;
    }

    public String getAlertName() {
        return alertName;
    }

    public void setAlertName(String alertName) {
        this.alertName = alertName;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getCweId() {
        return cweId;
    }

    public void setCweId(Integer cweId) {
        this.cweId = cweId;
    }

    public String getAffectedUrl() {
        return affectedUrl;
    }

    public void setAffectedUrl(String affectedUrl) {
        this.affectedUrl = affectedUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }
}
