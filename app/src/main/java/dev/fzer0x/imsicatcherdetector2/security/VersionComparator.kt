package dev.fzer0x.imsicatcherdetector2.security

object VersionComparator {
    /**
     * Compares versions in "versionCode-versionName" form.
     * Returns 1 when remote is newer, -1 when local is newer, or 0 when equal.
     *
     * GitHub release tags sometimes contain only a semantic version. In that case
     * the code is 0, so compare versionName rather than treating the missing code
     * as newer/older than a real Android versionCode.
     */
    fun compare(local: String, remote: String): Int {
        val localVersion = parse(local)
        val remoteVersion = parse(remote)

        if (localVersion.code > 0 && remoteVersion.code > 0 &&
            localVersion.code != remoteVersion.code
        ) {
            return if (remoteVersion.code > localVersion.code) 1 else -1
        }

        return compareParts(localVersion.nameParts, remoteVersion.nameParts)
    }

    private data class ParsedVersion(val code: Int, val nameParts: List<Int>)

    private fun parse(value: String): ParsedVersion {
        val separator = value.indexOf('-')
        val code = if (separator >= 0) value.substring(0, separator).toIntOrNull() ?: 0 else 0
        val name = if (separator >= 0) value.substring(separator + 1) else value
        val parts = Regex("\\d+").findAll(name).mapNotNull { it.value.toIntOrNull() }.toList()
        return ParsedVersion(code, parts)
    }

    private fun compareParts(local: List<Int>, remote: List<Int>): Int {
        for (i in 0 until maxOf(local.size, remote.size)) {
            val l = local.getOrElse(i) { 0 }
            val r = remote.getOrElse(i) { 0 }
            if (r > l) return 1
            if (l > r) return -1
        }
        return 0
    }
}
