package com.example.boton_emergencia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(
    private var items: MutableList<Contact>,
    private val listener: Listener,
    private val isSelectionMode: Boolean
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    interface Listener {
        fun onEdit(contact: Contact)
        fun onDelete(contact: Contact)
        fun onClick(contact: Contact)
        fun onSelect(contact: Contact)
    }

    private var selectedContactId: Long = -1

    fun setItems(newItems: List<Contact>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setSelectedContactId(contactId: Long) {
        selectedContactId = contactId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val c = items[position]
        holder.label.text = c.label ?: "(sin etiqueta)"
        holder.phone.text = c.phone
        holder.itemView.setOnClickListener { listener.onClick(c) }

        holder.selectCheck.isChecked = c.contactId == selectedContactId

        if (isSelectionMode) {
            holder.editBtn.visibility = View.GONE
            holder.deleteBtn.visibility = View.GONE
            holder.selectCheck.visibility = View.VISIBLE
            holder.selectCheck.isEnabled = true
            holder.selectCheck.setOnClickListener { listener.onSelect(c) }
        } else {
            holder.editBtn.visibility = View.VISIBLE
            holder.deleteBtn.visibility = View.VISIBLE
            holder.selectCheck.visibility = View.VISIBLE
            holder.selectCheck.isEnabled = false
            holder.editBtn.setOnClickListener { listener.onEdit(c) }
            holder.deleteBtn.setOnClickListener { listener.onDelete(c) }
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.contactLabel)
        val phone: TextView = v.findViewById(R.id.contactPhone)
        val editBtn: Button = v.findViewById(R.id.editContactButton)
        val deleteBtn: Button = v.findViewById(R.id.deleteContactButton)
        val selectCheck: CheckBox = v.findViewById(R.id.selectContactCheck)
    }
}
