package com.group7.agriserve.data.model

data class InventoryItem(
    val id: String = "",
    val item_name: String = "",
    val category: String = "",
    val quantity: Int = 0,
    val unit: String = ""
)
