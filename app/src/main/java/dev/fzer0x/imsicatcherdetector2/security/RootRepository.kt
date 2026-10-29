package dev.fzer0x.imsicatcherdetector2.security

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Dedizierter Root-Repository für eine stabile Shell-Interaktion.
 * Nutzt libsu für persistente Shell-Sessions, was Akku und CPU spart.
 */
object RootRepository {
    private const val TAG = "RootRepository"
    private const val SENTRY_MODULE_DIR = "/data/adb/modules/sentry_radio_hardening"
    private const val SENTRY_CTL_PATH = "$SENTRY_MODULE_DIR/system/bin/sentry-ctl"

    init {
        // Globale Konfiguration für libsu
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(10))
    }

    /**
     * Prüft, ob Root-Zugriff gewährt wurde.
     * Verbessert: Führt einen echten Test-Befehl aus und überprüft uid=0.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("id").exec()
            result.isSuccess && result.out.joinToString().contains("uid=0")
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }

    /**
     * Führt einen Befehl als Root aus und gibt das Ergebnis zurück.
     * Nutzt die globale Shell-Instanz von libsu (hält die Session offen).
     */
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        return@withContext try {
            // Guard both the command alias and absolute-path fallback. Otherwise a caller
            // could bypass the disabled-module check by retrying the full path.
            val trimmedCommand = command.trimStart()
            val isSentryCommand =
                trimmedCommand == "sentry-ctl" ||
                trimmedCommand.startsWith("sentry-ctl ") ||
                trimmedCommand.contains(SENTRY_CTL_PATH)
            if (isSentryCommand && !isSentryModuleEnabled()) {
                return@withContext ShellResult(
                    success = false,
                    output = "",
                    error = "Sentry hardening module is missing or disabled; no command was executed.",
                    exitCode = 126
                )
            }

            // Avoid PATH resolution for sentry-ctl: the dispatcher must select the backend.
            val resolvedCommand = if (
                trimmedCommand == "sentry-ctl" || trimmedCommand.startsWith("sentry-ctl ")
            ) {
                "sh '$SENTRY_CTL_PATH'" + trimmedCommand.removePrefix("sentry-ctl")
            } else {
                command
            }
            val result = Shell.cmd(resolvedCommand).exec()
            ShellResult(
                success = result.isSuccess,
                output = result.out.joinToString("\n"),
                error = result.err.joinToString("\n"),
                exitCode = result.code
            )
        } catch (e: Exception) {
            Log.e(TAG, "Root command failed: $command", e)
            ShellResult(
                success = false,
                output = "",
                error = "Execution failed: ${e.message}",
                exitCode = -1
            )
        }
    }

    /**
     * Führt einen Root-Befehl mit Retry-Mechanismus aus
     */
    suspend fun executeWithRetry(
        command: String, 
        maxRetries: Int = 3,
        retryDelayMs: Long = 1000
    ): ShellResult = withContext(Dispatchers.IO) {
        var lastResult: ShellResult? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val result = execute(command)
                if (result.success) {
                    return@withContext result
                }
                lastResult = result
                
                if (attempt < maxRetries - 1) {
                    Log.w(TAG, "Command failed (attempt ${attempt + 1}/$maxRetries): $command")
                    delay(retryDelayMs * (attempt + 1)) // Exponential backoff
                }
            } catch (e: Exception) {
                Log.e(TAG, "Command execution failed (attempt ${attempt + 1}/$maxRetries)", e)
                if (attempt < maxRetries - 1) {
                    delay(retryDelayMs * (attempt + 1))
                }
            }
        }
        
        lastResult ?: ShellResult(false, "", "All retries failed", -1)
    }

    /** True when the module files are installed, even if the module is disabled. */
    suspend fun isSentryModuleInstalled(): Boolean = fileExists("$SENTRY_MODULE_DIR/module.prop")

    /**
     * KernelSU/Magisk modules are disabled by a marker file named "disable".
     * Never infer active status from module.prop alone.
     */
    suspend fun isSentryModuleEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd(
                "[ -f '$SENTRY_MODULE_DIR/module.prop' ] && " +
                    "[ ! -e '$SENTRY_MODULE_DIR/disable' ] && " +
                    "[ ! -e '$SENTRY_MODULE_DIR/remove' ] && " +
                    "[ ! -e '/data/adb/modules/disable' ]"
            ).exec().isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Unable to determine Sentry module enabled state", e)
            false
        }
    }

    /**
     * Prüft die Existenz einer Datei oder eines Verzeichnisses als Root.
     */
    suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("[ -f \"$path\" ] || [ -d \"$path\" ]").exec().isSuccess
    }

    /**
     * Detects the chipset from read-only Android properties and returns its
     * backend profile. Unknown/conflicting fingerprints never select a vendor backend.
     */
    suspend fun detectChipsetProfile(): ChipsetProfile = withContext(Dispatchers.IO) {
        val result = execute(
            "getprop ro.hardware.soc.manufacturer; " +
                "getprop ro.board.platform; " +
                "getprop ro.hardware; " +
                "getprop ro.baseband; " +
                "getprop gsm.version.baseband; " +
                "getprop vendor.ril.gsm.version.baseband; " +
                "getprop init.svc.vendor.ril-daemon-mtk"
        )
        val values = result.output.split("\n")
        val keys = listOf(
            "ro.hardware.soc.manufacturer",
            "ro.board.platform",
            "ro.hardware",
            "ro.baseband",
            "gsm.version.baseband",
            "vendor.ril.gsm.version.baseband",
            "init.svc.vendor.ril-daemon-mtk"
        )
        ChipsetProfile.detect(keys.mapIndexed { index, key ->
            key to values.getOrElse(index) { "" }.trim()
        }.toMap())
    }

    data class ShellResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int
    )
}
