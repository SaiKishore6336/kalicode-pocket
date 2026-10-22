package com.saikishore.kalicodepocket

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.saikishore.kalicodepocket.data.Settings
import com.saikishore.kalicodepocket.data.Workspace
import com.saikishore.kalicodepocket.data.WorkspaceExporter
import com.saikishore.kalicodepocket.data.WorkspaceManager
import com.saikishore.kalicodepocket.data.WorkspacePaths
import com.saikishore.kalicodepocket.databinding.ActivityEditorBinding
import com.saikishore.kalicodepocket.databinding.DialogInputBinding
import com.saikishore.kalicodepocket.databinding.DialogSettingsBinding
import com.saikishore.kalicodepocket.editor.EditorLanguages
import com.saikishore.kalicodepocket.ui.FileBrowserSheet
import com.saikishore.kalicodepocket.ui.TabAdapter
import com.saikishore.kalicodepocket.ui.TabInfo
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private lateinit var workspace: Workspace
    private lateinit var settings: Settings
    private lateinit var tabAdapter: TabAdapter

    private val tabs = mutableListOf<OpenFile>()
    private var activeIndex = -1
    private var loading = false
    private var initialLoadStarted = false
    private var fileSheet: FileBrowserSheet? = null
    private var preparingPython = false

    private var searchCaseSensitive = false
    private var searchWholeWord = false
    private var searchRegex = false

    private val activeTab: OpenFile?
        get() = tabs.getOrNull(activeIndex)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        workspace = WorkspaceManager.current
            ?: WorkspaceManager.restoreLast(this)
            ?: run {
                toast(getString(R.string.open_failed))
                finish()
                return
            }
        settings = (application as PocketApp).settings

        setupToolbar()
        setupTabs()
        setupEditor()
        setupSymbols()
        setupSearch()
        setupBackHandling()

        binding.toolbar.subtitle = workspace.displayName
        refreshTabs()
        updateHeader()
        updateStatus()
        openInitialFile()
    }

    override fun onPause() {
        super.onPause()
        if (::workspace.isInitialized && settings.autoSave) {
            saveAllDirty(silent = true)
        }
    }

    override fun onDestroy() {
        fileSheet?.dismiss()
        super.onDestroy()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.toolbar.inflateMenu(R.menu.menu_editor)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    saveActive(showToast = true)
                    true
                }
                R.id.action_search -> {
                    showSearch()
                    true
                }
                R.id.action_run -> {
                    runActive()
                    true
                }
                R.id.action_files -> {
                    showFileSheet()
                    true
                }
                R.id.action_more -> {
                    showMore(binding.toolbar.findViewById<View>(R.id.action_more))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabs() {
        tabAdapter = TabAdapter(
            onSelect = { index -> if (index != activeIndex) showTab(index) },
            onClose = ::confirmCloseTab,
        )
        binding.tabStrip.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.tabStrip.adapter = tabAdapter
    }

    private fun setupEditor() {
        with(binding.editor) {
            setTypefaceText(Typeface.MONOSPACE)
            setTextSizePx(sp(settings.fontSize))
            setLineNumberEnabled(settings.lineNumbers)
            setWordwrap(settings.wordWrap)
            setTabWidth(settings.tabSize)
            setHighlightCurrentLine(true)
            setPinLineNumber(true)
            setScrollBarEnabled(true)
            setLineSpacingExtra(dp(2f).toFloat())
            setUndoEnabled(true)
            colorScheme = EditorLanguages.colorScheme()
            subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                if (!loading) onTextChanged()
            }
            subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                updateStatus()
            }
        }
    }

    private fun setupSymbols() {
        addSymbol("Tab", wide = true) {
            insertText(" ".repeat(settings.tabSize))
        }
        addSymbol("↶") { binding.editor.undo() }
        addSymbol("↷") { binding.editor.redo() }
        addSymbol("{}") { insertPair("{", "}") }
        addSymbol("()") { insertPair("(", ")") }
        addSymbol("[]") { insertPair("[", "]") }
        addSymbol("<>") { insertPair("<", ">") }
        addSymbol("\"") { insertText("\"") }
        addSymbol("'") { insertText("'") }
        addSymbol("=") { insertText("=") }
        addSymbol("+") { insertText("+") }
        addSymbol("-") { insertText("-") }
        addSymbol("*") { insertText("*") }
        addSymbol("/") { insertText("/") }
        addSymbol(";") { insertText(";") }
        addSymbol(":") { insertText(":") }
        addSymbol("_") { insertText("_") }
        addSymbol("#") { insertText("#") }
        addSymbol("$") { insertText("$") }
        addSymbol("&") { insertText("&") }
        addSymbol("|") { insertText("|") }
        addSymbol("!") { insertText("!") }
        addSymbol("?") { insertText("?") }
        addSymbol("\\") { insertText("\\") }
        addSymbol("@") { insertText("@") }
    }

    private fun addSymbol(label: String, wide: Boolean = false, action: () -> Unit) {
        val context = this
        val view = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(context, R.color.kali_text))
            textSize = 13f
            setBackgroundResource(R.drawable.bg_input)
            isClickable = true
            isFocusable = true
            minWidth = dp(if (wide) 48f else 38f)
            height = dp(32f)
            setPadding(dp(7f), 0, dp(7f), 0)
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(32f),
        )
        lp.marginEnd = dp(5f)
        binding.symbolRow.addView(view, lp)
    }

    private fun insertText(text: String) {
        if (activeTab == null) return
        binding.editor.insertText(text, text.length)
        binding.editor.requestFocus()
    }

    private fun insertPair(open: String, close: String) {
        if (activeTab == null) return
        binding.editor.insertText(open + close, open.length)
        binding.editor.requestFocus()
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applySearch()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchNext()
                true
            } else {
                false
            }
        }
        binding.searchPrev.setOnClickListener { searchPrevious() }
        binding.searchNext.setOnClickListener { searchNext() }
        binding.searchClose.setOnClickListener { hideSearch() }
        binding.caseToggle.setOnClickListener {
            searchCaseSensitive = !searchCaseSensitive
            updateSearchToggles()
            applySearch()
        }
        binding.wordToggle.setOnClickListener {
            searchWholeWord = !searchWholeWord
            if (searchWholeWord) searchRegex = false
            updateSearchToggles()
            applySearch()
        }
        binding.regexToggle.setOnClickListener {
            searchRegex = !searchRegex
            if (searchRegex) searchWholeWord = false
            updateSearchToggles()
            applySearch()
        }
        binding.replaceButton.setOnClickListener { replaceCurrent() }
        binding.replaceAllButton.setOnClickListener { replaceAll() }
        updateSearchToggles()
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })
    }

    private fun handleBack() {
        if (binding.searchBar.visibility == View.VISIBLE) {
            hideSearch()
            return
        }
        if (tabs.isEmpty()) {
            finish()
            return
        }
        if (settings.autoSave) {
            saveAllDirty(silent = true)
            finish()
            return
        }
        if (tabs.none { it.dirty }) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.discard_title)
            .setMessage("You have unsaved changes in one or more tabs.")
            .setNegativeButton(R.string.discard) { _, _ -> finish() }
            .setNeutralButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save_and_close) { _, _ ->
                if (saveAllDirty(silent = false)) finish()
            }
            .show()
    }

    private fun openInitialFile() {
        if (initialLoadStarted) return
        initialLoadStarted = true
        lifecycleScope.launch {
            val entries = runCatching { withContext(Dispatchers.IO) { workspace.list("") } }
                .getOrElse {
                    toast(it.message ?: getString(R.string.open_failed))
                    emptyList()
                }
            val preferred = listOf("index.html", "README.md", "main.py")
            val chosen = preferred.firstNotNullOfOrNull { name ->
                entries.firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) }
            } ?: entries.firstOrNull { !it.isDirectory }
            if (chosen != null) {
                openFile(chosen.path)
            } else if (!isFinishing && !isDestroyed) {
                binding.root.post { showFileSheet() }
            }
        }
    }

    private fun openFile(path: String) {
        if (path.isEmpty()) return
        val existing = tabs.indexOfFirst { it.path == path }
        if (existing >= 0) {
            showTab(existing)
            return
        }
        if (tabs.size >= 8) {
            toast("Close a tab before opening more than 8 files")
            return
        }
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { workspace.read(path) }
                val name = WorkspacePaths.baseName(path)
                val tab = OpenFile(
                    path = path,
                    name = name,
                    languageId = EditorLanguages.detect(name)?.id,
                    content = text,
                )
                tabs.add(tab)
                showTab(tabs.lastIndex)
            } catch (e: Exception) {
                toast(e.message ?: "Could not open file")
            }
        }
    }

    private fun showTab(index: Int) {
        if (index !in tabs.indices) return
        if (index != activeIndex) {
            captureActive()
            activeIndex = index
        }
        renderActive()
        refreshTabs()
    }

    private fun renderActive() {
        val tab = activeTab ?: run {
            clearEditor()
            return
        }
        loading = true
        try {
            binding.editor.setText(tab.content)
            val spec = EditorLanguages.byId(tab.languageId)
                ?: EditorLanguages.detect(tab.name).also { tab.languageId = it?.id }
            EditorLanguages.applyLanguage(binding.editor, spec)
            val line = tab.cursorLine.coerceIn(0, maxOf(0, binding.editor.text.lineCount - 1))
            runCatching { binding.editor.setSelection(line, tab.cursorColumn, false) }
                .onFailure { binding.editor.setSelection(line, 0, false) }
        } finally {
            loading = false
        }
        updateHeader()
        updateStatus()
    }

    private fun clearEditor() {
        activeIndex = -1
        loading = true
        try {
            binding.editor.setText("")
            EditorLanguages.applyLanguage(binding.editor, null)
        } finally {
            loading = false
        }
        updateHeader()
        updateStatus()
    }

    private fun captureActive() {
        val tab = activeTab ?: return
        tab.content = binding.editor.text.toString()
        val cursor = binding.editor.cursor
        tab.cursorLine = cursor.leftLine
        tab.cursorColumn = cursor.leftColumn
    }

    private fun onTextChanged() {
        val tab = activeTab ?: return
        val wasDirty = tab.dirty
        tab.dirty = true
        updateStatus()
        if (!wasDirty) refreshTabs()
    }

    private fun refreshTabs() {
        tabAdapter.submit(tabs.map { TabInfo(it.name, it.dirty) }, activeIndex)
        if (activeIndex >= 0) {
            binding.tabStrip.post { binding.tabStrip.scrollToPosition(activeIndex) }
        }
    }

    private fun updateHeader() {
        val tab = activeTab
        binding.toolbar.title = tab?.name ?: getString(R.string.no_file)
        binding.toolbar.subtitle = workspace.displayName
        val spec = tab?.let { EditorLanguages.byId(it.languageId) }
        binding.languageText.text = spec?.label ?: "Plain text"
        val menu = binding.toolbar.menu
        menu.findItem(R.id.action_save)?.isEnabled = tab != null
        menu.findItem(R.id.action_search)?.isEnabled = tab != null
        val runItem = menu.findItem(R.id.action_run)
        runItem?.isEnabled = tab != null
        runItem?.title = when (spec?.id) {
            "html", "css", "javascript" -> getString(R.string.preview)
            else -> getString(R.string.run)
        }
    }

    private fun updateStatus() {
        if (!::binding.isInitialized) return
        val cursor = binding.editor.cursor
        val selection = if (cursor.isSelected) " · selection" else ""
        binding.positionText.text = "Ln ${cursor.leftLine + 1}, Col ${cursor.leftColumn + 1}$selection"
        val tab = activeTab
        binding.modifiedText.text = if (tab?.dirty == true) "●" else ""
        binding.languageText.text = tab?.let { EditorLanguages.byId(it.languageId)?.label } ?: "Plain text"
    }

    private fun saveActive(showToast: Boolean): Boolean {
        val tab = activeTab ?: return false
        captureActive()
        return try {
            workspace.write(tab.path, tab.content)
            tab.dirty = false
            refreshTabs()
            updateStatus()
            if (showToast) toast(getString(R.string.saved))
            true
        } catch (e: Exception) {
            toast(e.message ?: getString(R.string.save_failed))
            false
        }
    }

    private fun saveTab(index: Int, showToast: Boolean): Boolean {
        val tab = tabs.getOrNull(index) ?: return false
        if (index == activeIndex) captureActive()
        return try {
            workspace.write(tab.path, tab.content)
            tab.dirty = false
            refreshTabs()
            updateStatus()
            if (showToast) toast(getString(R.string.saved))
            true
        } catch (e: Exception) {
            toast(e.message ?: getString(R.string.save_failed))
            false
        }
    }

    private fun saveAllDirty(silent: Boolean): Boolean {
        if (tabs.isEmpty()) return true
        captureActive()
        var failed = false
        tabs.forEach { tab ->
            if (!tab.dirty) return@forEach
            try {
                workspace.write(tab.path, tab.content)
                tab.dirty = false
            } catch (e: Exception) {
                failed = true
                if (!silent) toast(e.message ?: getString(R.string.save_failed))
            }
        }
        refreshTabs()
        updateStatus()
        return !failed
    }

    private fun confirmCloseTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        if (!tab.dirty) {
            closeTab(index)
            return
        }
        if (settings.autoSave) {
            val saved = if (index == activeIndex) saveActive(showToast = false) else saveTab(index, false)
            if (saved) closeTab(index)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(tab.name)
            .setMessage(R.string.discard_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.discard) { _, _ -> closeTab(index) }
            .setPositiveButton(R.string.save_and_close) { _, _ ->
                if (saveTab(index, showToast = false)) closeTab(index)
            }
            .show()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val wasActive = index == activeIndex
        if (wasActive) captureActive()
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            activeIndex = -1
            clearEditor()
            refreshTabs()
            return
        }
        when {
            wasActive -> {
                activeIndex = index.coerceAtMost(tabs.lastIndex)
                renderActive()
            }
            index < activeIndex -> activeIndex--
        }
        refreshTabs()
        updateHeader()
        updateStatus()
    }

    private fun showSearch() {
        if (activeTab == null) return
        binding.searchBar.visibility = View.VISIBLE
        binding.searchInput.requestFocus()
        binding.searchInput.setSelection(binding.searchInput.text.length)
        binding.searchInput.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun hideSearch() {
        binding.searchBar.visibility = View.GONE
        binding.searchInput.text?.clear()
        binding.replaceInput.text?.clear()
        binding.matchText.text = ""
        binding.editor.searcher.stopSearch()
        binding.editor.requestFocus()
    }

    private fun applySearch() {
        val query = binding.searchInput.text?.toString().orEmpty()
        binding.matchText.text = ""
        if (query.isEmpty()) {
            binding.editor.searcher.stopSearch()
            return
        }
        val type = when {
            searchRegex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
            searchWholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
            else -> EditorSearcher.SearchOptions.TYPE_NORMAL
        }
        try {
            binding.editor.searcher.search(
                query,
                EditorSearcher.SearchOptions(type, !searchCaseSensitive),
            )
            binding.editor.postDelayed({ updateMatchCount() }, 180)
        } catch (e: Exception) {
            binding.matchText.text = "bad regex"
        }
    }

    private fun updateMatchCount() {
        val searcher = binding.editor.searcher
        if (!searcher.hasQuery()) {
            binding.matchText.text = ""
            return
        }
        val count = searcher.matchedPositionCount
        binding.matchText.text = if (count > 0) {
            "${searcher.currentMatchedPositionIndex + 1}/$count"
        } else {
            "0"
        }
    }

    private fun updateSearchToggles() {
        val active = ContextCompat.getColor(this, R.color.kali_accent)
        val inactive = ContextCompat.getColor(this, R.color.kali_muted)
        binding.caseToggle.setTextColor(if (searchCaseSensitive) active else inactive)
        binding.wordToggle.setTextColor(if (searchWholeWord) active else inactive)
        binding.regexToggle.setTextColor(if (searchRegex) active else inactive)
    }

    private fun searchNext() {
        if (binding.editor.searcher.hasQuery()) {
            binding.editor.searcher.gotoNext()
            updateMatchCount()
        }
    }

    private fun searchPrevious() {
        if (binding.editor.searcher.hasQuery()) {
            binding.editor.searcher.gotoPrevious()
            updateMatchCount()
        }
    }

    private fun replaceCurrent() {
        if (!binding.editor.searcher.hasQuery()) return
        binding.editor.searcher.replaceCurrentMatch(binding.replaceInput.text?.toString().orEmpty())
        applySearch()
    }

    private fun replaceAll() {
        if (!binding.editor.searcher.hasQuery()) return
        binding.editor.searcher.replaceAll(binding.replaceInput.text?.toString().orEmpty())
        applySearch()
    }

    private fun runActive() {
        val tab = activeTab ?: return
        val spec = EditorLanguages.byId(tab.languageId)
        when (spec?.id) {
            "python" -> {
                if (!saveActive(showToast = false)) return
                preparePythonRun(tab)
            }
            "html", "css", "javascript" -> {
                if (!saveActive(showToast = false)) return
                val mode = if (spec.id == "html") "html" else if (spec.id == "css") "css" else "js"
                startActivity(
                    Intent(this, PreviewActivity::class.java)
                        .putExtra(PreviewActivity.EXTRA_PATH, tab.path)
                        .putExtra(PreviewActivity.EXTRA_TITLE, tab.name)
                        .putExtra(PreviewActivity.EXTRA_MODE, mode)
                )
            }
            else -> {
                showInfo(
                    getString(R.string.run),
                    "Live preview is available for HTML, CSS and JavaScript. This file can be edited with native syntax highlighting and search."
                )
            }
        }
    }

    private fun preparePythonRun(tab: OpenFile) {
        if (preparingPython) return
        preparingPython = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val runRoot = File(cacheDir, "python-run")
                    val runDirectory = File(runRoot, System.currentTimeMillis().toString())
                    val relative = WorkspaceExporter.exportProject(workspace, runDirectory, tab.path)
                    File(runDirectory, relative).absolutePath
                }
            }
            preparingPython = false
            result.onSuccess { scriptPath ->
                startActivity(
                    Intent(this@EditorActivity, PythonRunActivity::class.java)
                        .putExtra(PythonRunActivity.EXTRA_SCRIPT, scriptPath)
                        .putExtra(PythonRunActivity.EXTRA_TITLE, tab.name)
                )
            }.onFailure { error ->
                toast(error.message ?: getString(R.string.python_prepare_failed))
            }
        }
    }

    private fun showFileSheet() {
        val startPath = activeTab?.let { WorkspacePaths.parentOf(it.path) } ?: ""
        fileSheet?.dismiss()
        fileSheet = FileBrowserSheet(
            context = this,
            workspace = workspace,
            startPath = startPath,
            onOpenFile = { path -> openFile(path) },
            onRenamed = { oldPath, newPath -> onFileRenamed(oldPath, newPath) },
            onDeleted = { path -> onPathDeleted(path) },
            onCreatedFile = { path -> openFile(path) },
        ).also { it.show() }
    }

    private fun onFileRenamed(oldPath: String, newPath: String) {
        for (index in tabs.indices) {
            val tab = tabs[index]
            if (tab.path == oldPath || tab.path.startsWith("$oldPath/")) {
                val suffix = tab.path.removePrefix(oldPath)
                tab.path = newPath + suffix
                tab.name = WorkspacePaths.baseName(tab.path)
                tab.languageId = EditorLanguages.detect(tab.name)?.id
                if (index == activeIndex) {
                    EditorLanguages.applyLanguage(binding.editor, EditorLanguages.byId(tab.languageId))
                }
            }
        }
        refreshTabs()
        updateHeader()
    }

    private fun onPathDeleted(path: String) {
        val activePath = activeTab?.path
        tabs.removeAll { it.path == path || it.path.startsWith("$path/") }
        if (tabs.isEmpty()) {
            activeIndex = -1
            clearEditor()
            refreshTabs()
            return
        }
        val stillActive = tabs.indexOfFirst { it.path == activePath }
        activeIndex = if (stillActive >= 0) stillActive else activeIndex.coerceAtMost(tabs.lastIndex)
        renderActive()
        refreshTabs()
    }

    private fun showMore(anchor: View?) {
        val popup = PopupMenu(this, anchor ?: binding.toolbar)
        popup.menuInflater.inflate(R.menu.menu_editor_more, popup.menu)
        popup.menu.findItem(R.id.action_word_wrap)?.isChecked = settings.wordWrap
        popup.menu.findItem(R.id.action_go_to_line)?.isEnabled = activeTab != null
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_go_to_line -> {
                    goToLine()
                    true
                }
                R.id.action_word_wrap -> {
                    settings.wordWrap = !settings.wordWrap
                    binding.editor.setWordwrap(settings.wordWrap)
                    true
                }
                R.id.action_settings -> {
                    showSettings()
                    true
                }
                R.id.action_about -> {
                    showAbout()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun goToLine() {
        val input = DialogInputBinding.inflate(layoutInflater)
        input.inputName.hint = "Line number"
        input.inputName.inputType = InputType.TYPE_CLASS_NUMBER
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.go_to_line)
            .setView(input.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Go", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val line = input.inputName.text?.toString()?.toIntOrNull()
                if (line == null || line < 1 || line > binding.editor.text.lineCount) {
                    input.inputName.error = "Invalid line"
                    return@setOnClickListener
                }
                dialog.dismiss()
                binding.editor.setSelection(line - 1, 0, true)
                binding.editor.requestFocus()
            }
        }
        dialog.show()
        input.inputName.requestFocus()
    }

    private fun showSettings() {
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        dialogBinding.themeGroup.check(
            if (settings.editorTheme == "paper") R.id.themePaper else R.id.themeKali
        )
        dialogBinding.fontSeek.progress = (settings.fontSize - 10).coerceIn(0, 18)
        dialogBinding.fontValue.text = settings.fontSize.toString()
        dialogBinding.switchWrap.isChecked = settings.wordWrap
        dialogBinding.switchLineNumbers.isChecked = settings.lineNumbers
        dialogBinding.switchAutoSave.isChecked = settings.autoSave
        dialogBinding.tabSeek.progress = (settings.tabSize - 2).coerceIn(0, 6)
        dialogBinding.tabValue.text = "${settings.tabSize} spaces"

        dialogBinding.themeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val theme = if (checkedId == R.id.themePaper) "paper" else "kali"
            settings.editorTheme = theme
            EditorLanguages.applyTheme(theme)
            binding.editor.colorScheme = EditorLanguages.colorScheme()
            binding.editor.setBackgroundColor(
                if (theme == "paper") 0xFFFBFCF8.toInt() else 0xFF0B0F0C.toInt()
            )
        }

        dialogBinding.fontSeek.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 10
                settings.fontSize = size
                dialogBinding.fontValue.text = size.toString()
                binding.editor.setTextSizePx(sp(size))
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        dialogBinding.tabSeek.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 2
                settings.tabSize = size
                dialogBinding.tabValue.text = "$size spaces"
                binding.editor.setTabWidth(size)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        dialogBinding.switchWrap.setOnCheckedChangeListener { _, checked ->
            settings.wordWrap = checked
            binding.editor.setWordwrap(checked)
        }
        dialogBinding.switchLineNumbers.setOnCheckedChangeListener { _, checked ->
            settings.lineNumbers = checked
            binding.editor.setLineNumberEnabled(checked)
        }
        dialogBinding.switchAutoSave.setOnCheckedChangeListener { _, checked ->
            settings.autoSave = checked
            if (checked) saveAllDirty(silent = true)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setView(dialogBinding.root)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about)
            .setMessage(R.string.about_body)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showInfo(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    saveActive(showToast = true)
                    return true
                }
                KeyEvent.KEYCODE_F -> {
                    showSearch()
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed) binding.editor.redo() else binding.editor.undo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    binding.editor.redo()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun sp(value: Int): Float = value * resources.displayMetrics.scaledDensity

    private data class OpenFile(
        var path: String,
        var name: String,
        var languageId: String?,
        var content: String,
        var cursorLine: Int = 0,
        var cursorColumn: Int = 0,
        var dirty: Boolean = false,
    )
}
