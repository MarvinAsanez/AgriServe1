package com.group7.agriserve.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class NewsResponse(
    val status: String,
    val totalResults: Int? = null,
    val articles: List<NewsArticleDto> = emptyList(),
    val code: String? = null,
    val message: String? = null
)

data class NewsArticleDto(
    val title: String,
    val description: String?,
    val url: String,
    val urlToImage: String?,
    val source: NewsSourceDto
)

data class NewsSourceDto(
    val name: String?
)

interface NewsApiService {
    /**
     * Fetch news using NewsAPI.org.
     * To get news from a specific domain like da.gov.ph, we use the 'domains' parameter.
     */
    @GET("v2/everything")
    suspend fun getAgriculturalNews(
        @Query("q") query: String? = "agriculture",
        @Query("domains") domains: String? = "da.gov.ph",
        @Query("sortBy") sortBy: String? = "publishedAt",
        @Query("language") language: String? = "en",
        @Query("apiKey") apiKey: String,
        @Header("User-Agent") userAgent: String = "AgriServeApp/1.0"
    ): NewsResponse

    companion object {
        private const val BASE_URL = "https://newsapi.org/"

        fun create(): NewsApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NewsApiService::class.java)
        }
    }
}
