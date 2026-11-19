package com.nuvio.tv.core.calendar

import java.time.Duration
import java.time.Instant

internal const val CALENDAR_MAX_ENTRIES_PER_TITLE = 40
internal val CALENDAR_KEEP_PAST: Duration = Duration.ofDays(15)
internal val CALENDAR_KEEP_AHEAD: Duration = Duration.ofDays(366)

/**
 * One entry per release. An exact time beats a plain date and is only ever taken from a source that gave one; when
 * two sources give a time, the one fetched last wins, so a show that moved is not held back by an older answer.
 * Trakt's episode type beats every other label.
 */
fun mergeCalendarEntries(
    entries: List<CalendarEntry>,
    fetchedAtMs: Map<CalendarSource, Long> = emptyMap()
): List<CalendarEntry> =
    entries.groupBy(CalendarEntry::slot).values
        .map { mergeSameRelease(it, fetchedAtMs) }
        .sortedWith(compareBy<CalendarEntry, Instant?>(nullsLast()) { it.sortInstant() }
            .thenBy { it.season ?: 0 }.thenBy { it.episode ?: 0 })

private fun mergeSameRelease(candidates: List<CalendarEntry>, fetchedAtMs: Map<CalendarSource, Long>): CalendarEntry {
    if (candidates.size == 1) return candidates.single()
    val byPriority = candidates.sortedBy { it.source.priority }
    val base = byPriority.filter { it.timeKnown }.maxWithOrNull(
        compareBy<CalendarEntry> { fetchedAtMs[it.source] ?: 0L }.thenByDescending { it.source.priority }
    ) ?: byPriority.firstOrNull { it.airDate != null } ?: byPriority.first()
    val label = byPriority.firstOrNull { it.source.isTrakt }?.label
        ?: byPriority.firstOrNull { it.label != CalendarEpisodeLabel.NONE }?.label
        ?: CalendarEpisodeLabel.NONE
    val title = byPriority.firstNotNullOfOrNull { it.title?.takeIf(String::isNotBlank) }
    return base.copy(title = title, label = label)
}

fun deriveEpisodeLabel(season: Int?, episode: Int?): CalendarEpisodeLabel = when {
    season == null || season < 1 || episode != 1 -> CalendarEpisodeLabel.NONE
    season == 1 -> CalendarEpisodeLabel.NEW_SHOW
    else -> CalendarEpisodeLabel.SEASON_PREMIERE
}

fun traktEpisodeLabel(episodeType: String?, season: Int?, episode: Int?): CalendarEpisodeLabel =
    when (episodeType?.trim()?.lowercase()) {
        null, "" -> deriveEpisodeLabel(season, episode)
        "series_premiere" -> CalendarEpisodeLabel.NEW_SHOW
        "season_premiere" -> CalendarEpisodeLabel.SEASON_PREMIERE
        "mid_season_premiere" -> CalendarEpisodeLabel.MID_SEASON_PREMIERE
        "mid_season_finale" -> CalendarEpisodeLabel.MID_SEASON_FINALE
        "season_finale" -> CalendarEpisodeLabel.SEASON_FINALE
        "series_finale" -> CalendarEpisodeLabel.SERIES_FINALE
        else -> CalendarEpisodeLabel.NONE
    }

fun tmdbEpisodeLabel(episodeType: String?): CalendarEpisodeLabel = when (episodeType?.trim()?.lowercase()) {
    "finale" -> CalendarEpisodeLabel.SEASON_FINALE
    "mid_season" -> CalendarEpisodeLabel.MID_SEASON_FINALE
    else -> CalendarEpisodeLabel.NONE
}

fun keptCalendarEntries(entries: List<CalendarEntry>, now: Instant): List<CalendarEntry> {
    val from = now.minus(CALENDAR_KEEP_PAST)
    val to = now.plus(CALENDAR_KEEP_AHEAD)
    return entries.filter { entry -> entry.sortInstant()?.let { !it.isBefore(from) && !it.isAfter(to) } == true }
}

/** Releases from 15 days back to a year ahead, at most 40 per title after merging, earliest first. */
fun CalendarTitleRecord.bounded(now: Instant): CalendarTitleRecord {
    val kept = sources.mapValues { (_, entries) -> keptCalendarEntries(entries, now) }.filterValues { it.isNotEmpty() }
    val slots = copy(sources = kept).entries.take(CALENDAR_MAX_ENTRIES_PER_TITLE).mapTo(HashSet()) { it.slot }
    val trimmed = kept.mapValues { (_, entries) -> entries.filter { it.slot in slots } }.filterValues { it.isNotEmpty() }
    return copy(sources = trimmed, sourceFetchedAtMs = sourceFetchedAtMs.filterKeys { it in trimmed })
}

fun CalendarTitleRecord.withSource(source: CalendarSource, entries: List<CalendarEntry>, now: Instant): CalendarTitleRecord =
    copy(
        sources = sources - source + (source to entries),
        sourceFetchedAtMs = sourceFetchedAtMs + (source to now.toEpochMilli())
    ).bounded(now)

fun CalendarTitleRecord.withoutSources(vararg removed: CalendarSource): CalendarTitleRecord =
    if (removed.none { it in sources }) this
    else copy(sources = sources - removed.toSet(), sourceFetchedAtMs = sourceFetchedAtMs - removed.toSet())

fun applyAddonEntries(
    records: List<CalendarTitleRecord>,
    addonEntries: List<Pair<CalendarIds, CalendarEntry>>,
    now: Instant
): List<CalendarTitleRecord> = records.map { record ->
    if (record.type != CalendarTitleType.SERIES) return@map record
    val mine = addonEntries.filter { (ids, _) -> ids.sharesIdWith(record.ids) }.map { it.second }.distinctBy { it.slot }
    record.withSource(CalendarSource.ADDON, mine, now)
}


/**
 * [fork] Nuvio C round 52 `calendar_no_rereleases`: a film is in the Calendar for its own first release only. Any
 * film date (cinema or digital, from any source) more than [NUVIO_C_REREALEASE_DAYS] after the earliest date known
 * for that film is a re-release (4K, Blu-ray, anniversary, a new digital date for an old film) and is dropped, so a
 * 1999 film listed by MDBList with a 2026 digital date shows nothing. Shows and episodes are untouched. A belled film
 * keeps every date (the caller skips this).
 */
fun nuvioCDropFilmReReleases(type: CalendarTitleType, entries: List<CalendarEntry>): List<CalendarEntry> {
    if (!com.nuvio.tv.NuvioCFeatures.CALENDAR_NO_REREALEASES || type != CalendarTitleType.MOVIE) return entries
    fun dateOf(e: CalendarEntry): java.time.LocalDate? = e.airDate?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() }
        ?: e.airInstantMs?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
    val films = entries.filter { it.kind != CalendarEntryKind.EPISODE }
    val first = films.mapNotNull(::dateOf).minOrNull() ?: return entries
    val limit = first.plusDays(NUVIO_C_REREALEASE_DAYS)
    return entries.filter { e -> e.kind == CalendarEntryKind.EPISODE || (dateOf(e)?.let { !it.isAfter(limit) } ?: true) }
}

/** A digital release usually follows the cinema one within months; a year apart means a re-release. */
const val NUVIO_C_REREALEASE_DAYS = 365L
