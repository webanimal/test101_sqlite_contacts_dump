package ru.webanimal.sqlitecontacts

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import ru.webanimal.sqlitecontacts.ContactsAdapter.ContactHolder
import ru.webanimal.sqlitecontacts.databinding.ItemContactBinding

class ContactsAdapter : ListAdapter<String, ContactHolder>(ContactsDiffUtilCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactHolder(binding: ItemContactBinding) : ViewHolder(binding.root) {

        private val contactTv: TextView = binding.contactTitleTv

        fun bind(contact: String) {
            contactTv.text = contact
        }
    }
}