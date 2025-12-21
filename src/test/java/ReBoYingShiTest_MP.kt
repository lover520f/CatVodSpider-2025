import com.github.catvod.spider.ReBoYingShi
import com.github.catvod.utils.Json
import com.google.gson.GsonBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReBoYingShiTest_MP {

    private lateinit var spider: ReBoYingShi

    @BeforeEach
    @Throws(Exception::class)
    fun setUp() {
        spider = ReBoYingShi()
        spider.init("")
    }

    @Test
    @Throws(Exception::class)
    fun homeContent() {
        val content = spider.homeContent(true)
        val map = Json.safeObject(content)
        val gson = GsonBuilder().setPrettyPrinting().create()

        println("homeContent--" + gson.toJson(map))
    }

    @Test
    @Throws(Exception::class)
    fun homeVideoContent() {
        val content = spider.homeVideoContent()
        val map = Json.safeObject(content)
        val gson = GsonBuilder().setPrettyPrinting().create()

        println("homeVideoContent--" + gson.toJson(map))
    }

    @Test
    @Throws(Exception::class)
    fun categoryContent() {
        val content = spider.categoryContent("https://dyyjpro.com/category/dianying", "2", true, null)
        val map = Json.safeObject(content)
        val gson = GsonBuilder().setPrettyPrinting().create()
        println("categoryContent--" + gson.toJson(map))
    }

    @Test
    @Throws(Exception::class)
    fun detailContent() {
        val content = spider.detailContent(mutableListOf("/s/掌心窥爱.html"))
        println("detailContent--" + content)

        val map = Json.safeObject(content)
        val gson = GsonBuilder().setPrettyPrinting().create()
        println("detailContent--" + gson.toJson(map))
    }

    @Test
    @Throws(Exception::class)
    fun playerContent() {
        val content = spider.playerContent(
            "quark4K",
            "81c9aa49887d4b07aba861d7dd76d0ac++0ec2d75805f83bd045434f0d22f71489++4be1d75e17aa++wGlrbmw95nBbzO2rbCcEicZ8f4a+z5aKiuyoLQLA5SQ=",
            ArrayList<String?>()
        )
        println("playerContent--" + content)
        val map = Json.safeObject(content)
        val gson = GsonBuilder().setPrettyPrinting().create()
        println("playerContent--" + gson.toJson(map))
    }

    @Test
    @Throws(Exception::class)
    fun searchContent() {
        val content = spider.searchContent("屠户之子的科举之路", false)
        val map = Json.safeObject(content)
        val gson = GsonBuilder().setPrettyPrinting().create()
        println("searchContent--" + gson.toJson(map))
    }
}