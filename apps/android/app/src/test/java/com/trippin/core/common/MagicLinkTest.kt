package com.trippin.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MagicLinkTest {

    @Test
    fun `reads token and email from the login link`() {
        assertEquals(MagicLink("tok123", "kevin@example.com"), magicLinkFrom("/login", "tok123", "kevin@example.com"))
    }

    @Test
    fun `a link without an email still signs in with the token`() {
        assertEquals(MagicLink("tok123", null), magicLinkFrom("/login/", " tok123 ", " "))
    }

    @Test
    fun `other paths and missing tokens are not sign-in links`() {
        assertNull(magicLinkFrom("/t/abc", "tok123", null))
        assertNull(magicLinkFrom("/login/extra", "tok123", null))
        assertNull(magicLinkFrom("/login", null, "kevin@example.com"))
        assertNull(magicLinkFrom("/login", "  ", null))
        assertNull(magicLinkFrom(null, "tok123", null))
    }
}
