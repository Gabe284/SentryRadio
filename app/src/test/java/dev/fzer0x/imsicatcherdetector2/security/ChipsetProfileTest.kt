package dev.fzer0x.imsicatcherdetector2.security

import org.junit.Assert.assertEquals
import org.junit.Test

class ChipsetProfileTest {
    @Test
    fun identifiesCorroboratedMediaTekDevice() {
        val profile = ChipsetProfile.detect(
            mapOf(
                "ro.hardware.soc.manufacturer" to "mtk",
                "ro.board.platform" to "mt6835",
                "ro.hardware" to "mt6835",
                "vendor.ril.gsm.version.baseband" to "MT6835_NR17.RC",
                "init.svc.vendor.ril-daemon-mtk" to "running"
            )
        )
        assertEquals(ChipsetFamily.MEDIATEK, profile.family)
        assertEquals("mediatek", profile.backendName)
        assertEquals("corroborated", profile.confidence)
    }

    @Test
    fun identifiesCorroboratedQualcommDevice() {
        val profile = ChipsetProfile.detect(
            mapOf(
                "ro.hardware.soc.manufacturer" to "Qualcomm",
                "ro.board.platform" to "sm8550",
                "ro.hardware" to "qcom",
                "gsm.version.baseband" to "Snapdragon baseband"
            )
        )
        assertEquals(ChipsetFamily.QUALCOMM, profile.family)
        assertEquals("qualcomm", profile.backendName)
    }

    @Test
    fun unknownOrConflictingFingerprintDoesNotSelectBackend() {
        val profile = ChipsetProfile.detect(
            mapOf(
                "ro.hardware.soc.manufacturer" to "unknown",
                "ro.board.platform" to "board123",
                "ro.hardware" to "generic"
            )
        )
        assertEquals(ChipsetFamily.UNKNOWN, profile.family)
        assertEquals("none", profile.backendName)
    }
}
