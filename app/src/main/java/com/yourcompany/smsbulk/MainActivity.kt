package com.yourcompany.smsbulk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourcompany.smsbulk.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ContactsAdapter

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS
    )

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            loadContacts()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ContactsAdapter { count ->
            updateSelectedCount(count)
        }
        binding.recyclerContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerContacts.adapter = adapter

        binding.checkboxSelectAll.setOnCheckedChangeListener { _, isChecked ->
            adapter.setAllSelected(isChecked)
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnNext.setOnClickListener {
            val selected = adapter.getSelectedContacts()
            if (selected.isEmpty()) {
                Toast.makeText(this, "اختر جهة اتصال واحدة على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SelectionRepository.selectedContacts = selected
            startActivity(Intent(this, ComposeActivity::class.java))
        }

        if (hasAllPermissions()) {
            loadContacts()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadContacts() {
        binding.progressLoading.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) { fetchContacts() }
            binding.progressLoading.visibility = android.view.View.GONE
            adapter.submitList(contacts)
            updateSelectedCount(0)
        }
    }

    private fun fetchContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val rawNumber = it.getString(numIdx) ?: continue
                val cleanNumber = rawNumber.replace(" ", "").replace("-", "")
                contacts.add(Contact(name, cleanNumber))
            }
        }
        // إزالة الأرقام المكررة (جهة اتصال محفوظة أكثر من مرة)
        return contacts.distinctBy { it.number }
    }

    private fun updateSelectedCount(count: Int) {
        binding.tvSelectedCount.text = getString(R.string.selected_count, count)
        binding.btnNext.isEnabled = count > 0
    }
}
