package com.lastgenlabs.crunchylist.data

/**
 * One parent-approved series.
 *
 * [seriesId] is Crunchyroll's catalogue ID (e.g. "G4PH0WXVJ"). These are content
 * identifiers, not app internals — they survive Crunchyroll app updates, which is
 * why the whitelist itself carries no rename risk (audit §4.2.4).
 */
data class Show(
    val seriesId: String,
    val title: String,
    val imageUrl: String? = null,
    val dateAdded: String = ""
)
