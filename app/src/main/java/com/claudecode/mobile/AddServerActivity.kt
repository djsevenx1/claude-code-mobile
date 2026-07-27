package com.claudecode.mobile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.claudecode.mobile.data.ServerRepository
import com.claudecode.mobile.databinding.ActivityAddServerBinding
import com.google.android.material.snackbar.Snackbar

class AddServerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddServerBinding
    private lateinit var serverRepo: ServerRepository
    private var editId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddServerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        serverRepo = ServerRepository(this)

        val isEdit = intent.getBooleanExtra(SettingsActivity.EXTRA_IS_EDIT, false)
        if (isEdit) {
            editId = intent.getLongExtra(SettingsActivity.EXTRA_EDIT_ID, -1L)
            binding.editName.setText(intent.getStringExtra(SettingsActivity.EXTRA_EDIT_NAME))
            binding.editUrl.setText(intent.getStringExtra(SettingsActivity.EXTRA_EDIT_URL))
            binding.switchTrustAll.isChecked = intent.getBooleanExtra(SettingsActivity.EXTRA_EDIT_TRUST, false)
            binding.toolbar.title = getString(R.string.edit_server_title)
            binding.btnDelete.visibility = View.VISIBLE
        } else {
            binding.toolbar.title = getString(R.string.add_server_title)
            binding.btnDelete.visibility = View.GONE
        }

        binding.btnSave.setOnClickListener {
            saveServer()
        }

        binding.btnDelete.setOnClickListener {
            if (editId > 0) {
                serverRepo.delete(editId)
                Toast.makeText(this, "Server deleted", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun saveServer() {
        val name = binding.editName.text.toString().trim()
        val url = binding.editUrl.text.toString().trim()
        val trustAll = binding.switchTrustAll.isChecked

        if (name.isEmpty()) {
            binding.editNameLayout.error = "Name is required"
            return
        }
        if (url.isEmpty()) {
            binding.editUrlLayout.error = "URL is required"
            return
        }

        // Basic URL validation
        val testUrl = if (url.startsWith("http")) url else "http://$url"
        try {
            java.net.URI(testUrl)
        } catch (e: Exception) {
            binding.editUrlLayout.error = "Invalid URL"
            return
        }

        if (editId > 0) {
            serverRepo.update(editId, name, url, trustAll)
            Snackbar.make(binding.root, "Server updated", Snackbar.LENGTH_SHORT).show()
        } else {
            serverRepo.add(name, url, trustAll)
            Snackbar.make(binding.root, "Server added", Snackbar.LENGTH_SHORT).show()
        }

        setResult(RESULT_OK)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
