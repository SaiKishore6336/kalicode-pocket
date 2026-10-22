package com.saikishore.kalicodepocket.data

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Creates the bundled playground once. It is real, user-editable files in the app's
 * private storage, so run/preview work without any storage permission prompt.
 */
object SampleProject {
    private const val ASSET_ROOT = "sample/welcome"

    fun ensure(context: Context, root: File) {
        if (!root.isDirectory) root.mkdirs()
        val marker = File(root, "index.html")
        if (marker.isFile && root.listFiles()?.isNotEmpty() == true) return
        copyAssetTree(context, ASSET_ROOT, root)
    }

    private fun copyAssetTree(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            copyAssetFile(context, assetPath, target) { input, output ->
                input.copyTo(output)
            }
            return
        }
        target.mkdirs()
        for (child in children) {
            copyAssetTree(context, "$assetPath/$child", File(target, child))
        }
    }

    private fun copyAssetFile(
        context: Context,
        assetPath: String,
        target: File,
        copier: (java.io.InputStream, java.io.OutputStream) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> copier(input, output) }
            }
        } catch (e: IOException) {
            // A directory from assets.list() can occasionally surface as a file.
            if (!target.exists()) throw e
        }
    }
}
