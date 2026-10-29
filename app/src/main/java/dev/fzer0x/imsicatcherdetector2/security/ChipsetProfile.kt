package dev.fzer0x.imsicatcherdetector2.security

import java.util.Locale

/**
 * Read-only chipset profile selection. Detection chooses a backend family;
 * it does not claim that every vendor-specific radio control is supported.
 */
enum class ChipsetFamily { QUALCOMM, MEDIATEK, UNKNOWN }

data class ChipsetProfile(
    val family: ChipsetFamily,
    val platform: String,
    val manufacturer: String,
    val hardware: String,
    val baseband: String,
    val rilService: String,
    val confidence: String,
    /** Property names are candidates for backend-specific observation, not writes. */
    val propertyCandidates: List<String>,
    /** Observed node candidates only; this list does not grant access. */
    val interfaceCandidates: List<String>
) {
    val backendName: String
        get() = when (family) {
            ChipsetFamily.QUALCOMM -> "qualcomm"
            ChipsetFamily.MEDIATEK -> "mediatek"
            ChipsetFamily.UNKNOWN -> "none"
        }

    companion object {
        fun detect(properties: Map<String, String>): ChipsetProfile {
            fun value(key: String) = properties[key].orEmpty().trim()
            fun norm(key: String) = value(key).lowercase(Locale.ROOT)

            val manufacturer = value("ro.hardware.soc.manufacturer")
            val platform = value("ro.board.platform")
            val hardware = value("ro.hardware")
            val baseband = listOf(
                value("vendor.ril.gsm.version.baseband"),
                value("gsm.version.baseband"),
                value("ro.baseband")
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            val ril = value("init.svc.vendor.ril-daemon-mtk")
            val mfr = manufacturer.lowercase(Locale.ROOT)
            val plat = platform.lowercase(Locale.ROOT)
            val hw = hardware.lowercase(Locale.ROOT)
            val bb = baseband.lowercase(Locale.ROOT)

            val isMtk = (
                (mfr.contains("mtk") || mfr.contains("mediatek")) &&
                    (plat.startsWith("mt") || bb.contains("mt"))
                ) || (plat.startsWith("mt") && bb.contains("mt"))
            val isQualcomm = (
                (mfr.contains("qualcomm") || mfr.contains("qcom")) &&
                    (plat.isNotBlank() || bb.isNotBlank() || hw.isNotBlank())
                ) || (
                    (plat.startsWith("qcom") || plat.startsWith("msm") ||
                        Regex("^sm[0-9]").containsMatchIn(plat) || hw.contains("qcom")) &&
                        (mfr.contains("qualcomm") || bb.contains("snapdragon") ||
                            bb.contains("qualcomm") || hw.contains("qcom"))
                    )

            return when {
                isMtk && !isQualcomm -> ChipsetProfile(
                    family = ChipsetFamily.MEDIATEK,
                    platform = platform,
                    manufacturer = manufacturer,
                    hardware = hardware,
                    baseband = baseband,
                    rilService = ril,
                    confidence = "corroborated",
                    propertyCandidates = listOf(
                        "vendor.ril.gsm.version.baseband",
                        "vendor.ril.md_status_from_ccci",
                        "init.svc.vendor.ril-daemon-mtk",
                        "init.svc.vendor.modem-rfs-sh",
                        "persist.vendor.radio.multisim.config",
                        "vendor.ril.nr.type"
                    ),
                    interfaceCandidates = listOf(
                        "/dev/ccci_ccb_ctrl",
                        "/dev/ccci_raw_mdm",
                        "/dev/ttyCMIPC0"
                    )
                )
                isQualcomm && !isMtk -> ChipsetProfile(
                    family = ChipsetFamily.QUALCOMM,
                    platform = platform,
                    manufacturer = manufacturer,
                    hardware = hardware,
                    baseband = baseband,
                    rilService = "",
                    confidence = "corroborated",
                    propertyCandidates = listOf(
                        "gsm.version.baseband",
                        "ro.baseband",
                        "vendor.radio.cipher_algo",
                        "vendor.radio.rsrp",
                        "vendor.radio.snr",
                        "vendor.modem.temp"
                    ),
                    interfaceCandidates = emptyList()
                )
                else -> ChipsetProfile(
                    family = ChipsetFamily.UNKNOWN,
                    platform = platform,
                    manufacturer = manufacturer,
                    hardware = hardware,
                    baseband = baseband,
                    rilService = ril,
                    confidence = "unknown-or-conflicting",
                    propertyCandidates = emptyList(),
                    interfaceCandidates = emptyList()
                )
            }
        }
    }
}
