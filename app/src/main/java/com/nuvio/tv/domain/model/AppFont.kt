package com.nuvio.tv.domain.model

enum class AppFont(val displayName: String) {
    INTER("Inter"),
    DM_SANS("DM Sans"),
    OPEN_SANS("Open Sans"),
    // [fork] Nuvio C extra fonts (switch extra_fonts), appended so official entries never move.
    // Trimmed 2026-10-11 to fonts that look clearly different from each other.
    GOOGLE_SANS_FLEX("Google Sans Flex"),
    OUTFIT("Outfit"),
    ATKINSON_HYPERLEGIBLE_NEXT("Atkinson Hyperlegible Next"),
    GEIST("Geist"),
    SPACE_GROTESK("Space Grotesk"),
    LEXEND("Lexend"),
    SOURCE_SERIF_4("Source Serif 4")
}

/** [fork] Nuvio C: one of the extra fonts (everything after Open Sans). */
val AppFont.isNuvioCExtra: Boolean get() = ordinal > AppFont.OPEN_SANS.ordinal


/** [fork] Nuvio C: fonts dropped 2026-10-11; a profile still set to one of them gets Geist. */
val NUVIO_C_DROPPED_FONTS = setOf(
    "MANROPE", "FIGTREE", "PLUS_JAKARTA_SANS", "ONEST", "INSTRUMENT_SANS", "ALBERT_SANS", "HANKEN_GROTESK", "RETHINK_SANS"
)
