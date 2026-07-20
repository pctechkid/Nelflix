package com.nuvio.app.features.details

import com.nuvio.app.core.format.extractReleaseYearForDisplay

/**
 * Compact release line under the details hero: show a valid start year only.
 */
fun formatMetaReleaseLineForDetails(meta: MetaDetails): String? {
    val raw = meta.releaseInfo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return extractReleaseYearForDisplay(raw)?.toString()
}
