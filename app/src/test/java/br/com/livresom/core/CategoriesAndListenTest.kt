package br.com.livresom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesAndListenTest {
    private fun track()=Track(
        "local:sample","Test Track","Composer","Arquivo local",
        "","","","",false
    )
    @Test fun categorySearchIncludesSelectedGenre() {
        assertEquals("jazz music",Categories.queryFor("jazz",""))
        assertEquals("rock music live",Categories.queryFor("rock","live"))
        assertEquals("saxophone",Categories.queryFor("all"," saxophone "))
        assertEquals(Categories.options.size,Categories.options.map { it.id }.distinct().size)
    }
    @Test fun listenIsNotCountedOnTapOrSeek() {
        val s=ListenSession(track())
        assertFalse(s.sample(0,false,180_000))
        assertFalse(s.sample(10_000,true,180_000))
        assertFalse(s.sample(11_000,true,180_000))
        assertFalse(s.sample(19_000,true,180_000)) // capped at 2 s per tick
    }
    @Test fun listenCountsOnceAfterTwentyRealSeconds() {
        val s=ListenSession(track())
        s.sample(0,true,180_000)
        for(i in 1..19) assertFalse(s.sample(i*1000L,true,180_000))
        assertTrue(s.sample(20_000,true,180_000))
        assertFalse(s.sample(21_000,true,180_000))
        assertFalse(s.sample(22_000,false,180_000))
        assertFalse(s.sample(50_000,true,180_000))
    }
    @Test fun shortAudioNeedsMoreThanHalfDuration() {
        val s=ListenSession(track())
        s.sample(0,true,10_000)
        for(i in 1..5) assertFalse(s.sample(i*1000L,true,10_000))
        assertTrue(s.sample(6000,true,10_000))
    }
}
