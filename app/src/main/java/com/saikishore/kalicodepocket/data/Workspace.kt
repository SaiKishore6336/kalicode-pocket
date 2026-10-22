package com.saikishore.kalicodepocket.data

import java.io.File
import java.io.IOException

/**
 * A small, deliberately boring workspace abstraction. Two implementations keep the
 * editor independent of whether the user opened a real folder through SAF or is
 * using the bundled scratch project.
 */
interface Workspace {
    val id: String
    val displayName: String
    val isSample: Boolean

    fun list(relativePath: String): List<Entry>
    fun read(relativePath: String): String
    fun readBytes(relativePath: String): ByteArray
    fun write(relativePath: String, text: String)
    fun createFile(parentPath: String, name: String): String
    fun createDirectory(parentPath: String, name: String): String
    fun delete(relativePath: String)
    fun rename(relativePath: String, newName: String): String
}

data class Entry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
)

object WorkspacePaths {
    const val MAX_FILE_BYTES = 4 * 1024 * 1024
    const val MAX_ASSET_BYTES = 12 * 1024 * 1024
    private val ignoredNames = setOf(
        ".git", ".gradle", ".idea", "node_modules", "build", "__pycache__", ".dart_tool",
    )

    fun normalize(path: String): String {
        val trimmed = path.trim().replace('\\', '/').trim('/')
        if (trimmed.isEmpty()) return ""
        val parts = trimmed.split('/')
        require(parts.none { it.isEmpty() || it == "." || it == ".." }) {
            "Invalid workspace path"
        }
        return parts.joinToString("/")
    }

    fun join(parent: String, name: String): String {
        val cleanParent = normalize(parent)
        val cleanName = validateName(name)
        return if (cleanParent.isEmpty()) cleanName else "$cleanParent/$cleanName"
    }

    fun validateName(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Name cannot be empty" }
        require(clean != "." && clean != "..") { "Invalid name" }
        require('/' !in clean && '\\' !in clean && '\u0000' !in clean) {
            "Name cannot contain slashes"
        }
        return clean
    }

    fun shouldShow(name: String): Boolean = name !in ignoredNames

    fun extensionOf(name: String): String {
        val index = name.lastIndexOf('.')
        return if (index <= 0 || index == name.lastIndex) "" else name.substring(index + 1).lowercase()
    }

    fun baseName(path: String): String = path.substringAfterLast('/')

    fun parentOf(path: String): String {
        val clean = normalize(path)
        return if ('/' in clean) clean.substringBeforeLast('/') else ""
    }

    fun isProbablyText(bytes: ByteArray): Boolean {
        val sample = minOf(bytes.size, 4096)
        for (i in 0 until sample) {
            val value = bytes[i].toInt() and 0xff
            if (value == 0) return false
        }
        return true
    }
}

class WorkspaceException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal fun safeChild(root: File, relativePath: String): File {
    val clean = WorkspacePaths.normalize(relativePath)
    val candidate = if (clean.isEmpty()) root else File(root, clean)
    val canonicalRoot = root.canonicalFile
    val canonical = candidate.canonicalFile
    val rootPath = canonicalRoot.path + File.separator
    if (canonical != canonicalRoot && !canonical.path.startsWith(rootPath)) {
        throw WorkspaceException("Path escapes the project")
    }
    return canonical
}
