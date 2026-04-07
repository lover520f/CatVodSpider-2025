import com.github.catvod.spider.J91
import common.TestInterface
import org.junit.jupiter.api.Test

class J91Test : TestInterface<J91> {

    override var t: J91 = J91()
    override fun init() {
        t.init()
    }


    @Test
    override fun homeTest() {
        // 无
        val homeContent = t.homeContent(true)
        println(homeContent)
        assert(homeContent)
    }

    @Test
    override fun searchTest() {
        val searchContent = t.searchContent("阿凡达", false)
        println(searchContent)
        assert(searchContent)
    }

    @Test
    override fun cateTest() {
        val categoryContent = t.categoryContent("1", "1", false, hashMapOf())
        assert(categoryContent)
    }

    @Test
    override fun detailTest() {
        val detailContent = t.detailContent(listOf("527835"))
//        val detailContent = t.detailContent(listOf("143878"))
        assert(detailContent)
    }

    @Test
    override fun playTest() {
        val playerContent = t.playerContent("", "143878/1/45", listOf())
        assert(playerContent)
    }
}