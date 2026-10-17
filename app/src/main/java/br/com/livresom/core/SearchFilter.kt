package br.com.livresom.core

import java.text.Normalizer
import java.util.Locale

/**
 * Presentation-only filtering of results returned by the three catalogs.
 * Query terms typed by the user are distinct from suggested genre tags.
 */
data class SearchOptions(
    val words: String = "",
    val source: String = "Todas",
    val onlyDownloadable: Boolean = false,
    val onlyArtwork: Boolean = false,
    val field: Int = 0, // 0 both, 1 title, 2 artist
    val strict: Boolean = true,
    val sort: Int = 0 // 0 relevance, 1 downloads (items), 2 A-Z
)

object SearchFilter {
    private fun normalize(value: String): String {
        val deaccented = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return deaccented.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }

    fun tokens(phrase: String): List<String> =
        normalize(phrase).split(' ').filter { it.length >= 2 }.distinct().take(8)

    fun score(track: Track, typed: String, field: Int = 0): Int {
        val terms = tokens(typed)
        if (terms.isEmpty()) return 0
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        val t = if (field != 2) title else ""
        val a = if (field != 1) artist else ""
        val full = terms.joinToString(" ")
        var score = 0
        if (full.isNotBlank() && t.contains(full)) score += 100
        if (full.isNotBlank() && a.contains(full)) score += 65
        for (term in terms) {
            if (t.split(' ').any { it == term }) score += 25
            else if (t.contains(term)) score += 12
            if (a.split(' ').any { it == term }) score += 15
            else if (a.contains(term)) score += 7
        }
        return score
    }

    fun results(items: List<Track>, options: SearchOptions): List<Track> {
        val words = tokens(options.words)
        return items.distinctBy { it.id }.asSequence()
            .filter { options.source == "Todas" || it.source == options.source }
            .filter { !options.onlyDownloadable || it.downloadable }
            .filter { !options.onlyArtwork || it.coverUrl.isNotBlank() || it.local.isNotBlank() }
            .filter { track ->
                !options.strict || words.isEmpty() || run {
                    val title = if(options.field != 2) normalize(track.title) else ""
                    val artist = if(options.field != 1) normalize(track.artist) else ""
                    words.all { term -> title.contains(term) || artist.contains(term) }
                }
            }.sortedWith(when(options.sort) {
                1 -> compareByDescending<Track> { it.downloadsCount }.thenBy { normalize(it.title) }
                2 -> compareBy<Track> { normalize(it.title) }.thenBy { normalize(it.artist) }
                else -> compareByDescending<Track> { score(it,options.words,options.field) }
                    .thenByDescending { it.downloadsCount }
                    .thenBy { normalize(it.title) }
            }).toList()
    }
}
