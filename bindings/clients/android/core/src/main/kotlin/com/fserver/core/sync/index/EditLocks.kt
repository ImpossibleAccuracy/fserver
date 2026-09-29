package com.fserver.core.sync.index

import com.fserver.files.fs.scan.FoundFile
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Files an editor holds open, going by the lock file it leaves beside them. */
internal class EditLocks private constructor(private val locksByDir: Map<String, List<Lock>>) {

    fun isHeld(path: String): Boolean {
        val locks = locksByDir[path.substringBeforeLast('/', "")] ?: return false
        val name = path.substringAfterLast('/').lowercase()
        return locks.any { it.matches(name) }
    }

    /** [slack]: leading chars of the file name the lock may have dropped. */
    private class Lock(val name: String, val slack: Int) {
        fun matches(file: String): Boolean =
            file.endsWith(name) && file.length - name.length <= slack
    }

    companion object {
        /** An older lock is a crashed editor's leftover, or an edit long enough to sync mid-way. */
        val MaxAge = 2.hours

        fun of(files: List<FoundFile>, now: Instant): EditLocks {
            val locks = files
                .filter { now - it.lastModified < MaxAge }
                .mapNotNull { file ->
                    lockOf(file.path.substringAfterLast('/').lowercase())
                        ?.let { file.path.substringBeforeLast('/', "") to it }
                }
                .groupBy({ it.first }, { it.second })

            return EditLocks(locks)
        }

        // TODO: re-check when cross-platform will be implemented, cause desktop programs may use different lock file naming conventions on different OS
        private fun lockOf(name: String): Lock? = when {
            // MS Office: "~$port.docx" for "report.docx" - a long name loses its first two chars.
            name.startsWith("~$") -> Lock(name.removePrefix("~$"), slack = 2)
            // LibreOffice
            name.startsWith(".~lock.") && name.endsWith("#") -> Lock(
                name.removePrefix(".~lock.").removeSuffix("#"), 0
            )
            // emacs
            name.startsWith(".#") -> Lock(name.removePrefix(".#"), 0)
            // vim
            name.startsWith(".") && (name.endsWith(".swp") || name.endsWith(".swo")) ->
                Lock(name.removePrefix(".").dropLast(4), 0)

            else -> null
        }?.takeIf { it.name.isNotEmpty() }
    }
}
