package com.trippin.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {

    @Test
    fun `reads the token out of a plain share path`() {
        assertEquals("abc123", shareTokenFromPath("/t/abc123"))
    }

    @Test
    fun `tolerates a missing leading slash`() {
        assertEquals("abc123", shareTokenFromPath("t/abc123"))
    }

    @Test
    fun `tolerates a trailing slash`() {
        assertEquals("abc123", shareTokenFromPath("/t/abc123/"))
    }

    @Test
    fun `rejects a path with no token`() {
        assertNull(shareTokenFromPath("/t"))
        assertNull(shareTokenFromPath("/t/"))
    }

    @Test
    fun `rejects a path that is not the share shape`() {
        assertNull(shareTokenFromPath("/trip/abc123"))
        assertNull(shareTokenFromPath("/abc123"))
        assertNull(shareTokenFromPath("/"))
    }

    @Test
    fun `rejects a path with extra segments rather than guessing which one is the token`() {
        assertNull(shareTokenFromPath("/t/abc123/extra"))
    }

    @Test
    fun `rejects nothing at all`() {
        assertNull(shareTokenFromPath(null))
        assertNull(shareTokenFromPath(""))
        assertNull(shareTokenFromPath("   "))
    }

    @Test
    fun `does not mistake a path that merely contains t somewhere for a share link`() {
        assertNull(shareTokenFromPath("/trips/t/abc123"))
    }
}
