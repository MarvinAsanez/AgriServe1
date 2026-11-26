package com.group7.agriserve.util

/**
 * Intelligent real-time Content Safety & Anti-Violence Moderator.
 * Ensures the AgriServe Farmer Community remains a safe, respectful, and cooperative platform.
 * Features:
 * 1. Multilingual pattern detection for English, Tagalog, and Taglish violent content.
 * 2. Agricultural context whitelisting (prevents false positives for pest killer, weed control, etc.).
 * 3. Pre-dispatch blocking with user-friendly educational explanations.
 */
object ContentSafetyModerator {

    data class ModerationResult(
        val isSafe: Boolean,
        val flaggedPhrase: String? = null,
        val reason: String? = null
    )

    // Allowed agricultural terminology containing substrings that might otherwise match
    private val AGRICULTURAL_WHITELIST = listOf(
        "pest killer", "weed killer", "insect killer", "bug killer", "rat killer",
        "snail killer", "caterpillar killer", "aphid killer", "rodent killer",
        "pest control", "weed control", "fungicide", "herbicide", "pesticide",
        "rat poison", "slaughterhouse", "meat inspection", "authorized dressing plant",
        "butchering hogs", "butchering pigs", "poultry dressing", "culled chickens",
        "kill weeds", "kill pests", "kill insects", "kill snails", "kill aphids",
        "kill worms", "kill bugs", "kill fungus", "kill rats", "kill mice",
        "pamatay peste", "pamatay damo", "pamatay insekto", "pamatay kuhol",
        "kontrol sa peste", "puksaing peste", "puksaing damo", "pagpupuksa ng peste"
    )

    // Patterns for violent threats, weapons, bodily harm, physical assaults, terrorism
    private val VIOLENT_PATTERNS = listOf(
        // English violent threats & bodily harm
        "\\bkill\\s+(you|u|him|her|them|everyone|all|people|someone|myself)\\b",
        "\\bi('ll|\\s+will)\\s+kill\\b",
        "\\bmurder(er|ed|ing|s)?\\b",
        "\\bassassinate\\b",
        "\\bshoot\\s+(you|u|him|her|them|people|up)\\b",
        "\\bgun\\s+down\\b",
        "\\bstab\\s+(you|u|him|her|them|someone)\\b",
        "\\bslit\\s+(throat|wrists?)\\b",
        "\\bstrangle\\s+(you|him|her|them)?\\b",
        "\\bbeat\\s+(you|him|her|them)\\s+(up|to\\s+death)\\b",
        "\\bmassacre\\b",
        "\\bterrorist(s|ic)?\\b",
        "\\bbomb\\s+(them|you|u|the|building|place|people)\\b",
        "\\bexplode\\s+the\\b",
        "\\bbloodbath\\b",
        "\\bdecapitate\\b",
        "\\bexecute\\s+(you|him|her|them)\\b",
        "\\bhang\\s+(yourself|you|him|her)\\b",
        "\\bcommit\\s+suicide\\b",
        "\\bviolent\\s+attack\\b",
        "\\bbreak\\s+your\\s+(neck|bones|head|face)\\b",
        "\\bi('ll|\\s+will)\\s+(hurt|harm|destroy|end)\\s+(you|u)\\b",
        "\\bgo\\s+(die|kill\\s+yourself)\\b",
        "\\bhope\\s+you\\s+die\\b",
        "\\bchoke\\s+you\\b",

        // Filipino / Tagalog violent threats & attacks
        "\\bpapatayin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bpatayin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bipapapatay(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bmamatay\\s+ka\\s*(na)?\\b",
        "\\bbarilin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bbabarilin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bsaksakin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bsasaksakin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bpugutan\\b",
        "\\bbugbugin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bbubugbugin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\btatadtarin\\b",
        "\\blalaslasin\\b",
        "\\bsunugin\\s+ang\\s+(bahay|tao|pamilya)\\b",
        "\\bibobomba\\b",
        "\\bbombahin\\b",
        "\\bgigilitan\\b",
        "\\bsasapakin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bsapakin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bgulpihin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bgugulpihin(\\s+(kita|ka|kayo|sila|siya))?\\b",
        "\\bduguan\\s+ka\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    // Standalone violent weapon/action keywords
    private val SEVERE_VIOLENT_KEYWORDS = listOf(
        "mass shooter", "suicide bomb", "homemade bomb", "pipe bomb",
        "molotov cocktail", "grenade launcher", "assault rifle", "landmine",
        "nanaksak", "namaril", "mamaril", "nanunog ng tao", "terorista"
    )

    /**
     * Inspects text for violent, threatening, or harmful language.
     * Returns ModerationResult indicating whether content is approved.
     */
    fun checkContent(content: String): ModerationResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return ModerationResult(isSafe = true)

        val lowerText = trimmed.lowercase()

        // 1. Check if the text matches benign agricultural context first
        for (whitelistWord in AGRICULTURAL_WHITELIST) {
            if (lowerText.contains(whitelistWord)) {
                // If it's pure agricultural pest/weed control without personal threat pronouns, permit
                val hasPersonalThreat = Regex(
                    "\\b(kill|papatayin|patayin|barilin|saksakin|sasapakin|bugbugin)\\s+(you|u|ka|kita|kayo)\\b",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(lowerText)

                if (!hasPersonalThreat) {
                    return ModerationResult(isSafe = true)
                }
            }
        }

        // 2. Check severe violent keywords
        for (keyword in SEVERE_VIOLENT_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                return ModerationResult(
                    isSafe = false,
                    flaggedPhrase = keyword,
                    reason = "Content contains prohibited violent or dangerous weapon references ('$keyword')."
                )
            }
        }

        // 3. Check regular expression violent threat patterns
        for (pattern in VIOLENT_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                return ModerationResult(
                    isSafe = false,
                    flaggedPhrase = match.value,
                    reason = "Content contains violent, threatening, or harmful language ('${match.value}')."
                )
            }
        }

        return ModerationResult(isSafe = true)
    }
}
