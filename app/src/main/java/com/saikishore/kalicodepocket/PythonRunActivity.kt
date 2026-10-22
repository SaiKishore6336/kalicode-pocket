package com.saikishore.kalicodepocket

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.saikishore.kalicodepocket.databinding.ActivityPythonRunBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class PythonRunActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPythonRunBinding
    private var scriptPath = ""
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPythonRunBinding.inflate(layoutInflater)
        setContentView(binding.root)
        activeActivity = this

        scriptPath = intent.getStringExtra(EXTRA_SCRIPT).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (title.isNotBlank()) binding.toolbar.subtitle = title
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.copyOutput.setOnClickListener { copyOutput() }
        binding.runAgain.setOnClickListener { runScript() }

        if (scriptPath.isBlank() || !File(scriptPath).isFile) {
            binding.statusText.setText(R.string.python_prepare_failed)
            binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.kali_error))
            binding.outputText.text = scriptPath
            return
        }
        runScript()
    }

    private fun runScript() {
        if (running) return
        running = true
        binding.progress.visibility = View.VISIBLE
        binding.statusText.setText(R.string.python_starting)
        binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.kali_accent))
        binding.outputText.text = ""
        binding.runAgain.isEnabled = false
        binding.copyOutput.isEnabled = false

        lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(applicationContext))
                    }
                    Python.getInstance()
                        .getModule("kali_runner")
                        .callAttr("run_file", scriptPath)
                        .toString()
                }
            }
            val elapsed = formatDuration(System.currentTimeMillis() - startedAt)
            running = false
            binding.progress.visibility = View.GONE
            binding.runAgain.isEnabled = true
            binding.copyOutput.isEnabled = true

            outcome.onSuccess { text ->
                binding.statusText.text = getString(R.string.python_finished, elapsed)
                binding.outputText.text = text
                val failed = text.contains("Traceback (most recent call last):") ||
                    text.startsWith("Process exited with code")
                binding.statusText.setTextColor(
                    ContextCompat.getColor(
                        this@PythonRunActivity,
                        if (failed) R.color.kali_error else R.color.kali_accent
                    )
                )
            }.onFailure { error ->
                binding.statusText.setText(R.string.python_failed)
                binding.statusText.setTextColor(ContextCompat.getColor(this@PythonRunActivity, R.color.kali_error))
                binding.outputText.text = buildString {
                    append(error.message ?: error.javaClass.simpleName)
                    val cause = error.cause
                    if (cause != null && cause.message != error.message) {
                        append("\n\n")
                        append(cause.message ?: cause.javaClass.simpleName)
                    }
                }
            }
            binding.outputScroll.post { binding.outputScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        inputLatch?.countDown()
        if (activeActivity === this) activeActivity = null
        super.onDestroy()
    }

    private fun copyOutput() {
        val text = binding.outputText.text?.toString().orEmpty()
        if (text.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Python output", text))
        Toast.makeText(this, R.string.output_copied, Toast.LENGTH_SHORT).show()
    }

    private fun formatDuration(millis: Long): String =
        if (millis < 1000) {
            "$millis ms"
        } else {
            String.format(Locale.US, "%.1f s", millis / 1000.0)
        }

    companion object {
        const val EXTRA_SCRIPT = "python_script"
        const val EXTRA_TITLE = "python_title"

        @Volatile
        private var activeActivity: PythonRunActivity? = null

        @Volatile
        private var inputLatch: CountDownLatch? = null

        /** Called from Python's sys.stdin bridge on the Python worker thread. */
        @JvmStatic
        fun requestInput(): String? {
            val activity = activeActivity ?: return null
            val latch = CountDownLatch(1)
            inputLatch = latch
            val result = arrayOfNulls<String>(1)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    latch.countDown()
                    return@runOnUiThread
                }
                val field = EditText(activity).apply {
                    hint = activity.getString(R.string.program_input_hint)
                    inputType = InputType.TYPE_CLASS_TEXT
                    setSingleLine(true)
                }
                val answered = AtomicBoolean(false)
                fun finish(value: String?) {
                    if (answered.compareAndSet(false, true)) {
                        result[0] = value
                        latch.countDown()
                    }
                }
                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.program_input)
                    .setMessage(R.string.program_input_body)
                    .setView(field)
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish(field.text?.toString().orEmpty()) }
                    .setNegativeButton(R.string.cancel) { _, _ -> finish(null) }
                    .setOnCancelListener { finish(null) }
                    .create()
                dialog.setOnDismissListener { finish(result[0]) }
                dialog.show()
                field.requestFocus()
            }
            return try {
                latch.await()
                result[0]
            } catch (_: InterruptedException) {
                null
            } finally {
                if (inputLatch === latch) inputLatch = null
            }
        }
    }
}
