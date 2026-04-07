package com.github.catvod.spider;

import cn.hutool.core.text.UnicodeUtil;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.AESEncryption;
import com.github.catvod.utils.Util;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NCat extends Spider {

    private static final String siteUrl = "https://www.ncat3.app";
    private static final String picUrl = "https://vres.cfaqcgj.com";
    private static final String cateUrl = siteUrl + "/show/";
    private static final String detailUrl = siteUrl + "/detail/";
    private static final String searchUrl = siteUrl + "/search?os=pc&k=";
    private static final String playUrl = siteUrl + "/play/";
    // 缓存变量
    private String cachedPrefix = null;
    private int[] cachedByteConditions = null;
    private String cachedCookieValue = null;
    private String cachedPrefixSource = null; // 缓存前缀来源用于验证
    private long cacheTimestamp = 0;
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30分钟缓存有效期

    private void clearCache() {
        cachedPrefix = null;
        cachedByteConditions = null;
        cachedCookieValue = null;
        cachedPrefixSource = null;
        cacheTimestamp = 0;
    }

    private String getPrefixFromSite() {
        // 检查缓存是否有效
        if (cachedPrefix != null &&
                (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION) {
            return cachedPrefix;
        }

        try {
            String response = OkHttp.string(siteUrl, Util.webHeaders(siteUrl, siteUrl, ""));
            Document doc = Jsoup.parse(response);

            for (Element script : doc.select("script")) {
                String scriptContent = script.html();

                // 匹配混淆数组定义 - 使用通用变量名模式
                Pattern arrayPattern = Pattern.compile("const\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*\\[([\\s\\S]*?)\\];");
                Matcher arrayMatcher = arrayPattern.matcher(scriptContent);

                if (arrayMatcher.find()) {
                    String arrayContent = arrayMatcher.group(2);
                    String[] elements = arrayContent.split(",");
                    if (elements.length > 0) {
                        String firstElement = elements[0].trim();
                        firstElement = firstElement.replaceAll("['\"]", "");
                        if (firstElement.matches("[A-F0-9]{40}")) { // 验证是否为40位十六进制
                            // 更新缓存
                            cachedPrefix = firstElement;
                            cachedPrefixSource = firstElement; // 保存前缀用于后续验证
                            cacheTimestamp = System.currentTimeMillis();
                            return firstElement;
                        }
                    }
                }
                clearCache();
            }
        } catch (Exception e) {
            SpiderDebug.log("获取前缀失败: " + e.getMessage());
            clearCache();
        }

        // 如果获取失败，返回默认前缀但不更新缓存时间，允许稍后重试
        return "ED1F0FF4797D8D35685292503184768EB4E45E6E"; // 默认前缀
    }


    private int[] getByteConditionsFromSite() {
        // 检查缓存是否有效且前缀未发生变化
        if (cachedByteConditions != null &&
                cachedPrefixSource != null &&
                (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION) {
            return cachedByteConditions;
        }

        try {
            String response = OkHttp.string(siteUrl, Util.webHeaders(siteUrl, siteUrl, ""));
            Document doc = Jsoup.parse(response);

            for (Element script : doc.select("script")) {
                String scriptContent = script.html();

                // 尝试匹配多种字节校验模式
                Pattern[] patterns = {
                        // 匹配 s[n1]===0xb0&&s[n1+0x1]===0xb 模式
                        Pattern.compile("s\\[n1\\]\\s*===\\s*0x([0-9a-fA-F]+)\\s*&&\\s*s\\[n1\\+0x1\\]\\s*===\\s*0x([0-9a-fA-F]+)"),
                        // 匹配 s[pos1]===0xb0&&s[pos2]===0xb 模式
                        Pattern.compile("s\\[(\\d+)\\]\\s*===\\s*0x([0-9a-fA-F]+)\\s*&&\\s*s\\[(\\d+)\\]\\s*===\\s*0x([0-9a-fA-F]+)"),
                        // 匹配其他可能的模式
                        Pattern.compile("s\\[([a-zA-Z_][a-zA-Z0-9_]*)\\]\\s*===\\s*0x([0-9a-fA-F]+)\\s*&&\\s*s\\[\\1\\+0x1\\]\\s*===\\s*0x([0-9a-fA-F]+)")
                };

                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(scriptContent);
                    if (matcher.find()) {
                        if (matcher.groupCount() == 2) {
                            // 匹配到 s[n1] 模式
                            int val1 = Integer.parseInt(matcher.group(1), 16);
                            int val2 = Integer.parseInt(matcher.group(2), 16);

                            String prefix = getPrefixFromSite();
                            String firstChar = prefix.substring(0, 1);
                            int n1 = Integer.parseInt(firstChar, 16);

                            // 更新缓存
                            cachedByteConditions = new int[]{n1, val1, n1 + 1, val2};
                            cacheTimestamp = System.currentTimeMillis();
                            return cachedByteConditions;
                        } else if (matcher.groupCount() == 3) {
                            // 匹配到 s[pos1] s[pos2] 模式
                            int pos1 = Integer.parseInt(matcher.group(1));
                            int val1 = Integer.parseInt(matcher.group(2), 16);
                            int val2 = Integer.parseInt(matcher.group(3), 16);

                            // 更新缓存
                            cachedByteConditions = new int[]{pos1, val1, pos1 + 1, val2};
                            cacheTimestamp = System.currentTimeMillis();
                            return cachedByteConditions;
                        }
                    }
                }
            }
            clearCache();
        } catch (Exception e) {
            SpiderDebug.log("获取字节检查条件失败: " + e.getMessage());
            clearCache();
        }

        return null;
    }

    private String generateCdndefendCookie(String prefix) {
        // 如果前缀相同且缓存有效，直接返回缓存的cookie
        if (cachedCookieValue != null &&
                cachedPrefix != null &&
                cachedPrefix.equals(prefix) &&
                (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION) {
            return cachedCookieValue;
        }

        try {
            int[] byteConditions = getByteConditionsFromSite();

            if (byteConditions == null) {
                // 根据HTML中的默认值
                String firstChar = prefix.substring(0, 1);
                int n1 = Integer.parseInt("0x" + firstChar, 16); // 从前缀第一个字符计算
                byteConditions = new int[]{n1, 0xB0, n1 + 1, 0x0B};
            }

            int i = 0;
            while (true) {
                String candidate = prefix + i;
                byte[] sha1Hash = java.security.MessageDigest.getInstance("SHA-1")
                        .digest(candidate.getBytes("UTF-8"));

                // 使用动态获取的字节位置和值进行检查
                if ((sha1Hash[byteConditions[0]] & 0xFF) == byteConditions[1] &&
                        (sha1Hash[byteConditions[2]] & 0xFF) == byteConditions[3]) {
                    SpiderDebug.log("成功生成cookie: " + candidate);

                    // 更新缓存
                    cachedCookieValue = candidate;
                    cachedPrefix = prefix;
                    cacheTimestamp = System.currentTimeMillis();

                    return candidate;
                }

                // 限制尝试次数避免无限循环
                if (i > 1000000) {
                    SpiderDebug.log("尝试次数过多，生成cookie失败");
                    clearCache();
                    break;
                }
                i++;
            }
        } catch (Exception e) {
            SpiderDebug.log("生成cookie失败: " + e.getMessage());
            e.printStackTrace();
            clearCache();
        }
        return null;
    }


    private HashMap<String, String> getHeaders() {
        HashMap<String, String> map = Util.webHeaders(siteUrl, siteUrl, "");
        // 动态获取前缀并生成cookie
        String prefix = getPrefixFromSite();
        String cookieValue = generateCdndefendCookie(prefix);
        if (cookieValue != null) {
            map.put("Cookie", "cdndefend_js_cookie=" + cookieValue);
        }

        return map;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String[] typeIdList = {"1", "2", "3", "4", "6"};
        String[] typeNameList = {"电影", "连续剧", "动漫", "综艺", "短剧"};
        for (int i = 0; i < typeNameList.length; i++) {
            classes.add(new Class(typeIdList[i], typeNameList[i]));
        }
        List<Vod> list = getVodList(siteUrl);
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String target = cateUrl + tid + "-----3-" + pg + ".html";
        List<Vod> list = getVodList(target);
        return Result.string(list);
    }

    private @NotNull List<Vod> getVodList(String link) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(link, getHeaders()));
        for (Element element : doc.select("div.module-item > a.v-item")) {
            try {
                String imgSrc = element.select("img:not([id])").attr("data-original");
                if (imgSrc.isEmpty()) {
                    imgSrc = element.select("img:not([id])").attr("src");
                }

                String pic;
                if (imgSrc.startsWith("http")) {
                    pic = imgSrc;
                } else {
                    pic = picUrl + (imgSrc.startsWith("/") ? imgSrc : "/" + imgSrc);
                }

                String url = element.select("a").attr("href");
                String name = element.select("div[class=v-item-title]:not([style])").text();
                String id = url.split("/")[2];
                list.add(new Vod(id, name, pic));
            } catch (Exception e) {
                SpiderDebug.log("解析视频信息失败: " + e.getMessage());
            }
        }
        return list;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(detailUrl.concat(ids.get(0)), getHeaders()));
        String name = doc.select("div.detail-title strong:nth-child(2)").text();
        String pic = doc.select(".detail-pic img").attr("data-original");
        String year = doc.select("a.detail-tags-item").get(0).text();
        String desc = doc.select("div.detail-desc p").text();

        if (pic.isEmpty()) {
            pic = doc.select(".detail-pic img").attr("src");
        }
        if (!pic.startsWith("http")) {
            pic = picUrl + pic;
        }
        // 播放源
        Elements tabs = doc.select("a.source-item span");
        Elements list = doc.select("div.episode-list");
        String PlayFrom = "";
        String PlayUrl = "";
        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i).text();
            if (Arrays.asList("超清", "4K(高峰不卡)").contains(tabName)) continue;
            if (!"".equals(PlayFrom)) {
                PlayFrom = PlayFrom + "$$$" + tabName;
            } else {
                PlayFrom = PlayFrom + tabName;
            }
            Elements li = list.get(i).select("a");
            String liUrl = "";
            for (int i1 = 0; i1 < li.size(); i1++) {
                if (!"".equals(liUrl)) {
                    liUrl = liUrl + "#" + li.get(i1).text() + "$" + li.get(i1).attr("href").replace("/play/", "");
                } else {
                    liUrl = liUrl + li.get(i1).text() + "$" + li.get(i1).attr("href").replace("/play/", "");
                }
            }
            if (!"".equals(PlayUrl)) {
                PlayUrl = PlayUrl + "$$$" + liUrl;
            } else {
                PlayUrl = PlayUrl + liUrl;
            }
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(picUrl + pic);
        vod.setVodYear(year);
        vod.setVodName(name);
        vod.setVodContent(desc);
        vod.setVodPlayFrom(PlayFrom);
        vod.setVodPlayUrl(PlayUrl);
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(searchUrl.concat(URLEncoder.encode(key)).concat(".html"), getHeaders()));
        for (Element element : doc.select("a.search-result-item")) {
            try {
                String pic = picUrl + element.select("img:not([id])").attr("data-original");
                String url = element.attr("href");
                String name = element.select("img").attr("title");
                if (!pic.startsWith("http")) {
                    pic = picUrl + pic;
                }
                String id = url.split("/")[2];
                list.add(new Vod(id, name, pic));
            } catch (Exception e) {
            }
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(playUrl.concat(id), getHeaders()));
        String regex = "window.whatTMDwhatTMDPPPP = '(.*?)'";
        String playSource = "playSource=\\{(.*?)\\}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(doc.html());
        String url = "";
        Pattern playSourcePattern = Pattern.compile(playSource);
        Matcher playSourceMatcher = playSourcePattern.matcher(doc.html());
        boolean b = playSourceMatcher.find();
        if (!b) {
            playSource = "playSource\\s*=\\s*(\\{[^{}]*\\});";
            Pattern compile = Pattern.compile(playSource);
            Matcher matcher1 = compile.matcher(doc.html());
            boolean b1 = matcher1.find();
            if (!b1) {
                SpiderDebug.log("获取播放链接 方式2 失败");
            } else {
                String group = matcher1.group(1).replaceAll("\\s", "");
                Pattern compile1 = Pattern.compile("src:\"([^\"]+)\"");
                Matcher matcher2 = compile1.matcher(group);
                boolean b2 = matcher2.find();
                if (b2) {
                    url = matcher2.group(1);
                }
            }
        } else {
            if (matcher.find()) {
                url = matcher.group(1);
                String js = playSourceMatcher.group(1);
                String regex1 = "KKYS\\['safePlay'\\]\\(\\)\\['url'\\]\\(\"([^\"]+)\"\\)";
                Pattern pattern1 = Pattern.compile(regex1);
                Matcher matcher1 = pattern1.matcher(UnicodeUtil.toString(js));
                String iv = "VNF9aVQF!G*0ux@2hAigUeH3";
                if (matcher1.find()) {
                    iv = matcher1.group(1);
                }
                url = decryptUrl(url, iv);
            } else {
                SpiderDebug.log("方式1 匹配链接失败");
            }
        }
        return Result.get().url(url).header(getHeaders()).string();
    }

    public String decryptUrl(String encryptedData, String iv) {
        try {
            String encryptedKey = "VNF9aVQF!G*0ux@2hAigUeH3";

            return AESEncryption.decrypt(encryptedData, iv, "", AESEncryption.ECB_PKCS_7_PADDING);
        } catch (Exception e) {
            e.printStackTrace();
            return "123456";
        }
    }
}
