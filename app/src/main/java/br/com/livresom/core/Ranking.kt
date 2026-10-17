package br.com.livresom.core

/** Only rank data with a verifiable catalog-provided download count. */
object Ranking {
    fun mostDownloaded(items:List<Track>):List<Track> =
        items.filter { it.source=="Internet Archive" && it.downloadsCount>=0L }
            .sortedWith(compareByDescending<Track> { it.downloadsCount }
                .thenBy { it.title })
}
