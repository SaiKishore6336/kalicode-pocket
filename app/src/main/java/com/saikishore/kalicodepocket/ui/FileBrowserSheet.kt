package com.saikishore.kalicodepocket.ui

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.saikishore.kalicodepocket.R
import com.saikishore.kalicodepocket.data.Entry
import com.saikishore.kalicodepocket.data.Workspace
import com.saikishore.kalicodepocket.data.WorkspacePaths
import com.saikishore.kalicodepocket.databinding.DialogInputBinding
import com.saikishore.kalicodepocket.databinding.SheetFilesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileBrowserSheet(
    private val context: Context,
    private val workspace: Workspace,
    startPath: String,
    private val onOpenFile: (String) -> Unit,
    private val onRenamed: (String, String) -> Unit,
    private val onDeleted: (String) -> Unit,
    private val onCreatedFile: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var path: String = WorkspacePaths.normalize(startPath)
    private val binding = SheetFilesBinding.inflate(LayoutInflater.from(context))
    private val dialog = BottomSheetDialog(context)
    private val adapter = FileAdapter(
        onFile = { entry ->
            dismiss()
            onOpenFile(entry.path)
        },
        onDirectory = { entry -> load(entry.path) },
        onLong = ::showEntryActions,
    )

    init {
        dialog.setContentView(binding.root)
        dialog.setOnDismissListener {
            scope.cancel()
            hideKeyboard()
        }
        binding.fileList.layoutManager = LinearLayoutManager(context)
        binding.fileList.adapter = adapter
        binding.fileSheetUp.setOnClickListener { load(WorkspacePaths.parentOf(path)) }
        binding.fileSheetNewFile.setOnClickListener { promptCreate(isDirectory = false) }
        binding.fileSheetNewFolder.setOnClickListener { promptCreate(isDirectory = true) }
        load(path)
    }

    fun show() = dialog.show()

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    private fun load(newPath: String) {
        val normalized = WorkspacePaths.normalize(newPath)
        path = normalized
        binding.fileSheetPath.text = if (normalized.isEmpty()) "/" else "/$normalized"
        binding.fileSheetUp.alpha = if (normalized.isEmpty()) 0.35f else 1f
        binding.fileSheetUp.isEnabled = normalized.isNotEmpty()
        scope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { workspace.list(normalized) }
                if (!dialog.isShowing) return@launch
                adapter.submitList(entries)
                if (entries.isEmpty()) {
                    Toast.makeText(context, "This folder is empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                toast(e.message ?: "Could not read folder")
            }
        }
    }

    private fun showEntryActions(entry: Entry) {
        val actions = arrayOf(context.getString(R.string.rename), context.getString(R.string.delete))
        MaterialAlertDialogBuilder(context)
            .setTitle(entry.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> promptRename(entry)
                    1 -> confirmDelete(entry)
                }
            }
            .show()
    }

    private fun promptCreate(isDirectory: Boolean) {
        val input = DialogInputBinding.inflate(LayoutInflater.from(context))
        input.inputName.hint = context.getString(
            if (isDirectory) R.string.new_folder else R.string.new_file
        )
        input.inputName.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val dialogBuilder = MaterialAlertDialogBuilder(context)
            .setTitle(if (isDirectory) R.string.new_folder else R.string.new_file)
            .setView(input.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create, null)
        val alert = dialogBuilder.create()
        alert.setOnShowListener {
            alert.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.inputName.text?.toString().orEmpty().trim()
                if (name.isEmpty()) {
                    input.inputName.error = "Enter a name"
                    return@setOnClickListener
                }
                scope.launch {
                    try {
                        val created = withContext(Dispatchers.IO) {
                            if (isDirectory) workspace.createDirectory(path, name)
                            else workspace.createFile(path, name)
                        }
                        alert.dismiss()
                        hideKeyboard()
                        if (isDirectory) {
                            load(path)
                        } else {
                            onCreatedFile(created)
                            dismiss()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Could not create", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        alert.show()
        input.inputName.requestFocus()
        input.inputName.postDelayed({
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input.inputName, InputMethodManager.SHOW_IMPLICIT)
        }, 180)
    }

    private fun promptRename(entry: Entry) {
        val input = DialogInputBinding.inflate(LayoutInflater.from(context))
        input.inputName.setText(entry.name)
        input.inputName.setSelection(entry.name.length)
        input.inputName.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val alert = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.rename)
            .setView(input.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Rename", null)
            .create()
        alert.setOnShowListener {
            alert.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.inputName.text?.toString().orEmpty().trim()
                if (name.isEmpty()) {
                    input.inputName.error = "Enter a name"
                    return@setOnClickListener
                }
                scope.launch {
                    try {
                        val updated = withContext(Dispatchers.IO) { workspace.rename(entry.path, name) }
                        alert.dismiss()
                        hideKeyboard()
                        onRenamed(entry.path, updated)
                        load(path)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Could not rename", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        alert.show()
    }

    private fun confirmDelete(entry: Entry) {
        MaterialAlertDialogBuilder(context)
            .setTitle(entry.name)
            .setMessage(
                if (entry.isDirectory) "Delete this folder and everything inside it?"
                else "Delete this file?"
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { workspace.delete(entry.path) }
                        onDeleted(entry.path)
                        load(path)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
