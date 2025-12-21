package com.github.catvod.spider;

import com.github.catvod.api.AliYunTokenHandler;
import com.github.catvod.api.QuarkTokenHandler;
import com.github.catvod.api.UCTokenHandler;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Introduce extends Spider {


    @Override
    public void init(String extend) throws Exception {
        super.init(extend);

    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "UC"));
        classes.add(new Class("2", "Quark"));
        classes.add(new Class("3", "AliYun"));
        List<Vod> list = new ArrayList<>();
        String pic = "";
        list.add(new Vod("UCToken", "UC Token", pic));
        list.add(new Vod("QuarkToken", "Quark Token", pic));
        list.add(new Vod("AliYunToken", "AliYun Token[无法进行认证服务]", pic));
        return Result.string(classes,list);
    }


    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> vodList = new ArrayList<>();
        //UC
        if (tid.equals("1")) {
            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            String name = "点击设置Token";
            vodList.add(new Vod("UCToken", name, pic));
        }

        if (tid.equals("2")) {
            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            vodList.add(new Vod("QuarkToken", "设置quark Token", pic));
        }

        if (tid.equals("3")) {
            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            vodList.add(new Vod("AliYunToken", "注意：由于阿里云盘不允许搭建视频外链到视频网站播放分发服务,所以无法进行认证服务！！！", pic));
        }
        return Result.get().vod(vodList).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);

        //UC Token 扫码
        if (vodId.equals("UCToken")) {
            UCTokenHandler qrCodeHandler = new UCTokenHandler();
            qrCodeHandler.startUC_TOKENScan();
        }

        // Quark Token 扫码
        if (vodId.equals("QuarkToken")) {
            QuarkTokenHandler qrCodeHandler = new QuarkTokenHandler();
            qrCodeHandler.startQuarkTokenScan();
        }

        // AliYun Token 扫码
        if (vodId.equals("AliYunToken")) {
            AliYunTokenHandler qrCodeHandler = new AliYunTokenHandler();
            qrCodeHandler.startAliYunTokenScan();
        }
        Vod item = new Vod();
        return Result.string(item);
    }

}