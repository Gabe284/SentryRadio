#!/system/bin/sh
# Shared chipset detector/profile wrapper. Read-only; never changes radio state.
# Requires two independent fingerprint signals before choosing a backend.
sentry_detect_chipset() {
    _soc_mfr="$(getprop ro.hardware.soc.manufacturer 2>/dev/null | tr '[:upper:]' '[:lower:]')"
    _platform="$(getprop ro.board.platform 2>/dev/null | tr '[:upper:]' '[:lower:]')"
    _hardware="$(getprop ro.hardware 2>/dev/null | tr '[:upper:]' '[:lower:]')"
    _baseband="$(getprop ro.baseband 2>/dev/null) $(getprop gsm.version.baseband 2>/dev/null) $(getprop vendor.ril.gsm.version.baseband 2>/dev/null)"
    _baseband_lc="$(printf '%s' "$_baseband" | tr '[:upper:]' '[:lower:]')"
    _mtk_score=0
    _qcom_score=0

    case "$_soc_mfr" in
        *mtk*|*mediatek*) _mtk_score=$((_mtk_score + 1)) ;;
        *qualcomm*|*qcom*) _qcom_score=$((_qcom_score + 1)) ;;
    esac
    case "$_platform" in
        mt[0-9]*) _mtk_score=$((_mtk_score + 1)) ;;
        qcom*|msm*|sm[0-9]*) _qcom_score=$((_qcom_score + 1)) ;;
    esac
    case "$_hardware" in
        *mt[0-9]*) _mtk_score=$((_mtk_score + 1)) ;;
        *qcom*) _qcom_score=$((_qcom_score + 1)) ;;
    esac
    case "$_baseband_lc" in
        *mt[0-9]*) _mtk_score=$((_mtk_score + 1)) ;;
        *snapdragon*|*qualcomm*) _qcom_score=$((_qcom_score + 1)) ;;
    esac

    if [ "$_mtk_score" -ge 2 ] && [ "$_qcom_score" -eq 0 ]; then
        printf '%s\n' "mediatek"
        return 0
    fi
    if [ "$_qcom_score" -ge 2 ] && [ "$_mtk_score" -eq 0 ]; then
        printf '%s\n' "qualcomm"
        return 0
    fi
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
