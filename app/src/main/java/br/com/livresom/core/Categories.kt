package br.com.livresom.core

/**
 * Discovery presets. Music catalogs do not publish a uniform verified genre
 * taxonomy; these are search keywords, not certified labels for every track.
 */
data class MusicCategory(val id:String,val title:String,val query:String)

object Categories {
    val options:List<MusicCategory> = listOf(
        MusicCategory("instrumental","Instrumental","instrumental music"),
        MusicCategory("eletronica","Eletrônica","electronic music"),
        MusicCategory("rock","Rock","rock music"),
        MusicCategory("jazz","Jazz","jazz music"),
        MusicCategory("classica","Clássica","classical music"),
        MusicCategory("hiphop","Hip-hop","hip hop instrumental"),
        MusicCategory("lofi","Lo-fi","lofi beats"),
        MusicCategory("ambiente","Ambiente","ambient soundscape"),
        MusicCategory("natureza","Natureza","nature rain forest"),
        MusicCategory("meditacao","Meditação","meditation relaxing music"),
        MusicCategory("pop","Pop","pop music"),
        MusicCategory("reggae","Reggae","reggae music"),
        MusicCategory("folk","Folk","folk acoustic music"),
        MusicCategory("trilhas","Trilhas","cinematic soundtrack")
    )
    fun byId(id:String):MusicCategory? = options.firstOrNull { it.id==id }
    fun queryFor(categoryId:String,typed:String):String {
        val term=typed.trim()
        val category=byId(categoryId)?.query.orEmpty()
        return listOf(category,term).filter { it.isNotBlank() }.joinToString(" ")
    }
}
