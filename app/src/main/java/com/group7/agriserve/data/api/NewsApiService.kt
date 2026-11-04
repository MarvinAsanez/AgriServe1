package com.group7.agriserve.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class RssNewsResponse(
    val status: String? = null,
    val items: List<RssNewsItemDto> = emptyList(),
    val message: String? = null
)

data class RssNewsItemDto(
    val title: String = "",
    val description: String? = null,
    val content: String? = null,
    val link: String = "",
    val thumbnail: String? = null,
    val enclosure: RssEnclosureDto? = null,
    val pubDate: String? = null,
    val author: String? = null
)

data class RssEnclosureDto(
    val link: String? = null
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
    @GET("v1/api.json")
    suspend fun getAgriculturalNews(
        @Query("rss_url") rssUrl: String = "https://www.philrice.gov.ph/category/news/feed/"
    ): RssNewsResponse

    companion object {
        private const val BASE_URL = "https://api.rss2json.com/"

        fun create(): NewsApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NewsApiService::class.java)
        }
    }
}
