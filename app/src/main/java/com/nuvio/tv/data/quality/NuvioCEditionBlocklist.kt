package com.nuvio.tv.data.quality

// [fork] Nuvio C round 66 EDITIONS_V66: films with no official Extended or Director's Cut. Fan cuts of these are
// often just called "Extended", so those two editions never show for them. Add a film by its title as the details
// page names it (case, punctuation and "The" don't matter). Spider-Man: No Way Home is left out on purpose: its
// "More Fun Stuff" version is an official extended cut.

object NuvioCEditionBlocklist {
    private val NO_EXTENDED_OR_DC = setOf(
        // Marvel Cinematic Universe
        "iron man", "the incredible hulk", "iron man 2", "thor", "captain america the first avenger", "the avengers",
        "avengers", "marvels the avengers", "marvel s the avengers", "iron man 3", "thor the dark world", "captain america the winter soldier",
        "guardians of the galaxy", "avengers age of ultron", "ant man", "captain america civil war", "doctor strange",
        "guardians of the galaxy vol 2", "spider man homecoming", "thor ragnarok", "black panther", "avengers infinity war",
        "ant man and the wasp", "captain marvel", "avengers endgame", "spider man far from home", "black widow",
        "shang chi and the legend of the ten rings", "eternals", "doctor strange in the multiverse of madness",
        "thor love and thunder", "black panther wakanda forever", "ant man and the wasp quantumania",
        "guardians of the galaxy vol 3", "the marvels", "deadpool wolverine", "deadpool and wolverine",
        "captain america brave new world", "thunderbolts", "the fantastic four first steps"
    ).mapTo(HashSet()) { key(it) }

    private val BLOCKED = setOf(QualityEdition.EXTENDED, QualityEdition.DIRECTORS_CUT)

    fun key(title: String): String = title.lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("^the "), "")
        .replace(Regex(" +"), " ").trim()
            .removePrefix("the ")

    /** The editions never shown for [title]. */
    fun blocked(title: String?): Set<QualityEdition> =
        if (title != null && key(title) in NO_EXTENDED_OR_DC) BLOCKED else emptySet()
}
