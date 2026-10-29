# Chipset backend layout

Sentry uses a shared detector and separate vendor backends so vendor-specific properties and controls do not leak into the wrong device path.

## CLI entry point

- `system/bin/sentry-ctl`: shared dispatcher. It selects a backend only when at least two independent fingerprint signals agree; ambiguous/conflicting devices return exit code 2 without dispatching.
- `system/bin/chipsets/detect.sh`: read-only shell fingerprint and `--device-profile` output.
- `system/bin/chipsets/qualcomm/sentry-ctl`: the legacy Qualcomm CLI moved out of the shared entry point. It is only dispatched for a corroborated Qualcomm fingerprint.
- `system/bin/chipsets/mediatek/sentry-ctl`: MediaTek-specific diagnostic/control backend. Its current radio-changing controls remain explicitly unsupported until device-specific operations are verified.

Run `sentry-ctl --device-profile` for a read-only profile report.

## App-side API

- `security/ChipsetProfile.kt` models `QUALCOMM`, `MEDIATEK`, and `UNKNOWN`, along with each backend's property candidates and observed interface candidates.
- `RootRepository.detectChipsetProfile()` reads Android properties and returns the typed profile. Callers should branch on `family`; `UNKNOWN` must never fall back to Qualcomm behavior.

The property and interface lists are diagnostic candidates, not permission grants and not authorization to write properties or open device nodes.

## Safety and compatibility notes

- Detection does not itself make a vendor control compatible. Each operation must be implemented and validated for the selected modem/RIL stack.
- The Qualcomm backend preserves the legacy CLI implementation for isolation. Some legacy commands can change radio, process, or firewall state; audit them before invoking them. Do not run emergency, panic, reset, or firewall controls during ordinary chipset validation.
- The MediaTek backend retains its conservative behavior; no unverified SELinux grants or modem-node access are added by this split.
- The root module remains disabled until build, packaging, policy, and on-device checks are complete.
