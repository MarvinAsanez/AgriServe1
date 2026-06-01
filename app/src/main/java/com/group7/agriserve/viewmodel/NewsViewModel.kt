package com.group7.agriserve.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group7.agriserve.data.api.NewsApiService
import com.group7.agriserve.data.api.NewsArticleDto
import kotlinx.coroutines.launch

class NewsViewModel : ViewModel() {
    private val newsApiService = NewsApiService.create()
    
    var newsArticles = mutableStateOf<List<NewsArticleDto>>(emptyList())
        private set
        
    var isLoading = mutableStateOf(false)
        private set

    var error = mutableStateOf<String?>(null)
        private set

    init {
        fetchNews()
    }

    fun fetchNews() {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            try {
                // We search specifically for articles from the official DA domain
                // We use a broad query "agriculture" or "DA" to get all recent updates
                val response = newsApiService.getAgriculturalNews(
                    query = "agriculture OR DA OR farming",
                    domains = "da.gov.ph",
                    apiKey = "274849303c724796859546059639599c"
                )
                
                if (response.status == "ok") {
                    // Filter out articles that might have missing titles or are broken
                    val filteredArticles = response.articles.filter { 
                        it.title.isNotBlank() && !it.title.contains("[Removed]") 
                    }
                    
                    if (filteredArticles.isEmpty()) {
                        // Fallback: If no articles found on da.gov.ph (sometimes indexing delay),
                        // search for official Department of Agriculture news from all sources
                        val fallbackResponse = newsApiService.getAgriculturalNews(
                            query = "\"Department of Agriculture\" Philippines",
                            domains = null, // Search all sources
                            apiKey = "274849303c724796859546059639599c"
                        )
                        if (fallbackResponse.status == "ok") {
                            newsArticles.value = fallbackResponse.articles.take(10)
                        } else {
                            newsArticles.value = emptyList()
                        }
                    } else {
                        newsArticles.value = filteredArticles.take(10)
                    }
                } else {
                    error.value = response.message ?: "Failed to load news"
                }
            } catch (e: Exception) {
                error.value = e.message ?: "An unknown error occurred"
            } finally {
                isLoading.value = false
            }
        }
    }
}
