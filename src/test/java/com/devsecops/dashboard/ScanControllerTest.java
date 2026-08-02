package com.devsecops.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
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
    void runScan_validHttpUrlWithoutRiskLevels_delegatesWithEmptySet() {
        String targetUrl = "http://juice-shop:3000";
        List<ScanFinding> expected = List.of();
        when(scanService.runFullScan(targetUrl, Set.of())).thenReturn(expected);

        List<ScanFinding> result = controller.runScan(targetUrl, null);

        assertThat(result).isSameAs(expected);
        verify(scanService).runFullScan(targetUrl, Set.of());
    }

    @Test
    void runScan_riskLevelsAreParsedCaseInsensitivelyAndTrimmed() {
        String targetUrl = "https://example.com";
        when(scanService.runFullScan(eq(targetUrl), eq(Set.of(ScanRiskLevel.HIGH, ScanRiskLevel.MEDIUM, ScanRiskLevel.LOW))))
                .thenReturn(List.of());

        controller.runScan(targetUrl, "high, Medium,LOW");

        verify(scanService).runFullScan(targetUrl, Set.of(ScanRiskLevel.HIGH, ScanRiskLevel.MEDIUM, ScanRiskLevel.LOW));
    }

    @Test
    void runScan_invalidRiskLevel_returns400WithoutCallingScanService() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.runScan("http://example.com", "SUPER_HIGH"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scanService);
    }

    @Test
    void runScan_malformedUrlSyntax_returns400WithoutCallingScanService() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.runScan("http://[invalid-host", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scanService);
    }

    @Test
    void runScan_nonHttpScheme_returns400WithoutCallingScanService() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.runScan("ftp://example.com", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scanService);
    }

    @Test
    void runScan_missingHost_returns400WithoutCallingScanService() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.runScan("http:///path", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scanService);
    }

    @Test
    void listFindings_delegatesToRepository() {
        List<ScanFinding> expected = List.of();
        when(scanFindingRepository.findAllByOrderByDetectedAtDesc()).thenReturn(expected);

        List<ScanFinding> result = controller.listFindings();

        assertThat(result).isSameAs(expected);
    }
}
