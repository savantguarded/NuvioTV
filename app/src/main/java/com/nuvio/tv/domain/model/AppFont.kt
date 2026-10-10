package com.nuvio.tv.domain.model

enum class AppFont(val displayName: String) {
    INTER("Inter"),
    DM_SANS("DM Sans"),
    OPEN_SANS("Open Sans"),
    // [fork] Nuvio C extra fonts (switch extra_fonts), appended so official entries never move
    GOOGLE_SANS_FLEX("Google Sans Flex"),
    MANROPE("Manrope"),
    FIGTREE("Figtree"),
    PLUS_JAKARTA_SANS("Plus Jakarta Sans"),
    OUTFIT("Outfit"),
    ATKINSON_HYPERLEGIBLE_NEXT("Atkinson Hyperlegible Next"),
    GEIST("Geist"),
    ONEST("Onest"),
    INSTRUMENT_SANS("Instrument Sans"),
    ALBERT_SANS("Albert Sans"),
    HANKEN_GROTESK("Hanken Grotesk"),
    RETHINK_SANS("Rethink Sans")
}

/** [fork] Nuvio C: one of the extra fonts (everything after Open Sans). */
val AppFont.isNuvioCExtra: Boolean get() = ordinal > AppFont.OPEN_SANS.ordinal

