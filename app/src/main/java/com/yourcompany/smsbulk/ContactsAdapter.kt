package com.yourcompany.smsbulk

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yourcompany.smsbulk.databinding.ItemContactBinding

class ContactsAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    // القائمة الكاملة (غير مفلترة) والقائمة المعروضة حالياً (بعد البحث)
    private var fullList: List<Contact> = emptyList()
    private var visibleList: List<Contact> = emptyList()

    fun submitList(contacts: List<Contact>) {
        fullList = contacts
        visibleList = contacts
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        visibleList = if (query.isBlank()) {
            fullList
        } else {
            fullList.filter {
                it.name.contains(query, ignoreCase = true) || it.number.contains(query)
            }
        }
        notifyDataSetChanged()
    }

    fun setAllSelected(selected: Boolean) {
        fullList.forEach { it.isSelected = selected }
        notifyDataSetChanged()
        onSelectionChanged(selectedCount())
    }

    fun selectedCount(): Int = fullList.count { it.isSelected }

    fun getSelectedContacts(): List<Contact> = fullList.filter { it.isSelected }

    inner class ContactViewHolder(val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = visibleList[position]
        holder.binding.tvName.text = contact.name
        holder.binding.tvNumber.text = contact.number

        // إزالة أي مستمع سابق قبل ضبط الحالة لتفادي تكرار الاستدعاء عند إعادة التدوير
        holder.binding.checkbox.setOnCheckedChangeListener(null)
        holder.binding.checkbox.isChecked = contact.isSelected
        holder.binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            contact.isSelected = isChecked
            onSelectionChanged(selectedCount())
        }

        holder.binding.root.setOnClickListener {
            holder.binding.checkbox.isChecked = !holder.binding.checkbox.isChecked
        }
    }

    override fun getItemCount(): Int = visibleList.size
}
