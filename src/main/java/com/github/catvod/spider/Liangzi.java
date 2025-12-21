package com.github.catvod.spider;

import cn.hutool.core.util.URLUtil;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Liangzi extends Spider {

    private final String siteUrl = "https://lzi888.com";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
//        header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        return Util.webHeaders("");
//        return header;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {

        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        List<String> typeNames = Arrays.asList("电影", "连续剧", "动漫", "综艺", "电影解说", "体育");
        List<String> typeIds = Arrays.asList("1", "2", "3", "4", "39", "40");
        for (int i = 0; i < typeIds.size(); i++) {
            classes.add(new Class(typeIds.get(i), typeNames.get(i)));
            //  filters.put(typeIds.get(i), Arrays.asList(new Filter("filters", "過濾", Arrays.asList(new Filter.Value("全部", ""), new Filter.Value("單人作品", "individual"), new Filter.Value("中文字幕", "chinese-subtitle")))));

        }
        List<Vod> list = getVodList(doc);
        SpiderDebug.log("++++++++++++量子-homeContent" + Json.toJson(list));
        return Result.string(classes, list);
    }

    private @NotNull List<Vod> getVodList(Document doc) {
        List<Vod> list = new ArrayList<>();

        // 优先处理视频列表区域，而非轮播图
        for (Element div : doc.select(".module-item")) {
            String id = siteUrl + div.attr("href");
            String name = div.select(".module-item-pic > img").attr("alt");
            // 优先使用 data-original 属性获取封面图
            String pic = div.select(".module-item-pic > img").attr("data-original");
            if (pic.isEmpty()) {
                pic = div.select(".module-item-pic > img").attr("src");
            }
            if (pic.isEmpty()) {
                pic = div.select("img").attr("src");
            }
            String remark = div.select(".module-item-note").text();

            list.add(new Vod(id, name, pic, remark));
        }

        // 如果没有找到 .module-item 元素，再尝试处理轮播图（作为备选）
        if (list.isEmpty()) {
            for (Element div : doc.select(".swiper-slide")) {
                Element linkElement = div.select("a.banner").first();
                if (linkElement != null) {
                    String id = siteUrl + linkElement.attr("href");
                    String name = div.select(".mobile-v-info .v-title span").text();
                    // 背景图片在style属性中
                    String style = linkElement.attr("style");
                    String pic = "";
                    if (style.contains("url(")) {
                        int start = style.indexOf("url(") + 4;
                        int end = style.indexOf(")", start);
                        pic = style.substring(start, end);
                        // 处理可能存在的引号
                        if (pic.startsWith("'") || pic.startsWith("\"")) {
                            pic = pic.substring(1, pic.length() - 1);
                        }
                    }
                    String remark = div.select(".mobile-v-info .v-ins p").first().text();

                    list.add(new Vod(id, name, pic, remark));
                }
            }
        }

        return list;
    }



    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String target = siteUrl + "/index.php/vod/show/id/" + tid + "/page/" + pg + ".html";
        //String filters = extend.get("filters");
        String html = OkHttp.string(target);
        Document doc = Jsoup.parse(html);
        List<Vod> list = getVodList(doc);
        String total = "" + Integer.MAX_VALUE;
        for (Element element : doc.select("script")) {
            if (element.data().contains("mac_total")) {
                total = element.data().split("'")[1];
            }
        }

        SpiderDebug.log("++++++++++++量子-categoryContent" + Json.toJson(list));
        return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(total) / 72 + ((Integer.parseInt(total) % 72) > 0 ? 1 : 0), 72, Integer.parseInt(total)).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        SpiderDebug.log("++++++++++++量子-detailContent--args" + Json.toJson(ids));
        Document doc = Jsoup.parse(OkHttp.string(ids.get(0), getHeader()));

        String title = doc.select("div.module-info-heading h1").text();
        String year = doc.select("div.module-info-tag-link").first().text();
        String area = doc.select("div.module-info-tag-link").eq(1).text();
        String director = doc.select("div.module-info-item:contains(导演：) a").text();
        String actor = doc.select("div.module-info-item:contains(主演：) a").text();
        String brief = doc.select("div.module-info-introduction-content p").text();
        String pic = doc.select("div.module-item-pic img").attr("data-original");

        Elements circuits = doc.select(".module-tab-item.tab-item");
        Elements sources = doc.select(".module-list, .module-blocklist, .module-player .scroll-content, .scroll-content");

        StringBuilder vod_play_url = new StringBuilder();
        StringBuilder vod_play_from = new StringBuilder();

        for (int i = 0; i < sources.size(); i++) {
            String spanText = circuits.get(i).select("span").text();
            String smallText = circuits.get(i).select("small").text();
            String playFromText = spanText + "(共" + smallText + "集)";
            vod_play_from.append(playFromText).append("$$$");

            Elements aElementArray = sources.get(i).select("a");
            for (int j = 0; j < aElementArray.size(); j++) {
                Element a = aElementArray.get(j);
                String href = siteUrl + a.attr("href");
                String text = a.text();
                vod_play_url.append(text).append("$").append(href);
                boolean notLastEpisode = j < aElementArray.size() - 1;
                vod_play_url.append(notLastEpisode ? "#" : "$$$");
            }
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodYear(year);
        vod.setVodName(title);
        vod.setVodArea(area);
        vod.setVodActor(actor);
        vod.setVodPic(pic);
        vod.setVodContent(brief);
        vod.setVodDirector(director);
        vod.setVodPlayFrom(vod_play_from.toString());
        vod.setVodPlayUrl(vod_play_url.toString());

        SpiderDebug.log("++++++++++++量子-detailContent" + Json.toJson(vod));
        return Result.string(vod);
    }


    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = siteUrl + "/index.php/vod/search.html?wd=";
        String html = OkHttp.string(searchUrl + key);
        Document document = Jsoup.parse(html);
        List<Vod> list = new ArrayList<>();

        // 使用新的选择器匹配搜索结果项
        for (Element div : document.select(".module-card-item")) {
            String vodId = siteUrl + div.select("a").first().attr("href");
            String name = div.select(".module-card-item-title a").text();
            String remark = div.select(".module-item-note").text();
            String pic = div.select(".module-item-pic img").attr("data-original");

            // 如果data-original为空，尝试其他属性
            if (pic.isEmpty()) {
                pic = div.select(".module-item-pic img").attr("src");
            }

            list.add(new Vod(vodId, name, pic, remark));
        }

        SpiderDebug.log("++++++++++++量子-searchContent" + Json.toJson(list));
        return Result.string(list);
    }


    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String content = OkHttp.string(id, getHeader());
        Matcher matcher = Pattern.compile("player_aaaa=(.*?)</script>").matcher(content);
        String json = matcher.find() ? matcher.group(1) : "";
        JSONObject player = new JSONObject(json);
        String realUrl = player.getString("url");
        SpiderDebug.log("++++++++++++量子-playerContent" + Json.toJson(realUrl));
        if(!realUrl.contains("m3u8")){
            String videoContent = OkHttp.string(realUrl, getHeader());
            Matcher mainMatcher = Pattern.compile("var main = \"(.*?)\";").matcher(videoContent);
            String mainUrl = mainMatcher.find() ? mainMatcher.group(1) : "";
            realUrl= URLUtil.getHost(new URL(realUrl)).toString()+mainUrl;
        }
        return Result.get().url(realUrl).header(getHeader()).string();
    }

}
