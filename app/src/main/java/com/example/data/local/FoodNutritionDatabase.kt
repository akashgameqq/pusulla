package com.example.data.local

import java.util.Locale

data class FoodNutritionItem(
    val name: String,
    val defaultPortion: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val keywords: List<String>,
)

data class FoodMatchCandidate(
    val item: FoodNutritionItem,
    val matchScore: Int,
)

object FoodNutritionDatabase {

    val items: List<FoodNutritionItem> = listOf(
        FoodNutritionItem("Izgara Tavuk Salata & Kinoa", "1 Kase (~350g)", 465, 39.0, 31.0, 18.5, listOf("chicken", "salad", "bowl", "quinoa", "avocado", "greens", "vegetable", "tavuk", "salata", "kinoa", "diyet", "healthy", "dish", "food")),
        FoodNutritionItem("Izgara Somon & Sebze", "1 Porsiyon (~300g)", 520, 38.0, 18.0, 31.0, listOf("salmon", "fish", "seafood", "somon", "balık", "ızgara", "izgara", "sebze", "vegetable", "dinner")),
        FoodNutritionItem("Tavuklu Pilav", "1 Tabak (~320g)", 545, 34.0, 62.0, 16.0, listOf("rice", "chicken", "pilaf", "pilav", "tavuk", "meat", "meal", "lunch")),
        FoodNutritionItem("Menemen (2 Yumurtalı)", "1 Porsiyon (~260g)", 315, 16.0, 11.0, 22.0, listOf("egg", "omelette", "tomato", "pepper", "menemen", "yumurta", "kahvaltı", "breakfast")),
        FoodNutritionItem("Haşlanmış Yumurta & Avokado Tost", "2 Dilim (~240g)", 410, 18.0, 34.0, 22.0, listOf("toast", "bread", "avocado", "egg", "tost", "ekmek", "avokado", "yumurta", "breakfast")),
        FoodNutritionItem("Mercimek Çorbası", "1 Kase (~300ml)", 210, 12.0, 30.0, 4.5, listOf("soup", "lentil", "bowl", "çorba", "corba", "mercimek", "broth")),
        FoodNutritionItem("Izgara Köfte & Bulgur", "1 Porsiyon (~320g)", 585, 33.0, 42.0, 29.0, listOf("meatball", "beef", "meat", "bulgur", "köfte", "kofte", "et", "grill", "kebab")),
        FoodNutritionItem("Adana / Urfa Kebap & Lavaş", "1 Porsiyon (~340g)", 690, 36.0, 44.0, 41.0, listOf("kebab", "skewer", "meat", "grill", "kebap", "adana", "urfa", "et", "lavas")),
        FoodNutritionItem("Lahmacun (1 Adet)", "1 Adet (~160g)", 340, 15.0, 42.0, 12.0, listOf("pizza", "flatbread", "lahmacun", "pide", "dough", "baked")),
        FoodNutritionItem("Kıymalı / Kaşarlı Pide", "1 Porsiyon (~320g)", 680, 28.0, 74.0, 29.0, listOf("pizza", "bread", "cheese", "pide", "hamur", "kaşar", "kıyma")),
        FoodNutritionItem("İskender Kebap", "1 Porsiyon (~400g)", 860, 40.0, 58.0, 51.0, listOf("meat", "yogurt", "sauce", "iskender", "döner", "doner", "kebap")),
        FoodNutritionItem("Tavuk Döner Dürüm", "1 Dürüm (~280g)", 560, 31.0, 54.0, 23.0, listOf("wrap", "sandwich", "burrito", "dürüm", "durum", "döner", "doner", "tavuk")),
        FoodNutritionItem("Yulaf Ezmesi & Meyve & Yoğurt", "1 Kase (~280g)", 350, 14.0, 52.0, 8.5, listOf("oatmeal", "cereal", "yogurt", "fruit", "berry", "banana", "yulaf", "yoğurt", "kahvaltı", "granola")),
        FoodNutritionItem("Süzme Yoğurt & Çiğ Badem", "1 Kase (~200g)", 235, 16.0, 9.0, 14.5, listOf("yogurt", "dairy", "nut", "almond", "yoğurt", "badem", "ara öğün", "snack")),
        FoodNutritionItem("Serpme / Klasik Türk Kahvaltısı Tabağı", "1 Tabak (~350g)", 610, 26.0, 44.0, 36.0, listOf("breakfast", "cheese", "olive", "egg", "tomato", "bread", "kahvaltı", "peynir", "zeytin")),
        FoodNutritionItem("Simit & Beyaz Peynir", "1 Porsiyon (~160g)", 395, 13.5, 56.0, 12.5, listOf("bagel", "bread", "sesame", "cheese", "simit", "peynir", "pastry")),
        FoodNutritionItem("Kuru Fasulye & Pilav", "1 Tabak (~380g)", 560, 21.0, 82.0, 15.0, listOf("bean", "stew", "rice", "fasulye", "pilav", "bakliyat")),
        FoodNutritionItem("Zeytinyağlı Sebze Yemeği", "1 Tabak (~280g)", 245, 6.5, 22.0, 14.0, listOf("vegetable", "stew", "vegetarian", "sebze", "kabak", "taze fasulye", "enginar")),
        FoodNutritionItem("Spagetti Bolonez / Kıymalı Makarna", "1 Tabak (~320g)", 540, 24.0, 68.0, 18.0, listOf("pasta", "spaghetti", "noodle", "sauce", "makarna", "spagetti")),
        FoodNutritionItem("Hamburger & Patates", "1 Menü (~380g)", 790, 32.0, 74.0, 40.0, listOf("burger", "hamburger", "fries", "sandwich", "fast food", "patates")),
        FoodNutritionItem("Margherita / Karışık Pizza (2 Dilim)", "2 Dilim (~240g)", 590, 23.0, 66.0, 25.0, listOf("pizza", "cheese", "tomato", "crust", "slice")),
        FoodNutritionItem("Ton Balıklı Salata", "1 Kase (~300g)", 340, 31.0, 14.0, 17.0, listOf("salad", "tuna", "fish", "greens", "ton balığı", "salata")),
        FoodNutritionItem("Biftek / Antrikot & Patates Püresi", "1 Porsiyon (~340g)", 640, 44.0, 28.0, 38.0, listOf("steak", "beef", "meat", "potato", "biftek", "antrikot", "et")),
        FoodNutritionItem("Omlet (Peynirli & Sebzeli)", "1 Porsiyon (~210g)", 310, 20.0, 4.0, 23.0, listOf("omelette", "egg", "cheese", "omlet", "yumurta")),
        FoodNutritionItem("Protein Smoothie & Muz", "1 Bardak (~350ml)", 290, 26.0, 36.0, 4.5, listOf("smoothie", "drink", "shake", "banana", "milk", "muz", "protein")),
        FoodNutritionItem("Sütlü Kahve / Latte (Şekersiz)", "1 Büyük Fincan (~300ml)", 135, 7.5, 11.0, 6.5, listOf("coffee", "latte", "espresso", "cup", "drink", "kahve", "süt")),
        FoodNutritionItem("Elma / Muz / Mevsim Meyvesi", "1 Porsiyon (~180g)", 105, 1.0, 26.0, 0.4, listOf("fruit", "apple", "banana", "orange", "meyve", "elma", "muz")),
        FoodNutritionItem("Karışık Kuruyemiş (1 Avuç)", "35g", 215, 6.5, 6.0, 18.5, listOf("nut", "almond", "walnut", "peanut", "kuruyemiş", "badem", "ceviz", "fındık")),
        FoodNutritionItem("Baklava / Şerbetli Tatlı (2 Dilim)", "2 Dilim (~110g)", 460, 6.5, 54.0, 24.0, listOf("dessert", "pastry", "cake", "sweet", "baklava", "tatlı")),
        FoodNutritionItem("Suşi Set (8 Parça Somon/Avokado)", "8 Parça (~240g)", 380, 18.0, 52.0, 11.0, listOf("sushi", "fish", "rice", "japanese", "suşi")),
    )

