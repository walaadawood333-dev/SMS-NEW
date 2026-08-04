package com.yourcompany.smsbulk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.yourcompany.smsbulk.databinding.ActivityComposeBinding

class ComposeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComposeBinding
    private var isSending = false

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val current = intent.getIntExtra(SmsSenderService.EXTRA_CURRENT, 0)
            val total = intent.getIntExtra(SmsSenderService.EXTRA_TOTAL, 0)
            val status = intent.getStringExtra(SmsSenderService.EXTRA_STATUS).orEmpty()
            val finished = intent.getBooleanExtra(SmsSenderService.EXTRA_FINISHED, false)

            binding.progressBar.max = if (total > 0) total else 1
            binding.progressBar.progress = current
            binding.tvProgressCount.text = "$current / $total"
            binding.tvStatus.text = status

            if (finished) {
                setSendingState(false)
                Toast.makeText(this@ComposeActivity, status, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val selectedContacts = SelectionRepository.selectedContacts
        if (selectedContacts.isEmpty()) {
            Toast.makeText(this, "لم يتم اختيار جهات اتصال", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvSelectedInfo.text = getString(R.string.selected_count, selectedContacts.size)
        binding.etDelay.setText("10")

        binding.btnStart.setOnClickListener {
            if (isSending) return@setOnClickListener
            startSending(selectedContacts)
        }

        binding.btnStop.setOnClickListener {
            val stopIntent = Intent(this, SmsSenderService::class.java).apply {
                action = SmsSenderService.ACTION_STOP
            }
            startService(stopIntent)
        }

        setSendingState(false)
    }

    private fun startSending(contacts: List<Contact>) {
        val message = binding.etMessage.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "يرجى كتابة نص الرسالة أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val delaySeconds = binding.etDelay.text.toString().toIntOrNull()
        if (delaySeconds == null || delaySeconds < 3) {
            Toast.makeText(this, "الرجاء إدخال تأخير 3 ثواني على الأقل لتجنب حظر الشريحة", Toast.LENGTH_LONG).show()
            return
        }

        val names = ArrayList(contacts.map { it.name })
        val numbers = ArrayList(contacts.map { it.number })

        val startIntent = Intent(this, SmsSenderService::class.java).apply {
            action = SmsSenderService.ACTION_START
            putStringArrayListExtra(SmsSenderService.EXTRA_NAMES, names)
            putStringArrayListExtra(SmsSenderService.EXTRA_NUMBERS, numbers)
            putExtra(SmsSenderService.EXTRA_MESSAGE, message)
            putExtra(SmsSenderService.EXTRA_DELAY_SECONDS, delaySeconds)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, startIntent)
        } else {
            startService(startIntent)
        }

        setSendingState(true)
        binding.progressBar.progress = 0
        binding.tvStatus.text = "جاري البدء..."
    }

    private fun setSendingState(sending: Boolean) {
        isSending = sending
        binding.btnStart.isEnabled = !sending
        binding.btnStop.isEnabled = sending
        binding.etMessage.isEnabled = !sending
        binding.etDelay.isEnabled = !sending
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            progressReceiver, IntentFilter(SmsSenderService.ACTION_PROGRESS)
        )
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(progressReceiver)
    }
}
