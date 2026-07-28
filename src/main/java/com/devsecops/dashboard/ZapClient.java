package com.devsecops.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ZapClient {

    private final RestClient restClient;
    private final String apiKey;

    public ZapClient(RestClient.Builder builder,
                      @Value("${zap.api.base-url}") String baseUrl,
                      @Value("${zap.api.key}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public String startSpiderScan(String targetUrl) {
        ScanActionResponse response = restClient.get()
                .uri(uriBuilder -> withApiKey(uriBuilder.path("/JSON/spider/action/scan/")
                        .queryParam("url", targetUrl)).build())
                .retrieve()
                .body(ScanActionResponse.class);
        return response.scan();
    }

    public int getSpiderStatus(String scanId) {
        StatusResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/JSON/spider/view/status/")
                        .queryParam("scanId", scanId)
                        .build())
                .retrieve()
                .body(StatusResponse.class);
        return parseStatus(response.status());
    }

    public String startActiveScan(String targetUrl) {
        ScanActionResponse response = restClient.get()
                .uri(uriBuilder -> withApiKey(uriBuilder.path("/JSON/ascan/action/scan/")
                        .queryParam("url", targetUrl)).build())
                .retrieve()
                .body(ScanActionResponse.class);
        return response.scan();
    }

    public int getActiveScanStatus(String scanId) {
        StatusResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/JSON/ascan/view/status/")
                        .queryParam("scanId", scanId)
                        .build())
                .retrieve()
                .body(StatusResponse.class);
        return parseStatus(response.status());
    }

    /**
     * Restringe el active scan a solo los scanners cuya categoria de riesgo (segun
     * ZapScannerRiskCatalog) este en riskLevels. Si riskLevels es null o vacio,
     * se habilitan todos los scanners (comportamiento por defecto, sin filtrar).
     */
    public void configureScannersForRiskLevels(Set<ScanRiskLevel> riskLevels) {
        if (riskLevels == null || riskLevels.isEmpty()) {
            enableAllScanners();
            return;
        }
        disableAllScanners();
        Set<String> scannerIds = ZapScannerRiskCatalog.scannerIdsForRiskLevels(riskLevels);
        scannerIds.retainAll(getAvailableScannerIds());
        if (!scannerIds.isEmpty()) {
            enableScanners(scannerIds);
        }
    }

    /**
     * IDs de scanners realmente cargados en esta instancia de ZAP. El catalogo de
     * riesgo puede incluir reglas (p.ej. de calidad alpha/beta) que no siempre estan
     * disponibles segun la version de los addons instalados, asi que se cruza contra
     * esta lista antes de habilitar scanners para evitar errores "does_not_exist".
     */
    private Set<String> getAvailableScannerIds() {
        ScannersResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/JSON/ascan/view/scanners/").build())
                .retrieve()
                .body(ScannersResponse.class);
        return response.scanners().stream()
                .map(ScannerInfo::id)
                .collect(Collectors.toSet());
    }

    private void enableAllScanners() {
        restClient.get()
                .uri(uriBuilder -> withApiKey(uriBuilder.path("/JSON/ascan/action/enableAllScanners/")).build())
                .retrieve()
                .toBodilessEntity();
    }

    private void disableAllScanners() {
        restClient.get()
                .uri(uriBuilder -> withApiKey(uriBuilder.path("/JSON/ascan/action/disableAllScanners/")).build())
                .retrieve()
                .toBodilessEntity();
    }

    private void enableScanners(Set<String> scannerIds) {
        String ids = String.join(",", scannerIds);
        restClient.get()
                .uri(uriBuilder -> withApiKey(uriBuilder.path("/JSON/ascan/action/enableScanners/")
                        .queryParam("ids", ids)).build())
                .retrieve()
                .toBodilessEntity();
    }

    public List<ZapAlert> getAlerts(String baseUrl) {
        AlertsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/JSON/core/view/alerts/")
                        .queryParam("baseurl", baseUrl)
                        .build())
                .retrieve()
                .body(AlertsResponse.class);
        return response.alerts();
    }

    private UriBuilder withApiKey(UriBuilder uriBuilder) {
        return StringUtils.hasText(apiKey) ? uriBuilder.queryParam("apikey", apiKey) : uriBuilder;
    }

    private int parseStatus(String rawStatus) {
        try {
            return Integer.parseInt(rawStatus.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalStateException("Respuesta de estado invalida de ZAP: '" + rawStatus + "'", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ZapAlert(String id, String alert, String risk, String cweid, String url, String description, String solution) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScanActionResponse(String scan) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StatusResponse(String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AlertsResponse(List<ZapAlert> alerts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScannerInfo(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScannersResponse(List<ScannerInfo> scanners) {
    }
}
