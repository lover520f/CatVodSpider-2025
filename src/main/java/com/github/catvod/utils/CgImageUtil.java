package com.github.catvod.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.Base64;


public class CgImageUtil {
    private static final String IV = "97b60394abc2fbe1";
    private static final String KEY = "f5d965df75336270";

    private static byte[] aesDecrypt(byte[] encryptedBytes) {
        try {
            byte[] keyBytes = KEY.getBytes("UTF-8");
            byte[] ivBytes = IV.getBytes("UTF-8");

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(ivBytes));

            return cipher.doFinal(encryptedBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String loadBackgroundImage(String bgUrl) {
        if (isCdnImg(bgUrl)) {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(bgUrl)
                        .build();
                Response response = client.newCall(request).execute();
                ResponseBody body = response.body();
                if (body != null) {
                    byte[] imageBytes = body.bytes();
                    byte[] decryptedBytes = aesDecrypt(imageBytes);

                    if (decryptedBytes != null) {
                        String base64Str = Base64.getEncoder().encodeToString(decryptedBytes);
                        String[] ary = bgUrl.split("\\.");
                        return "data:image/" + ary[ary.length - 1] + ";base64," + base64Str;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bgUrl;
    }

    private static boolean isCdnImg(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.contains("/xiao/")) {
            return true;
        }
        if (path.contains("/upload/upload/")) {
            return true;
        }
        return false;
    }
}
