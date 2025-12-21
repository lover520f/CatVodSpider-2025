package com.github.catvod.spider

import com.github.catvod.bean.Result
import com.github.catvod.bean.Vod
import com.github.catvod.crawler.Spider
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Json
import com.github.catvod.utils.Util
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * 电影云集 (Compose-Multiplatform版本)
 *
 * @author lushunming
 * @createdate 2024-12-03
 */
class ReBoYingShi : Cloud() {
    private val siteUrl = "https://reboys.cn"

    private val headerWithCookie: MutableMap<String?, String?>
        get() {
            val header: MutableMap<String?, String?> = HashMap<String?, String?>()
            header["User-Agent"] =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0"
            header["Accept"] =
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
            header["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6"
            header["Connection"] = "keep-alive"
            header["Upgrade-Insecure-Requests"] = "1"
            header["Sec-Fetch-Dest"] = "document"
            header["Sec-Fetch-Mode"] = "navigate"
            header["Sec-Fetch-Site"] = "none"
            header["Sec-Fetch-User"] = "?1"
            header["Sec-Ch-Ua"] = "\"Microsoft Edge\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\""
            header["Sec-Ch-Ua-Mobile"] = "?0"
            header["Sec-Ch-Ua-Platform"] = "\"Windows\""
            header["Sec-Gpc"] = "1"
            header["Dnt"] = "1"
            header["Priority"] = "u=0, i"
            return header
        }


    @Throws(Exception::class)
    override fun init(extend: String?) {
        super.init(extend)
    }

    @Throws(Exception::class)
    override fun homeContent(filter: Boolean): String {
        val homeUrl = siteUrl
        val html = OkHttp.string(homeUrl, headerWithCookie)
        val document = org.jsoup.Jsoup.parse(html)

        // 提取分类 - 使用更精确的选择器
        val classes = mutableListOf<com.github.catvod.bean.Class>()
        val navItems = document.select(".nav > div.nav-item") // 更精确的选择器
        navItems.forEachIndexed { index, element ->
            val typeName = element.text().trim()
            val typeId = (index + 1).toString()
            if (typeName.isNotEmpty()) { // 过滤空名称
                classes.add(com.github.catvod.bean.Class(typeId, typeName))
            }
        }

        // 提取推荐视频 - 从所有block中提取
        val vodList = mutableListOf<Vod>()
        val blocks = document.select(".block")
        blocks.forEach { block ->
            val items = block.select(".list .item")
            items.forEach { item ->
                try {
                    val linkElement = item.attr("href")
                    val titleElement = item.select("p")
                    val imgElement = item.select(".img img")

                    if (linkElement.isNotEmpty() && titleElement.isNotEmpty()) {
                        val vodId = linkElement
                        val title = titleElement.first()?.text()?.trim() ?: ""
                        val imgUrl = if (imgElement.isNotEmpty()) imgElement.first()?.attr("src") ?: "" else ""
                        if (title.isNotEmpty() && vodId.isNotEmpty()) { // 过滤无效数据
                            vodList.add(Vod(vodId, title, imgUrl, ""))
                        }
                    }
                } catch (e: Exception) {
                    // 忽略单个解析错误
                }
            }
        }

        val result = com.github.catvod.bean.Result()
        result.classes(classes)
        result.vod(vodList)
        return result.string()
    }


    @Throws(Exception::class)
    override fun searchContent(key: String?, quick: Boolean): String? {
        return doSearchContent(key, "1")
    }

    @Throws(Exception::class)
    override fun searchContent(key: String?, quick: Boolean, pg: String?): String? {
        return doSearchContent(key, pg)
    }

    override fun detailContent(ids: MutableList<String>): String? {
        val id = ids[0]
        val url = if (id.startsWith("http")) id else "$siteUrl$id"

        try {
            // 首先从搜索接口获取真实资源链接
            val title = extractTitleFromId(id) // 从ID中提取标题
            val shareLinks = getRealShareLinks(title)

            val html = OkHttp.string(url, headerWithCookie)
            val document = org.jsoup.Jsoup.parse(html)

            // 提取基本信息
            val pageTitle = document.select("meta[property='og:title']").attr("content")
                .ifEmpty { document.select("title").text() }
            val pic = document.select("meta[property='og:image']").attr("content")
            val description = document.select("meta[property='og:description']").attr("content")
                .ifEmpty { document.select(".video-info-content").text() }

            // 使用标准构造函数创建 Vod 对象
            val vod = Vod(id, pageTitle.ifEmpty { "未知影片" }, pic, description)

            // 如果找到了真实网盘链接，则设置播放源信息
            if (shareLinks.isNotEmpty()) {
                println("shareLinks: $shareLinks")
                // 调用父类 Cloud 的方法处理网盘链接
                val playFrom = detailContentVodPlayFrom(shareLinks)
                val playUrl = detailContentVodPlayUrl(shareLinks)

                vod.setVodPlayFrom(playFrom)
                vod.setVodPlayUrl(playUrl)
                return Result.string(vod)
            }

            return Result.string(vod)
        } catch (e: Exception) {
            e.printStackTrace()
            // 出错时返回基础信息
            val vod = Vod(id, "未知影片", "", "")
            return Result.string(vod)
        }
    }

    // 从ID中提取搜索关键词
    private fun extractTitleFromId(id: String): String {
        // 例如从 "/s/掌心窥爱.html" 提取 "掌心窥爱"
        val regex = Regex("/s/(.+?)\\.html")
        val matchResult = regex.find(id)
        return if (matchResult != null) {
            matchResult.groupValues[1]
        } else {
            ""
        }
    }

    // 通过搜索接口获取真实资源链接（只获取第一个有效链接）
    private fun getRealShareLinks(keyword: String): List<String> {
        val shareLinks = mutableListOf<String>()

        try {
            val searchPageURL = "$siteUrl/s/${URLEncoder.encode(keyword, Charset.defaultCharset().name())}.html"
            val html = OkHttp.string(searchPageURL, headerWithCookie)
            val apiToken = Util.findByRegex("const apiToken = \"(.*?)\";", html, 1)

            val searchURL = "$siteUrl/search?keyword=${URLEncoder.encode(keyword, Charset.defaultCharset().name())}"
            val header = headerWithCookie.toMutableMap()
            header["API-TOKEN"] = apiToken

            val json = OkHttp.string(searchURL, header)
            val jsonObj = Json.safeObject(json)

            if (jsonObj.get("code").asInt == 0) {
                val results = jsonObj.get("data").asJsonObject.get("data").asJsonObject.get("results").asJsonArray
                // 只遍历直到找到第一个有效链接为止
                outerLoop@ for (item in results) {
                    val links = item.asJsonObject.get("links").asJsonArray
                    for (link in links) {
                        val url = link.asJsonObject.get("url").asString
                        if (url.isNotEmpty() && isValidResourceLink(url)) {
                            shareLinks.add(url)
                            break@outerLoop // 找到第一个有效链接后立即退出
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return shareLinks
    }


    // 判断是否为有效资源链接
    private fun isValidResourceLink(link: String): Boolean {
        // 排除已知的推广链接ID
        val promotionIds = listOf("5997998f2ac7")

        promotionIds.forEach { id ->
            if (link.contains(id)) {
                return false
            }
        }

        // 确保链接是有效的网盘链接格式
        return link.contains("pan.quark.cn/s/") ||
                link.contains("drive.uc.cn/s/") ||
                link.contains("alipan.com/s/")
    }


    private fun doSearchContent(key: String?, pg: String?): String? {
        val searchPageURL = siteUrl + "/s/${URLEncoder.encode(key, Charset.defaultCharset().name())}.html"
        val html = OkHttp.string(searchPageURL, this.headerWithCookie)
        val apiToken = Util.findByRegex("const apiToken = \"(.*?)\";", html, 1)

        val searchURL = siteUrl + "/search?keyword=${URLEncoder.encode(key, Charset.defaultCharset().name())}"
        val header = headerWithCookie.toMutableMap()
        header["API-TOKEN"] = apiToken
        val json = OkHttp.string(searchURL, header)
        val jsonObj = Json.safeObject(json)
        var vodList = emptyList<Vod>()
        if (jsonObj.get("code").asInt == 0) {
            val results = jsonObj.get("data").asJsonObject.get("data").asJsonObject.get("results").asJsonArray
            vodList = results.map {
                val title = it.asJsonObject.get("title").asString
                val vodId = it.asJsonObject.get("links").asJsonArray[0].asJsonObject.get("url").asString
                Vod(vodId, title, "", "")
            }
        }
        return Result.string(vodList)
    }


}