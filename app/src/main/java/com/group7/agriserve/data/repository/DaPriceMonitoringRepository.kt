package com.group7.agriserve.data.repository

import com.group7.agriserve.data.model.MarketCommodity
import com.group7.agriserve.data.model.WeeklyPriceComparison

/**
 * Official Philippine Department of Agriculture (DA) Price Monitoring (Bantay Presyo) Data Repository.
 * Dedicated exclusively to Land-Farm Agricultural Commodities (Grains, Field Crops, Vegetables, Fruits, Spices).
 * Excludes livestock, poultry, and fisheries.
 * Data ground truth from latest official DA bulletins:
 * - Daily Retail Price Monitoring: Tuesday, October 27, 2026.
 * - Weekly Average Retail Prices: October 19–25, 2026 vs October 12–18, 2026.
 * Source: https://www.da.gov.ph/price-monitoring/
 */
object DaPriceMonitoringRepository {

    const val BULLETIN_DATE = "Tuesday, October 27, 2026"
    const val BULLETIN_DATE_SHORT = "Oct 27, 2026"
    const val CURRENT_WEEK_PERIOD = "Oct 19–25, 2026"
    const val PREVIOUS_WEEK_PERIOD = "Oct 12–18, 2026"
    const val SOURCE_AGENCY = "DA Agribusiness & Marketing Assistance Service (AMAS)"
    const val PORTAL_URL = "https://www.da.gov.ph/price-monitoring/"
    const val TOTAL_MARKETS_MONITORED = 33

    val categories = listOf(
        "All",
        "Rice & Corn",
        "Lowland Vegetables",
        "Highland Vegetables",
        "Fruits",
        "Spices & Other"
    )

