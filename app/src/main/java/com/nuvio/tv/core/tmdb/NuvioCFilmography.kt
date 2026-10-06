package com.nuvio.tv.core.tmdb

// [fork] Nuvio C cast-page filmography. TMDB's person credits mix real work with talk shows,
// award shows, documentaries, archive footage and producer / "thanks" credits. This keeps:
//  - actors: only cast credits that are actual roles (no Self / Himself / Herself, no archive
//    footage, no blank character, no TV talk / news / reality shows); never crew credits instead.
//  - directors, writers, creators: only Creator, Directing and Writing credits (no producer,
//    executive producer, thanks, consultant, camera, editing…).
// It also works out the line shown under each title: the character (voice roles keep "(voice)"),
// or the jobs merged in a fixed order, "Creator · Director · Writer".
// Switch: NuvioCFeatures.CAST_ACTING_ONLY (off = official filmography).

import com.nuvio.tv.data.remote.api.TmdbPersonCreditCast
import com.nuvio.tv.data.remote.api.TmdbPersonCreditCrew
import com.nuvio.tv.data.remote.api.TmdbPersonCreditsResponse
import com.nuvio.tv.domain.model.MetaPreview
import java.util.Locale

internal object NuvioCFilmography {

    private const val GENRE_NEWS = 10763
    private const val GENRE_REALITY = 10764
    private const val GENRE_TALK = 10767

    const val JOB_CREATOR = "Creator"
    const val JOB_DIRECTOR = "Director"
    const val JOB_WRITER = "Writer"
    private val JOB_ORDER = listOf(JOB_CREATOR, JOB_DIRECTOR, JOB_WRITER)

    private val SELF = Regex("""^\s*(self|himself|herself|themselves|themself)\b""", RegexOption.IGNORE_CASE)
    private val DIRECTOR_JOBS = setOf("director", "co-director", "series director")
    private val WRITING_JOBS = setOf(
        "writer", "screenplay", "story", "novel", "characters", "author", "teleplay", "book",
        "original story", "short story", "comic book", "graphic novel", "theatre play", "play",
        "co-writer", "original concept", "adaptation", "head writer", "staff writer", "idea",
        "original film writer", "original series creator", "screenstory", "dialogue",
        "story by", "written by", "novelist"
    )
    private val WRITING_EXCLUDE = listOf("editor", "consultant", "coordinator", "assistant", "researcher", "supervisor", "production", "producer", "lyric", "song")

    /** Map key shared by the service and the cast page: "movie:123" / "tv:123". */
    fun key(mediaType: String?, tmdbId: Int): String = "${mediaType ?: "?"}:$tmdbId"

    // ── actors ──

    fun isRealRole(credit: TmdbPersonCreditCast): Boolean {
        val character = credit.character?.trim().orEmpty()
        if (character.isEmpty()) return false
        if (SELF.containsMatchIn(character)) return false
        if (character.contains("archive footage", ignoreCase = true)) return false
        if (credit.mediaType == "tv") {
            val genres = credit.genreIds.orEmpty()
            if (GENRE_TALK in genres || GENRE_NEWS in genres || GENRE_REALITY in genres) return false
        }
        return true
    }

    fun actingCredits(cast: List<TmdbPersonCreditCast>): List<TmdbPersonCreditCast> = cast.filter(::isRealRole)

    /** Character per title; two parts in one title are joined "A / B". */
    fun characterLines(cast: List<TmdbPersonCreditCast>): Map<String, String> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for (credit in cast) {
            val character = credit.character?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val list = out.getOrPut(key(credit.mediaType, credit.id)) { mutableListOf() }
            if (list.none { it.equals(character, ignoreCase = true) }) list += character
        }
        return out.mapValues { (_, v) -> v.joinToString(" / ") }
    }

    // ── directors / writers / creators ──

    /** The label a crew credit counts as, or null when it is not a creator/directing/writing credit. */
    fun jobLabel(department: String?, job: String?): String? {
        val dept = department?.trim()?.lowercase(Locale.US).orEmpty()
        val j = job?.trim()?.lowercase(Locale.US).orEmpty()
        if (dept == "creator" || j == "creator") return JOB_CREATOR
        if (j in DIRECTOR_JOBS && (dept.isEmpty() || dept == "directing")) return JOB_DIRECTOR
        if (dept == "writing" || (dept.isEmpty() && j in WRITING_JOBS)) {
            if (j.isEmpty()) return JOB_WRITER
            if (WRITING_EXCLUDE.any { it in j }) return null
            return JOB_WRITER
        }
        return null
    }

    fun creatorCredits(crew: List<TmdbPersonCreditCrew>): List<TmdbPersonCreditCrew> =
        crew.filter { jobLabel(it.department, it.job) != null }

    /** "Creator · Director · Writer" per title, always in that order, each once. */
    fun jobLines(crew: List<TmdbPersonCreditCrew>): Map<String, String> {
        val out = LinkedHashMap<String, MutableSet<String>>()
        for (credit in crew) {
            val label = jobLabel(credit.department, credit.job) ?: continue
            out.getOrPut(key(credit.mediaType, credit.id)) { mutableSetOf() } += label
        }
        return out.mapValues { (_, labels) -> JOB_ORDER.filter { it in labels }.joinToString(" · ") }
    }

    // ── hook used by TmdbMetadataService.fetchPersonDetail ──

    class Pick(val movies: List<MetaPreview>, val tv: List<MetaPreview>, val roleLines: Map<String, String>)

    /** The credit lists official code maps (filtered when [active]), and which list the page shows. */
    class Source private constructor(
        val cast: List<TmdbPersonCreditCast>,
        val crew: List<TmdbPersonCreditCrew>,
        private val active: Boolean
    ) {
        /**
         * Null when the switch is off (official choice stands). Opened from the cast row
         * ([preferCrewCredits] false): acting roles only, never crew. Opened as director / writer /
         * creator, or a person known for that: their crew list (acting only if they have none).
         */
        fun pick(
            preferCrewCredits: Boolean?,
            preferCrewFilmography: Boolean,
            castMovies: List<MetaPreview>,
            castTv: List<MetaPreview>,
            crewMovies: List<MetaPreview>,
            crewTv: List<MetaPreview>
        ): Pick? {
            if (!active) return null
            val hasCast = castMovies.isNotEmpty() || castTv.isNotEmpty()
            val hasCrew = crewMovies.isNotEmpty() || crewTv.isNotEmpty()
            val useCrew = when {
                preferCrewCredits == false -> false
                preferCrewFilmography -> hasCrew || !hasCast
                else -> !hasCast && hasCrew
            }
            return if (useCrew) Pick(crewMovies, crewTv, jobLines(crew))
            else Pick(castMovies, castTv, characterLines(cast))
        }

        companion object {
            fun of(credits: TmdbPersonCreditsResponse?, active: Boolean): Source {
                val cast = credits?.cast.orEmpty()
                val crew = credits?.crew.orEmpty()
                return if (active) Source(actingCredits(cast), creatorCredits(crew), true)
                else Source(cast, crew, false)
            }
        }
    }
}
