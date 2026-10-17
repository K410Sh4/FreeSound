package br.com.livresom.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import br.com.livresom.core.Downloads
import br.com.livresom.core.Library
import br.com.livresom.core.Ranking
import br.com.livresom.core.Player
import br.com.livresom.core.Saved
import br.com.livresom.core.Search
import br.com.livresom.core.Track
import java.util.concurrent.Executors
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {
    private val bg=Color.rgb(11,17,26)
    private val panelColor=Color.rgb(25,35,48)
    private val panelAlt=Color.rgb(35,45,59)
    private val white=Color.rgb(241,246,250)
    private val muted=Color.rgb(159,176,191)
    private val accent=Color.rgb(99,230,198)
    private val main=Handler(Looper.getMainLooper())
    private val workers=Executors.newFixedThreadPool(3)
    private lateinit var library:Library
    private lateinit var downloads:Downloads
    private lateinit var player:Player
    private lateinit var content:LinearLayout
    private lateinit var searchRow:LinearLayout
    private lateinit var searchEdit:EditText
    private lateinit var nav:LinearLayout
    private lateinit var playerLabel:TextView
    private lateinit var playerArtwork:ImageView
    private lateinit var playerToggle:Button
    private lateinit var seek:SeekBar
    private lateinit var timing:TextView
    private var tab=0
    private var filter=0
    private var popularOnly=false
    private var lastQuery=""
    private var serial=0
    private var searching=false
    private val songs=mutableListOf<Track>()
    private val errors=mutableListOf<String>()
    private var completed=0
    private val ticker=object:Runnable {
        override fun run() {
            if(::seek.isInitialized) {
                seek.max=maxOf(1,player.duration())
                if(!seek.isPressed) seek.progress=player.position()
                timing.text=time(player.position())+" / "+time(player.duration())
                playerToggle.text=if(player.isPlaying()) "Ⅱ" else "▶"
            }
            main.postDelayed(this,850)
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        library=Library(this)
        downloads=Downloads(this,library)
        player=Player(this) { playing,error ->
            runOnUiThread {
                if(::playerLabel.isInitialized) playerLabel.text=player.title.ifBlank { "Nenhuma música" }
                if(::playerToggle.isInitialized) playerToggle.text=if(playing) "Ⅱ" else "▶"
                if(error!=null) toast(error)
            }
        }
        build()
        render()
        main.post(ticker)
    }
    override fun onStop() { player.pause();super.onStop() }
    override fun onDestroy() {
        serial++;main.removeCallbacksAndMessages(null);workers.shutdownNow()
        player.release();super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        if(::content.isInitialized && tab==1) render()
    }

    private fun build() {
        val root=vertical()
        root.setBackgroundColor(bg)
        setContentView(root)
        if(Build.VERSION.SDK_INT>=35) {
            root.setOnApplyWindowInsetsListener { view,insets ->
                val bars=insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0,bars.top,0,bars.bottom)
                insets
            }
            root.requestApplyInsets()
        }
        val header=horizontal().apply {
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(20),dp(16),dp(20),dp(10))
        }
        val logo=text("♫",30f,bg,true).apply {
            gravity=Gravity.CENTER;background=rounded(accent,15)
        }
        header.addView(logo,LinearLayout.LayoutParams(dp(53),dp(53)))
        val names=vertical().apply { setPadding(dp(12),0,0,0) }
        names.addView(text("FreeSound",24f,white,true))
        names.addView(text("Música livre e biblioteca offline",12f,muted))
        header.addView(names,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header)

        searchRow=horizontal().apply { setPadding(dp(17),dp(8),dp(17),dp(9)) }
        searchEdit=EditText(this).apply {
            hint="Buscar música ou artista..."
            setSingleLine(true);textSize=14f
            setTextColor(white);setHintTextColor(muted)
            setPadding(dp(12),0,dp(9),0)
            background=rounded(panelAlt,12)
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _,_,_-> startSearch();true }
        }
        searchRow.addView(searchEdit,LinearLayout.LayoutParams(0,dp(48),1f))
        searchRow.addView(button("Buscar",true) { startSearch() },
            LinearLayout.LayoutParams(dp(84),dp(48)).apply { leftMargin=dp(8) })
        root.addView(searchRow)

        val scroller=ScrollView(this)
        content=vertical().apply { setPadding(dp(16),dp(5),dp(16),dp(24)) }
        scroller.addView(content)
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))

        val playerPanel=vertical().apply {
            setPadding(dp(17),dp(11),dp(17),dp(9))
            background=rounded(panelColor,12)
        }
        val line=horizontal().apply { gravity=Gravity.CENTER_VERTICAL }
        playerArtwork=ImageView(this).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP
            setImageResource(br.com.livresom.R.drawable.ic_livresom)
            background=rounded(panelAlt,10)
            clipToOutline=true
        }
        line.addView(playerArtwork,LinearLayout.LayoutParams(dp(46),dp(46)).apply { marginEnd=dp(10) })
        val titles=vertical()
        titles.addView(text("REPRODUTOR",10f,muted,true))
        playerLabel=text("Nenhuma música",14f,white,true).apply { maxLines=1 }
        titles.addView(playerLabel)
        line.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        playerToggle=button("▶",true) { player.toggle() }
        line.addView(playerToggle,LinearLayout.LayoutParams(dp(55),dp(42)))
        playerPanel.addView(line)
        val seekLine=horizontal().apply { gravity=Gravity.CENTER_VERTICAL }
        seek=SeekBar(this).apply {
            progressTintList=android.content.res.ColorStateList.valueOf(accent)
            thumbTintList=android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) {}
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) { player.seek(bar?.progress ?: 0) }
            })
        }
        seekLine.addView(seek,LinearLayout.LayoutParams(0,dp(30),1f))
        timing=text("00:00 / 00:00",10f,muted)
        seekLine.addView(timing)
        playerPanel.addView(seekLine)
        root.addView(playerPanel)

        nav=horizontal().apply {
            setPadding(dp(11),dp(8),dp(11),dp(8))
            setBackgroundColor(bg)
        }
        root.addView(nav)
    }
    private fun render() {
        content.removeAllViews();nav.removeAllViews()
        searchRow.visibility=if(tab==0) View.VISIBLE else View.GONE
        listOf("Buscar","Biblioteca","Fontes").forEachIndexed { i,name ->
            nav.addView(button(name,i==tab) { tab=i;render() },
                LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(5) })
        }
        when(tab) {
            0->renderSearch()
            1->renderLibrary()
            else->renderSources()
        }
    }
    private fun renderSearch() {
        heading("Descubra músicas","Licenças declaradas nas fontes; confira cada obra.")
        val orderRow=horizontal()
        orderRow.addView(button("Relevância",!popularOnly) {
            if(popularOnly) { popularOnly=false;filter=0;switchRanking() }
        },weight())
        orderRow.addView(button("★ Mais populares",popularOnly) {
            if(!popularOnly) { popularOnly=true;filter=0;switchRanking() }
        },weight())
        content.addView(orderRow)
        space(9)
        if(popularOnly) {
            note("Mais baixadas no Archive",
                "Ordem pela contagem acumulada de downloads do item no Internet Archive. " +
                "Não é um ranking mundial de músicas. Digite um termo ou explore músicas populares sem pesquisar.")
        } else {
            val chips=horizontal()
            listOf("Todas","Commons","Archive","Openverse").forEachIndexed { i,name ->
                chips.addView(button(name,i==filter) { filter=i;render() },
                    LinearLayout.LayoutParams(0,dp(37),1f).apply { marginEnd=dp(4) })
            }
            content.addView(chips)
        }
        space(10)
        if(searching) note("Pesquisando...","Consultando os catálogos disponíveis.")
        if(!searching && songs.isEmpty()) {
            note(if(lastQuery.isBlank()) "Comece por uma pesquisa" else "Nenhum resultado",
                if(lastQuery.isBlank()) "Pesquise piano, chuva, instrumental, jazz ou natureza."
                else "Tente outro termo ou consulte as fontes oficiais.")
        }
        if(errors.isNotEmpty()) note("Algumas fontes falharam",errors.joinToString("; ").take(230))
        val filtered=songs.filter { track ->
            if(popularOnly) track.source=="Internet Archive" && track.downloadsCount>=0
            else filter==0 || when(filter) {
                1->track.source=="Wikimedia Commons"
                2->track.source=="Internet Archive"
                else->track.source=="Openverse"
            }
        }
        val ordered=if(popularOnly) Ranking.mostDownloaded(filtered) else filtered
        ordered.forEach { songCard(it) }
    }
    private fun switchRanking() {
        if(searchEdit.text.toString().isNotBlank() || popularOnly) {
            startSearch()
        } else {
            songs.clear();lastQuery="";render()
        }
    }
    private fun startSearch() {
        val term=searchEdit.text.toString().trim().ifBlank { if(popularOnly) "music" else "" }
        if(term.length<2) { toast("Digite pelo menos 2 caracteres");return }
        lastQuery=term
        serial++
        val request=serial
        completed=0;searching=true;songs.clear();errors.clear();render()
        val sources=if(popularOnly) {
            listOf(Pair("Internet Archive", { Search.archive(term,true) }))
        } else {
            listOf(
                Pair("Wikimedia", { Search.commons(term) }),
                Pair("Internet Archive", { Search.archive(term) }),
                Pair("Openverse", { Search.openverse(term) })
            )
        }
        sources.forEach { (name,call) ->
            workers.execute {
                val result=try { call() } catch(e:Exception) {
                    runOnUiThread { if(serial==request) {
                        errors.add("$name: indisponível");render()
                    }}
                    emptyList()
                }
                runOnUiThread {
                    if(serial!=request || isFinishing) return@runOnUiThread
                    songs.addAll(result)
                    completed++
                    searching=completed<sources.size
                    render()
                }
            }
        }
    }
    private fun songCard(t:Track) {
        val box=panel()
        val headingRow=horizontal().apply { gravity=Gravity.CENTER_VERTICAL }
        headingRow.addView(cover(t),LinearLayout.LayoutParams(dp(76),dp(76)).apply { marginEnd=dp(12) })
        val information=vertical()
        information.addView(text(t.title,16f,white,true).apply { maxLines=2 })
        tiny(information,t.artist+" • "+t.source)
        spaceIn(information,5)
        if(t.downloadsCount>=0 && t.source=="Internet Archive") {
            information.addView(text(formatDownloads(t.downloadsCount)+" downloads do item",11f,muted))
            spaceIn(information,3)
        }
        information.addView(text(t.license.ifBlank { "Verificar direitos" },11f,accent))
        headingRow.addView(information,LinearLayout.LayoutParams(0,-2,1f))
        box.addView(headingRow)
        spaceIn(box,11)
        val row=horizontal()
        row.addView(button("Ouvir",true) { preview(t) },weight())
        row.addView(button(if(library.favorite(t)) "♥" else "♡",false) {
            library.toggle(t);render()
        },weight())
        row.addView(button(if(t.downloadable) "Baixar" else "Origem",false) {
            if(t.downloadable) startDownload(t) else openPage(t.page)
        },weight())
        box.addView(row)
        spaceIn(box,7)
        val row2=horizontal()
        row2.addView(button("Página original",false) { openPage(t.page) },weight())
        row2.addView(button("Créditos",false) { copyCredits(t) },weight())
        box.addView(row2)
        content.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(9) })
    }
    private fun formatDownloads(count:Long)=NumberFormat.getIntegerInstance(Locale("pt","BR")).format(count)
    private fun cover(t:Track,local:Uri?=null)=ImageView(this).apply {
        scaleType=ImageView.ScaleType.CENTER_CROP
        background=rounded(panelAlt,11)
        clipToOutline=true
        ArtworkLoader.bind(this@MainActivity,this,t,local)
    }
    private fun renderLibrary() {
        heading("Minha biblioteca","Arquivos salvos e suas músicas favoritas.")
        content.addView(button("Importar áudio do celular",true) { importAudio() },
            LinearLayout.LayoutParams(-1,dp(46)))
        space(12)
        val saved=library.downloads()
        if(saved.isEmpty()) note("Sem downloads","Baixe arquivos autorizados ou importe áudios locais.")
        saved.forEach { s ->
            val box=panel()
            val itemRow=horizontal().apply { gravity=Gravity.CENTER_VERTICAL }
            val savedUri=downloads.uri(s)
            itemRow.addView(cover(s.track,savedUri),LinearLayout.LayoutParams(dp(64),dp(64)).apply { marginEnd=dp(12) })
            val details=vertical()
            details.addView(text(s.track.title,15f,white,true).apply { maxLines=2 })
            tiny(details,if(s.downloadId==0L) "Arquivo local" else when(downloads.status(s.downloadId)) {
                DownloadManager.STATUS_SUCCESSFUL->"Salvo para ouvir offline"
                DownloadManager.STATUS_RUNNING->"Baixando..."
                DownloadManager.STATUS_PENDING->"Aguardando download..."
                else->"Verificar download"
            })
            itemRow.addView(details,LinearLayout.LayoutParams(0,-2,1f))
            box.addView(itemRow)
            spaceIn(box,8)
            val row=horizontal()
            row.addView(button("Reproduzir",true) {
                val uri=downloads.uri(s)
                if(uri!=null) { player.open(s.track.title,uri);ArtworkLoader.bind(this,playerArtwork,s.track,uri) }
                else toast("Download indisponível")
            },weight())
            row.addView(button("Remover",false) { confirmDelete(s) },weight())
            box.addView(row)
            content.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
        space(10)
        content.addView(text("Favoritas",20f,white,true))
        space(10)
        val favs=library.favorites()
        if(favs.isEmpty()) note("Nenhuma favorita","Toque em ♡ para salvar uma música.")
        favs.forEach { songCard(it) }
    }
    private fun renderSources() {
        heading("Fontes musicais","Downloads variam conforme as licenças de cada obra.")
        note("Integradas","Wikimedia Commons, Internet Archive e Openverse. O Openverse abre o site de origem para downloads.")
        val sources=listOf(
            Triple("Wikimedia Commons","Domínio público e licenças abertas","https://commons.wikimedia.org/wiki/Category:Audio_files"),
            Triple("Internet Archive","Verifique gravação e composição","https://archive.org/details/audio"),
            Triple("Openverse","Agregador de conteúdo com licenças abertas","https://openverse.org/"),
            Triple("Free Music Archive","Termos por faixa","https://freemusicarchive.org/"),
            Triple("Jamendo","Download no serviço oficial","https://www.jamendo.com/"),
            Triple("Musopen","Música clássica; licenças variáveis","https://musopen.org/music/"),
            Triple("ccMixter","Músicas Creative Commons","https://ccmixter.org/"),
            Triple("FreePD","Músicas public domain","https://freepd.com/"),
            Triple("Pixabay Music","Licença própria; confira as condições","https://pixabay.com/music/"),
            Triple("Mixkit","Biblioteca de músicas gratuitas","https://mixkit.co/free-stock-music/"),
            Triple("Bandcamp","Conteúdo gratuito ou adquirido","https://bandcamp.com/"),
            Triple("SoundCloud","Somente download autorizado pelo artista","https://soundcloud.com/"),
            Triple("Spotify","Offline só dentro do serviço oficial","https://open.spotify.com/")
        )
        sources.forEach { (name,detail,url) ->
            val box=panel()
            box.addView(text(name,15f,white,true))
            tiny(box,detail)
            spaceIn(box,8)
            box.addView(button("Abrir fonte oficial ↗",false) { openPage(url) },
                LinearLayout.LayoutParams(-1,dp(40)))
            content.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
        note("Direitos autorais","Uso pessoal não autoriza automaticamente a cópia de qualquer música. O FreeSound não extrai DRM.")
    }
    private fun preview(t:Track) {
        val uri=when {
            t.local.isNotBlank()->Uri.parse(t.local)
            t.audio.startsWith("https://")->Uri.parse(t.audio)
            else->null
        }
        if(uri==null) toast("Abra o catálogo de origem para escutar") else {
            player.open(t.title,uri)
            ArtworkLoader.bind(this,playerArtwork,t)
        }
    }
    private fun startDownload(t:Track) {
        try {
            downloads.start(t)
            toast("Download iniciado; acompanhe pela Biblioteca")
        } catch(e:Exception) { toast(e.message ?: "Falha ao baixar") }
    }
    private fun confirmDelete(s:Saved) {
        AlertDialog.Builder(this).setTitle("Remover da biblioteca?")
            .setMessage(if(s.downloadId==0L) "O arquivo original será preservado."
                else "O download será removido do dispositivo.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Remover") { _,_->downloads.remove(s);render() }.show()
    }
    private fun importAudio() {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent,701)
    }
    @Deprecated("Android document picker")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=701 || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            var name="Música importada"
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                if(cursor.moveToFirst()) name=cursor.getString(0) ?: name
            }
            val t=Track("local:$uri",name,"Arquivo local","Arquivo local","","",
                "Licença do proprietário","",false,local=uri.toString())
            library.add(Saved(t,0L))
            tab=1;render()
        } catch(e:Exception) { toast("Falha na importação: "+e.message) }
    }
    private fun copyCredits(t:Track) {
        val data="${t.title} — ${t.artist}\nOrigem: ${t.page}\nLicença: ${t.license}\n${t.licenseUrl}"
        val clip=ClipData.newPlainText("Créditos",data)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        toast("Créditos copiados")
    }
    private fun openPage(link:String) {
        if(!link.startsWith("https://")) { toast("Link não disponível");return }
        try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link))) }
        catch (_:Exception) { toast("Navegador indisponível") }
    }
    private fun heading(title:String,description:String) {
        content.addView(text(title,23f,white,true))
        space(5)
        content.addView(text(description,12f,muted))
        space(17)
    }
    private fun note(title:String,detail:String) {
        val box=panel()
        box.addView(text(title,15f,accent,true))
        spaceIn(box,5)
        tiny(box,detail)
        content.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    private fun tiny(parent:LinearLayout,value:String) {
        parent.addView(text(value,12f,muted))
    }
    private fun panel()=vertical().apply {
        setPadding(dp(13),dp(13),dp(13),dp(13))
        background=rounded(panelColor,13)
    }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply {
        shape=GradientDrawable.RECTANGLE
        cornerRadius=dp(radius).toFloat()
        setColor(color)
    }
    private fun text(value:String,sp:Float,color:Int,bold:Boolean=false)=TextView(this).apply {
        text=value;textSize=sp;setTextColor(color)
        if(bold) setTypeface(typeface,Typeface.BOLD)
    }
    private fun button(value:String,primary:Boolean,onClick:()->Unit)=Button(this).apply {
        text=value;textSize=12f;isAllCaps=false
        setTextColor(if(primary) bg else white)
        setTypeface(typeface,Typeface.BOLD)
        background=rounded(if(primary) accent else panelAlt,10)
        minHeight=0;minWidth=0
        setPadding(dp(6),0,dp(6),0)
        setOnClickListener { onClick() }
    }
    private fun vertical()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun horizontal()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
    private fun weight()=LinearLayout.LayoutParams(0,dp(41),1f).apply { marginEnd=dp(5) }
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    private fun space(n:Int) { content.addView(View(this),LinearLayout.LayoutParams(1,dp(n))) }
    private fun spaceIn(parent:LinearLayout,n:Int) {
        parent.addView(View(this),LinearLayout.LayoutParams(1,dp(n)))
    }
    private fun time(ms:Int):String {
        val seconds=maxOf(0,ms)/1000
        return "%02d:%02d".format(seconds/60,seconds%60)
    }
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
}
