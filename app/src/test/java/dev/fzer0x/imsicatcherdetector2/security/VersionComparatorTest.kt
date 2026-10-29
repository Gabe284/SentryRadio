package dev.fzer0x.imsicatcherdetector2.security

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun newerReleaseTagIsComparedBySemanticVersionWhenCodeIsUnavailable() {
        assertEquals(1, VersionComparator.compare("12-0.5.0", "0-0.8.0"))
    }

    @Test
    fun olderReleaseTagIsNotReportedAsAnUpdate() {
        assertEquals(-1, VersionComparator.compare("12-0.5.0", "0-0.4.6"))
    }

    @Test
    fun versionCodeWinsWhenBothVersionsProvideOne() {
        assertEquals(1, VersionComparator.compare("12-0.5.0", "13-0.4.0"))
    }

    @Test
    fun equivalentVersionsCompareEqual() {
        assertEquals(0, VersionComparator.compare("12-0.5.0", "0-0.5.0"))
    }
}
