package com.nuvio.tv.updater

// [fork] Nuvio C (2026-10-15): forced rebuilds replace the release of the same official version
// (tag "<official>+nuvioc"), so the version alone can't tell them apart. The workflow writes the
// Actions run number into the release notes as a hidden marker and into the APK (BuildConfig
// NUVIO_C_BUILD). The tag becomes "<official>+nuvioc.<run>" here (semver build metadata, ignored by
// the version comparison), so dismissing or downloading one rebuild never covers the next.
internal object NuvioCUpdateBuild {
    private val marker = Regex("""<!--\s*nuvioc-build:\s*(\d+)\s*-->""")

    fun buildFrom(notes: String?): Int? = notes?.let { marker.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    fun stripMarker(notes: String): String = notes.replace(marker, "").trim()

    fun tagWithBuild(tag: String, notes: String?): String {
        val build = buildFrom(notes) ?: return tag
        return if (tag.endsWith("+nuvioc")) "$tag.$build" else tag
    }

    fun buildOf(tag: String?): Int? = tag?.substringAfter("+nuvioc.", "")?.toIntOrNull()

    /**
     * [fork] round 20: newer than this APK, either a newer official version or a forced rebuild of this one.
     * Official beta.8's background download, idle install and "updated" banner use this too, so rebuilds
     * travel the same path (downloaded, compiled at install, silent installs on Android 12+).
     */
    fun isNewerThanInstalled(tag: String?): Boolean =
        VersionUtils.isRemoteNewer(tag, com.nuvio.tv.BuildConfig.VERSION_NAME) ||
            isNewerRebuild(tag, com.nuvio.tv.BuildConfig.VERSION_NAME, com.nuvio.tv.BuildConfig.NUVIO_C_BUILD)

    /** Same official version, higher run number than the installed APK. */
    fun isNewerRebuild(tag: String?, localVersion: String?, localBuild: Int): Boolean {
        val remote = VersionUtils.parse(tag) ?: return false
        val local = VersionUtils.parse(localVersion) ?: return false
        if (remote.compareTo(local) != 0) return false
        val build = buildOf(tag) ?: return false
        return build > localBuild
    }
}
