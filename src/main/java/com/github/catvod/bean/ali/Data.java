package com.github.catvod.bean.ali;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class Data {

    @SerializedName("data")
    private Data data;
    @SerializedName("content")
    private Data content;
    @SerializedName("t")
    private String t;
    @SerializedName("ck")
    private String ck;
    @SerializedName("codeContent")
    private String codeContent;
    @SerializedName("qrCodeStatus")
    private String qrCodeStatus;
    @SerializedName("bizExt")
    private String bizExt;
    @SerializedName("resultCode")
    private Integer resultCode;
    @SerializedName("processFinished")
    private Boolean processFinished;
    @SerializedName("status")
    private Integer status;
    @SerializedName("success")
    private Boolean success;
    @SerializedName("hasError")
    private Boolean hasError;

    public static Data objectFrom(String str) {
        try {
            Data data = new Gson().fromJson(str, Data.class);
            return data == null ? new Data() : data;
        } catch (Exception e) {
            return new Data();
        }
    }

    public Data getData() {
        return data == null ? new Data() : data;
    }

    public Data getContent() {
        return content == null ? new Data() : content;
    }

    public String getT() {
        return t == null ? "" : t;
    }

    public String getCk() {
        return ck == null ? "" : ck;
    }

    public String getCodeContent() {
        return codeContent == null ? "" : codeContent;
    }

    public String getQrCodeStatus() {
        return qrCodeStatus == null ? "" : qrCodeStatus;
    }

    public String getBizExt() {
        return bizExt == null ? "" : bizExt;
    }

    public String getToken() {
        try {
            String bizExt = getBizExt();
            if (bizExt != null && !bizExt.isEmpty()) {
                String decoded = new String(Base64.getDecoder().decode(bizExt));
                // 使用 Gson 解析解码后的 JSON 字符串
                com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(decoded).getAsJsonObject();
                // 检查是否存在 pds_login_result 字段（实际字段名）
                if (jsonObject.has("pds_login_result")) {
                    com.google.gson.JsonObject pdsLoginResult = jsonObject.getAsJsonObject("pds_login_result");
                    // 优先获取accessToken，如果没有则获取refreshToken
                    if (pdsLoginResult.has("accessToken")) {
                        return pdsLoginResult.get("accessToken").getAsString();
                    } else if (pdsLoginResult.has("refreshToken")) {
                        return pdsLoginResult.get("refreshToken").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            // 解码或解析失败时返回空字符串而不是抛出异常
            System.out.println("解析 bizExt 失败: " + e.getMessage());
            return "";
        }
        return "";
    }

    public boolean hasToken() {
        return "CONFIRMED".equals(getQrCodeStatus()) &&
                getBizExt() != null &&
                !getBizExt().isEmpty();
    }

    //public boolean hasToken() {
    //    return getQrCodeStatus().equals("CONFIRMED") && getBizExt().length() > 0;
    //}

    public Map<String, String> getParams() {
        Map<String, String> params = new HashMap<>();
        params.put("t", getT());
        params.put("ck", getCk());
        params.put("appName", "aliyun_drive");
        params.put("appEntrance", "web");
        params.put("isMobile", "false");
        params.put("lang", "zh_CN");
        params.put("returnUrl", "");
        params.put("fromSite", "52");
        params.put("bizParams", "");
        params.put("navlanguage", "zh-CN");
        params.put("navPlatform", "MacIntel");
        return params;
    }
}
