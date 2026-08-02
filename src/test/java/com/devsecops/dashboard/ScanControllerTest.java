package com.devsecops.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanControllerTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ScanFindingRepository scanFindingRepository;

    private ScanController controller;

    @BeforeEach
    void setUp() {
        controller = new ScanController(scanService, scanFindingRepository);
    }

    @Test
    void startScan_validHttpUrlWithoutRiskLevels_returns202WithRunView() {
        String targetUrl = "http://juice-shop:3000";
        ScanRun run = new ScanRun(targetUrl, Set.of(), 15);
        when(scanService.startScan(targetUrl, Set.of())).thenReturn(run);

        ResponseEntity<ScanRunView> response = controller.startScan(targetUrl, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().scanId()).isEqualTo(run.getScanId());
        assertThat(response.getBody().phase()).isEqualTo(ScanPhase.SPIDER);
        verify(scanService).startScan(targetUrl, Set.of());
    }

    @Test
    void startScan_riskLevelsAreParsedCaseInsensitivelyAndTrimmed() {
        String targetUrl = "https://example.com";
        Set<ScanRiskLevel> expectedLevels = Set.of(ScanRiskLevel.HIGH, ScanRiskLevel.MEDIUM, ScanRiskLevel.LOW);
        ScanRun run = new ScanRun(targetUrl, expectedLevels, 15);
        when(scanService.startScan(eq(targetUrl), eq(expectedLevels))).thenReturn(run);

        controller.startScan(targetUrl, "high, Medium,LOW");

        verify(scanService).startScan(targetUrl, expectedLevels);
    }

    @Test
    void startScan_anotherScanAlreadyRunning_returns409() {
        when(scanService.startScan(anyString(), anySet()))
                .thenThrow(new ScanAlreadyRunningException("existing-id"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.startScan("http://example.com", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getReason()).contains("existing-id");
    }

    @Test
    void startScan_invalidRiskLevel_returns400WithoutCallingScanService() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.startScan("http://example.com", "SUPER_HIGH"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scanService);
    }

    @Test
    void startScan_malformedUrlSyntax_returns400WithoutCallingScanService() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.startScan("http://[invalid-host", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scanService);
    }

    @Test
    void startScan_nonHttpScheme_returns400WithoutCallingScanService() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.startScan("ftp://example.com", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scanService);
    }

    @Test
    void startScan_missingHost_returns400WithoutCallingScanService() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.startScan("http:///path", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scanService);
    }

    @Test
    void getScanStatus_found_returnsView() {
        ScanRun run = new ScanRun("http://a", Set.of(), 15);
        when(scanService.findRun(run.getScanId())).thenReturn(Optional.of(run));

        ScanRunView view = controller.getScanStatus(run.getScanId());

        assertThat(view.scanId()).isEqualTo(run.getScanId());
    }

    @Test
    void getScanStatus_unknownId_returns404() {
        when(scanService.findRun("nope")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getScanStatus("nope"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void stopScan_found_requestsStopAndReturnsView() {
        ScanRun run = new ScanRun("http://a", Set.of(), 15);
        when(scanService.findRun(run.getScanId())).thenReturn(Optional.of(run));

        ScanRunView view = controller.stopScan(run.getScanId());

        verify(scanService).requestStop(run.getScanId());
        assertThat(view.scanId()).isEqualTo(run.getScanId());
    }

    @Test
    void stopScan_unknownId_returns404() {
        doThrow(new ScanNotFoundException("nope")).when(scanService).requestStop("nope");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.stopScan("nope"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listFindings_delegatesToRepository() {
        List<ScanFinding> expected = List.of();
        when(scanFindingRepository.findAllByOrderByDetectedAtDesc()).thenReturn(expected);

        List<ScanFinding> result = controller.listFindings();

        assertThat(result).isSameAs(expected);
    }
}
