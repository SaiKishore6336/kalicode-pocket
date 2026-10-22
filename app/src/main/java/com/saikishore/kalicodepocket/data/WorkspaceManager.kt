package com.saikishore.kalicodepocket.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RecentProject(
    val type: String,
    val uri: String,
    val name: String,
    val openedAt: Long,
)

object WorkspaceManager {
    private const val PREFS = "projects"
    private const val KEY_RECENT = "recent"
    private const val KEY_LAST = "last"
    private const val MAX_RECENT = 8

    var current: Workspace? = null
        private set

    fun restoreLast(context: Context): Workspace? {
        current?.let { return it }
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            when (json.getString("type")) {
                "sample" -> openSample(context)
                "saf" -> openTree(context, Uri.parse(json.getString("uri")), remember = false)
                else -> null
            }
        }.getOrNull()
    }

    fun openSample(context: Context): Workspace {
        val root = File(context.filesDir, "projects/welcome")
        SampleProject.ensure(context, root)
        return FileWorkspace(root, isSample = true).also {
            current = it
            remember(context, RecentProject("sample", "", it.displayName, System.currentTimeMillis()))
            setLast(context, "sample", "")
        }
    }

    fun openTree(context: Context, uri: Uri, remember: Boolean = true): Workspace {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        val document = DocumentFile.fromTreeUri(context, uri)
            ?: throw WorkspaceException("Could not open that folder")
        if (!document.canRead()) throw WorkspaceException("Read access was not granted")
        val workspace = SafWorkspace(context, uri)
        current = workspace
        if (remember) {
            remember(
                context,
                RecentProject(
                    type = "saf",
                    uri = uri.toString(),
                    name = workspace.displayName,
                    openedAt = System.currentTimeMillis(),
                ),
            )
            setLast(context, "saf", uri.toString())
        }
        return workspace
    }

    fun close() {
        current = null
    }

    fun recent(context: Context): List<RecentProject> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENT, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        RecentProject(
                            type = item.optString("type"),
                            uri = item.optString("uri"),
                            name = item.optString("name", "Project"),
                            openedAt = item.optLong("openedAt"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun forget(context: Context, project: RecentProject) {
        val remaining = recent(context).filterNot {
            it.type == project.type && it.uri == project.uri
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECENT, encode(remaining)).apply()
        if (project.type == current?.let { if (it.isSample) "sample" else "saf" } &&
            project.uri == current?.id.orEmpty().removePrefix("saf:")
        ) {
            prefs.edit().remove(KEY_LAST).apply()
        }
    }

    private fun remember(context: Context, project: RecentProject) {
        val updated = (listOf(project) + recent(context).filterNot {
            it.type == project.type && it.uri == project.uri
        }).take(MAX_RECENT)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT, encode(updated))
            .apply()
    }

    private fun encode(items: List<RecentProject>): String {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("type", it.type)
                    .put("uri", it.uri)
                    .put("name", it.name)
                    .put("openedAt", it.openedAt)
            )
        }
        return array.toString()
    }

    private fun setLast(context: Context, type: String, uri: String) {
        val json = JSONObject().put("type", type).put("uri", uri)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST, json.toString()).apply()
    }
}
