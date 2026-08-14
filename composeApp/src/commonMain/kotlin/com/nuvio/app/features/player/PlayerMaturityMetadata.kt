package com.nuvio.app.features.player

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo

internal data class PlayerMaturityMetadata(
    val ratingCode: String?,
    val genresLine: String?,
)

internal fun resolvePlayerMaturityMetadata(
    episode: MetaVideo?,
    parent: MetaDetails?,
    imdbGenres: List<String> = emptyList(),
): PlayerMaturityMetadata {
    val ratingCode = episode?.ageRating.normalizedMetadataValue()
        ?: parent?.ageRating.normalizedMetadataValue()
    val genres = episode?.genres.normalizedMetadataValues()
        .ifEmpty { parent?.genres.normalizedMetadataValues() }
        .ifEmpty { imdbGenres.normalizedMetadataValues() }

    return PlayerMaturityMetadata(
        ratingCode = ratingCode,
        genresLine = genres.take(4).joinToString(", ").takeIf(String::isNotBlank),
    )
}

private fun String?.normalizedMetadataValue(): String? =
    this?.trim()?.takeIf(String::isNotBlank)

private fun List<String>?.normalizedMetadataValues(): List<String> =
    this.orEmpty()
        .mapNotNull { value -> value.normalizedMetadataValue() }
        .distinctBy(String::lowercase)
