package com.saikishore.kalicodepocket

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.saikishore.kalicodepocket.data.RecentProject
import com.saikishore.kalicodepocket.data.WorkspaceManager
import com.saikishore.kalicodepocket.databinding.ActivityMainBinding
import com.saikishore.kalicodepocket.ui.RecentAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var recentAdapter: RecentAdapter
    private var opening = false

    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) openSaf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recentAdapter = RecentAdapter(
            onClick = ::openRecent,
            onLongClick = ::confirmForget,
        )
        binding.recentList.layoutManager = LinearLayoutManager(this)
        binding.recentList.adapter = recentAdapter

        binding.openFolder.setOnClickListener { openTree.launch(null) }
        binding.openPlayground.setOnClickListener { openPlayground() }
    }

    override fun onResume() {
        super.onResume()
        renderRecent()
    }

    private fun renderRecent() {
        val recent = WorkspaceManager.recent(this)
        recentAdapter.submitList(recent)
        binding.emptyRecent.visibility = if (recent.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.recentList.visibility = if (recent.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

        val latest = recent.firstOrNull()
        if (latest == null) {
            binding.continueCard.visibility = android.view.View.GONE
        } else {
            binding.continueCard.visibility = android.view.View.VISIBLE
            binding.continueName.text = latest.name
            binding.continueCard.setOnClickListener { openRecent(latest) }
        }
    }

    private fun openPlayground() {
        if (opening) return
        setOpening(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { WorkspaceManager.openSample(this@MainActivity) }
                launchEditor()
            } catch (e: Exception) {
                toast(e.message ?: getString(R.string.open_failed))
            } finally {
                setOpening(false)
            }
        }
    }

    private fun openSaf(uri: Uri) {
        if (opening) return
        setOpening(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { WorkspaceManager.openTree(this@MainActivity, uri) }
                launchEditor()
            } catch (e: Exception) {
                toast(e.message ?: getString(R.string.open_failed))
            } finally {
                setOpening(false)
            }
        }
    }

    private fun openRecent(project: RecentProject) {
        if (opening) return
        if (project.type == "sample") {
            openPlayground()
            return
        }
        val uri = runCatching { Uri.parse(project.uri) }.getOrNull()
        if (uri == null) {
            toast(getString(R.string.open_failed))
            return
        }
        openSaf(uri)
    }

    private fun confirmForget(project: RecentProject) {
        MaterialAlertDialogBuilder(this)
            .setTitle(project.name)
            .setMessage("Remove this project from the recent list? The files stay on disk.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Remove") { _, _ ->
                WorkspaceManager.forget(this, project)
                renderRecent()
            }
            .show()
    }

    private fun launchEditor() {
        startActivity(Intent(this, EditorActivity::class.java))
    }

    private fun setOpening(value: Boolean) {
        opening = value
        binding.openFolder.isEnabled = !value
        binding.openPlayground.isEnabled = !value
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
