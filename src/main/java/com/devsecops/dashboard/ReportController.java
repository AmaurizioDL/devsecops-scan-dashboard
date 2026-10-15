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

    @GetMapping("/api/reports/executive")
    public ResponseEntity<byte[]> getExecutiveReportPdf(@RequestParam(required = false) String targetUrl) {
        return pdfResponse(vulnerabilityReportService.generateExecutivePdf(targetUrl), "executive-security-summary.pdf");
    }

    /** /api/reports/pdf se mantiene como alias del reporte tecnico para no romper links existentes. */
    @GetMapping({"/api/reports/technical", "/api/reports/pdf"})
    public ResponseEntity<byte[]> getTechnicalReportPdf(@RequestParam(required = false) String targetUrl) {
        return pdfResponse(vulnerabilityReportService.generateTechnicalPdf(targetUrl), "technical-vulnerability-report.pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(pdf);
    }
}
