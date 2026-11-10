package com.nuvio.tv.ui.screens.ambient

data class AmbientSlide(
    val id: String,
    val title: String,
    val backdropUrl: String,
    val logoUrl: String?,
    val genres: List<String>,
    // [fork] round 38 ambient_open_details: what OK opens
    val itemId: String = "",
    val itemType: String = "",
    val addonBaseUrl: String? = null
)
