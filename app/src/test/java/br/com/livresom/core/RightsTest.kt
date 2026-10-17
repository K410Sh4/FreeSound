package br.com.livresom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RightsTest {
    @Test fun allowsKnownHosts() {
        assertTrue(Rights.allowedHost("Wikimedia Commons","https://upload.wikimedia.org/wikipedia/commons/test.ogg"))
        assertTrue(Rights.allowedHost("Internet Archive","https://archive.org/download/example/test.mp3"))
    }
    @Test fun blocksBadHostsAndUrls() {
        assertFalse(Rights.allowedHost("Wikimedia Commons","https://upload.wikimedia.org.evil.example/test.mp3"))
        assertFalse(Rights.allowedHost("Internet Archive","http://archive.org/download/example"))
        assertFalse(Rights.allowedHost("Openverse","https://archive.org/download/example"))
        assertFalse(Rights.allowedHost("Internet Archive","https://archive.org:8443/test.mp3"))
        assertFalse(Rights.allowedHost("Internet Archive","https://user@archive.org/test.mp3"))
    }
    @Test fun recognizesCreativeCommons() {
        assertTrue(Rights.creativeCommons("https://creativecommons.org/licenses/by/4.0/"))
        assertTrue(Rights.creativeCommons("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertFalse(Rights.creativeCommons("https://creativecommons.org.evil.org/licenses/by/4.0"))
        assertFalse(Rights.creativeCommons("http://creativecommons.org/licenses/by/4.0"))
    }
    @Test fun removesInvalidCharacters() {
        assertEquals("Musica",Rights.filename("   "))
        assertEquals("Minha Musica",Rights.filename("Minha Musica"))
    }
}
