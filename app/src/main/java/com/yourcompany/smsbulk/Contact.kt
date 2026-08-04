package com.yourcompany.smsbulk

data class Contact(
    val name: String,
    val number: String,
    var isSelected: Boolean = false
)
