package br.com.livresom.core

/** Curated discovery queries. Titles are editorial sections, not hardcoded tracks. */
data class DiscoveryShelf(
    val key:String,
    val title:String,
    val description:String,
    val query:String,
    val catalog:String,
    val popular:Boolean,
    val page:Int
)

object DiscoveryPlan {
    private data class Template(
        val key:String,
        val title:String,
        val description:String,
        val query:String,
        val catalog:String,
        val popular:Boolean=false
    )
    private val templates=listOf(
        Template("popular","Mais baixadas","Itens populares do Internet Archive","music","archive",true),
        Template("instrumental","Instrumental para descobrir","Faixas e composições instrumentais","instrumental music","openverse"),
        Template("nature","Sons da natureza","Chuva, florestas e ambientes naturais","nature rain forest","commons"),
        Template("electronic","Eletrônica em destaque","Batidas e composições eletrônicas","electronic music","openverse"),
        Template("ambient","Para relaxar","Ambientes calmos e sons atmosféricos","ambient music","openverse"),
        Template("jazz","Jazz e blues","Uma seleção temática de jazz","jazz music","archive"),
        Template("classical","Clássica para ouvir","Obras e gravações clássicas","classical music","commons"),
        Template("lofi","Lo-fi e beats","Batidas suaves e experimentais","lofi beats","openverse"),
        Template("acoustic","Acústico e folk","Violão, instrumentos e folk","acoustic folk","archive"),
        Template("meditation","Meditação e foco","Música tranquila e paisagens sonoras","meditation music","openverse"),
        Template("rock","Rock independente","Rock disponível nos catálogos","rock music","commons"),
        Template("hiphop","Hip-hop instrumental","Ritmos instrumentais","hip hop instrumental","openverse"),
        Template("cinematic","Trilhas cinematográficas","Música para cenas e narrativas","cinematic soundtrack","archive"),
        Template("piano","Piano e teclas","Piano, solo e interpretações","piano music","commons")
    )

    val templateCount:Int get()=templates.size

    /** Page-aware rails, rather than cyclically repeating the same tracks. */
    fun shelf(index:Int):DiscoveryShelf {
        require(index>=0)
        val base=templates[index%templates.size]
        val page=index/templates.size+1
        val title=if(page==1) base.title else base.title+" · mais faixas"
        return DiscoveryShelf(base.key+":"+page,title,base.description,base.query,
            base.catalog,base.popular,page)
    }

    fun nextBatch(from:Int,count:Int):List<DiscoveryShelf> =
        (from until from+count).map(::shelf)

    fun query(shelf:DiscoveryShelf):String=shelf.query
}
