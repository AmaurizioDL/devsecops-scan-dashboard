package com.devsecops.dashboard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OwaspTop10MapperTest {

    @Test
    void mapCwe_knownInjectionCwe_mapsToInjectionCategory() {
        assertThat(OwaspTop10Mapper.mapCwe(89)).isEqualTo(OwaspTop10Category.A03_INJECTION); // SQL Injection
        assertThat(OwaspTop10Mapper.mapCwe(79)).isEqualTo(OwaspTop10Category.A03_INJECTION); // XSS
    }

    @Test
    void mapCwe_knownAccessControlCwe_mapsToBrokenAccessControlCategory() {
        assertThat(OwaspTop10Mapper.mapCwe(22)).isEqualTo(OwaspTop10Category.A01_BROKEN_ACCESS_CONTROL); // Path Traversal
        assertThat(OwaspTop10Mapper.mapCwe(352)).isEqualTo(OwaspTop10Category.A01_BROKEN_ACCESS_CONTROL); // CSRF
    }

    @Test
    void mapCwe_knownMisconfigurationCwe_mapsToSecurityMisconfigurationCategory() {
        assertThat(OwaspTop10Mapper.mapCwe(611)).isEqualTo(OwaspTop10Category.A05_SECURITY_MISCONFIGURATION); // XXE
    }

    @Test
    void mapCwe_nullCwe_mapsToUnmapped() {
        assertThat(OwaspTop10Mapper.mapCwe(null)).isEqualTo(OwaspTop10Category.UNMAPPED);
    }

    @Test
    void mapCwe_unknownCwe_mapsToUnmapped() {
        assertThat(OwaspTop10Mapper.mapCwe(999999)).isEqualTo(OwaspTop10Category.UNMAPPED);
    }

    @Test
    void map_zapOwasp2021Tag_winsOverCweMapping() {
        // Tags reales de la regla CORS de ZAP (10098): CWE-264 + OWASP_2021_A01, y tags de otras ediciones
        Map<String, String> tags = Map.of(
                "OWASP_2021_A01", "https://owasp.org/Top10/2021/A01_2021-Broken_Access_Control/",
                "OWASP_2017_A05", "https://owasp.org/www-project-top-ten/2017/A5_2017-Broken_Access_Control.html",
                "OWASP_2025_A01", "https://owasp.org/Top10/2025/A01_2025-Broken_Access_Control/",
                "CWE-264", "https://cwe.mitre.org/data/definitions/264.html");

        assertThat(OwaspTop10Mapper.map(tags, 89)).isEqualTo(OwaspTop10Category.A01_BROKEN_ACCESS_CONTROL);
    }

    @Test
    void map_noOwasp2021Tag_fallsBackToCwe() {
        assertThat(OwaspTop10Mapper.map(Map.of("OWASP_2017_A06", "x"), 693))
                .isEqualTo(OwaspTop10Category.A05_SECURITY_MISCONFIGURATION);
        assertThat(OwaspTop10Mapper.map(null, 693)).isEqualTo(OwaspTop10Category.A05_SECURITY_MISCONFIGURATION);
    }

    @Test
    void map_noTagsAndNoCwe_mapsToUnmapped() {
        assertThat(OwaspTop10Mapper.map(Map.of(), null)).isEqualTo(OwaspTop10Category.UNMAPPED);
    }

    @Test
    void mapCwe_cwe264_mapsToBrokenAccessControl() {
        assertThat(OwaspTop10Mapper.mapCwe(264)).isEqualTo(OwaspTop10Category.A01_BROKEN_ACCESS_CONTROL);
    }
}
