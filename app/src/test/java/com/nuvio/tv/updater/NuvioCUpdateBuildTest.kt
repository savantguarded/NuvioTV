package com.nuvio.tv.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCUpdateBuildTest {
    private val notes = "Nuvio C, built from official.\n\n<!-- nuvioc-build:412 -->"

    @Test
    fun `tag gets the run number and notes lose the marker`() {
        assertEquals("1.1.0-beta.6+nuvioc.412", NuvioCUpdateBuild.tagWithBuild("1.1.0-beta.6+nuvioc", notes))
        assertEquals("1.1.0-beta.6+nuvioc", NuvioCUpdateBuild.tagWithBuild("1.1.0-beta.6+nuvioc", "no marker"))
        assertEquals("Nuvio C, built from official.", NuvioCUpdateBuild.stripMarker(notes))
    }

    @Test
    fun `rebuild of the same official release is newer only with a higher run`() {
        assertTrue(NuvioCUpdateBuild.isNewerRebuild("1.1.0-beta.6+nuvioc.412", "1.1.0-beta.6", 0))
        assertTrue(NuvioCUpdateBuild.isNewerRebuild("1.1.0-beta.6+nuvioc.412", "1.1.0-beta.6", 411))
        assertFalse(NuvioCUpdateBuild.isNewerRebuild("1.1.0-beta.6+nuvioc.412", "1.1.0-beta.6", 412))
        assertFalse(NuvioCUpdateBuild.isNewerRebuild("1.1.0-beta.5+nuvioc.500", "1.1.0-beta.6", 1))
        assertFalse(NuvioCUpdateBuild.isNewerRebuild("1.1.0-beta.6+nuvioc", "1.1.0-beta.6", 0))
        assertTrue(VersionUtils.parse("1.1.0-beta.6+nuvioc.412") != null)
    }
}
