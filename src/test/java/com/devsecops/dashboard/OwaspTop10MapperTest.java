package com.devsecops.dashboard;

import org.junit.jupiter.api.Test;

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
}
