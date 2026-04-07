package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.util.*;

public class XuanFeng extends Spider {

    private static final String siteUrl = "https://miaotv.net/";
    private static final String apiRecent = "https://api.miaotv.net/api/recent";

    private HashMap<String, String> getHeaders() {
        return Util.webHeaders(siteUrl);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));

        // === 解析分类导航（静态 HTML，可靠）===
        for (Element li : doc.select("ul.navbar-nav > li.nav-item")) {
            if (li.hasClass("dropdown")) {
                // 处理下拉菜单
                for (Element a : li.select("ul.dropdown-menu a.dropdown-item")) {
                    String href = a.attr("href");
                    String text = a.text();
                    if (href.startsWith("/tag/")) {
                        classes.add(new Class(href, text));
                    }
                }
            } else {
                // 处理一级菜单（如首页）
                Element a = li.selectFirst("a.nav-link");
                if (a != null) {
                    String href = a.attr("href");
                    String text = a.text();
                    if ("/".equals(href)) {
                        classes.add(new Class("/", "首页"));
                    }
                }
            }
        }

        // === 首页推荐：改用 API 获取（更稳定）===
        try {
            Map<String, String> form = new HashMap<>();
            form.put("page", "1");
            form.put("type", ""); // 首页最近更新

            OkResult res = OkHttp.post(apiRecent, form, getHeaders());
            if (res.getCode() != 200) {
                System.out.println("Failed to fetch recent videos: " + res.getBody());
                return Result.string(classes, list);
            }
            if (res.getCode() == 200) {
                System.out.println("XuanFeng: " + res.getBody());
                List<Map<String, Object>> videos = Json.parseSafe(res.getBody(), List.class);
                if (videos != null) {
                    for (Map<String, Object> v : videos) {
                        String id = (String) v.get("ID");
                        String title = (String) v.get("Title");
                        String cover = Objects.toString(v.get("Cover"), "").trim();

                        if (id != null && !id.isEmpty() && title != null && !title.isEmpty() && !cover.isEmpty()) {
                            list.add(new Vod("/video/" + id, title, cover));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略 API 失败，不影响分类
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        Map<String, String> form = new HashMap<>();
        form.put("page", pg);
        form.put("type", tid);

        OkResult result = OkHttp.post(apiRecent, form, getHeaders());
        if (result.getCode() == 200) {
            List<Map<String, Object>> videos = Json.parseSafe(result.getBody(), List.class);
            if (videos != null) {
                for (Map<String, Object> video : videos) {
                    String id = (String) video.get("ID");
                    String title = (String) video.get("Title");
                    String cover = Objects.toString(video.get("Cover"), "").trim();
                    if (id != null && title != null && !cover.isEmpty()) {
                        list.add(new Vod("/video/" + id, title, cover));
                    }
                }
            }
        }

        // 总数未知，设为足够大
        return Result.string(Integer.parseInt(pg), Integer.parseInt(pg) + 1, list.size(), 999, list);
    }


    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = siteUrl + ids.get(0).substring(1); // 确保不 double slash
        System.out.println("XuanFeng: " + url);
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        // 修复选择器以匹配网站结构
        String name = doc.select("h1.h5.fw-bold").first() != null ?
                doc.select("h1.h5.fw-bold").first().ownText().trim() : "";
        String pic = doc.select("div.d-flex img, .cover-wrap img").attr("src").trim();
        String desc = doc.select("div.text-break, .description").text();

        // 提取年份信息
        String year = "";
        Element yearElement = doc.select("div.text-secondary.ft14").first();
        if (yearElement != null) {
            String yearText = yearElement.text();
            // 从时间信息中提取年份，如从"21987 次观看 · 时间：2025-11-22 12:32:44"中提取2025
            java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("(\\d{4})-");
            java.util.regex.Matcher matcher = yearPattern.matcher(yearText);
            if (matcher.find()) {
                year = matcher.group(1);
            }
        }

        // === 修复：正确提取多源播放列表 ===
        String jsonStr = Util.findByRegex("JSON\\.parse\\(\\\\?\"(.*?)\\\\?\"\\)", doc.html(), 1);
        if (jsonStr == null || jsonStr.isEmpty()) {
            // fallback：构造空源
            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            vod.setVodName(name);
            vod.setVodPic(pic);
            vod.setVodYear(year);
            vod.setVodContent(desc);
            return Result.string(vod);
        }

        // 修复转义
        jsonStr = jsonStr.replaceAll("\\\\u0022", "\"")
                .replaceAll("\\\\\\\\", "\\\\")
                .replaceAll("\\\\/", "/");

        Map<String, Object> data = Json.parseSafe(jsonStr, Map.class);
        List<Map<String, Object>> headers = (List<Map<String, Object>>) data.get("headers");
        // 修复：正确处理clips数据结构
        Object clipsObj = data.get("clips");
        List<List<List<String>>> allClips = null;

        if (clipsObj instanceof List<?>) {
            try {
                allClips = (List<List<List<String>>>) clipsObj;
            } catch (ClassCastException e) {
                // 处理不同类型结构
                System.out.println("Clips structure is different than expected");
            }
        }

        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        if (headers != null && allClips != null && headers.size() == allClips.size()) {
            for (int i = 0; i < headers.size(); i++) {
                String tabName = (String) headers.get(i).get("Name");
                List<List<String>> tabClips = allClips.get(i);

                StringBuilder tabUrl = new StringBuilder();
                for (List<String> clip : tabClips) {
                    if (tabUrl.length() > 0) tabUrl.append("#");
                    // 确保clip至少有两个元素
                    if (clip.size() >= 2) {
                        tabUrl.append(clip.get(0)).append("$").append(clip.get(1));
                    }
                }

                if (playFrom.length() > 0) {
                    playFrom.append("$$$");
                    playUrl.append("$$$");
                }
                playFrom.append(tabName);
                playUrl.append(tabUrl);
            }
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodYear(year);
        vod.setVodContent(desc);
        vod.setVodPlayFrom(playFrom.toString());
        vod.setVodPlayUrl(playUrl.toString());
        return Result.string(vod);
    }


    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String searchUrl = siteUrl + "search?q=" + URLEncoder.encode(key, "UTF-8");
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeaders()));

        for (Element item : doc.select("#container .col-md-2.col-6")) {
            try {
                Element a = item.selectFirst("div.cover-wrap a");
                Element img = item.selectFirst("img");
                Element title = item.selectFirst("h6");
                if (a != null && img != null && title != null) {
                    String url = a.attr("href");
                    String pic = img.attr("src").trim();
                    String name = title.text();
                    if (!url.isEmpty() && !name.isEmpty() && !pic.isEmpty()) {
                        list.add(new Vod(url, name, pic));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(id).header(getHeaders()).string();
    }
}