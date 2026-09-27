package com.fetchr.app.util

object FormatHelper {

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    fun formatViews(count: Long): String {
        return when {
            count <= 0 -> ""
            count >= 1_000_000 -> "%.1fM views".format(count / 1_000_000.0)
            count >= 1_000 -> "%.1fK views".format(count / 1_000.0)
            else -> "$count views"
        }
    }

    fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return "~"
        return when {
            bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
            else -> "%.1f MB/s".format(bytesPerSec / 1024.0 / 1024.0)
        }
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/*?:"<>|]"""), "_").take(120).trim()
    }
}
