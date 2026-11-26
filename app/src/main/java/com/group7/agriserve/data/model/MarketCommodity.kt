package com.group7.agriserve.data.model

data class MarketCommodity(
    val id: String,
    val name: String,
    val localName: String? = null,
    val category: String,
    val unit: String,
    val prevailingPrice: Double,
    val lowPrice: Double,
    val highPrice: Double,
    val avgPrice: Double,
    val note: String? = null,
    val emoji: String
)

data class WeeklyPriceComparison(
    val id: String,
    val name: String,
    val specification: String? = null,
    val category: String,
    val unit: String,
    val currentWeekPrice: Double,
    val previousWeekPrice: Double,
    val emoji: String
) {
    val priceDiff: Double get() = currentWeekPrice - previousWeekPrice
    val percentChange: Double get() = if (previousWeekPrice > 0) ((currentWeekPrice - previousWeekPrice) / previousWeekPrice) * 100.0 else 0.0
    val isUp: Boolean get() = priceDiff > 0.10
    val isDown: Boolean get() = priceDiff < -0.10
    val isStable: Boolean get() = !isUp && !isDown
}
