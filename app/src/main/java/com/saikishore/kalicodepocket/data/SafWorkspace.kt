package com.saikishore.kalicodepocket.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets

class SafWorkspace(
    private val context: Context,
    private val treeUri: Uri,
) : Workspace {
    override val id: String = treeUri.toString()
    override val isSample: Boolean = false
    override val displayName: String =
        DocumentFile.fromTreeUri(context, treeUri)?.name?.takeIf { it.isNotBlank() } ?: "Project"

    private fun root(): DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri) ?: throw WorkspaceException("Project is no longer available")

    private fun resolve(relativePath: String, requireExists: Boolean = true): DocumentFile {
        var current = root()
        val clean = WorkspacePaths.normalize(relativePath)
        if (clean.isEmpty()) return current
        for (segment in clean.split('/')) {
            val next = current.findFile(segment)
                ?: if (requireExists) throw WorkspaceException("$segment not found") else break
            current = next
        }
        return current
    }

    private fun parentOf(relativePath: String): DocumentFile {
        val parent = WorkspacePaths.parentOf(relativePath)
        return resolve(parent)
    }

    override fun list(relativePath: String): List<Entry> {
        val directory = resolve(relativePath)
        if (!directory.isDirectory) throw WorkspaceException("Not a folder")
        return (directory.listFiles() ?: emptyArray())
            .asSequence()
            .filter { WorkspacePaths.shouldShow(it.name.orEmpty()) }
            .map {
                Entry(
                    path = WorkspacePaths.join(relativePath, it.name.orEmpty()),
                    name = it.name.orEmpty(),
                    isDirectory = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    modified = it.lastModified(),
                )
            }
            .sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    override fun read(relativePath: String): String {
        val file = resolve(relativePath)
        if (!file.isFile) throw WorkspaceException("File not found")
        if (file.length() > WorkspacePaths.MAX_FILE_BYTES) {
            throw WorkspaceException("File is larger than 4 MB")
        }
        val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
            ?: throw FileNotFoundException("Could not open ${file.name}")
        if (!WorkspacePaths.isProbablyText(bytes)) {
            throw WorkspaceException("Binary files cannot be edited")
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    override fun readBytes(relativePath: String): ByteArray {
        val file = resolve(relativePath)
        if (!file.isFile) throw WorkspaceException("File not found")
        if (file.length() > WorkspacePaths.MAX_ASSET_BYTES) {
            throw WorkspaceException("Asset is larger than 12 MB")
        }
        return context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
            ?: throw FileNotFoundException("Could not open ${file.name}")
    }

    override fun write(relativePath: String, text: String) {
        val file = resolve(relativePath)
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(StandardCharsets.UTF_8))
        } ?: throw WorkspaceException("Could not write ${file.name}")
    }

    override fun createFile(parentPath: String, name: String): String {
        val cleanName = WorkspacePaths.validateName(name)
        val parent = resolve(parentPath)
        val mime = MimeTypes.forName(cleanName)
        val created = parent.createFile(mime, cleanName)
            ?: throw WorkspaceException("Could not create $cleanName")
        return WorkspacePaths.join(parentPath, created.name ?: cleanName)
    }

    override fun createDirectory(parentPath: String, name: String): String {
        val cleanName = WorkspacePaths.validateName(name)
        val parent = resolve(parentPath)
        val created = parent.createDirectory(cleanName)
            ?: throw WorkspaceException("Could not create folder")
        return WorkspacePaths.join(parentPath, created.name ?: cleanName)
    }

    override fun delete(relativePath: String) {
        val target = resolve(relativePath)
        if (target.uri == treeUri) throw WorkspaceException("Cannot delete the project")
        if (!target.delete()) throw WorkspaceException("Could not delete ${target.name}")
    }

    override fun rename(relativePath: String, newName: String): String {
        val target = resolve(relativePath)
        val cleanName = WorkspacePaths.validateName(newName)
        if (!target.renameTo(cleanName)) throw WorkspaceException("Could not rename ${target.name}")
        return WorkspacePaths.join(WorkspacePaths.parentOf(relativePath), cleanName)
    }

    override fun toString(): String = "SafWorkspace($treeUri)"
}
