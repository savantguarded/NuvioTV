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
    ) + if (NuvioCFeatures.MORE_FONTS) listOf( // [fork] round 27 more_fonts
        Option("manrope", "Manrope", "Manrope", "Manrope"),
        Option("figtree", "Figtree", "Figtree", "Figtree"),
        Option("noto_sans", "Noto Sans", "NotoSans", "Noto Sans"),
        Option("nunito_sans", "Nunito Sans", "NunitoSans", "Nunito Sans"),
        Option("public_sans", "Public Sans", "PublicSans", "Public Sans")
    ) else emptyList()

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
        stageForMpv(context, option)
    }

    /** Next (+1) / previous (-1) font, wrapping round. */
    fun step(context: Context, delta: Int) {
        load(context)
        val i = options.indexOf(current).coerceAtLeast(0)
        val next = options[(i + delta).mod(options.size)]
        current = next
        NuvioCPrefs.setSubtitleFont(context, next.id)
        stageForMpv(context, next)
    }

    /** [fork] round 27: a font picked while mpv is playing is added to its folder at once (nothing removed until mpv
     * next starts), so switching fonts mid-playback keeps working as before. */
    private fun stageForMpv(context: Context, option: Option) {
        if (!NuvioCFeatures.MORE_FONTS || !NuvioCFeatures.SUBTITLE_FONT) return
        val file = option.file ?: return
        runCatching {
            val dir = File(context.filesDir, ASSET_DIR)
            if (!dir.isDirectory) return@runCatching
            listOf("$file-Regular.ttf", "$file-Bold.ttf").forEach { name ->
                val target = File(dir, name)
                if (!target.exists()) context.assets.open("$ASSET_DIR/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }.onFailure { Log.w(TAG, "Couldn't add ${option.id} for mpv: ${it.message}") }
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
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            if (NuvioCFeatures.MORE_FONTS) {
                // [fork] round 27: libass scans the whole folder each time mpv starts, so it only ever holds the
                // chosen font's two files (nothing at all for Default).
                val option = load(context)
                val wanted = option.file?.let { listOf("$it-Regular.ttf", "$it-Bold.ttf") }.orEmpty()
                val version = "$installed:${option.id}"
                if (!(stamp.exists() && stamp.readText() == version)) {
                    dir.mkdirs()
                    dir.listFiles()?.forEach { if (it.name != stamp.name && it.name !in wanted) it.delete() }
                    wanted.forEach { name ->
                        context.assets.open("$ASSET_DIR/$name").use { input ->
                            File(dir, name).outputStream().use { input.copyTo(it) }
                        }
                    }
                    stamp.writeText(version)
                }
                return@runCatching dir
            }
            val version = installed
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
