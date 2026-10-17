package br.com.livresom.core

import org.junit.Assert.*
import org.junit.Test

class DiscoveryPlanTest {
    @Test fun firstRailIsPopularityFromArchive() {
        val shelf=DiscoveryPlan.shelf(0)
        assertEquals("archive",shelf.catalog)
        assertTrue(shelf.popular)
        assertEquals(1,shelf.page)
    }
    @Test fun nextPageChangesCatalogOffsetWithoutDuplicatingFirstShelf() {
        val first=DiscoveryPlan.shelf(1)
        val next=DiscoveryPlan.shelf(DiscoveryPlan.templateCount+1)
        assertEquals(first.query,next.query)
        assertEquals(first.catalog,next.catalog)
        assertNotEquals(first.key,next.key)
        assertEquals(1,first.page)
        assertEquals(2,next.page)
    }
    @Test fun expectedCuratedRailsAndStableBatch() {
        assertTrue(DiscoveryPlan.templateCount>=12)
        val batch=DiscoveryPlan.nextBatch(0,4)
        assertEquals(4,batch.size)
        assertEquals(4,batch.map { it.key }.distinct().size)
    }
}
