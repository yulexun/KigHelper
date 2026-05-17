package com.ziegler.kighelper.data

import java.util.UUID

data class SocialMediaEntry(
    val platform: String = "",
    val handle: String = ""
)

data class ReceivedInfoCard(
    val id: String = UUID.randomUUID().toString(),
    val card: InfoCard,
    val receivedAt: Long = System.currentTimeMillis(),
    val source: String = "Wi-Fi Direct"
)

data class InfoCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val socialEntries: List<SocialMediaEntry> = listOf(SocialMediaEntry()),
    val themeColorHex: String = "#6750A4",
    val backgroundImagePath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun normalized(): InfoCard {
        val normalizedSocials = socialEntries
            .map { entry ->
                SocialMediaEntry(
                    platform = entry.platform.trim(),
                    handle = entry.handle.trim()
                )
            }
            .ifEmpty { listOf(SocialMediaEntry()) }

        return copy(
            name = name.trim(),
            socialEntries = normalizedSocials,
            themeColorHex = normalizeColor(themeColorHex),
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun empty(): InfoCard = InfoCard()

        fun normalizeColor(rawColor: String): String {
            val value = rawColor.trim().uppercase()
            return if (Regex("^#[0-9A-F]{6}$").matches(value)) {
                value
            } else {
                "#6750A4"
            }
        }
    }
}

