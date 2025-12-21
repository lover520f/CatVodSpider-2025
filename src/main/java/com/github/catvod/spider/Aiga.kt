package com.github.catvod.spider

import cn.hutool.crypto.digest.MD5
import com.github.catvod.bean.Result
import com.github.catvod.bean.Vod
import com.github.catvod.crawler.Spider
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Json
import org.jsoup.Jsoup

class Aiga : Spider() {
    private val host = "https://aigua.tv/"
    private val home = "/video/index"
    var a: String = "https://tvapi211.magicetech.com/"
    var b: String = "hr_1_1_0/apptvapi/web/index.php"

    private fun getWebHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0"
        headers["Accept"] =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
        headers["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6"
        headers["Connection"] = "keep-alive"
        headers["Cookie"] =
            "hs13_bk123=%7B%22count%22:%220%22,%22book%22:%5B%5D%7D; _ga=GA1.1.751942287.1759913099; currentcountry=1"
        headers["Cache-Control"] = "max-age=0"
        headers["Upgrade-Insecure-Requests"] = "1"
        headers["Sec-Fetch-Dest"] = "document"
        headers["Sec-Fetch-Mode"] = "navigate"
        headers["Sec-Fetch-Site"] = "none"
        headers["Sec-Fetch-User"] = "?1"
        headers["Sec-Ch-Ua"] = "\"Microsoft Edge\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\""
        headers["Sec-Ch-Ua-Mobile"] = "?0"
        headers["Sec-Ch-Ua-Platform"] = "\"Windows\""
        headers["Sec-Gpc"] = "1"
        headers["Dnt"] = "1"
        return headers
    }


    override fun homeContent(filter: Boolean): String {
        try {
            val doc = Jsoup.connect(host).headers(getWebHeaders()).get()
            val vodList = mutableListOf<Vod>()

            // 抓取推荐内容区域
            val videoElements = doc.select(".video-box-new") // 根据实际页面结构调整选择器

            videoElements.forEach { element ->
                val linkElement = element.select("a.Movie-name02").first() // 根据实际结构调整

                if (linkElement != null) {
                    val vod = Vod().apply {
                        this.setVodName(linkElement.text())
                        this.setVodId(linkElement.attr("href"))
                        this.setVodPic(element.select("img.Movie-img").attr("src"))
                    }
                    vodList.add(vod)
                }
            }

            return Result.string(vodList)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.error("获取主页内容失败")
        }
    }


    private fun commonParam(): Map<String, String> {
        var map: MutableMap<String, String> = Json.parseSafe<MutableMap<String, String>>(
            "{\n" +
                    "    \"debug\":\"1\",\n" +
                    "\"appId\":\"1\",\n" +
                    "\"osType\":\"3\",\n" +
                    "\"product\":\"4\",\n" +
                    "\"sysVer\":\"30\",\n" +
//                "\"token\": \"\",\n" +
                    "\"udid\":\"0A2233445566\",\n" +
                    "\"ver\":\"1.1.0\",\n" +
                    "\"packageName\":\"com.gzsptv.gztvvideo\",\n" +
                    "\"marketChannel\":\"tv\"" +
//                ",\n" +
//                "\"authcode\": \"\"\n" +
                    "\n" +
                    "}", MutableMap::class.java
        )
        map["time"] = (System.currentTimeMillis() / 1000).toString()
        getSign(map)
        return map
    }

    private fun getSign(map: MutableMap<String, String>): String {
        val mutableListOf = mutableListOf<String>()
        map.entries.forEach {
            mutableListOf.add("${it.key}=${it.value}")
        }
        mutableListOf.sort()
        val buildString = buildString {
            append("jI7POOBbmiUZ0lmi")
            mutableListOf.forEachIndexed { i, o ->
                if (i == 0) append(o)
                else append("&$o")
            }
            append("D9ShYdN51ksWptpkTu11yenAJu7Zu3cR")
        }
        val digestHex = MD5.create().digestHex(buildString)
        map["sign"] = digestHex
        return digestHex
    }

    override fun categoryContent(tid: String?, pg: String?, filter: Boolean, extend: HashMap<String, String>?): String {
        try {
            val page = pg?.toIntOrNull() ?: 1
            val categoryId = tid ?: "1" // 默认电影分类
            val url = "${host}video/channel?channel_id=$categoryId&page=$page"

            val html = OkHttp.string(url, getWebHeaders())
            val doc = Jsoup.parse(html)
            val vodList = mutableListOf<Vod>()

            // 根据实际页面结构调整选择器
            // 使用更准确的选择器匹配视频项
            val videoElements = doc.select(".Movie-list")

            videoElements.forEach { element ->
                // 获取视频链接元素
                val linkElement = element.select("a.Movie").first()

                // 获取视频标题（优先使用Movie-name01，备选Movie-name02）
                val titleElement = element.select(".Movie-name01").first()
                    ?: element.select("a.Movie-name02").first()

                // 获取图片
                val imgElement = element.select("img.Movie-img").first()

                if (linkElement != null && titleElement != null) {
                    val vod = Vod().apply {
                        this.setVodName(titleElement.text().trim())
                        this.setVodId(linkElement.attr("href"))
                        // 使用originalSrc属性获取高清图片
                        this.setVodPic(imgElement?.attr("originalSrc") ?: imgElement?.attr("src") ?: "")
                    }
                    vodList.add(vod)
                }
            }

            // 尝试从页面中解析总页数
            var pagecount = 10
            val pageElements = doc.select(".pagination a")
            if (pageElements.isNotEmpty()) {
                var maxPage = 0
                pageElements.forEach { element ->
                    val pageNum = element.text().toIntOrNull()
                    if (pageNum != null && pageNum > maxPage) {
                        maxPage = pageNum
                    }
                }
                if (maxPage > 0) {
                    pagecount = maxPage
                }
            }


            return Result.get().vod(vodList).page(page, pagecount, 20, pagecount * 20).string()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.error("获取分类内容失败: ${e.message}")
        }
    }


    override fun detailContent(ids: MutableList<String>?): String {
        return super.detailContent(ids)
    }

    override fun searchContent(key: String?, quick: Boolean): String {
        return super.searchContent(key, quick)
    }

    override fun playerContent(flag: String?, id: String?, vipFlags: MutableList<String>?): String {
        return super.playerContent(flag, id, vipFlags)
    }
}