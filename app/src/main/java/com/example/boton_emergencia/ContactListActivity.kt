package com.example.boton_emergencia

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.boton_emergencia.db.DbHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.concurrent.Executors

class ContactListActivity : AppCompatActivity(), ContactAdapter.Listener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private lateinit var addButton: FloatingActionButton
    private lateinit var db: DbHelper
    private val executor = Executors.newSingleThreadExecutor()

    private var controlNumber: String = ""
    private var isSelectionMode: Boolean = false

    companion object {
        const val EXTRA_CONTROL = "controlNumber"
        const val EXTRA_IS_SELECTION_MODE = "isSelectionMode"
        const val REQ_ADD = 1001
        const val REQ_EDIT = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)

        controlNumber = intent.getStringExtra(EXTRA_CONTROL) ?: ""
        isSelectionMode = intent.getBooleanExtra(EXTRA_IS_SELECTION_MODE, false)
        db = DbHelper(this)

        if (isSelectionMode) {
            title = "Seleccionar Contacto Principal"
        }

        recyclerView = findViewById(R.id.contactsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ContactAdapter(mutableListOf(), this, isSelectionMode)
        recyclerView.adapter = adapter

        addButton = findViewById(R.id.addContactFab)
        addButton.setOnClickListener {
            val i = Intent(this, ContactoActivity::class.java)
            i.putExtra(ContactoActivity.EXTRA_CONTROL_NUMBER, controlNumber)
            startActivityForResult(i, REQ_ADD)
        }

        loadContacts()
    }

    private fun loadContacts() {
        executor.execute {
            val cursor = db.getContactsForUser(controlNumber)
            val list = mutableListOf<Contact>()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id"))
                    val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"))
                    val label = cursor.getString(cursor.getColumnIndexOrThrow("label"))
                    val createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
                    list.add(Contact(id, phone, label, createdAt))
                }
                cursor.close()
            }
            val prefs = getSharedPreferences("contact_prefs", MODE_PRIVATE)
            val key = "selected_${controlNumber}"
            val savedId = prefs.getLong(key, -1L)
            runOnUiThread {
                adapter.setItems(list)
                if (savedId > 0) {
                    adapter.setSelectedContactId(savedId)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && (requestCode == REQ_ADD || requestCode == REQ_EDIT)) {
            loadContacts()
        }
    }

    override fun onEdit(contact: Contact) {
        val i = Intent(this, ContactoActivity::class.java)
        i.putExtra(ContactoActivity.EXTRA_CONTROL_NUMBER, controlNumber)
        i.putExtra("contactId", contact.contactId)
        i.putExtra("phone", contact.phone)
        i.putExtra("label", contact.label)
        startActivityForResult(i, REQ_EDIT)
    }

    override fun onDelete(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar contacto")
            .setMessage("¿Eliminar ${contact.label ?: contact.phone}?")
            .setPositiveButton("Eliminar") { _, _ ->
                executor.execute {
                    db.deleteContact(contact.contactId)
                    runOnUiThread {
                        // Also clear selection if the selected contact is deleted
                        val prefs = getSharedPreferences("contact_prefs", MODE_PRIVATE)
                        val key = "selected_${controlNumber}"
                        if (prefs.getLong(key, -1L) == contact.contactId) {
                            prefs.edit().remove(key).apply()
                        }
                        Toast.makeText(this, "Contacto eliminado", Toast.LENGTH_SHORT).show()
                        loadContacts()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onClick(contact: Contact) {
        if (isSelectionMode) {
            onSelect(contact)
        } else {
            val i = Intent(this, EmergencyActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
            i.putExtra("controlNumber", controlNumber)
            i.putExtra("selectedContactId", contact.contactId)
            startActivity(i)
            finish()
        }
    }

    override fun onSelect(contact: Contact) {
        val prefs = getSharedPreferences("contact_prefs", MODE_PRIVATE)
        val key = "selected_${controlNumber}"
        prefs.edit().putLong(key, contact.contactId).apply()

        Toast.makeText(this, "\"${contact.label}\" es ahora tu contacto principal", Toast.LENGTH_SHORT).show()

        val result = Intent()
        result.putExtra("selectedContactId", contact.contactId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
