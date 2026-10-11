package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C subtitle font (2026-10-11): the app's extra fonts for plain-text subtitles too,
// picked in the player's subtitle style panel ("Font") or Settings. Stored app-wide in NuvioCPrefs.
//  - ExoPlayer: the subtitle view's typeface (static Regular / Bold files, so Bold stays real bold).
//  - mpv: the same files are copied once to app storage and handed to libass (sub-fonts-dir), then
//    sub-font is set to the family name.
// ASS / SSA subtitles keep the fonts their file asks for, and picture subtitles (PGS) are images, so
// neither changes. Missing letters (other scripts) fall back to the system font as before.
// Files: assets/nuvio_c_subfonts/<Name>-Regular.ttf / -Bold.ttf (Latin subsets of res/font).
// Switch: NuvioCFeatures.SUBTITLE_FONT (off = official Roboto / system default).

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.nuvioc.NuvioCPrefs
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal object NuvioCSubtitleFont {
    private const val TAG = "NuvioCSubtitleFont"
    private const val ASSET_DIR = "nuvio_c_subfonts"

    /** [id] stored in prefs, [label] shown, [file] asset name stem, [family] the name libass matches. */
    data class Option(val id: String, val label: String, val file: String?, val family: String?)

    val options: List<Option> = listOf(
        Option("", "Default", null, null),
        Option("geist", "Geist", "Geist", "Geist"),
        Option("google_sans_flex", "Google Sans", "GoogleSansFlex", "Google Sans Flex"),
        Option("outfit", "Outfit", "Outfit", "Outfit"),
        Option("space_grotesk", "Space Grotesk", "SpaceGrotesk", "Space Grotesk"),
        Option("lexend", "Lexend", "Lexend", "Lexend"),
        Option("atkinson", "Atkinson", "AtkinsonHyperlegibleNext", "Atkinson Hyperlegible Next"),
        Option("source_serif", "Source Serif", "SourceSerif4", "Source Serif 4")
    )

    /** Current choice; a Compose state so subtitle views re-apply their style when it changes. */
    var current: Option by mutableStateOf(options.first())
        private set
    private var loaded = false

    fun load(context: Context): Option {
        if (!NuvioCFeatures.SUBTITLE_FONT) return options.first()
        if (!loaded) {
            loaded = true
            val id = NuvioCPrefs.subtitleFont(context)
            current = options.firstOrNull { it.id == id } ?: options.first()
        }
        return current
    }

    /** Pick a font by [id] (the Settings dialog). */
    fun select(context: Context, id: String) {
        load(context)
        val option = options.firstOrNull { it.id == id } ?: return
        current = option
        NuvioCPrefs.setSubtitleFont(context, option.id)
    }

    /** Next (+1) / previous (-1) font, wrapping round. */
    fun step(context: Context, delta: Int) {
        load(context)
        val i = options.indexOf(current).coerceAtLeast(0)
        val next = options[(i + delta).mod(options.size)]
        current = next
        NuvioCPrefs.setSubtitleFont(context, next.id)
    }

    private val typefaces = ConcurrentHashMap<String, Typeface>()

    /** Typeface for ExoPlayer subtitles, or null for the official default. */
    fun typeface(context: Context, bold: Boolean): Typeface? = typefaceFor(context, load(context), bold)

    /** Typeface of any [option] (Settings list previews each font in itself). */
    fun typefaceFor(context: Context, option: Option, bold: Boolean = false): Typeface? {
        val file = option.file ?: return null
        val name = "$ASSET_DIR/$file-${if (bold) "Bold" else "Regular"}.ttf"
        return typefaces[name] ?: runCatching { Typeface.createFromAsset(context.assets, name) }
            .onFailure { Log.w(TAG, "Couldn't load $name: ${it.message}") }
            .getOrNull()
            ?.also { typefaces[name] = it }
    }

    /** Folder libass reads the fonts from (copied from the APK once per app version). */
    fun mpvFontsDir(context: Context): File? {
        if (!NuvioCFeatures.SUBTITLE_FONT) return null
        return runCatching {
            val dir = File(context.filesDir, ASSET_DIR)
            val stamp = File(dir, ".version")
            val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            if (!(stamp.exists() && stamp.readText() == version)) {
                dir.mkdirs()
                context.assets.list(ASSET_DIR).orEmpty().forEach { name ->
                    context.assets.open("$ASSET_DIR/$name").use { input ->
                        File(dir, name).outputStream().use { input.copyTo(it) }
                    }
                }
                stamp.writeText(version)
            }
            dir
        }.onFailure { Log.w(TAG, "Couldn't prepare subtitle fonts for mpv: ${it.message}") }.getOrNull()
    }

    /** Family name for mpv's sub-font, or null to keep official's. */
    fun mpvFamily(context: Context): String? = load(context).family
}
