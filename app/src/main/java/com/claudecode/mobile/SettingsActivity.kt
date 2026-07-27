package com.claudecode.mobile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.claudecode.mobile.data.ServerConfig
import com.claudecode.mobile.data.ServerRepository
import com.claudecode.mobile.databinding.ActivitySettingsBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var serverRepo: ServerRepository
    private lateinit var adapter: ServerAdapter
    private val servers = mutableListOf<ServerConfig>()

    companion object {
        const val EXTRA_EDIT_ID = "edit_id"
        const val EXTRA_EDIT_NAME = "edit_name"
        const val EXTRA_EDIT_URL = "edit_url"
        const val EXTRA_EDIT_TRUST = "edit_trust"
        const val EXTRA_IS_EDIT = "is_edit"
    }

    private val addServerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                refreshList()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        serverRepo = ServerRepository(this)

        adapter = ServerAdapter(servers,
            onDefaultClick = { server ->
                serverRepo.setDefault(server.id)
                refreshList()
                Snackbar.make(binding.root, "Default server: ${server.name}", Snackbar.LENGTH_SHORT).show()
            },
            onEditClick = { server ->
                val intent = Intent(this, AddServerActivity::class.java).apply {
                    putExtra(EXTRA_IS_EDIT, true)
                    putExtra(EXTRA_EDIT_ID, server.id)
                    putExtra(EXTRA_EDIT_NAME, server.name)
                    putExtra(EXTRA_EDIT_URL, server.url)
                    putExtra(EXTRA_EDIT_TRUST, server.trustAllCerts)
                }
                addServerLauncher.launch(intent)
            },
            onDeleteClick = { server ->
                AlertDialog.Builder(this)
                    .setTitle("Delete server")
                    .setMessage("Delete '${server.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        serverRepo.delete(server.id)
                        refreshList()
                        Snackbar.make(binding.root, "Deleted ${server.name}", Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.recyclerServers.layoutManager = LinearLayoutManager(this)
        binding.recyclerServers.adapter = adapter

        binding.fabAdd.setOnClickListener {
            addServerLauncher.launch(Intent(this, AddServerActivity::class.java))
        }

        refreshList()
    }

    private fun refreshList() {
        servers.clear()
        servers.addAll(serverRepo.getAll())
        adapter.notifyDataSetChanged()

        binding.textEmpty.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private inner class ServerAdapter(
        private val items: List<ServerConfig>,
        private val onDefaultClick: (ServerConfig) -> Unit,
        private val onEditClick: (ServerConfig) -> Unit,
        private val onDeleteClick: (ServerConfig) -> Unit
    ) : RecyclerView.Adapter<ServerAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.text_server_name)
            val url: TextView = view.findViewById(R.id.text_server_url)
            val badge: TextView = view.findViewById(R.id.text_default_badge)
            val btnDefault: View = view.findViewById(R.id.btn_set_default)
            val btnEdit: View = view.findViewById(R.id.btn_edit)
            val btnDelete: View = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_server, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val server = items[position]
            holder.name.text = server.name
            holder.url.text = server.url
            holder.badge.visibility = if (server.isDefault) View.VISIBLE else View.GONE
            holder.btnDefault.visibility = if (server.isDefault) View.GONE else View.VISIBLE
            holder.btnDefault.setOnClickListener { onDefaultClick(server) }
            holder.btnEdit.setOnClickListener { onEditClick(server) }
            holder.btnDelete.setOnClickListener { onDeleteClick(server) }
        }

        override fun getItemCount() = items.size
    }
}
