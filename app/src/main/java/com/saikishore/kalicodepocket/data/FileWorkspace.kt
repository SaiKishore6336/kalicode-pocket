package com.saikishore.kalicodepocket.data

import java.io.File
import java.nio.charset.StandardCharsets

class FileWorkspace(private val root: File, override val isSample: Boolean = false) : Workspace {
    override val id: String = "file:" + root.absolutePath
    override val displayName: String = root.name.ifBlank { "Project" }

    override fun list(relativePath: String): List<Entry> {
        val directory = safeChild(root, relativePath)
        if (!directory.isDirectory) throw WorkspaceException("Not a folder")
        val children = directory.listFiles() ?: emptyArray()
        return children
            .asSequence()
            .filter { WorkspacePaths.shouldShow(it.name) }
            .map {
                val childPath = WorkspacePaths.join(relativePath, it.name)
                Entry(
                    path = childPath,
                    name = it.name,
                    isDirectory = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    modified = it.lastModified(),
                )
            }
            .sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    override fun read(relativePath: String): String {
        val file = safeChild(root, relativePath)
        if (!file.isFile) throw WorkspaceException("File not found")
        if (file.length() > WorkspacePaths.MAX_FILE_BYTES) {
            throw WorkspaceException("File is larger than 4 MB")
        }
        val bytes = file.readBytes()
        if (!WorkspacePaths.isProbablyText(bytes)) {
            throw WorkspaceException("Binary files cannot be edited")
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    override fun readBytes(relativePath: String): ByteArray {
        val file = safeChild(root, relativePath)
        if (!file.isFile) throw WorkspaceException("File not found")
        if (file.length() > WorkspacePaths.MAX_ASSET_BYTES) {
            throw WorkspaceException("Asset is larger than 12 MB")
        }
        return file.readBytes()
    }

    override fun write(relativePath: String, text: String) {
        val file = safeChild(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(text, StandardCharsets.UTF_8)
    }

    override fun createFile(parentPath: String, name: String): String {
        val path = WorkspacePaths.join(parentPath, name)
        val file = safeChild(root, path)
        file.parentFile?.mkdirs()
        if (!file.createNewFile()) throw WorkspaceException("A file with that name already exists")
        return path
    }

    override fun createDirectory(parentPath: String, name: String): String {
        val path = WorkspacePaths.join(parentPath, name)
        val directory = safeChild(root, path)
        if (!directory.mkdirs()) throw WorkspaceException("Could not create folder")
        return path
    }

    override fun delete(relativePath: String) {
        val file = safeChild(root, relativePath)
        if (file == root.canonicalFile) throw WorkspaceException("Cannot delete the project")
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!deleted) throw WorkspaceException("Could not delete ${file.name}")
    }

    override fun rename(relativePath: String, newName: String): String {
        val file = safeChild(root, relativePath)
        val parent = WorkspacePaths.parentOf(relativePath)
        val target = safeChild(root, WorkspacePaths.join(parent, WorkspacePaths.validateName(newName)))
        if (target.exists()) throw WorkspaceException("A file with that name already exists")
        if (!file.renameTo(target)) throw WorkspaceException("Could not rename ${file.name}")
        return WorkspacePaths.join(parent, target.name)
    }

    override fun toString(): String = "FileWorkspace(${root.absolutePath})"
}
