package br.com.livresom.core

import org.junit.Assert.*
import org.junit.Test

class SearchFilterTest {
    private fun track(
        title:String, artist:String="Artista", source:String="Internet Archive",
        downloaded:Boolean=true, count:Long=-1L, artwork:String=""
    ) = Track(
        id="$source|$title",title=title,artist=artist,source=source,page="",audio="",
        license="CC BY",licenseUrl="",downloadable=downloaded,
        downloadsCount=count,coverUrl=artwork
    )

    @Test fun titleAndArtistCaseAccentInsensitive() {
        val input=listOf(
            track("Música de Verão","João"),
            track("Noite","JOÃO SOMBRA"),
            track("Blues", "Outro")
        )
        assertEquals(listOf("Música de Verão"),SearchFilter.results(
            input,SearchOptions(words="musica verao")).map { it.title })
        assertEquals(2,SearchFilter.results(input,SearchOptions(words="Joao",field=2)).size)
        assertEquals(0,SearchFilter.results(input,SearchOptions(words="Joao",field=1)).size)
    }

    @Test fun exactTermsRequireAllTypedWords() {
        val songs=listOf(track("Jazz piano solo"),track("Jazz"),track("Piano"))
        assertEquals(listOf("Jazz piano solo"),
            SearchFilter.results(songs,SearchOptions(words="jazz piano")).map { it.title })
        assertEquals(3,SearchFilter.results(songs,SearchOptions(words="jazz piano",strict=false)).size)
    }

    @Test fun respectSourceAndDownloadLicense() {
        val input=listOf(track("Chuva",downloaded=true),
            track("Natureza",source="Openverse",downloaded=false),
            track("Ruido",source="Wikimedia Commons",downloaded=true))
        assertEquals(1,SearchFilter.results(input,SearchOptions(
            source="Internet Archive",onlyDownloadable=true)).size)
        assertEquals("Chuva",SearchFilter.results(input,SearchOptions(
            onlyDownloadable=true,source="Internet Archive")).single().title)
    }

    @Test fun artworkFilterAndPopularitySort() {
        val entries=listOf(track("B",count=15),track("A",count=100,artwork="https://archive.org/img"),
            track("C",count=40))
        assertEquals(listOf("A","C","B"),SearchFilter.results(entries,SearchOptions(sort=1)).map { it.title })
        assertEquals(listOf("A"),SearchFilter.results(entries,SearchOptions(onlyArtwork=true)).map { it.title })
        assertEquals(listOf("A","B","C"),SearchFilter.results(entries,SearchOptions(sort=2)).map { it.title })
    }

    @Test fun scoreGivesTitlePriorityAndDeduplicates() {
        val entries=listOf(track("Sleep","Ambient"),track("Ambient Dream","Other"),track("Ambient Dream","Other"))
        val result=SearchFilter.results(entries,SearchOptions(words="ambient"))
        assertEquals(2,result.size)
        assertEquals("Ambient Dream",result.first().title)
    }
}
