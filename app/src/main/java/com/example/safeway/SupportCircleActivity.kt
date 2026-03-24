package com.example.safeway

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.safeway.data.AppDatabase
import com.example.safeway.data.Contact
import kotlinx.coroutines.launch

class SupportCircleActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnAddContact: LinearLayout
    private lateinit var contactsContainer: LinearLayout
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support_circle)

        database = AppDatabase.getDatabase(this)

        initializeViews()
        setupListeners()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_circle)
        btnAddContact = findViewById(R.id.btn_add_contact)
        contactsContainer = findViewById(R.id.contacts_container)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnAddContact.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            try {
                val contacts = database.contactDao().getAllContacts()
                displayContacts(contacts)
            } catch (e: Exception) {
                Toast.makeText(this@SupportCircleActivity, "Error loading contacts", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayContacts(contacts: List<Contact>) {
        contactsContainer.removeAllViews()

        if (contacts.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No trusted contacts added yet. Tap the + button to add one."
                setTextColor(ContextCompat.getColor(this@SupportCircleActivity, R.color.neutral_muted))
                textSize = 12f
                setPadding(16, 16, 16, 16)
            }
            contactsContainer.addView(emptyText)
            return
        }

        contacts.forEach { contact ->
            val card = createContactCard(contact)
            contactsContainer.addView(card)
        }
    }

    private fun createContactCard(contact: Contact): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card_background)
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        }

        // Avatar
        val avatar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx())
            setBackgroundResource(R.drawable.avatar_background)
        }

        val initials = TextView(this).apply {
            text = contact.name.take(2).uppercase()
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@SupportCircleActivity, R.color.highlight_accent))
        }
        avatar.addView(initials)

        // Info Layout
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12.dpToPx(), 0, 0, 0)
        }

        val name = TextView(this).apply {
            text = contact.name
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@SupportCircleActivity, R.color.text_primary))
        }

        val phone = TextView(this).apply {
            text = "${contact.relationship} • ${contact.phone}"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@SupportCircleActivity, R.color.neutral_muted))
            setPadding(0, 4, 0, 0)
        }

        infoLayout.addView(name)
        infoLayout.addView(phone)

        // Status Layout
        val statusLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8.dpToPx()
            }
        }

        val statusChip = com.google.android.material.chip.Chip(this).apply {
            text = if (contact.smsAlerts) "SMS alerts" else "Disabled"
            textSize = 11f
            isClickable = false
            isCheckable = false
            chipBackgroundColor = ContextCompat.getColorStateList(
                this@SupportCircleActivity,
                if (contact.smsAlerts) R.color.primary_accent else R.color.card_background
            )
            chipStrokeColor = ContextCompat.getColorStateList(
                this@SupportCircleActivity,
                R.color.border_dark
            )
            chipStrokeWidth = 1f
            setEnsureMinTouchTargetSize(false)
        }

        statusLayout.addView(statusChip)

        // Delete Button
        val deleteBtn = Button(this).apply {
            text = "Remove"
            textSize = 11f
            minHeight = 36.dpToPx()
            setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
            setTextColor(ContextCompat.getColor(this@SupportCircleActivity, R.color.emergency_red))
            background = ContextCompat.getDrawable(this@SupportCircleActivity, R.drawable.button_secondary_bg)
            setOnClickListener {
                val dialog = AlertDialog.Builder(this@SupportCircleActivity)
                    .setTitle("Remove Contact")
                    .setMessage("Remove ${contact.name} from your support circle?")
                    .setPositiveButton("Remove") { _, _ ->
                        lifecycleScope.launch {
                            database.contactDao().deleteContact(contact)
                            loadContacts()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        ?.setTextColor(ContextCompat.getColor(this@SupportCircleActivity, R.color.emergency_red))
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        ?.setTextColor(ContextCompat.getColor(this@SupportCircleActivity, R.color.highlight_accent))
                }
                dialog.show()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        card.addView(avatar)
        card.addView(infoLayout)
        card.addView(statusLayout)
        card.addView(deleteBtn)

        return card
    }

    private fun showAddContactDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_add_contact, null)

        val nameInput = view.findViewById<EditText>(R.id.input_name)
        val phoneInput = view.findViewById<EditText>(R.id.input_phone)
        val relationInput = view.findViewById<EditText>(R.id.input_relationship)
        val smsAlertsCheckbox = view.findViewById<CheckBox>(R.id.checkbox_sms_alerts)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Trusted Contact")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                if (nameInput.text.isBlank() || phoneInput.text.isBlank()) {
                    Toast.makeText(this, "Please fill in name and phone", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val contact = Contact(
                    name = nameInput.text.toString(),
                    phone = phoneInput.text.toString(),
                    relationship = relationInput.text.toString().ifBlank { "Friend" },
                    smsAlerts = smsAlertsCheckbox.isChecked
                )

                lifecycleScope.launch {
                    database.contactDao().insertContact(contact)
                    loadContacts()
                    Toast.makeText(this@SupportCircleActivity, "Contact added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.highlight_accent))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.neutral_text))
        }
        dialog.show()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}



