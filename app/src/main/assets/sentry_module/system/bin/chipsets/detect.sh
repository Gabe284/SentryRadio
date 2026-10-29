#!/system/bin/sh
# Shared chipset detection/profile wrapper. Read-only: never changes radio state.
# Return values: qualcomm, mediatek, unknown.
sentry_detect_chipset() {
    _soc_mfr="$(getprop ro.hardware.soc.manufacturer 2>/dev/null | tr '[:upper:]' '[:lower:]')"
    _platform="$(getprop ro.board.platform 2>/dev/null | tr '[:upper:]' '[:lower:]')"
    _hardware="$(getprop ro.hardware 2>/dev/null | tr '[:upper:]' '[:lower:]')"
    _baseband="$(getprop ro.baseband 2>/dev/null) $(getprop gsm.version.baseband 2>/dev/null) $(getprop vendor.ril.gsm.version.baseband 2>/dev/null)"
    _baseband_lc="$(printf '%s' "$_baseband" | tr '[:upper:]' '[:lower:]')"

    # Require corroborating signals; ambiguous devices are deliberately unknown.
    case "$_soc_mfr:$_platform:$_hardware:$_baseband_lc" in
        *qualcomm*:*:*:*|*:*qcom*:*:*|*:*msm*:*:*|*:*sm[0-9]*:*:*|*:*:*qcom*:*|*:*:*:*snapdragon*)
            printf '%s\n' "qualcomm"
            return 0
            ;;
        *mtk*:*:*:*|*:*mt[0-9]*:*:*|*:*:*mt[0-9]*:*|*:*:*:*mt[0-9]*)
            printf '%s\n' "mediatek"
            return 0
            ;;
    esac
    printf '%s\n' "unknown"
    return 1
}

sentry_print_profile() {
    _family="$(sentry_detect_chipset)"
    echo "Chipset family: $_family"
    echo "Manufacturer: $(getprop ro.hardware.soc.manufacturer)"
    echo "Platform: $(getprop ro.board.platform)"
    echo "Hardware: $(getprop ro.hardware)"
    echo "Baseband: $(getprop vendor.ril.gsm.version.baseband)"
    case "$_family" in
        qualcomm)
            echo "Backend: chipsets/qualcomm/sentry-ctl"
            ;;
        mediatek)
            echo "Backend: chipsets/mediatek/sentry-ctl"
            echo "MTK RIL service: $(getprop init.svc.vendor.ril-daemon-mtk)"
            echo "Modem status: $(getprop vendor.ril.md_status_from_ccci)"
            ;;
        *)
            echo "Backend: none (unknown/ambiguous chipset; no control dispatched)"
            ;;
    esac
}
