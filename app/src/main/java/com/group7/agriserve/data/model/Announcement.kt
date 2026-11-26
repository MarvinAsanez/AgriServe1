package com.group7.agriserve.data.model

import com.google.firebase.Timestamp

data class Announcement(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "General DA Advisory",
    val severity: String = "Info", // "Emergency", "Warning", "Info"
    val targetBarangay: String = "All Barangays",
    val imageUrl: String? = null,
    val author: String = "Municipal Agriculture Office",
    val isActive: Boolean = true,
    val createdAt: Timestamp? = null
)

