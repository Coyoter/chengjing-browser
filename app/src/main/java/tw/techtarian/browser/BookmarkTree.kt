package tw.techtarian.browser

/** Presentation-only hierarchy: keeps all existing bookmark IDs, folder strings and sync data intact. */
class BookmarkTree(rows:List<Bookmark>) {
    class Node(val name:String,val path:List<String>){
        val children=linkedMapOf<String,Node>()
        val bookmarks=mutableListOf<Bookmark>()
        var total=0
    }
    val root=Node("書籤",emptyList())
    init{
        rows.forEach{bookmark->
            var node=root;node.total++
            for(part in bookmark.folder.split(" / ").filter{it.isNotEmpty()}){
                node=node.children.getOrPut(part){Node(part,node.path+part)};node.total++
            }
            node.bookmarks.add(bookmark)
        }
    }
    fun node(path:List<String>):Node?{
        var current=root
        for(part in path)current=current.children[part]?:return null
        return current
    }
}
