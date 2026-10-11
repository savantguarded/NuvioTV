package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCHdr10PlusSniffTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun findsSignatureInOneRead() {
        val m = NuvioCHdr10PlusSniff.Matcher()
        m.feed(bytes(0x00, 0x00, 0x01, 0x4E, 0x01, 0x04, 0x40, 0xB5, 0x00, 0x3C, 0x00, 0x01, 0x04, 0x01), 0, 14)
        assertTrue(m.matched)
    }

    @Test
    fun findsSignatureAcrossReads() {
        val m = NuvioCHdr10PlusSniff.Matcher()
        m.feed(bytes(0x11, 0xB5, 0x00, 0x3C), 0, 4)
        assertFalse(m.matched)
        m.feed(bytes(0x00, 0x01, 0x04), 0, 3)
        assertTrue(m.matched)
    }

    @Test
    fun restartsOnRepeatedLeadByte() {
        val m = NuvioCHdr10PlusSniff.Matcher()
        m.feed(bytes(0xB5, 0xB5, 0x00, 0x3C, 0x00, 0x01, 0x04), 0, 7)
        assertTrue(m.matched)
    }

    @Test
    fun ignoresOtherT35Messages() {
        val m = NuvioCHdr10PlusSniff.Matcher()
        // Dolby / other provider codes, and HDR10+ header with a different application id
        m.feed(bytes(0xB5, 0x00, 0x31, 0x47, 0x41, 0x39, 0x34, 0xB5, 0x00, 0x3C, 0x00, 0x01, 0x05), 0, 13)
        assertFalse(m.matched)
    }
}
