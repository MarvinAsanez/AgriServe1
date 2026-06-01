package com.group7.agriserve.data.model

data class NewsItem(
    val title: String,
    val description: String?,
    val url: String,
    val urlToImage: String?,
    val publishedAt: String,
    val sourceName: String?
)
