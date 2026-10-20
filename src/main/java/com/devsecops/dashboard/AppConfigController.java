package com.devsecops.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime settings for the static trigger page (src/main/resources/static/index.html), read
 * from env vars so they can change without rebuilding the image. The React frontend gets the
 * same settings from its own nginx container.
 */
@RestController
public class AppConfigController {

    private static final MediaType JAVASCRIPT = MediaType.parseMediaType("text/javascript;charset=UTF-8");

    private final String grafanaUrl;

    public AppConfigController(@Value("${app.grafana-url:}") String grafanaUrl) {
        this.grafanaUrl = grafanaUrl == null ? "" : grafanaUrl.trim();
    }

    /** Empty grafanaUrl = the page derives it from its own URL. */
    @GetMapping("/config.js")
    public ResponseEntity<String> getConfigScript() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(JAVASCRIPT)
                .body("window.APP_CONFIG = { grafanaUrl: \"" + escapeJsString(grafanaUrl) + "\" };\n");
    }

    /** Escapes a value for a double-quoted JS string literal. */
    static String escapeJsString(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case ' ' -> out.append("\\u2028");
                case ' ' -> out.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