    val commodities: List<MarketCommodity> = listOf(
        // ==================== RICE & CORN ====================
        MarketCommodity(
            id = "rice_kadiwa_p20",
            name = "P20 Benteng Bigas Meron Na",
            localName = "Kadiwa Rice-For-All",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 20.00,
            lowPrice = 20.00,
            highPrice = 20.00,
            avgPrice = 20.00,
            note = "Special subsidized price at designated Kadiwa outlets & public markets (Agora, La Huerta, Mandaluyong I, Maypajo)",
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_imp_basmati",
            name = "Imported Rice (Basmati)",
            localName = "Aromatic Long Grain",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 170.00,
            lowPrice = 150.00,
            highPrice = 250.00,
            avgPrice = 194.29,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_imp_glutinous",
            name = "Imported Rice (Glutinous)",
            localName = "Imported Malagkit",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 60.00,
            lowPrice = 52.00,
            highPrice = 75.00,
            avgPrice = 62.04,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_imp_jasponica",
            name = "Imported Rice (Jasponica / Japonica)",
            localName = "Japanese Style Rice",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 60.00,
            lowPrice = 55.00,
            highPrice = 65.00,
            avgPrice = 60.15,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_imp_special",
            name = "Imported Rice (Special)",
            localName = "Imported Dinorado / Sinandomeng",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 60.00,
            lowPrice = 57.00,
            highPrice = 65.00,
            avgPrice = 60.70,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_imp_premium",
            name = "Imported Rice (Premium)",
            localName = "5% Broken Grain",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 60.00,
            lowPrice = 48.00,
            highPrice = 64.00,
            avgPrice = 56.00,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_imp_well_milled",
            name = "Imported Rice (Well Milled)",
            localName = "Well-Milled Imported",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 48.00,
            lowPrice = 47.00,
            highPrice = 51.00,
            avgPrice = 48.67,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_imp_regular_milled",
            name = "Imported Rice (Regular Milled)",
            localName = "Regular Milled Imported",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 45.00,
            lowPrice = 45.00,
            highPrice = 46.00,
            avgPrice = 45.33,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_loc_glutinous",
            name = "Local Rice (Glutinous)",
            localName = "Lokal na Malagkit",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 60.00,
            lowPrice = 50.00,
            highPrice = 120.00,
            avgPrice = 73.88,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_loc_special",
            name = "Local Rice (Special)",
            localName = "Lokal na Dinorado / Milagrosa",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 60.00,
            lowPrice = 52.00,
            highPrice = 68.00,
            avgPrice = 60.25,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_loc_premium",
            name = "Local Rice (Premium)",
            localName = "Lokal na Premium",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 55.00,
            lowPrice = 45.00,
            highPrice = 60.00,
            avgPrice = 54.07,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_loc_well_milled",
            name = "Local Rice (Well Milled)",
            localName = "Lokal na Well-Milled",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 50.00,
            lowPrice = 40.00,
            highPrice = 53.00,
            avgPrice = 49.02,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "rice_loc_regular_milled",
            name = "Local Rice (Regular Milled)",
            localName = "Lokal na Regular Milled",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 45.00,
            lowPrice = 38.00,
            highPrice = 54.00,
            avgPrice = 45.56,
            emoji = "🌾"
        ),
        MarketCommodity(
            id = "corn_white",
            name = "Corn (White)",
            localName = "Puting Mais",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 100.00,
            lowPrice = 80.00,
            highPrice = 150.00,
            avgPrice = 114.52,
            emoji = "🌽"
        ),
        MarketCommodity(
            id = "corn_yellow_sweet",
            name = "Yellow Sweet Corn (Cob)",
            localName = "Dilaw na Mais",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 100.00,
            lowPrice = 75.00,
            highPrice = 120.00,
            avgPrice = 100.00,
            emoji = "🌽"
        ),
        MarketCommodity(
            id = "corn_cracked_feed",
            name = "Corn Cracked (Feed Grade)",
            localName = "Binatog / Patuka",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 50.00,
            lowPrice = 45.00,
            highPrice = 50.00,
            avgPrice = 48.20,
            emoji = "🌽"
        ),
        MarketCommodity(
            id = "mung_bean",
            name = "Mung Bean",
            localName = "Monggo",
            category = "Rice & Corn",
            unit = "/kg",
            prevailingPrice = 150.00,
            lowPrice = 100.00,
            highPrice = 190.00,
            avgPrice = 145.98,
            emoji = "🫘"
        ),

        // ==================== LOWLAND VEGETABLES ====================
        MarketCommodity(
            id = "veg_ampalaya",
            name = "Bittergourd (Ampalaya)",
            localName = "Ampalaya",
            category = "Lowland Vegetables",
            unit = "/kg",
            prevailingPrice = 120.00,
            lowPrice = 50.00,
            highPrice = 160.00,
            avgPrice = 114.53,
            emoji = "🥒"
        ),
        MarketCommodity(
            id = "veg_talong",
            name = "Eggplant (Talong)",
            localName = "Talong",
            category = "Lowland Vegetables",
            unit = "/kg",
            prevailingPrice = 100.00,
            lowPrice = 50.00,
            highPrice = 130.00,
            avgPrice = 87.97,
            emoji = "🍆"
        ),
        MarketCommodity(
            id = "veg_pechay_tagalog",
            name = "Pechay Tagalog",
            localName = "Pechay Tagalog",
            category = "Lowland Vegetables",
            unit = "/kg",
            prevailingPrice = 100.00,
            lowPrice = 60.00,
            highPrice = 130.00,
            avgPrice = 97.83,
            emoji = "🥬"
        ),
        MarketCommodity(
            id = "veg_sitao",
            name = "String Beans (Sitao)",
            localName = "Sitaw",
            category = "Lowland Vegetables",
            unit = "/kg",
            prevailingPrice = 120.00,
            lowPrice = 40.00,
            highPrice = 160.00,
            avgPrice = 115.83,
            emoji = "🌱"
        ),
        MarketCommodity(
            id = "veg_kalabasa",
            name = "Squash (Kalabasa)",
            localName = "Kalabasa",
            category = "Lowland Vegetables",
            unit = "/kg",
            prevailingPrice = 60.00,
            lowPrice = 20.00,
            highPrice = 80.00,
            avgPrice = 60.39,
            emoji = "🎃"
        ),
        MarketCommodity(
            id = "veg_kamatis",
            name = "Tomato (Kamatis)",
            localName = "Kamatis",
            category = "Lowland Vegetables",
            unit = "/kg",
            prevailingPrice = 100.00,
            lowPrice = 60.00,
            highPrice = 150.00,
            avgPrice = 99.66,
            emoji = "🍅"
        ),
        MarketCommodity(
            id = "veg_chili_green",
            name = "Chili (Green / Pansigang)",
            localName = "Siling Haba / Pangsigang",
            category = "Lowland Vegetables",
            unit = "/kg",
            prevailingPrice = 150.00,
            lowPrice = 60.00,
            highPrice = 250.00,
            avgPrice = 170.33,
            emoji = "🌶️"
        ),

        // ==================== HIGHLAND VEGETABLES ====================
        MarketCommodity(
            id = "veg_cabbage_repolyo",
            name = "Cabbage Repolyo (Scorpio / Wonder Ball)",
            localName = "Repolyo",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 180.00,
            lowPrice = 100.00,
            highPrice = 210.00,
            avgPrice = 171.05,
            emoji = "🥬"
        ),
        MarketCommodity(
            id = "veg_carrot",
            name = "Carrot (Karot)",
            localName = "Karot",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 150.00,
            lowPrice = 100.00,
            highPrice = 160.00,
            avgPrice = 143.58,
            emoji = "🥕"
        ),
        MarketCommodity(
            id = "veg_patatas",
            name = "White Potato (Patatas)",
            localName = "Patatas (Lokal)",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 180.00,
            lowPrice = 130.00,
            highPrice = 220.00,
            avgPrice = 177.42,
            emoji = "🥔"
        ),
        MarketCommodity(
            id = "veg_pechay_baguio",
            name = "Pechay Baguio (Wongbok)",
            localName = "Pechay Baguio",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 120.00,
            lowPrice = 60.00,
            highPrice = 180.00,
            avgPrice = 110.64,
            emoji = "🥬"
        ),
        MarketCommodity(
            id = "veg_baguio_beans",
            name = "Baguio Beans (Habitchuelas)",
            localName = "Habichuelas",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 120.00,
            lowPrice = 60.00,
            highPrice = 200.00,
            avgPrice = 124.87,
            emoji = "🫛"
        ),
        MarketCommodity(
            id = "veg_sayote",
            name = "Chayote (Sayote)",
            localName = "Sayote",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 50.00,
            lowPrice = 20.00,
            highPrice = 90.00,
            avgPrice = 53.60,
            emoji = "🍐"
        ),
        MarketCommodity(
            id = "veg_bell_pepper_green",
            name = "Bell Pepper (Green)",
            localName = "Luntiang Bell Pepper",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 250.00,
            lowPrice = 120.00,
            highPrice = 400.00,
            avgPrice = 286.23,
            emoji = "🫑"
        ),
        MarketCommodity(
            id = "veg_bell_pepper_red",
            name = "Bell Pepper (Red)",
            localName = "Pulang Bell Pepper",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 250.00,
            lowPrice = 100.00,
            highPrice = 350.00,
            avgPrice = 267.67,
            emoji = "🫑"
        ),
        MarketCommodity(
            id = "veg_cauliflower",
            name = "Cauliflower",
            localName = "Koliplawer",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 350.00,
            lowPrice = 250.00,
            highPrice = 400.00,
            avgPrice = 323.79,
            emoji = "🥦"
        ),
        MarketCommodity(
            id = "veg_lettuce_iceberg",
            name = "Lettuce (Iceberg)",
            localName = "Letsugas Iceberg",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 300.00,
            lowPrice = 120.00,
            highPrice = 450.00,
            avgPrice = 253.78,
            emoji = "🥗"
        ),
        MarketCommodity(
            id = "veg_celery",
            name = "Celery",
            localName = "Kintsay / Celery",
            category = "Highland Vegetables",
            unit = "/kg",
            prevailingPrice = 300.00,
            lowPrice = 170.00,
            highPrice = 360.00,
            avgPrice = 293.33,
            emoji = "🌿"
        ),

        // ==================== FRUITS ====================
        MarketCommodity(
            id = "fruit_banana_lakatan",
            name = "Banana (Lakatan)",
            localName = "Saging Lakatan",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 100.00,
            lowPrice = 60.00,
            highPrice = 110.00,
            avgPrice = 95.87,
            emoji = "🍌"
        ),
        MarketCommodity(
            id = "fruit_banana_latundan",
            name = "Banana (Latundan)",
            localName = "Saging Latundan",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 80.00,
            lowPrice = 35.00,
            highPrice = 100.00,
            avgPrice = 77.16,
            emoji = "🍌"
        ),
        MarketCommodity(
            id = "fruit_banana_saba",
            name = "Banana (Saba)",
            localName = "Saging Saba",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 70.00,
            lowPrice = 45.00,
            highPrice = 85.00,
            avgPrice = 66.20,
            emoji = "🍌"
        ),
        MarketCommodity(
            id = "fruit_calamansi",
            name = "Calamansi",
            localName = "Kalamansi",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 100.00,
            lowPrice = 40.00,
            highPrice = 150.00,
            avgPrice = 106.91,
            emoji = "🍋"
        ),
        MarketCommodity(
            id = "fruit_mango_carabao",
            name = "Mango (Carabao)",
            localName = "Manggang Kalabaw (Hinog)",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 250.00,
            lowPrice = 180.00,
            highPrice = 380.00,
            avgPrice = 258.80,
            emoji = "🥭"
        ),
        MarketCommodity(
            id = "fruit_papaya",
            name = "Papaya",
            localName = "Hinog na Papaya",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 80.00,
            lowPrice = 50.00,
            highPrice = 120.00,
            avgPrice = 82.28,
            emoji = "🍈"
        ),
        MarketCommodity(
            id = "fruit_watermelon",
            name = "Watermelon",
            localName = "Pakwan",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 80.00,
            lowPrice = 60.00,
            highPrice = 90.00,
            avgPrice = 78.58,
            emoji = "🍉"
        ),
        MarketCommodity(
            id = "fruit_pomelo",
            name = "Pomelo",
            localName = "Suha",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 200.00,
            lowPrice = 130.00,
            highPrice = 260.00,
            avgPrice = 187.79,
            emoji = "🍊"
        ),
        MarketCommodity(
            id = "fruit_avocado",
            name = "Avocado",
            localName = "Abukado",
            category = "Fruits",
            unit = "/kg",
            prevailingPrice = 600.00,
            lowPrice = 350.00,
            highPrice = 650.00,
            avgPrice = 487.86,
            emoji = "🥑"
        ),

        // ==================== SPICES & OTHER FIELD CROPS ====================
        MarketCommodity(
            id = "spice_onion_red_local",
            name = "Local Red Onion",
            localName = "Pulang Sibuyas (Lokal)",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 120.00,
            lowPrice = 80.00,
            highPrice = 190.00,
            avgPrice = 125.82,
            emoji = "🧅"
        ),
        MarketCommodity(
            id = "spice_onion_white_local",
            name = "Local White Onion",
            localName = "Puting Sibuyas (Lokal)",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 160.00,
            lowPrice = 140.00,
            highPrice = 160.00,
            avgPrice = 153.33,
            emoji = "🧅"
        ),
        MarketCommodity(
            id = "spice_onion_white_imported",
            name = "Imported White Onion",
            localName = "Imported Puting Sibuyas",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 190.00,
            lowPrice = 120.00,
            highPrice = 300.00,
            avgPrice = 196.98,
            emoji = "🧅"
        ),
        MarketCommodity(
            id = "spice_garlic_local",
            name = "Local Garlic (Bawang)",
            localName = "Lokal na Bawang (Ilocos)",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 350.00,
            lowPrice = 350.00,
            highPrice = 450.00,
            avgPrice = 375.71,
            emoji = "🧄"
        ),
        MarketCommodity(
            id = "spice_garlic_imported",
            name = "Imported Garlic",
            localName = "Imported na Bawang",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 150.00,
            lowPrice = 120.00,
            highPrice = 190.00,
            avgPrice = 149.01,
            emoji = "🧄"
        ),
        MarketCommodity(
            id = "spice_ginger",
            name = "Ginger (Luya)",
            localName = "Luya",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 200.00,
            lowPrice = 100.00,
            highPrice = 240.00,
            avgPrice = 193.55,
            emoji = "🫚"
        ),
        MarketCommodity(
            id = "spice_chili_labuyo",
            name = "Chili (Siling Labuyo)",
            localName = "Siling Labuyo",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 350.00,
            lowPrice = 200.00,
            highPrice = 450.00,
            avgPrice = 342.04,
            emoji = "🌶️"
        ),
        MarketCommodity(
            id = "other_sugar_refined",
            name = "Sugar (Refined White)",
            localName = "Puting Asukal (Tubó)",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 80.00,
            lowPrice = 65.00,
            highPrice = 90.00,
            avgPrice = 80.86,
            emoji = "🧂"
        ),
        MarketCommodity(
            id = "other_sugar_brown",
            name = "Sugar (Brown)",
            localName = "Kayumangging Asukal (Tubó)",
            category = "Spices & Other",
            unit = "/kg",
            prevailingPrice = 70.00,
            lowPrice = 64.00,
            highPrice = 85.00,
            avgPrice = 71.86,
            emoji = "🧂"
        ),
        MarketCommodity(
            id = "other_palm_oil_1l",
            name = "Palm Cooking Oil (1L)",
            localName = "Mantika (Palm Oil)",
            category = "Spices & Other",
            unit = "/1L",
            prevailingPrice = 100.00,
            lowPrice = 80.00,
            highPrice = 120.00,
            avgPrice = 101.74,
            emoji = "🫗"
        ),
        MarketCommodity(
            id = "other_coconut_oil_1l",
            name = "Coconut Cooking Oil (1L)",
            localName = "Lokal na Langis ng Niyog",
            category = "Spices & Other",
            unit = "/1L",
            prevailingPrice = 160.00,
            lowPrice = 114.00,
            highPrice = 170.00,
            avgPrice = 152.09,
            emoji = "🥥"
        )
    )

