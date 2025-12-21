package com.github.catvod.spider;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhixc
 */
public class PanSearch extends Ali {

    private final String URL = "https://www.pansearch.me/";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        header.put("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
        header.put("Accept-Encoding", "gzip, deflate");
        header.put("Connection", "keep-alive");
        header.put("Upgrade-Insecure-Requests", "1");
        return header;
    }


    private Map<String, String> getSearchHeader() {
        Map<String, String> header = getHeader();
        header.put("x-nextjs-data", "1");
        header.put("referer", URL);
        return header;
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String html = OkHttp.string(URL, getHeader());
        System.out.println("DOC " + html);
        String data = Jsoup.parse(html).select("script[id=__NEXT_DATA__]").get(0).data();
        String buildId = new JSONObject(data).getString("buildId");
        String url = URL + "_next/data/" + buildId + "/search.json?keyword=" + URLEncoder.encode(key, Charset.defaultCharset().name()) + "&pan=aliyundrive";
        String result = OkHttp.string(url, getSearchHeader());
        JSONArray array = new JSONObject(result).getJSONObject("pageProps").getJSONObject("data").getJSONArray("data");
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String content = item.optString("content");
            String[] split = content.split("\\n");
            if (split.length == 0) continue;
            String vodId = Jsoup.parse(content).select("a").attr("href");
            String name = split[0].replaceAll("</?[^>]+>", "");
            String remark = item.optString("time");
            String pic = item.optString("image");
            list.add(new Vod(vodId, name, pic, remark));
        }
        return Result.string(list);
    }
}
