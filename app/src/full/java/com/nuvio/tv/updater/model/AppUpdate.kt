package com.nuvio.tv.updater.model

data class AppUpdate(
    val tag: String,
    val title: String,
    val notes: String,
    val releaseUrl: String?,
    val assetName: String,
    val assetUrl: String,
    val assetSizeBytes: Long?,
    val profileUrl: String? = null, // [fork] install_profile: the .dm for this Android version
    val profileName: String? = null
)
