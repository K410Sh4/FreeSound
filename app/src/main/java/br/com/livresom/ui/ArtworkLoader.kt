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

    private fun acceptedUrl(value:String):Boolean {
        val url=try { URI(value) } catch (_:Exception) { return false }
        if(!url.scheme.equals("https",true) || url.userInfo!=null || url.port!=-1) return false
        return when(url.host?.lowercase()) {
            "archive.org","api.openverse.org","upload.wikimedia.org" -> true
            else -> false
        }
    }

    private fun fromNetwork(ctx:Context,url:String):Bitmap? {
        if(!acceptedUrl(url)) return null
        val dir=File(ctx.cacheDir,"freesound_covers")
        val name=MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".img"
        val file=File(dir,name)
        if(file.isFile && file.length() in 1..MAX_IMAGE_BYTES.toLong()) {
            val b=BitmapFactory.decodeFile(file.path)
            if(b!=null) return b
            file.delete()
        }
        val c=URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=6000
        c.readTimeout=8500
        c.instanceFollowRedirects=false // Don't fetch image from untrusted redirects.
        c.setRequestProperty("Accept","image/*")
        c.setRequestProperty("User-Agent","FreeSound/1.0 (Android)")
        return try {
            if(c.responseCode!=HttpURLConnection.HTTP_OK) return null
            if(!c.contentType.orEmpty().startsWith("image/")) return null
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
            val bytes=output.toByteArray()
            val bitmap=decode(bytes) ?: return null
            try {
                dir.mkdirs()
                file.outputStream().use { it.write(bytes) }
            } catch (_:Exception) {}
            bitmap
        } finally { c.disconnect() }
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
