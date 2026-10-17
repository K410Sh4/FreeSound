package br.com.livresom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RankingTest {
    private fun track(id:String,source:String,count:Long)=Track(
        id=id,title=id,artist="Test",source=source,page="",audio="",
        license="",licenseUrl="",downloadable=false,downloadsCount=count
    )
    @Test fun sortsDescendingByReportedCounts() {
        val result=Ranking.mostDownloaded(listOf(
            track("b","Internet Archive",12),
            track("a","Internet Archive",80),
            track("c","Internet Archive",33)
        ))
        assertEquals(listOf("a","c","b"),result.map { it.id })
    }
    @Test fun doesNotFabricateMetricsForOtherCatalogs() {
        val result=Ranking.mostDownloaded(listOf(
            track("commons","Wikimedia Commons",999),
            track("unknown","Internet Archive",-1),
            track("valid","Internet Archive",15)
        ))
        assertEquals(listOf("valid"),result.map { it.id })
    }
}
