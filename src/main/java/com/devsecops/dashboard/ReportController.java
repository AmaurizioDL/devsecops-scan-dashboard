package com.devsecops.dashboard;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final VulnerabilityReportService vulnerabilityReportService;

    public ReportController(VulnerabilityReportService vulnerabilityReportService) {
        this.vulnerabilityReportService = vulnerabilityReportService;
    }

    @GetMapping("/api/reports/pdf")
    public ResponseEntity<byte[]> getVulnerabilityReportPdf(@RequestParam(required = false) String targetUrl) {
        byte[] pdf = vulnerabilityReportService.generatePdf(targetUrl);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename("vulnerability-report.pdf")
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(pdf);
    }
}
