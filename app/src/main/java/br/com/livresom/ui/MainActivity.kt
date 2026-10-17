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
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import br.com.livresom.core.Downloads
import br.com.livresom.core.Categories
import br.com.livresom.core.DiscoveryPlan
import br.com.livresom.core.DiscoveryShelf
import br.com.livresom.core.ListeningHistory
import br.com.livresom.core.ListenSession
import br.com.livresom.core.Library
import br.com.livresom.core.Ranking
import br.com.livresom.core.Player
import br.com.livresom.core.Saved
import br.com.livresom.core.Search
import br.com.livresom.core.SearchOptions
import br.com.livresom.core.SearchFilter
import br.com.livresom.core.Track
import java.util.concurrent.Executors
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {
    // Steam-inspired navy storefront styling without Steam logos or artwork.
    private val bg=Color.rgb(23,26,33)
    private val panelColor=Color.rgb(27,40,56)
    private val panelAlt=Color.rgb(42,71,94)
    private val white=Color.rgb(236,243,249)
    private val muted=Color.rgb(164,184,199)
    private val accent=Color.rgb(102,192,244)
    private val green=Color.rgb(164,208,7)
    private val main=Handler(Looper.getMainLooper())
    private val workers=Executors.newFixedThreadPool(3)
    private lateinit var library:Library
    private lateinit var listening:ListeningHistory
    private var listenSession:ListenSession?=null
    private lateinit var downloads:Downloads
    private lateinit var player:Player
    private lateinit var content:LinearLayout
    private lateinit var contentScroll:ScrollView
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
    private var browseMode=0 // 0 = pesquisa, 1 = mais baixadas, 2 = mais ouvidas por você
    private var selectedCategory="all"
    private var onlyDownloads=false
    private var withCover=false
    private var exactMatch=true
    private var searchField=0
    private var sortMode=0
    private var filtersVisible=false
    private var searchHasRun=false

    // Home storefront: preserve fetched tracks across navigation, paginate lazily.
    private val homeCache=mutableMapOf<String,List<Track>>()
    private val homeLoading=mutableSetOf<String>()
    private val homeRows=mutableMapOf<String,LinearLayout>()
    private var homeHero:LinearLayout?=null
    private var homeFeatured:Track?=null
    private var homeNextIndex=0
    private var homeAppendPending=false
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
                val session=listenSession
                if(session!=null && session.sample(SystemClock.elapsedRealtime(),player.isPlaying(),player.duration())) {
                    listening.record(session.track)
                    if(tab==0 && browseMode==2) render()
                }
            }
            main.postDelayed(this,850)
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        library=Library(this)
        listening=ListeningHistory(this)
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
            setPadding(dp(17),dp(16),dp(17),dp(14))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(37,66,96),panelColor,bg))
        }
        val logo=text("♫",30f,white,true).apply {
            gravity=Gravity.CENTER
            background=rounded(panelAlt,7)
        }
        header.addView(logo,LinearLayout.LayoutParams(dp(53),dp(53)))
        val names=vertical().apply { setPadding(dp(12),0,0,0) }
        names.addView(text("FREESOUND",21f,white,true))
        names.addView(text("DESCUBRA • OUÇA • SALVE",10f,accent,true))
        header.addView(names,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header)

        searchRow=horizontal().apply { setPadding(dp(17),dp(8),dp(17),dp(9)) }
        searchEdit=EditText(this).apply {
            hint="Música, artista ou palavra-chave"
            setSingleLine(true);textSize=14f
            setTextColor(white);setHintTextColor(muted)
            setPadding(dp(12),0,dp(9),0)
            background=rounded(panelAlt,12)
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _,_,_-> startSearch();true }
        }
        searchRow.addView(searchEdit,LinearLayout.LayoutParams(0,dp(48),1f))
        searchRow.addView(button("BUSCAR",true) { startSearch() },
            LinearLayout.LayoutParams(dp(88),dp(48)).apply { leftMargin=dp(8) })
        root.addView(searchRow)

        contentScroll=ScrollView(this).apply {
            isFillViewport=false
            isVerticalScrollBarEnabled=false
        }
        content=vertical().apply { setPadding(dp(16),dp(5),dp(16),dp(24)) }
        contentScroll.addView(content)
        contentScroll.setOnScrollChangeListener { _,_,scrollY,_,_ ->
            if(isHomeActive() && content.height-(scrollY+contentScroll.height)<dp(650)) {
                // Post after layout; never queue repeated network requests here.
                if(!homeAppendPending && homeNextIndex<DiscoveryPlan.templateCount*6) {
                    homeAppendPending=true
                    main.postDelayed({
                        homeAppendPending=false
                        if(isHomeActive() && homeNextIndex<DiscoveryPlan.templateCount*6) {
                            appendHomeShelves(2)
                        }
                    },180L)
                }
            }
        }
        root.addView(contentScroll,LinearLayout.LayoutParams(-1,0,1f))

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
            setPadding(dp(12),dp(10),dp(12),dp(9))
            setBackgroundColor(panelColor)
        }
        root.addView(nav)
    }
    private fun isHomeActive():Boolean =
        tab==0 && browseMode==0 && !searchHasRun &&
            selectedCategory=="all" && searchEdit.text.toString().isBlank()

    private fun render() {
        homeRows.clear()
        homeHero=null
        content.removeAllViews();nav.removeAllViews()
        searchRow.visibility=if(tab==0 && browseMode!=2) View.VISIBLE else View.GONE
        listOf("⌂  Descobrir","♫  Biblioteca","◈  Fontes").forEachIndexed { i,name ->
            nav.addView(navButton(name,i==tab) {
                if(i==0) {
                    tab=0
                    browseMode=0
                    searchHasRun=false
                    selectedCategory="all"
                    searchEdit.setText("")
                    filter=0
                } else tab=i
                render()
                contentScroll.post { contentScroll.scrollTo(0,0) }
            },
                LinearLayout.LayoutParams(0,dp(47),1f).apply { marginEnd=dp(5) })
        }
        when(tab) {
            0->{
                if(isHomeActive()) renderHomeStore()
                else renderSearch()
            }
            1->renderLibrary()
            else->renderSources()
        }
    }
    /**
     * Storefront first, filters only on demand.
     * Real tracks are loaded from catalog APIs, not hardcoded examples.
     */
    private fun renderHomeStore() {
        homeNextIndex=0
        content.addView(text("DESCUBRA NO FREESOUND",11f,accent,true))
        space(5)
        content.addView(text("Sua vitrine musical",25f,white,true))
        space(5)
        content.addView(text("Capas, coleções e faixas livres para explorar.",12f,muted))
        space(14)

        val hero=vertical().apply {
            setPadding(dp(14),dp(14),dp(14),dp(16))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(37,75,109),Color.rgb(26,46,68),panelColor))
                .apply { cornerRadius=dp(11).toFloat() }
        }
        homeHero=hero
        showHomeHero(homeFeatured)
        content.addView(hero,LinearLayout.LayoutParams(-1,-2))
        space(15)

        val actions=horizontal()
        actions.addView(button("☷  Busca avançada",false) {
            searchHasRun=true
            browseMode=0
            render()
        },LinearLayout.LayoutParams(0,dp(43),1f).apply { marginEnd=dp(6) })
        actions.addView(button("↓  Mais baixadas",false) {
            browseMode=1
            requeryOrClear()
        },LinearLayout.LayoutParams(0,dp(43),1f))
        content.addView(actions)
        space(20)
        content.addView(text("EXPLORAR COLEÇÕES",18f,white,true))
        space(5)
        content.addView(text("Deslize os cards para os lados e role para descobrir mais.",12f,muted))
        space(10)
        appendHomeShelves(3)
    }

    private fun showHomeHero(featured:Track?) {
        val parent=homeHero ?: return
        parent.removeAllViews()
        parent.addView(text("EM DESTAQUE NO CATÁLOGO",10f,accent,true))
        spaceIn(parent,12)
        if(featured==null) {
            parent.addView(text("Músicas sendo carregadas…",19f,white,true))
            spaceIn(parent,7)
            parent.addView(text("Preparando uma faixa real dos catálogos conectados.",12f,muted))
            spaceIn(parent,18)
            parent.addView(text("WIKIMEDIA • ARCHIVE • OPENVERSE",10f,accent,true))
            return
        }
        val row=horizontal().apply { gravity=Gravity.CENTER_VERTICAL }
        row.addView(cover(featured),LinearLayout.LayoutParams(dp(126),dp(126)).apply {
            marginEnd=dp(13)
        })
        val details=vertical()
        details.addView(text(featured.title,18f,white,true).apply { maxLines=3 })
        spaceIn(details,7)
        details.addView(text(featured.artist,12f,white).apply { maxLines=2 })
        spaceIn(details,7)
        details.addView(text(featured.source,11f,accent,true))
        spaceIn(details,5)
        details.addView(text(
            if(featured.downloadable) "DOWNLOAD DISPONÍVEL" else "VERIFICAR NA ORIGEM",
            10f,if(featured.downloadable) green else muted,true))
        row.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        parent.addView(row)
        spaceIn(parent,12)
        val buttons=horizontal()
        buttons.addView(button("▶ OUVIR",true) { preview(featured) },
            LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(6) })
        buttons.addView(button(if(featured.downloadable) "↓ BAIXAR" else "↗ ORIGEM",false) {
            if(featured.downloadable) startDownload(featured) else openPage(featured.page)
        },LinearLayout.LayoutParams(0,dp(44),1f))
        parent.addView(buttons)
    }

    /** Append 2 curated rails when the user nears the bottom of the page. */
    private fun appendHomeShelves(count:Int) {
        if(!isHomeActive()) return
        repeat(count) {
            if(homeNextIndex>=DiscoveryPlan.templateCount*6) return@repeat
            val spec=DiscoveryPlan.shelf(homeNextIndex++)
            addHomeShelf(spec)
        }
        if(homeNextIndex==DiscoveryPlan.templateCount*6) {
            content.addView(text(
                "Fim das páginas disponíveis desta vitrine. Use a busca para descobrir outras músicas.",
                12f,muted))
        }
    }

    private fun addHomeShelf(spec:DiscoveryShelf) {
        val header=horizontal().apply { gravity=Gravity.CENTER_VERTICAL }
        val names=vertical()
        names.addView(text(spec.title,18f,white,true))
        names.addView(text(spec.description,11f,muted))
        header.addView(names,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("VER TUDO ›",false) { openHomeShelf(spec) },
            LinearLayout.LayoutParams(dp(95),dp(37)))
        content.addView(header)
        space(10)

        val carousel=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val rail=horizontal().apply {
            setPadding(0,0,dp(7),0)
        }
        carousel.addView(rail)
        content.addView(carousel,LinearLayout.LayoutParams(-1,-2))
        space(21)

        homeRows[spec.key]=rail
        val already=homeCache[spec.key]
        if(already!=null) fillHomeRail(rail,already)
        else {
            rail.addView(text("Buscando músicas e capas…",13f,muted).apply {
                gravity=Gravity.CENTER
                background=rounded(panelColor,8)
            },LinearLayout.LayoutParams(dp(200),dp(140)))
            if(homeLoading.add(spec.key)) {
                workers.execute {
                    val found=try {
                        val initial=try {
                            when(spec.catalog) {
                                "archive" -> Search.archive(spec.query,spec.popular,spec.page)
                                "commons" -> Search.commons(spec.query,spec.page)
                                else -> Search.openverse(spec.query,spec.page)
                            }
                        } catch (_:Exception) { emptyList() }
                        val fallback=if(initial.isEmpty() && !spec.popular) {
                            try {
                                if(spec.catalog=="openverse")
                                    Search.commons(spec.query,spec.page)
                                else Search.openverse(spec.query,spec.page)
                            } catch (_:Exception) { emptyList() }
                        } else emptyList()
                        (initial+fallback).distinctBy { it.id }.take(12)
                    } catch (_:Exception) { emptyList<Track>() }
                    main.post {
                        homeLoading.remove(spec.key)
                        homeCache[spec.key]=found
                        if(homeFeatured==null && found.isNotEmpty()) {
                            homeFeatured=found.first()
                            if(isHomeActive()) showHomeHero(homeFeatured)
                        }
                        if(isHomeActive()) homeRows[spec.key]?.let { fillHomeRail(it,found) }
                    }
                }
            }
        }
    }

    private fun fillHomeRail(rail:LinearLayout,tracks:List<Track>) {
        rail.removeAllViews()
        if(tracks.isEmpty()) {
            rail.addView(text("Sem músicas desta fonte nesta página.",12f,muted).apply {
                gravity=Gravity.CENTER
                background=rounded(panelColor,8)
            },LinearLayout.LayoutParams(dp(222),dp(118)))
            return
        }
        tracks.forEach { track ->
            val card=vertical().apply {
                background=rounded(panelColor,8)
                setPadding(dp(8),dp(8),dp(8),dp(10))
            }
            card.addView(cover(track),LinearLayout.LayoutParams(-1,dp(126)))
            spaceIn(card,9)
            card.addView(text(track.title,13f,white,true).apply {
                maxLines=2
                minLines=2
            })
            spaceIn(card,5)
            card.addView(text(track.artist,11f,muted).apply {
                maxLines=1
            })
            spaceIn(card,5)
            card.addView(text(track.source,10f,accent).apply { maxLines=1 })
            spaceIn(card,8)
            val buttons=horizontal()
            buttons.addView(button("▶",true) { preview(track) },
                LinearLayout.LayoutParams(dp(42),dp(39)).apply { marginEnd=dp(5) })
            buttons.addView(button(if(track.downloadable) "↓" else "↗",false) {
                if(track.downloadable) startDownload(track) else openPage(track.page)
            },LinearLayout.LayoutParams(0,dp(39),1f))
            card.addView(buttons)
            rail.addView(card,LinearLayout.LayoutParams(dp(157),-2).apply { marginEnd=dp(9) })
        }
    }

    private fun openHomeShelf(spec:DiscoveryShelf) {
        searchEdit.setText(spec.query)
        browseMode=0
        filter=when(spec.catalog) {
            "archive" -> 1
            "commons" -> 2
            else -> 3
        }
        selectedCategory="all"
        startSearch()
        contentScroll.post { contentScroll.scrollTo(0,0) }
    }

    private fun renderSearch() {
        if(browseMode!=2) {
            content.addView(button("← VOLTAR À VITRINE",false) {
                browseMode=0
                searchHasRun=false
                selectedCategory="all"
                searchEdit.setText("")
                render()
                contentScroll.post { contentScroll.scrollTo(0,0) }
            },LinearLayout.LayoutParams(-1,dp(41)))
            space(13)
        }
        content.addView(text("SUA CENTRAL DE MÚSICA",10f,accent,true))
        space(5)
        heading("Descubra algo novo","Encontre faixas com filtros mais precisos.")
        val modes=horizontal()
        listOf("EXPLORAR","MAIS BAIXADAS","SUAS OUVIDAS").forEachIndexed { i,label ->
            modes.addView(button(label,browseMode==i) { selectBrowseMode(i) },
                LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(5) })
        }
        content.addView(modes)
        space(15)
        if(browseMode==2) {
            sectionTitle("SUAS MAIS OUVIDAS","Ordenadas por reproduções neste celular")
            val items=listening.mostListened()
            if(items.isEmpty()) note("Sem histórico","Toque para ouvir uma faixa e complete ao menos 20 segundos.")
            items.forEach { songCard(it.track,it.plays) }
            return
        }
        if(browseMode==1) note("TOP DOWNLOADS · INTERNET ARCHIVE",
            "Contagem acumulada por item do Archive, não um ranking global de audições.")
        sectionTitle("NAVEGAR POR ESTILOS","A categoria complementa sua busca")
        val scroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        val categories=horizontal()
        (listOf(Pair("all","Todos"))+Categories.options.map { Pair(it.id,it.title) }).forEach { pair ->
            categories.addView(button(pair.second,selectedCategory==pair.first) {
                selectCategory(pair.first)
            },LinearLayout.LayoutParams(-2,dp(40)).apply { marginEnd=dp(7) })
        }
        scroll.addView(categories)
        content.addView(scroll)
        space(15)
        if(browseMode==0) {
            sectionTitle("FONTES","Busque em todos ou em um catálogo específico")
            val sources=horizontal()
            listOf("Todos","Archive","Commons","Openverse").forEachIndexed { i,name ->
                sources.addView(button(name,filter==i) {
                    if(filter!=i) { filter=i;requeryOrClear() }
                },LinearLayout.LayoutParams(0,dp(39),1f).apply { marginEnd=dp(4) })
            }
            content.addView(sources)
            space(10)
        }
        val quick=horizontal()
        quick.addView(button(if(onlyDownloads) "✓ Com download" else "↓ Só downloads",onlyDownloads) {
            onlyDownloads=!onlyDownloads;render()
        },LinearLayout.LayoutParams(0,dp(41),1f).apply { marginEnd=dp(5) })
        quick.addView(button(if(filtersVisible) "⌃ Ocultar filtros" else "☷ Mais filtros",filtersVisible) {
            filtersVisible=!filtersVisible;render()
        },LinearLayout.LayoutParams(0,dp(41),1f))
        content.addView(quick)
        if(filtersVisible) {
            space(9)
            val box=panel()
            box.addView(text("REFINAR BUSCA",12f,accent,true))
            spaceIn(box,9)
            box.addView(text("Pesquisar em",12f,muted))
            val fields=horizontal()
            listOf("Tudo","Título","Artista").forEachIndexed { i,label ->
                fields.addView(button(label,searchField==i) { searchField=i;render() },
                    LinearLayout.LayoutParams(0,dp(37),1f).apply { marginEnd=dp(4) })
            }
            box.addView(fields)
            spaceIn(box,9)
            box.addView(text("Ordenar resultados",12f,muted))
            val sorts=horizontal()
            listOf("Relevância","Downloads","A–Z").forEachIndexed { i,label ->
                sorts.addView(button(label,sortMode==i) { sortMode=i;render() },
                    LinearLayout.LayoutParams(0,dp(37),1f).apply { marginEnd=dp(4) })
            }
            box.addView(sorts)
            spaceIn(box,9)
            val choices=horizontal()
            choices.addView(button(if(exactMatch) "✓ Correspondências" else "Incluir relacionados",exactMatch) {
                exactMatch=!exactMatch;render()
            },LinearLayout.LayoutParams(0,dp(39),1f).apply { marginEnd=dp(5) })
            choices.addView(button(if(withCover) "✓ Com capa" else "Com capa",withCover) {
                withCover=!withCover;render()
            },LinearLayout.LayoutParams(0,dp(39),1f))
            box.addView(choices)
            spaceIn(box,9)
            box.addView(button("LIMPAR FILTROS",false) { clearFilters() },
                LinearLayout.LayoutParams(-1,dp(39)))
            content.addView(box)
        }
        space(16)
        val options=SearchOptions(
            words=searchEdit.text.toString(),
            source=if(browseMode==0) sourceName() else "Internet Archive",
            onlyDownloadable=onlyDownloads,onlyArtwork=withCover,
            field=searchField,strict=exactMatch,
            sort=if(browseMode==1 && sortMode==0) 1 else sortMode
        )
        val filtered=SearchFilter.results(songs,options)
        val count=if(searching) "Consultando catálogos…" else
            if(!searchHasRun) "Escolha uma categoria ou digite para pesquisar."
            else filtered.size.toString()+" de "+songs.size.toString()+" resultados"
        sectionTitle("RESULTADOS",count)
        if(searching) note("Buscando músicas","Algumas fontes respondem mais lentamente.")
        if(errors.isNotEmpty()) note("Fontes indisponíveis",errors.joinToString("; ").take(220))
        if(!searching && !searchHasRun) {
            val featured=panel()
            featured.addView(text("DESCUBRA SEU PRÓXIMO SOM",18f,white,true))
            spaceIn(featured,7)
            tiny(featured,"Explore por estilo, pesquise músicas ou filtre por disponibilidade de download.")
            spaceIn(featured,10)
            featured.addView(button("EXPLORAR INSTRUMENTAL",true) { selectCategory("instrumental") },
                LinearLayout.LayoutParams(-1,dp(45)))
            content.addView(featured)
        } else if(!searching && filtered.isEmpty()) {
            note("Nenhuma correspondência","Altere os filtros, a categoria ou a fonte.")
            if(exactMatch && searchEdit.text.isNotBlank()) {
                content.addView(button("MOSTRAR RESULTADOS RELACIONADOS",true) {
                    exactMatch=false;render()
                },LinearLayout.LayoutParams(-1,dp(44)))
            }
        }
        filtered.forEach { songCard(it) }
    }

    private fun sourceName():String=when(filter) {
        1 -> "Internet Archive"
        2 -> "Wikimedia Commons"
        3 -> "Openverse"
        else -> "Todas"
    }
    private fun sectionTitle(label:String,detail:String) {
        content.addView(text(label,14f,white,true))
        space(4)
        content.addView(text(detail,11f,muted))
        space(10)
    }
    private fun clearFilters() {
        onlyDownloads=false;withCover=false;exactMatch=true
        searchField=0;sortMode=0;filter=0;selectedCategory="all"
        requeryOrClear()
    }
    private fun selectBrowseMode(mode:Int) {
        if(mode==browseMode) return
        browseMode=mode
        if(mode==2) { serial++;searching=false;errors.clear();render() }
        else requeryOrClear()
    }
    private fun selectCategory(id:String) {
        if(selectedCategory==id) return
        selectedCategory=id
        requeryOrClear()
    }
    private fun requeryOrClear() {
        val q=Categories.queryFor(selectedCategory,searchEdit.text.toString())
        if(q.length>=2 || browseMode==1) startSearch()
        else {
            serial++;searching=false;searchHasRun=false
            songs.clear();errors.clear();render()
        }
    }
    private fun startSearch() {
        if(browseMode==2) { render();return }
        val combined=Categories.queryFor(selectedCategory,searchEdit.text.toString())
        val term=combined.ifBlank { if(browseMode==1) "music" else "" }
        if(term.length<2) { toast("Informe título, artista ou escolha um estilo");return }
        lastQuery=term;serial++
        val request=serial
        completed=0;searching=true;searchHasRun=true
        songs.clear();errors.clear();render()
        val sources=if(browseMode==1) listOf(Pair("Internet Archive",{ Search.archive(term,true) }))
        else listOf(
            Pair("Wikimedia Commons",{ Search.commons(term) }),
            Pair("Internet Archive",{ Search.archive(term) }),
            Pair("Openverse",{ Search.openverse(term) })
        ).filter { sourceName()=="Todas" || it.first==sourceName() }
        sources.forEach { (name,call) ->
            workers.execute {
                val result=try { call() } catch (_:Exception) {
                    runOnUiThread { if(serial==request) { errors.add(name);render() } }
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
    private fun songCard(t:Track,personalPlays:Int=0) {
        val box=panel()
        val headingRow=horizontal().apply { gravity=Gravity.CENTER_VERTICAL }
        headingRow.addView(cover(t),LinearLayout.LayoutParams(dp(90),dp(90)).apply { marginEnd=dp(12) })
        val information=vertical()
        information.addView(text(t.title,16f,white,true).apply { maxLines=2 })
        tiny(information,t.artist+"  ·  "+t.source)
        if(personalPlays>0) {
            spaceIn(information,4)
            information.addView(text("$personalPlays reproduções suas",11f,accent))
        }
        spaceIn(information,5)
        if(t.downloadsCount>=0 && t.source=="Internet Archive") {
            information.addView(text(formatDownloads(t.downloadsCount)+" downloads do item",11f,muted))
            spaceIn(information,3)
        }
        information.addView(text(
            if(t.downloadable) "↓  DOWNLOAD AUTORIZADO" else "↗  CONSULTAR NA ORIGEM",
            10f,if(t.downloadable) green else accent,true))
        information.addView(text(t.license.ifBlank { "Licença não verificada" },10f,muted))
        headingRow.addView(information,LinearLayout.LayoutParams(0,-2,1f))
        box.addView(headingRow)
        spaceIn(box,11)
        val row=horizontal()
        row.addView(button("▶  Ouvir",true) { preview(t) },weight())
        row.addView(button(if(library.favorite(t)) "♥" else "♡",false) {
            library.toggle(t);render()
        },weight())
        row.addView(button(if(t.downloadable) "↓ Baixar" else "↗ Origem",false) {
            if(t.downloadable) startDownload(t) else openPage(t.page)
        },weight())
        box.addView(row)
        spaceIn(box,7)
        val row2=horizontal()
        row2.addView(button("Página original",false) { openPage(t.page) },weight())
        row2.addView(button("Créditos",false) { copyCredits(t) },weight())
        box.addView(row2)
        content.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    private fun formatDownloads(count:Long)=NumberFormat.getIntegerInstance(Locale("pt","BR")).format(count)
    private fun cover(t:Track,local:Uri?=null)=ImageView(this).apply {
        scaleType=ImageView.ScaleType.CENTER_CROP
        background=rounded(panelAlt,11)
        clipToOutline=true
        ArtworkLoader.bind(this@MainActivity,this,t,local)
    }
    private fun renderLibrary() {
        heading("Sua biblioteca","Arquivos offline, músicas importadas e faixas favoritas.")
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
                if(uri!=null) playTrack(s.track,uri)
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
        heading("Fontes musicais","Catálogos originais e informações sobre as licenças.")
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
            playTrack(t,uri)
        }
    }
    private fun playTrack(t:Track,uri:Uri) {
        listenSession=ListenSession(t)
        player.open(t.title,uri)
        ArtworkLoader.bind(this,playerArtwork,t,
            if(uri.scheme=="content") uri else null)
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
        background=rounded(panelColor,7)
    }
    private fun navButton(label:String,selected:Boolean,onClick:()->Unit)=button(label,selected,onClick).apply {
        textSize=12f
        if(selected) {
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(62,116,149),panelAlt)).apply {
                cornerRadius=dp(7).toFloat()
            }
            setTextColor(white)
        }
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
        background=if(primary) GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(101,178,224),accent)).apply { cornerRadius=dp(7).toFloat() }
        else rounded(panelAlt,7)
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
