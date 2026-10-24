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
    SOURCE_SERIF_4("Source Serif 4"),
    // [fork] round 27 (switch more_fonts), appended so stored choices never move.
    MANROPE("Manrope"),
    FIGTREE("Figtree"),
    NOTO_SANS("Noto Sans"),
    NUNITO_SANS("Nunito Sans"),
    PUBLIC_SANS("Public Sans"),
    SCHIBSTED_GROTESK("Schibsted Grotesk"),
    MONTSERRAT("Montserrat"),
    ARCHIVO("Archivo"),
    FAMILJEN_GROTESK("Familjen Grotesk"),
    BRICOLAGE_GROTESQUE("Bricolage Grotesque")
}

/** [fork] round 27: one of the fonts added by more_fonts. */
val AppFont.isNuvioCRound27: Boolean get() = ordinal > AppFont.SOURCE_SERIF_4.ordinal

/** [fork] Nuvio C: one of the extra fonts (everything after Open Sans). */
val AppFont.isNuvioCExtra: Boolean get() = ordinal > AppFont.OPEN_SANS.ordinal


/** [fork] Nuvio C: fonts dropped 2026-10-11; a profile still set to one of them gets Geist. */
val NUVIO_C_DROPPED_FONTS: Set<String> get() = setOf(
    "PLUS_JAKARTA_SANS", "ONEST", "INSTRUMENT_SANS", "ALBERT_SANS", "HANKEN_GROTESK", "RETHINK_SANS"
) + (if (com.nuvio.tv.NuvioCFeatures.MORE_FONTS) emptySet() else setOf("MANROPE", "FIGTREE")) // [fork] round 27: back in
