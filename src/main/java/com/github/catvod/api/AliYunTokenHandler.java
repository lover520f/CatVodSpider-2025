package com.github.catvod.api;

import com.github.catvod.bean.ali.Cache;
import com.github.catvod.bean.ali.Data;
import com.github.catvod.bean.ali.User;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.*;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AliYunTokenHandler {
    private ScheduledExecutorService service;
    private JDialog dialog;
    private final Cache cache;
    private String qrCodeToken;
    private Map<String, String> qrCodeParams;

    public AliYunTokenHandler() {
        cache = Cache.objectFrom(Path.read(getCache()));
    }

    public File getCache() {
        return Path.tv("aliyuntoken");
    }

    /**
     * 启动阿里云盘二维码扫描登录流程
     */
    public void startAliYunTokenScan() throws Exception {
        // 获取二维码token
        Map<String, Object> qrCodeData = getQRCodeData();

        if (qrCodeData == null || qrCodeData.isEmpty()) {
            Util.notify("获取阿里云盘二维码失败，请检查网络或稍后重试");
            throw new RuntimeException("获取二维码信息失败");
        }

        qrCodeToken = (String) qrCodeData.get("token");
        qrCodeParams = (Map<String, String>) qrCodeData.get("params");
        String qrCodeContent = (String) qrCodeData.get("codeContent");

        if (StringUtils.isBlank(qrCodeToken) || StringUtils.isBlank(qrCodeContent)) {
            Util.notify("获取阿里云盘二维码失败，二维码信息不完整");
            throw new RuntimeException("获取二维码信息失败");
        }

        // 显示二维码
        Init.execute(() -> showQRCode(qrCodeContent));

        // 启动轮询服务检查扫码状态
        Init.execute(this::startService);
    }


    /**
     * 获取二维码登录的信息
     */
    private Map<String, Object> getQRCodeData() {
        try {
            // 添加必要的请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36 Edg/141.0.0.0");
            headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");

            String json = OkHttp.string("https://passport.aliyundrive.com/newlogin/qrcode/generate.do?appName=aliyun_drive&fromSite=52&appName=aliyun_drive&appEntrance=web&isMobile=false&lang=zh_CN&returnUrl=&bizParams=&_bx-v=2.2.3", headers);

            if (StringUtils.isBlank(json)) {
                SpiderDebug.log("阿里云盘二维码接口返回空数据");
                return null;
            }

            Data data = Data.objectFrom(json).getContent().getData();

            // 检查必要字段是否存在
            if (StringUtils.isBlank(data.getCodeContent()) || StringUtils.isBlank(data.getT()) || StringUtils.isBlank(data.getCk())) {
                SpiderDebug.log("阿里云盘二维码数据不完整");
                return null;
            }

            Map<String, Object> result = new HashMap<>();
            // 使用lgToken作为token标识
            String lgToken = extractLgToken(data.getCodeContent());
            result.put("token", lgToken);
            result.put("params", data.getParams());
            result.put("codeContent", data.getCodeContent());
            return result;
        } catch (Exception e) {
            SpiderDebug.log("获取阿里云盘二维码失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从二维码内容中提取lgToken
     */
    private String extractLgToken(String codeContent) {
        try {
            String[] parts = codeContent.split("lgToken=");
            if (parts.length > 1) {
                String tokenPart = parts[1];
                int endIndex = tokenPart.indexOf("&");
                if (endIndex > 0) {
                    return tokenPart.substring(0, endIndex);
                } else {
                    return tokenPart;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("提取lgToken失败: " + e.getMessage());
        }
        return "";
    }


    /**
     * 显示二维码
     */
    private void showQRCode(String content) {
        try {
            final int size = 300;
            SwingUtilities.invokeLater(() -> {
                BufferedImage image = QRCode.getBitmap(content, size, 2);
                JPanel jPanel = new JPanel();
                jPanel.setSize(Swings.dp2px(size), Swings.dp2px(size));
                jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));
                jPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                jPanel.setBackground(Color.WHITE);

                // 添加标题
                JLabel titleLabel = new JLabel("LumenTV-请使用阿里云盘App扫描");
                titleLabel.setAlignmentX(JLabel.CENTER);
                titleLabel.setFont(titleLabel.getFont().deriveFont(18.0f));
                jPanel.add(titleLabel);

                // 添加二维码图片
                JLabel imageLabel = new JLabel(new ImageIcon(image));
                imageLabel.setAlignmentX(JLabel.CENTER);
                jPanel.add(imageLabel);

                // 添加取消按钮
                JButton cancelButton = new JButton("取消");
                cancelButton.setAlignmentX(JButton.CENTER);
                cancelButton.setFont(cancelButton.getFont().deriveFont(16.0f));
                cancelButton.setPreferredSize(new Dimension(100, 40));
                cancelButton.addActionListener((event) -> {
                    cancelScan();
                    if (dialog != null) {
                        dialog.setVisible(false);
                        dialog.dispose();
                    }
                });
                jPanel.add(Box.createVerticalStrut(10));
                jPanel.add(cancelButton);

                dialog = Util.showDialog(jPanel, "阿里云盘扫码登录");
            });
            Util.notify("请使用阿里云盘App扫描二维码");
        } catch (Exception ignored) {
        }
    }

    /**
     * 启动轮询服务检查扫码状态
     */
    private void startService() {
        SpiderDebug.log("----start AliYun token service");

        if (service != null && !service.isShutdown()) {
            stopService();
        }

        service = Executors.newScheduledThreadPool(1);

        service.scheduleWithFixedDelay(() -> {
            try {
                SpiderDebug.log("----checkAliYunTokenStatus中");
                checkAliYunTokenStatus();
            } catch (Exception e) {
                SpiderDebug.log("检查扫码状态出错: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 检查扫码状态
     */
    // 修改 AliYunTokenHandler.checkAliYunTokenStatus 方法
    private void checkAliYunTokenStatus() {
        if (StringUtils.isBlank(qrCodeToken) || qrCodeParams == null) {
            SpiderDebug.log("----qrCodeToken为空");
            stopService();
            return;
        }

        try {
            String result = OkHttp.post("https://passport.aliyundrive.com/newlogin/qrcode/query.do?appName=aliyun_drive&fromSite=52&_bx-v=2.2.3", qrCodeParams);

            SpiderDebug.log("----AliContent:" + result);

            // 直接解析JSON
            com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(result).getAsJsonObject();
            com.google.gson.JsonObject content = jsonObject.getAsJsonObject("content");

            if (content.has("success") && content.get("success").getAsBoolean()) {
                com.google.gson.JsonObject data = content.getAsJsonObject("data");
                // 检查qrCodeStatus是否为CONFIRMED
                if (data.has("qrCodeStatus") && "CONFIRMED".equals(data.get("qrCodeStatus").getAsString())) {
                    if (data.has("bizExt")) {
                        String bizExt = data.get("bizExt").getAsString();
                        try {
                            // 解码bizExt获取token信息
                            String decodedBizExt = new String(java.util.Base64.getDecoder().decode(bizExt));
                            com.google.gson.JsonObject bizExtObject = com.google.gson.JsonParser.parseString(decodedBizExt).getAsJsonObject();

                            if (bizExtObject.has("pds_login_result")) {
                                com.google.gson.JsonObject pdsLoginResult = bizExtObject.getAsJsonObject("pds_login_result");

                                // 获取refreshToken
                                if (pdsLoginResult.has("refreshToken")) {
                                    String refreshToken = pdsLoginResult.get("refreshToken").getAsString();

                                    if (StringUtils.isNotBlank(refreshToken)) {
                                        // 保存到本地缓存
                                        cache.getUser().setRefreshToken(refreshToken);
                                        cache.save();

                                        SpiderDebug.log("阿里云盘RefreshToken获取成功: " + refreshToken);
                                        Util.notify("阿里云盘Token获取成功");

                                        // 设置refreshToken并刷新获取accessToken
                                        AliYun.get().setRefreshToken(refreshToken);
                                        AliYun.get().refreshAccessToken();

                                        // 确保在正确的位置停止服务
                                        Init.execute(this::stopService);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            SpiderDebug.log("解码bizExt失败: " + e.getMessage());
                        }
                    }
                }
                // 如果是CONFIRMED状态，也应该停止服务
                else if (data.has("qrCodeStatus") && "NEW".equals(data.get("qrCodeStatus").getAsString())) {
                    // 保持轮询
                    return;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("检查阿里云盘扫码状态出错: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 停止服务
     */
    private void stopService() {
        if (service != null) {
            SpiderDebug.log("阿里云盘线程池关闭");
            service.shutdownNow();
            service = null;
            if (dialog != null) {
                dialog.dispose();
            }
        }
    }

    /**
     * 取消扫码
     */
    public void cancelScan() {
        qrCodeToken = null;
        qrCodeParams = null;
        stopService();
    }
}