    fun findClosestMatches(
        labelsWithScores: List<Pair<String, Float>>,
        userHint: String = "",
        limit: Int = 4,
    ): List<FoodMatchCandidate> {
        val hintTokens = tokenize(userHint)
        val normalizedLabels = labelsWithScores.map { tokenize(it.first) to it.second }

        val scored = items.map { item ->
            var score = 0f
            val itemTextTokens = tokenize(item.name) + item.keywords.flatMap { tokenize(it) }

            for (hint in hintTokens) {
                if (itemTextTokens.any { it == hint }) {
                    score += 55f
                } else if (itemTextTokens.any { it.contains(hint) || hint.contains(it) }) {
                    score += 30f
                }
            }

            for ((labelTokens, confidence) in normalizedLabels) {
                for (token in labelTokens) {
                    if (item.keywords.any { it.equals(token, ignoreCase = true) }) {
                        score += 40f * confidence.coerceIn(0.2f, 1.0f)
                    } else if (itemTextTokens.any { it.contains(token) || token.contains(it) }) {
                        score += 20f * confidence.coerceIn(0.2f, 1.0f)
                    }
                }
            }

            // General food fallback bonus so we always surface a realistic balanced meal when labels are generic ("Food", "Dish")
            if (score == 0f && normalizedLabels.any { (tokens, _) -> tokens.any { it in setOf("food", "dish", "meal", "cuisine", "tableware", "plate") } }) {
                score = if (item.name.contains("Tavuk")) 28f else 15f
            }

            FoodMatchCandidate(
                item = item,
                matchScore = score.toInt().coerceIn(15, 98),
            )
        }

        return scored.sortedByDescending { it.matchScore }.take(limit)
    }

    private fun tokenize(raw: String): List<String> =
        raw.lowercase(Locale("tr", "TR"))
            .replace(Regex("[^a-z0-9çğıöşü\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
}
