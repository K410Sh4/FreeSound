package br.com.livresom.core

import android.app.DownloadManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Track(
    val id: String, val title: String, val artist: String, val source: String,
    val page: String, val audio: String, val license: String, val licenseUrl: String,
    val downloadable: Boolean, val mime: String = "audio/mpeg", val local: String = "",
    val coverUrl: String = "", val downloadsCount: Long = -1L
) {
    fun json(): JSONObject = JSONObject().apply {
        put("id",id); put("title",title); put("artist",artist); put("source",source)
        put("page",page); put("audio",audio); put("license",license)
        put("licenseUrl",licenseUrl); put("downloadable",downloadable)
        put("mime",mime); put("local",local)
        put("coverUrl",coverUrl); put("downloadsCount",downloadsCount)
    }
    companion object {
        fun from(o: JSONObject) = Track(
            o.optString("id"),o.optString("title"),o.optString("artist"),
            o.optString("source"),o.optString("page"),o.optString("audio"),
            o.optString("license"),o.optString("licenseUrl"),
            o.optBoolean("downloadable"),o.optString("mime","audio/mpeg"),
            o.optString("local"),o.optString("coverUrl"),o.optLong("downloadsCount",-1L)
        )
    }
}
data class Saved(val track: Track,val downloadId: Long) {
    fun json() = JSONObject().put("track",track.json()).put("downloadId",downloadId)
}
class Library(ctx: Context) {
    private val prefs=ctx.getSharedPreferences("freesound_v1",Context.MODE_PRIVATE)
    private fun <T> read(key: String, parse:(JSONObject)->T):List<T> {
        val arr=try { JSONArray(prefs.getString(key,"[]")) } catch (_:Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            try { parse(arr.getJSONObject(i)) } catch (_:Exception) { null }
        }
    }
    private fun write(key:String, values:List<JSONObject>) {
        prefs.edit().putString(key,JSONArray().apply { values.forEach { put(it) } }.toString()).apply()
    }
    fun downloads()=read("downloads") { Saved(Track.from(it.getJSONObject("track")),it.optLong("downloadId",-1)) }
    fun favorites()=read("favorites") { Track.from(it) }
    fun add(s:Saved) { write("downloads",(listOf(s)+downloads().filterNot { it.track.id==s.track.id }).map { it.json() }) }
    fun remove(id:String) { write("downloads",downloads().filterNot { it.track.id==id }.map { it.json() }) }
    fun favorite(t:Track)=favorites().any { it.id==t.id }
    fun toggle(t:Track):Boolean {
        val old=favorites()
        val exists=old.any { it.id==t.id }
        write("favorites",(if(exists) old.filterNot { it.id==t.id } else listOf(t)+old).map { it.json() })
        return !exists
    }
}
object Rights {
    fun allowedHost(source:String,link:String):Boolean {
        val uri=try { URI(link) } catch (_:Exception) { return false }
        if (!uri.scheme.equals("https",true) || uri.port != -1 || uri.userInfo != null) return false
        return when(source) {
            "Wikimedia Commons" -> uri.host.equals("upload.wikimedia.org",true)
            "Internet Archive" -> uri.host.equals("archive.org",true)
            else -> false
        }
    }
    fun creativeCommons(link:String):Boolean {
        val uri=try { URI(link) } catch (_:Exception) { return false }
        return uri.scheme.equals("https",true) && uri.host.equals("creativecommons.org",true) &&
            (uri.path.orEmpty().startsWith("/licenses/") || uri.path.orEmpty().startsWith("/publicdomain/"))
    }
    fun filename(text:String)=text.replace(Regex("[^\\p{L}\\p{N} ._-]"),"_")
        .trim().trimStart('.').take(65).ifBlank { "Musica" }
}
class Downloads(ctx:Context,private val library:Library) {
    private val dm=ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    fun start(t:Track) {
        require(t.downloadable && t.local.isEmpty() && Rights.allowedHost(t.source,t.audio)) { "Sem autorização de download" }
        val ext=when(t.mime) {
            "audio/mpeg"->"mp3"; "audio/ogg"->"ogg"; "audio/flac"->"flac";
            "audio/mp4"->"m4a"; "audio/wav"->"wav"; else->"audio"
        }
        val req=DownloadManager.Request(Uri.parse(t.audio)).setTitle(t.title)
            .setMimeType(t.mime)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC,
                "FreeSound/"+Rights.filename(t.title)+"_"+System.currentTimeMillis()+".$ext")
        library.add(Saved(t,dm.enqueue(req)))
    }
    fun status(id:Long):Int {
        if(id==0L) return DownloadManager.STATUS_SUCCESSFUL
        if(id<0L) return DownloadManager.STATUS_FAILED
        return try {
            dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if(c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                else DownloadManager.STATUS_FAILED
            } ?: DownloadManager.STATUS_FAILED
        } catch (_:Exception) { DownloadManager.STATUS_FAILED }
    }
    fun uri(s:Saved):Uri? {
        if(s.downloadId==0L) return s.track.local.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if(status(s.downloadId)==DownloadManager.STATUS_SUCCESSFUL) dm.getUriForDownloadedFile(s.downloadId) else null
    }
    fun remove(s:Saved) {
        if(s.downloadId>0L) dm.remove(s.downloadId)
        library.remove(s.track.id)
    }
}
internal fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
private fun fetch(link:String):JSONObject {
    require(link.startsWith("https://"))
    val c=URL(link).openConnection() as HttpURLConnection
    c.connectTimeout=10000; c.readTimeout=14000
    c.setRequestProperty("User-Agent","FreeSound/1.0 (Android; open license music browser)")
    c.setRequestProperty("Accept","application/json")
    return try {
        if(c.responseCode !in 200..299) error("HTTP "+c.responseCode)
        JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    } finally { c.disconnect() }
}
object Search {
    fun commons(q:String):List<Track> {
        val link="https://commons.wikimedia.org/w/api.php?action=query"+
            "&generator=search&gsrsearch="+enc("filetype:audio $q")+
            "&gsrnamespace=6&gsrlimit=20&prop=imageinfo&iiprop=url%7Cmime%7Cextmetadata&iiurlwidth=160"+
            "&format=json&formatversion=2"
        val pages=fetch(link).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        return (0 until pages.length()).mapNotNull { i ->
            val page=pages.optJSONObject(i) ?: return@mapNotNull null
            val file=page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            val mime=file.optString("mime")
            val audio=file.optString("url")
            if(!mime.startsWith("audio/") || !Rights.allowedHost("Wikimedia Commons",audio)) return@mapNotNull null
            val meta=file.optJSONObject("extmetadata") ?: JSONObject()
            fun field(name:String)=meta.optJSONObject(name)?.optString("value").orEmpty()
                .replace(Regex("<[^>]*>")," ").replace("&amp;","&").trim()
            val license=field("LicenseShortName")
            val raw=field("LicenseUrl")
            val licenseUrl=if(raw.startsWith("http://")) "https://"+raw.removePrefix("http://")
                else raw.takeIf { it.startsWith("https://") }.orEmpty()
            val allowed=Rights.creativeCommons(licenseUrl) ||
                license.equals("CC0",true) || license.contains("public domain",true)
            Track(
                "commons:"+page.optLong("pageid"),
                page.optString("title").substringAfter(':').substringBeforeLast('.').replace('_',' '),
                field("Artist").ifBlank { "Veja os créditos na origem" }.take(150),
                "Wikimedia Commons",file.optString("descriptionurl"),audio,
                license.ifBlank { "Licença não confirmada" },licenseUrl,allowed,mime,
                coverUrl=file.optString("thumburl").takeIf { it.startsWith("https://upload.wikimedia.org/") }.orEmpty()
            )
        }
    }
    fun archive(q:String, popular:Boolean=false):List<Track> {
        val clean=q.replace(Regex("[^\\p{L}\\p{N} _-]")," ")
            .split(Regex("\\s+")).take(5).joinToString(" ")
        val link="https://archive.org/advancedsearch.php?q="+enc("mediatype:audio AND ($clean)")+
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=downloads"+
            "&rows="+(if(popular) "18" else "8")+"&output=json"+
            (if(popular) "&sort%5B%5D=downloads+desc" else "")
        val docs=fetch(link).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        val pool=Executors.newFixedThreadPool(4)
        return try {
            val work=(0 until docs.length()).mapNotNull { i ->
                docs.optJSONObject(i)?.let { d -> Callable { inspect(d) } }
            }
            pool.invokeAll(work,25,TimeUnit.SECONDS).mapNotNull { f ->
                try { if(f.isCancelled) null else f.get() } catch (_:Exception) { null }
            }
        } finally { pool.shutdownNow() }
    }
    private fun inspect(d:JSONObject):Track? {
        val id=d.optString("identifier")
        if(!id.matches(Regex("[A-Za-z0-9._-]{1,150}"))) return null
        val data=fetch("https://archive.org/metadata/"+enc(id))
        val meta=data.optJSONObject("metadata") ?: return null
        val files=data.optJSONArray("files") ?: return null
        val file=(0 until files.length()).mapNotNull { files.optJSONObject(it) }
            .firstOrNull {
                val n=it.optString("name").lowercase()
                it.optString("source")=="original" && !it.optBoolean("private") &&
                    listOf(".mp3",".ogg",".flac",".m4a",".wav").any { ext -> n.endsWith(ext) }
            } ?: return null
        val name=file.optString("name")
        val raw=meta.opt("licenseurl")
        val license=(if(raw is JSONArray) raw.optString(0) else if(raw is String) raw else "")
            .replace("http://","https://")
        val allowed=Rights.creativeCommons(license)
        val artist=when(val creator=meta.opt("creator")) {
            is String -> creator
            is JSONArray -> creator.optString(0)
            else -> "Artista não informado"
        }
        val mime=when(name.substringAfterLast('.',"").lowercase()) {
            "mp3"->"audio/mpeg";"ogg"->"audio/ogg";"flac"->"audio/flac";
            "wav"->"audio/wav";"m4a"->"audio/mp4";else->"application/octet-stream"
        }
        return Track("archive:$id:$name",meta.optString("title",name),artist.take(150),
            "Internet Archive","https://archive.org/details/"+enc(id),
            "https://archive.org/download/"+enc(id)+"/"+enc(name).replace("%2F","/"),
            if(allowed) license else "Licença não verificada",
            if(allowed) license else "",allowed,mime,
            coverUrl="https://archive.org/services/img/"+enc(id),
            downloadsCount=d.optLong("downloads",-1L))
    }
    fun openverse(q:String):List<Track> {
        val results=fetch("https://api.openverse.org/v1/audio/?q="+enc(q)+"&page_size=20")
            .optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val item=results.optJSONObject(i) ?: return@mapNotNull null
            val category=item.optString("category")
            if(category.isNotBlank() && category!="music") return@mapNotNull null
            val page=item.optString("foreign_landing_url")
            if(!page.startsWith("https://")) return@mapNotNull null
            Track("openverse:"+item.optString("id"),
                item.optString("title","Sem título"),item.optString("creator","Desconhecido"),
                "Openverse",page,item.optString("url").takeIf { it.startsWith("https://") }.orEmpty(),
                item.optString("license","Verifique licença"),item.optString("license_url"),false,
                coverUrl=item.optString("id").takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) }
                    ?.let { "https://api.openverse.org/v1/audio/$it/thumb/" }.orEmpty())
        }
    }
}
class Player(private val ctx:Context,private val changed:(Boolean,String?)->Unit) {
    private var player:MediaPlayer?=null
    var title:String="";private set
    private var ready=false
    fun open(text:String,uri:Uri) {
        release(); title=text
        try {
            val p=MediaPlayer();player=p
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            p.setDataSource(ctx,uri)
            p.setOnPreparedListener { mp -> if(player===mp) {
                ready=true;mp.start();changed(true,null)
            }}
            p.setOnCompletionListener { mp -> if(player===mp) changed(false,null) }
            p.setOnErrorListener { mp,_,_ ->
                if(player===mp) { release();changed(false,"Erro de reprodução") }
                true
            }
            p.prepareAsync()
        } catch (_:Exception) { release();changed(false,"Não foi possível abrir a música") }
    }
    fun isPlaying()=try { player?.isPlaying==true } catch (_:Exception) { false }
    fun toggle() {
        val p=player ?: return
        if(!ready) return
        try {
            if(p.isPlaying) p.pause() else p.start()
            changed(p.isPlaying,null)
        } catch (_:Exception) {}
    }
    fun pause() { if(isPlaying()) toggle() }
    fun duration()=if(ready) try { player?.duration ?: 0 } catch (_:Exception) { 0 } else 0
    fun position()=if(ready) try { player?.currentPosition ?: 0 } catch (_:Exception) { 0 } else 0
    fun seek(ms:Int) { if(ready) try { player?.seekTo(ms) } catch (_:Exception) {} }
    fun release() {
        ready=false
        try { player?.release() } catch (_:Exception) {}
        player=null
    }
}
