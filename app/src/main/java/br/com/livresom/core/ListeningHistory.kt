package br.com.livresom.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ListenEntry(val track:Track,val plays:Int,val lastPlayedAt:Long) {
    fun json():JSONObject=JSONObject().apply {
        put("track",track.json())
        put("plays",plays)
        put("lastPlayedAt",lastPlayedAt)
    }
    companion object {
        fun from(value:JSONObject)=ListenEntry(
            Track.from(value.getJSONObject("track")),
            value.optInt("plays",0),
            value.optLong("lastPlayedAt",0L)
        )
    }
}

/** Only listens on this device; no external analytics or fabricated global ranking. */
class ListeningHistory(context:Context) {
    private val prefs=context.getSharedPreferences("freesound_listening_v1",Context.MODE_PRIVATE)
    @Synchronized fun entries():List<ListenEntry> {
        val values=try { JSONArray(prefs.getString("history","[]")) }
                   catch (_:Exception) { JSONArray() }
        return (0 until values.length()).mapNotNull { i ->
            try { ListenEntry.from(values.getJSONObject(i)) } catch (_:Exception) { null }
        }
    }
    @Synchronized fun record(track:Track,now:Long=System.currentTimeMillis()) {
        if(track.id.isBlank()) return
        val all=entries().toMutableList()
        val old=all.firstOrNull { it.track.id==track.id }
        all.removeAll { it.track.id==track.id }
        // Keep the most recently available artwork and source information.
        all.add(0,ListenEntry(track,(old?.plays ?: 0).coerceAtMost(Int.MAX_VALUE-1)+1,now))
        val saved=all.sortedByDescending { it.lastPlayedAt }.take(300)
        prefs.edit().putString("history",JSONArray().apply {
            saved.forEach { put(it.json()) }
        }.toString()).apply()
    }
    fun mostListened():List<ListenEntry> =
        entries().filter { it.plays>0 }
            .sortedWith(compareByDescending<ListenEntry> { it.plays }
                .thenByDescending { it.lastPlayedAt })
}

/**
 * Count a real listening session, not a tap or a seek. The UI passes elapsed
 * realtime (monotonic), player.isPlaying and duration every ~850 ms.
 */
class ListenSession(val track:Track) {
    private var lastSample:Long?=null
    private var previousPlaying=false
    private var listenedMs=0L
    private var recorded=false
    fun sample(nowElapsedMs:Long,isPlaying:Boolean,durationMs:Int):Boolean {
        val previous=lastSample
        lastSample=nowElapsedMs
        if (!isPlaying) {
            previousPlaying=false
            return false
        }
        if(previousPlaying && previous!=null) {
            listenedMs+=(nowElapsedMs-previous).coerceIn(0L,2000L)
        }
        previousPlaying=true
        val threshold=if(durationMs in 1..39999)
            (durationMs*55L/100L).coerceAtLeast(1000L)
        else 20_000L
        if(!recorded && listenedMs>=threshold) {
            recorded=true
            return true
        }
        return false
    }
}
