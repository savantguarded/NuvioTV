package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 37 `roulette_refine` + `roulette_pool_size`: what the Refine panel narrows.
// Every option is a plain TMDB discover filter on Roulette's own raw shuffle (NuvioCRouletteRaw), so a refined
// spin is still one random title from everything that matches. Saved per Nuvio profile with the other choices.

/** How deep the shuffle reaches. Curated = the round 34 floor (300 votes, 6.5). */
enum class NuvioCRoulettePool(val key: String) {
    CURATED("curated"), WIDE("wide"), DEEP("deep"),
    /** [fork] round 40 roulette_popularity: Deep cuts, a handful of votes is enough (small film industries live here). */
    CUTS("cuts");

    /** Minimum TMDB votes and score for one decade of one type. Older decades need fewer votes (TMDB votes skew recent). */
    fun floor(type: String, anime: Boolean, decadeStart: Int?): Pair<Int, Float> {
        val old = decadeStart != null && decadeStart < 1980
        val tv = type == "tv"
        return when (this) {
            CURATED -> (if (anime) 150 else 300) to 6.5f
            WIDE -> when {
                anime -> 60
                tv -> if (old) 30 else 80
                else -> if (old) 50 else 100
            } to 6.4f
            DEEP -> when {
                anime -> 30
                tv -> if (old) 15 else 30
                else -> if (old) 25 else 40
            } to 6.0f
            CUTS -> when {
                anime -> 12
                tv -> 8
                else -> 10
            } to 5.8f
        }
    }
}

enum class NuvioCRouletteDecade(val key: String, val from: Int?, val to: Int?) {
    ANY("any", null, null),
    D2020("2020s", 2020, 2029),
    D2010("2010s", 2010, 2019),
    D2000("2000s", 2000, 2009),
    D1990("1990s", 1990, 1999),
    D1980("1980s", 1980, 1989),
    D1970("1970s", 1970, 1979),
    D1960("1960s", 1960, 1969),
    EARLIER("earlier", null, 1959)
}

/** Production countries (TMDB `with_origin_country`, "|" = any of them). */
enum class NuvioCRouletteRegion(val key: String, val countries: String?) {
    ANY("any", null),
    AFRICA("africa", "NG|ZA|KE|GH|EG|MA|SN"),
    EUROPE("europe", "GB|IE|FR|DE|IT|ES|PT|NL|BE|AT|CH|PL|CZ|HU|RO|GR"),
    NORDIC("nordic", "SE|NO|DK|FI|IS"),
    EAST_ASIA("east_asia", "JP|KR|CN|TW|HK"),
    SOUTH_ASIA("south_asia", "IN|PK|BD|LK|TH|ID|PH|MY|SG|VN"),
    MIDDLE_EAST("middle_east", "TR|IL|IR|SA|AE|EG|LB|JO"),
    LATIN_AMERICA("latin_america", "MX|BR|AR|CO|CL|PE|CU"),
    NORTH_AMERICA("north_america", "US|CA"),
    OCEANIA("oceania", "AU|NZ"),
    // [fork] round 39 roulette_refine_more: single countries, after the regions so saved choices keep their meaning
    HOLLYWOOD("us", "US"),
    BRITISH("gb", "GB"),
    FRENCH("fr", "FR"),
    SPANISH("es", "ES"),
    KOREAN("kr", "KR"),
    JAPANESE("jp", "JP"),
    INDIAN("in", "IN"),
    NIGERIAN("ng", "NG")
}

/** [fork] round 39 roulette_refine_more: a theme, from TMDB keywords (War uses the war genre). */
enum class NuvioCRouletteTheme(val key: String, val keyword: String?) {
    ANY("any", null),
    HEIST("heist", "10051"),
    REVENGE("revenge", "9748"),
    BOOK("book", "818"),
    TIME_TRAVEL("time_travel", "4379"),
    SURVIVAL("survival", "10349"),
    COURTROOM("courtroom", "33519"),
    SPORTS("sports", "6075"),
    WAR("war", null)
}

/** [fork] round 39 roulette_refine_more: who it's for (films only: TMDB has US certifications for films, not shows). */
enum class NuvioCRouletteAudience(val key: String) { ANY("any"), FAMILY("family"), ADULTS("adults") }

/** [fork] round 39 roulette_refine_more: show status / format (shows only, TMDB `with_status` / `with_type`). */
enum class NuvioCRouletteStatus(val key: String) { ANY("any"), ONGOING("ongoing"), ENDED("ended"), MINISERIES("miniseries") }

