package br.com.livresom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import br.com.livresom.R
import br.com.livresom.core.Track
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Real catalog artwork or embedded audio artwork; music-note fallback if absent.
 * Memory cache + small app cache prevents repeated network calls while scrolling.
 */
internal object ArtworkLoader {
    private val executor=Executors.newFixedThreadPool(3)
    private val main=Handler(Looper.getMainLooper())
    private val cache=object:LruCache<String,Bitmap>(12*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap):Int=value.byteCount
    }
    private const val MAX_IMAGE_BYTES=3*1024*1024

    fun bind(context:Context,view:ImageView,track:Track,localUri:Uri?=null) {
        val embedded=localUri?.toString() ?: track.local
        val key=if(embedded.isNotBlank()) "local:$embedded" else track.coverUrl
        view.tag=key
        view.setImageResource(R.drawable.ic_livresom)
        if(key.isBlank()) return
        synchronized(cache) { cache.get(key) }?.let {
            view.setImageBitmap(it)
            return
        }
        val image=WeakReference(view)
        val app=context.applicationContext
        executor.execute {
            val bitmap=try {
                val local=if(embedded.isNotBlank()) fromLocal(app,embedded) else null
                local ?: fromNetwork(app,track.coverUrl)
            } catch (_:Exception) { null }
            if(bitmap!=null) {
                synchronized(cache) { cache.put(key,bitmap) }
                main.post {
                    val target=image.get()
                    if(target!=null && target.tag==key) target.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun fromLocal(ctx:Context,uriString:String):Bitmap? {
        val retriever=MediaMetadataRetriever()
        return try {
            retriever.setDataSource(ctx,Uri.parse(uriString))
            val artwork=retriever.embeddedPicture ?: return null
            if(artwork.size>MAX_IMAGE_BYTES) return null
            decode(artwork)
        } catch (_:Exception) { null } finally {
            try { retriever.release() } catch (_:Exception) {}
        }
    }

    private fun validLocation(value:String,original:String):Boolean {
        val u=try { URI(value) } catch (_:Exception) { return false }
        val root=try { URI(original).host?.lowercase() } catch (_:Exception) { null }
        if(!u.scheme.equals("https",true) || u.userInfo!=null || u.port!=-1) return false
        val host=u.host?.lowercase() ?: return false
        return when(root) {
            "archive.org" -> host=="archive.org" || host.endsWith(".archive.org")
            "api.openverse.org" -> host=="api.openverse.org" || host.endsWith(".openverse.org")
            "upload.wikimedia.org" -> host=="upload.wikimedia.org" || host.endsWith(".wikimedia.org")
            else -> false
        }
    }

    private fun fromNetwork(ctx:Context,url:String):Bitmap? {
        if(!validLocation(url,url)) return null
        val dir=File(ctx.cacheDir,"freesound_covers")
        val name=MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".img"
        val file=File(dir,name)
        if(file.isFile && file.length() in 1..MAX_IMAGE_BYTES.toLong()) {
            val b=BitmapFactory.decodeFile(file.path)
            if(b!=null) return b
            file.delete()
        }
        val bytes=fetchImage(url) ?: return null
        val bitmap=decode(bytes) ?: return null
        try {
            dir.mkdirs()
            file.outputStream().use { it.write(bytes) }
        } catch (_:Exception) {}
        return bitmap
    }

    /**
     * The Archive artwork endpoint commonly redirects to its own CDN.
     * Follow at most 3 HTTPS redirects within the same provider-owned domain.
     */
    private fun fetchImage(original:String):ByteArray? {
        var current=original
        for(hop in 0..3) {
            if(!validLocation(current,original)) return null
            val c=URL(current).openConnection() as HttpURLConnection
            c.connectTimeout=6000
            c.readTimeout=8500
            c.instanceFollowRedirects=false
            c.setRequestProperty("Accept","image/*")
            c.setRequestProperty("User-Agent","FreeSound/1.0 (Android)")
            try {
                val status=c.responseCode
                if(status in listOf(301,302,303,307,308)) {
                    val location=c.getHeaderField("Location") ?: return null
                    current=URI(current).resolve(location).toString()
                    continue
                }
                if(status!=HttpURLConnection.HTTP_OK) return null
                if(!c.contentType.orEmpty().lowercase().startsWith("image/")) return null
                if(c.contentLength>MAX_IMAGE_BYTES) return null
                val output=ByteArrayOutputStream()
                c.inputStream.use { input ->
                    val buffer=ByteArray(8192)
                    while(true) {
                        val n=input.read(buffer)
                        if(n<0) break
                        if(output.size()+n>MAX_IMAGE_BYTES) return null
                        output.write(buffer,0,n)
                    }
                }
                return output.toByteArray()
            } finally { c.disconnect() }
        }
        return null
    }

    private fun decode(bytes:ByteArray):Bitmap? {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth<=0 || bounds.outHeight<=0) return null
        var sample=1
        while(bounds.outWidth/sample>300 || bounds.outHeight/sample>300) sample*=2
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size,
            BitmapFactory.Options().apply { inSampleSize=sample })
    }
}
