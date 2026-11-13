package com.nuvio.tv.ui.screens.detail

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** [fork] round 44 detail_themes: TMDB keywords become clean Themes. */
class NuvioCRound44Test {
    @Test
    fun metaTagsDropped_titleCase_capped() {
        val out = nuvioCCleanThemes(
            listOf(
                "based on novel or book", "con artist", "duringcreditsstinger", "mother son relationship",
                "woman director", "neo-noir", "con artist", "sequel", "los angeles, california", "1990s",
                "grifter", "betrayal", "femme fatale", "racetrack", "extra one"
            )
        )
        assertEquals(
            listOf("Con Artist", "Mother Son Relationship", "Neo-noir", "Los Angeles, California", "1990s", "Grifter", "Betrayal", "Femme Fatale"),
            out
        )
    }

    @Test
    fun smallWordsStayLowercase_andShowsUseResults() {
        val o = Json.parseToJsonElement("""{"keywords":{"results":[{"id":1,"name":"fall of the empire"},{"id":2,"name":"aftercreditsstinger"}]}}""").jsonObject
        assertEquals(listOf("Fall of the Empire"), nuvioCThemes(o))
    }

    @Test
    fun noKeywords_empty() {
        val o = Json.parseToJsonElement("""{"id":5}""").jsonObject
        assertEquals(emptyList<String>(), nuvioCThemes(o))
    }
}