/** Film running time / episode length (TMDB `with_runtime`, minutes). */
enum class NuvioCRouletteLength(val key: String, val movieMin: Int?, val movieMax: Int?, val tvMin: Int?, val tvMax: Int?) {
    ANY("any", null, null, null, null),
    SHORT("short", null, 90, null, 30),
    STANDARD("standard", null, 120, null, 60),
    LONG("long", 150, null, 50, null)
}

/** How well received. Gems = loved but little known. */
enum class NuvioCRouletteAcclaim(val key: String) {
    ANY("any"), CROWD("crowd"), TOP("top"), GEMS("gems"), CULT("cult")
}

data class NuvioCRouletteRefine(
    val decade: NuvioCRouletteDecade = NuvioCRouletteDecade.ANY,
    val region: NuvioCRouletteRegion = NuvioCRouletteRegion.ANY,
    val length: NuvioCRouletteLength = NuvioCRouletteLength.ANY,
    val acclaim: NuvioCRouletteAcclaim = NuvioCRouletteAcclaim.ANY,
    val pool: NuvioCRoulettePool = NuvioCRoulettePool.WIDE,
    val theme: NuvioCRouletteTheme = NuvioCRouletteTheme.ANY,
    val audience: NuvioCRouletteAudience = NuvioCRouletteAudience.ANY,
    val status: NuvioCRouletteStatus = NuvioCRouletteStatus.ANY
) {
    /** Filters switched on (pool size isn't a filter). Shown on the Refine chip. */
    val active: Int
        get() = listOf(decade != NuvioCRouletteDecade.ANY, region != NuvioCRouletteRegion.ANY,
            length != NuvioCRouletteLength.ANY, acclaim != NuvioCRouletteAcclaim.ANY,
            theme != NuvioCRouletteTheme.ANY, audience != NuvioCRouletteAudience.ANY, status != NuvioCRouletteStatus.ANY).count { it }

    /** [fork] round 39: the filters only (pool size kept), what a fresh visit starts from. */
    fun cleared(): NuvioCRouletteRefine = NuvioCRouletteRefine(pool = pool)

    /** What a raw query actually uses (switch off = round 34 behaviour). */
    fun effective(): NuvioCRouletteRefine = when {
        !com.nuvio.tv.NuvioCFeatures.ROULETTE_REFINE && !com.nuvio.tv.NuvioCFeatures.ROULETTE_POOL_SIZE -> NuvioCRouletteRefine(pool = NuvioCRoulettePool.CURATED)
        !com.nuvio.tv.NuvioCFeatures.ROULETTE_REFINE -> NuvioCRouletteRefine(pool = pool)
        !com.nuvio.tv.NuvioCFeatures.ROULETTE_POOL_SIZE -> copy(pool = NuvioCRoulettePool.CURATED)
        !com.nuvio.tv.NuvioCFeatures.ROULETTE_POPULARITY && pool == NuvioCRoulettePool.CUTS -> copy(pool = NuvioCRoulettePool.DEEP)
        else -> this
    }

    fun encode(): String = listOf(decade.key, region.key, length.key, acclaim.key, pool.key, theme.key, audience.key, status.key).joinToString(",")

    companion object {
        fun decode(s: String?): NuvioCRouletteRefine {
            val p = s?.split(',').orEmpty()
            return NuvioCRouletteRefine(
                decade = NuvioCRouletteDecade.entries.firstOrNull { it.key == p.getOrNull(0) } ?: NuvioCRouletteDecade.ANY,
                region = NuvioCRouletteRegion.entries.firstOrNull { it.key == p.getOrNull(1) } ?: NuvioCRouletteRegion.ANY,
                length = NuvioCRouletteLength.entries.firstOrNull { it.key == p.getOrNull(2) } ?: NuvioCRouletteLength.ANY,
                acclaim = NuvioCRouletteAcclaim.entries.firstOrNull { it.key == p.getOrNull(3) } ?: NuvioCRouletteAcclaim.ANY,
                pool = NuvioCRoulettePool.entries.firstOrNull { it.key == p.getOrNull(4) } ?: NuvioCRoulettePool.WIDE,
                theme = NuvioCRouletteTheme.entries.firstOrNull { it.key == p.getOrNull(5) } ?: NuvioCRouletteTheme.ANY,
                audience = NuvioCRouletteAudience.entries.firstOrNull { it.key == p.getOrNull(6) } ?: NuvioCRouletteAudience.ANY,
                status = NuvioCRouletteStatus.entries.firstOrNull { it.key == p.getOrNull(7) } ?: NuvioCRouletteStatus.ANY
            )
        }
    }
}
