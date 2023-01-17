package ru.webanimal.sqlitecontacts

import androidx.recyclerview.widget.DiffUtil

class ContactsDiffUtilCallback : DiffUtil.ItemCallback<String>() {

    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem.lowercase() == newItem.lowercase()
    }
}