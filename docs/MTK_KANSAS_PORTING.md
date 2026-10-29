# MediaTek MT6835 / Kansas Porting Notes

## Scope

This branch targets the user's rooted Android 16 device identified by `ro.board.platform=mt6835`, MediaTek RIL service `vendor.ril-daemon-mtk`, and baseband string `MT6835_NR17.RC.MP.V40.2.P182.01.287R`.

These values are device observations, not proof that every vendor interface is available to third-party root processes. Validate interfaces on-device before relying on them.

## First safety pass

- Keep the module disabled on the test device while porting.
- Do not auto-apply panic mode, firewall rules, modem resets, AT commands, or SELinux policy changes at boot.
- Avoid Qualcomm-specific device nodes and guessed vendor properties until evidence shows they exist and are supported.
- Make diagnostics report unknown/unavailable data honestly; never synthesize a cipher, signal metric, or network mode.
- Distinguish Android framework observations (for example `dumpsys telephony.registry`) from modem-level controls. Setting a property is not proof that the modem accepted a command.
- Test shell scripts with Android's available shell and static checks before installing a rebuilt module.

## Known upstream hazards under review

- `common/service.sh` automatically called `--harden-sepolicy` and restored persisted panic state on boot.
- `system.prop` contains numerous Qualcomm/Snapdragon-oriented or unverified radio properties.
- `sepolicy.rule` adds broad `allow` rules for radio, SMD, and TTY device types; this does not implement the claimed restrictive isolation.
- `sentry-ctl` includes unverified 5G forcing, panic/firewall, modem-reset, and hardware-shutdown paths. Treat those paths as untrusted until separately audited.

## Porting sequence

1. Make boot behavior passive and remove unverified property/policy mutations.
2. Replace diagnostic commands with read-only, framework-backed reports.
3. Audit every control command; keep unsupported controls explicitly unavailable rather than pretending they succeeded.
4. Add tests/static checks and compare device output before considering module activation.
5. Build the app/module and test each repaired function independently with recovery steps documented.

This document records the initial safety baseline. It is not a claim that the module is fully MediaTek-compatible.
