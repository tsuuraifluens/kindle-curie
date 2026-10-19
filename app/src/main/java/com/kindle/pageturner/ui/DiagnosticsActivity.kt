package com.kindle.pageturner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kindle.pageturner.databinding.ActivityDiagnosticsBinding
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import kotlinx.coroutines.launch

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding
    private val adapter = LogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvLogs.layoutManager = layoutManager
        binding.rvLogs.adapter = adapter

        lifecycleScope.launch {
            DiagnosticLogger.logsFlow.collect { logs ->
                adapter.submitList(logs) {
                    if (logs.isNotEmpty()) {
                        binding.rvLogs.scrollToPosition(logs.size - 1)
                    }
                }
            }
        }

        binding.btnClearLogs.setOnClickListener {
            DiagnosticLogger.clear()
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
        }

        binding.btnExportLogs.setOnClickListener {
            exportLogs()
        }
    }

    private fun exportLogs() {
        val logText = DiagnosticLogger.exportAsText()

        // Also copy to clipboard for convenience
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KindleTurnerLogs", logText))

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, logText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "导出 Kindle 诊断日志 (已复制到剪贴板)")
        startActivity(shareIntent)
    }
}
