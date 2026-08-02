package com.devsecops.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class ScanController {

    private final ScanService scanService;
    private final ScanFindingRepository scanFindingRepository;

    public ScanController(ScanService scanService, ScanFindingRepository scanFindingRepository) {
        this.scanService = scanService;
        this.scanFindingRepository = scanFindingRepository;
    }

    @PostMapping("/api/scans")
    public ResponseEntity<ScanRunView> startScan(@RequestParam String targetUrl,
                                                  @RequestParam(required = false) String riskLevels) {
        validateTargetUrl(targetUrl);
        Set<ScanRiskLevel> parsedRiskLevels = parseRiskLevels(riskLevels);
        try {
            ScanRun run = scanService.startScan(targetUrl, parsedRiskLevels);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ScanRunView.from(run));
        } catch (ScanAlreadyRunningException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/api/scans/{scanId}")
    public ScanRunView getScanStatus(@PathVariable String scanId) {
        return scanService.findRun(scanId)
                .map(ScanRunView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan no encontrado: " + scanId));
    }

    @PostMapping("/api/scans/{scanId}/stop")
    public ScanRunView stopScan(@PathVariable String scanId) {
        try {
            scanService.requestStop(scanId);
        } catch (ScanNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return scanService.findRun(scanId)
                .map(ScanRunView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan no encontrado: " + scanId));
    }

    /**
     * Convierte "HIGH,MEDIUM" (case-insensitive) en un Set&lt;ScanRiskLevel&gt;.
     * riskLevels ausente o en blanco significa "sin filtro" (cobertura completa).
     */
    private Set<ScanRiskLevel> parseRiskLevels(String riskLevels) {
        if (!StringUtils.hasText(riskLevels)) {
            return Set.of();
        }
        try {
            return Arrays.stream(riskLevels.split(","))
                    .filter(StringUtils::hasText)
                    .map(ScanRiskLevel::fromString)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "riskLevels invalido: '" + riskLevels + "'. Valores permitidos: HIGH, MEDIUM, LOW");
        }
    }

    private void validateTargetUrl(String targetUrl) {
        URI uri;
        try {
            uri = new URI(targetUrl);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetUrl invalido: " + targetUrl);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetUrl debe usar esquema http o https");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetUrl debe incluir un host valido");
        }
    }

    @GetMapping("/api/findings")
    public List<ScanFinding> listFindings() {
        return scanFindingRepository.findAllByOrderByDetectedAtDesc();
    }
}
