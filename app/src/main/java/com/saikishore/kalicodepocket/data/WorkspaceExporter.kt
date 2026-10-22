package com.saikishore.kalicodepocket.data

import java.io.File

/**
 * Copies a small text-only project into a writable cache directory so embedded
 * Python can import sibling modules and read project files even when the
 * original project lives behind SAF.
 */
object WorkspaceExporter {
    private const val MAX_FILES = 400
    private const val MAX_TOTAL_BYTES = 25L * 1024 * 1024
    private const val MAX_DEPTH = 12

    fun exportProject(workspace: Workspace, destination: File, activePath: String): String {
        destination.mkdirs()
        var fileCount = 0
        var totalBytes = 0L

        fun walk(directory: String, depth: Int) {
            if (depth > MAX_DEPTH || fileCount >= MAX_FILES || totalBytes >= MAX_TOTAL_BYTES) return
            val entries = runCatching { workspace.list(directory) }.getOrElse { return }
            for (entry in entries) {
                if (fileCount >= MAX_FILES || totalBytes >= MAX_TOTAL_BYTES) return
                if (!WorkspacePaths.shouldShow(entry.name)) continue
                if (entry.isDirectory) {
                    walk(entry.path, depth + 1)
                    continue
                }
                if (entry.size > WorkspacePaths.MAX_FILE_BYTES) continue
                val bytes = runCatching { workspace.readBytes(entry.path) }.getOrNull() ?: continue
                if (!WorkspacePaths.isProbablyText(bytes)) continue
                val target = File(destination, entry.path)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                fileCount++
                totalBytes += bytes.size
            }
        }

        walk("", 0)

        val cleanActive = WorkspacePaths.normalize(activePath)
        val activeFile = File(destination, cleanActive)
        if (!activeFile.isFile) {
            activeFile.parentFile?.mkdirs()
            activeFile.writeText(workspace.read(activePath))
        }
        return cleanActive
    }
}
