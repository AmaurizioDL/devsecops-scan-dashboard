package com.devsecops.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigControllerTest {

    @Test
    void getConfigScript_withoutGrafanaUrl_leavesItEmptyAndIsNeverCached() {
        ResponseEntity<String> response = new AppConfigController("").getConfigScript();

        assertThat(response.getBody()).isEqualTo("window.APP_CONFIG = { grafanaUrl: \"\" };\n");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeaders().getContentType()).hasToString("text/javascript;charset=UTF-8");
    }

    @Test
    void getConfigScript_writesTheConfiguredUrlTrimmed() {
        ResponseEntity<String> response = new AppConfigController(" https://grafana.example.com ").getConfigScript();

        assertThat(response.getBody()).contains("grafanaUrl: \"https://grafana.example.com\"");
    }

    @Test
    void escapeJsString_cannotBreakOutOfTheStringOrScript() {
        assertThat(AppConfigController.escapeJsString("a\"b\\c</script>\n"))
                .isEqualTo("a\\\"b\\\\c\\u003c/script\\u003e\\u000a");
    }
}