    /**
     * Official DA Weekly Average Price Movement Comparison for Land-Farm Agricultural Products.
     * Week 1: October 19–25, 2026 vs Week 2: October 12–18, 2026.
     * Excludes livestock, poultry, and fisheries.
     */
    val weeklyComparisons: List<WeeklyPriceComparison> = listOf(
        // RICE & CORN
        WeeklyPriceComparison("w_kadiwa_p20", "P20 Benteng Bigas Meron Na", "Subsidized Kadiwa Rice", "Rice & Corn", "₱/kg", 20.00, 20.00, "🌾"),
        WeeklyPriceComparison("w_rice_imp_basmati", "Imported Basmati Rice", "Aromatic Long Grain", "Rice & Corn", "₱/kg", 187.95, 189.77, "🌾"),
        WeeklyPriceComparison("w_rice_imp_glutinous", "Imported Glutinous Rice", "Malagkit", "Rice & Corn", "₱/kg", 61.94, 62.14, "🌾"),
        WeeklyPriceComparison("w_rice_imp_jasponica", "Imported Jasponica", "Japanese Grain", "Rice & Corn", "₱/kg", 60.12, 60.12, "🌾"),
        WeeklyPriceComparison("w_rice_imp_premium", "Imported Premium Rice", "5% broken", "Rice & Corn", "₱/kg", 55.39, 55.43, "🌾"),
        WeeklyPriceComparison("w_rice_imp_well_milled", "Imported Well Milled Rice", "1-19% bran streak", "Rice & Corn", "₱/kg", 48.59, 48.52, "🌾"),
        WeeklyPriceComparison("w_rice_imp_reg_milled", "Imported Regular Milled Rice", "20-40% bran streak", "Rice & Corn", "₱/kg", 45.33, 45.24, "🌾"),
        WeeklyPriceComparison("w_rice_loc_glutinous", "Local Glutinous Rice", "Lokal na Malagkit", "Rice & Corn", "₱/kg", 76.72, 78.18, "🌾"),
        WeeklyPriceComparison("w_rice_loc_special", "Local Special Rice", "Dinorado / Sinandomeng", "Rice & Corn", "₱/kg", 60.25, 60.41, "🌾"),
        WeeklyPriceComparison("w_rice_loc_premium", "Local Premium Rice", "5% broken", "Rice & Corn", "₱/kg", 54.11, 54.12, "🌾"),
        WeeklyPriceComparison("w_rice_loc_well_milled", "Local Well Milled Rice", "1-19% bran streak", "Rice & Corn", "₱/kg", 48.93, 48.44, "🌾"),
        WeeklyPriceComparison("w_rice_loc_reg_milled", "Local Regular Milled Rice", "20-40% bran streak", "Rice & Corn", "₱/kg", 45.37, 45.19, "🌾"),
        WeeklyPriceComparison("w_corn_white", "White Corn", "Cob, Glutinous", "Rice & Corn", "₱/kg", 114.32, 114.76, "🌽"),
        WeeklyPriceComparison("w_corn_yellow", "Yellow Sweet Corn", "Cob, Sweet Corn", "Rice & Corn", "₱/kg", 96.76, 89.19, "🌽"),
        WeeklyPriceComparison("w_corn_grits_food", "Corn Grits (Food Grade)", "White / Yellow Mix", "Rice & Corn", "₱/kg", 98.89, 95.33, "🌽"),
        WeeklyPriceComparison("w_mungbean", "Mungbean (Monggo)", "Whole Green", "Rice & Corn", "₱/kg", 147.27, 148.49, "🫘"),

        // LOWLAND VEGETABLES
        WeeklyPriceComparison("w_ampalaya", "Bittergourd (Ampalaya)", "4-5 pcs/kg", "Lowland Vegetables", "₱/kg", 117.74, 113.84, "🥒"),
        WeeklyPriceComparison("w_chili_green", "Chili (Green / Panigang)", "Siling Haba", "Lowland Vegetables", "₱/kg", 186.96, 200.60, "🌶️"),
        WeeklyPriceComparison("w_eggplant", "Eggplant (Talong)", "3-4 Small Bundles", "Lowland Vegetables", "₱/kg", 85.88, 83.37, "🍆"),
        WeeklyPriceComparison("w_pechay_tagalog", "Native Pechay", "3-4 Small Bundles", "Lowland Vegetables", "₱/kg", 106.56, 114.19, "🥬"),
        WeeklyPriceComparison("w_sitao", "Pole Sitao", "3-4 Small Bundles", "Lowland Vegetables", "₱/kg", 128.17, 134.85, "🌱"),
        WeeklyPriceComparison("w_kalabasa", "Squash (Kalabasa)", "Suprema Variety", "Lowland Vegetables", "₱/kg", 61.82, 61.50, "🎃"),
        WeeklyPriceComparison("w_tomato", "Tomato (Kamatis)", "15-18 pcs/kg", "Lowland Vegetables", "₱/kg", 104.02, 106.37, "🍅"),

        // HIGHLAND VEGETABLES
        WeeklyPriceComparison("w_cabbage_scorpio", "Cabbage (Scorpio)", "750g - 1kg/head", "Highland Vegetables", "₱/kg", 154.53, 144.23, "🥬"),
        WeeklyPriceComparison("w_cabbage_rareball", "Cabbage (Rare Ball)", "510g - 1kg/head", "Highland Vegetables", "₱/kg", 155.17, 147.48, "🥬"),
        WeeklyPriceComparison("w_carrots", "Carrots, Local", "8-10 pcs/kg", "Highland Vegetables", "₱/kg", 131.69, 119.31, "🥕"),
        WeeklyPriceComparison("w_potato_white", "White Potato (Patatas), Local", "10-12 pcs/kg", "Highland Vegetables", "₱/kg", 173.05, 151.84, "🥔"),
        WeeklyPriceComparison("w_pechay_baguio", "Pechay Baguio", "Wongbok", "Highland Vegetables", "₱/kg", 122.73, 142.76, "🥬"),
        WeeklyPriceComparison("w_sayote", "Chayote (Sayote)", "Medium (301-400g)", "Highland Vegetables", "₱/kg", 58.51, 71.66, "🍐"),
        WeeklyPriceComparison("w_baguio_beans", "Baguio Beans (Habitchuelas)", "Fresh Local", "Highland Vegetables", "₱/kg", 130.73, 135.46, "🫛"),
        WeeklyPriceComparison("w_bell_pepper_green", "Bell Pepper (Green)", "Medium (151-250g)", "Highland Vegetables", "₱/kg", 299.13, 333.56, "🫑"),
        WeeklyPriceComparison("w_bell_pepper_red", "Bell Pepper (Red)", "Medium (151-250g)", "Highland Vegetables", "₱/kg", 294.20, 289.94, "🫑"),
        WeeklyPriceComparison("w_cauliflower", "Cauliflower, Local", "Medium (8-10cm)", "Highland Vegetables", "₱/kg", 322.31, 319.81, "🥦"),
        WeeklyPriceComparison("w_celery", "Celery, Local", "Medium (501-800g)", "Highland Vegetables", "₱/kg", 317.04, 301.89, "🌿"),
        WeeklyPriceComparison("w_lettuce_iceberg", "Lettuce (Iceberg)", "Medium (301-450cm)", "Highland Vegetables", "₱/kg", 273.36, 324.76, "🥗"),
        WeeklyPriceComparison("w_lettuce_green_ice", "Lettuce (Green Ice)", "Fresh Baguio Cuts", "Highland Vegetables", "₱/kg", 201.23, 220.21, "🥗"),

        // SPICES & CONDIMENTS
        WeeklyPriceComparison("w_siling_labuyo", "Chili (Siling Labuyo / Red)", "Tingala", "Spices & Other", "₱/kg", 348.73, 290.66, "🌶️"),
        WeeklyPriceComparison("w_onion_red_loc", "Local Red Onion", "13-15 pcs/kg", "Spices & Other", "₱/kg", 124.40, 123.61, "🧅"),
        WeeklyPriceComparison("w_onion_white_loc", "Local White Onion", "Puting Sibuyas", "Spices & Other", "₱/kg", 202.80, 183.58, "🧅"),
        WeeklyPriceComparison("w_garlic_native", "Native Garlic", "Ilocos White", "Spices & Other", "₱/kg", 372.39, 373.20, "🧄"),
        WeeklyPriceComparison("w_garlic_imp", "Imported Garlic", "Imported Bawang", "Spices & Other", "₱/kg", 150.15, 150.28, "🧄"),
        WeeklyPriceComparison("w_ginger_loc", "Local Ginger (Luya)", "Fairly well-matured", "Spices & Other", "₱/kg", 195.13, 194.76, "🫚"),
        WeeklyPriceComparison("w_sugar_refined", "Sugar (Refined White)", "Packed Standard", "Spices & Other", "₱/kg", 81.44, 81.49, "🧂"),
        WeeklyPriceComparison("w_sugar_washed", "Sugar (Washed)", "Standard", "Spices & Other", "₱/kg", 74.67, 74.65, "🧂"),
        WeeklyPriceComparison("w_sugar_brown", "Sugar (Brown)", "Standard Raw", "Spices & Other", "₱/kg", 72.28, 72.17, "🧂"),
        WeeklyPriceComparison("w_oil_palm_1l", "Palm Cooking Oil (1L)", "1 Liter Bottle", "Spices & Other", "₱/L", 101.85, 101.91, "🫗"),
        WeeklyPriceComparison("w_oil_coconut_1l", "Coconut Cooking Oil (1L)", "1 Liter Bottle", "Spices & Other", "₱/L", 153.30, 154.41, "🥥"),

        // FRUITS
        WeeklyPriceComparison("w_banana_lakatan", "Banana (Lakatan)", "8-10 pcs/kg", "Fruits", "₱/kg", 96.48, 96.73, "🍌"),
        WeeklyPriceComparison("w_banana_latundan", "Banana (Latundan)", "10-12 pcs/kg", "Fruits", "₱/kg", 78.79, 78.39, "🍌"),
        WeeklyPriceComparison("w_banana_saba", "Banana (Saba)", "Standard Bunch", "Fruits", "₱/kg", 66.42, 66.16, "🍌"),
        WeeklyPriceComparison("w_calamansi", "Calamansi", "Fresh Citrus", "Fruits", "₱/kg", 103.41, 95.40, "🍋"),
        WeeklyPriceComparison("w_mango_carabao", "Mango (Carabao)", "Ripe, 3-4 pcs/kg", "Fruits", "₱/kg", 258.54, 255.71, "🥭"),
        WeeklyPriceComparison("w_papaya", "Papaya (Solo)", "Ripe, 2-3 pcs/kg", "Fruits", "₱/kg", 82.30, 81.10, "🍈"),
        WeeklyPriceComparison("w_watermelon", "Watermelon", "Local Sweet", "Fruits", "₱/kg", 78.88, 77.07, "🍉"),
        WeeklyPriceComparison("w_pomelo", "Pomelo", "Davao Sweet", "Fruits", "₱/kg", 189.49, 188.48, "🍊"),
        WeeklyPriceComparison("w_avocado", "Avocado", "Fresh Local", "Fruits", "₱/kg", 418.26, 408.78, "🥑")
    )
}
