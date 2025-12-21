package com.github.catvod.api;

import com.github.catvod.bean.quark.Cache;
import com.github.catvod.bean.quark.User;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.*;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QuarkTokenHandler {
    private ScheduledExecutorService service;
    private JDialog dialog;
    private final Cache cache;
    private String qrCodeToken;
    private volatile boolean isScanning = false;

    public QuarkTokenHandler() {
        cache = Cache.objectFrom(Path.read(getCache()));
    }

    public File getCache() {
        return Path.tv("quark");
    }

    /**
     * 启动夸克网盘二维码扫描登录流程
     */
    public void startQuarkTokenScan() throws Exception {
        // 设置扫描状态
        isScanning = true;

        // 获取二维码token
        qrCodeToken = getQrCodeToken();

        if (StringUtils.isBlank(qrCodeToken)) {
            isScanning = false;
            throw new RuntimeException("获取二维码token失败");
        }

        // 生成二维码URL
        String qrCodeUrl = "https://su.quark.cn/4_eMHBJ?uc_param_str=&token=" + qrCodeToken +
                "&client_id=532&uc_biz_str=S%3Acustom%7COPT%3ASAREA%400%7COPT%3AIMMERSIVE%401%7COPT%3ABACK_BTN_STYLE%400";

        // 显示二维码
        Init.execute(() -> showQRCode(qrCodeUrl));

        // 启动轮询服务检查扫码状态
        Init.execute(this::startService);
    }


    /**
     * 获取二维码登录的令牌
     */
    private String getQrCodeToken() {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", "386");
        params.put("v", "1.2");
        params.put("request_id", UUID.randomUUID().toString());

        OkResult res = OkHttp.get("https://uop.quark.cn/cas/ajax/getTokenForQrcodeLogin", params, new HashMap<>());

        Map<String, Object> json = Json.parseSafe(res.getBody(), Map.class);
        if (Objects.equals(json.get("message"), "ok")) {
            return (String) ((Map<String, Object>) ((Map<String, Object>) json.get("data")).get("members")).get("token");
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
                // 检查是否仍在扫描状态
                if (!isScanning) {
                    return;
                }

                BufferedImage image = QRCode.getBitmap(content, size, 2);
                JPanel jPanel = new JPanel();
                jPanel.setSize(Swings.dp2px(size), Swings.dp2px(size));
                jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));
                jPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // 增加边距
                jPanel.setBackground(Color.darkGray); // 使用深色背景

                // 添加标题
                JLabel titleLabel = new JLabel("LumenTV-请使用夸克网盘App扫描");
                titleLabel.setAlignmentX(JLabel.CENTER);
                titleLabel.setBackground(Color.DARK_GRAY);
                titleLabel.setForeground(Color.white); // 白色文字
                titleLabel.setFont(titleLabel.getFont().deriveFont(18.0f)); // 增大字体
                jPanel.add(titleLabel);

                // 添加二维码图片
                JLabel imageLabel = new JLabel(new ImageIcon(image));
                imageLabel.setAlignmentX(JLabel.CENTER);
                jPanel.add(imageLabel);

                // 添加取消按钮
                JButton cancelButton = new JButton("取消");
                cancelButton.setAlignmentX(JButton.CENTER);
                cancelButton.setFont(cancelButton.getFont().deriveFont(16.0f)); // 增大字体
                cancelButton.setPreferredSize(new Dimension(100, 40)); // 设置按钮大小
                cancelButton.addActionListener((event) -> {
                    cancelScan();
                    if (dialog != null) {
                        dialog.setVisible(false);
                        dialog.dispose();
                        dialog = null;
                    }
                });
                jPanel.add(Box.createVerticalStrut(10)); // 添加垂直间距
                jPanel.add(cancelButton);

                // 创建并配置对话框（参考UCTokenHandler的方式）
                dialog = new JDialog((Frame) null);
                dialog.setUndecorated(true);
                dialog.setContentPane(jPanel);
                dialog.pack();
                dialog.setLocationRelativeTo(null);
                dialog.setLocation(Swings.getCenter(jPanel.getWidth(), jPanel.getHeight()));
                dialog.setVisible(true);
            });

            // 只有在仍在扫描时才发送通知
            if (isScanning) {
                Util.notify("请使用夸克网盘App扫描二维码");
            }
        } catch (Exception ignored) {
            SpiderDebug.log("显示二维码失败: " + ignored.getMessage());
        }
    }

    /**
     * 启动轮询服务检查扫码状态
     */
    private void startService() {
        SpiderDebug.log("----start Quark token service");

        if (service != null && !service.isShutdown()) {
            stopService();
        }

        service = Executors.newScheduledThreadPool(1);

        service.scheduleWithFixedDelay(() -> {
            try {
                SpiderDebug.log("----checkQuarkTokenStatus中");
                checkQuarkTokenStatus();
            } catch (Exception e) {
                SpiderDebug.log("检查扫码状态出错: " + e.getMessage());
            }
        }, 1, 3, TimeUnit.SECONDS);
    }

    /**
     * 检查扫码状态
     */
    private void checkQuarkTokenStatus() {
        // 检查是否仍在扫描状态
        if (!isScanning) {
            stopService();
            return;
        }

        if (StringUtils.isBlank(qrCodeToken)) {
            stopService();
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("client_id", "532");
        params.put("v", "1.2");
        params.put("request_id", UUID.randomUUID().toString());
        params.put("token", qrCodeToken);

        String result = OkHttp.string("https://uop.quark.cn/cas/ajax/getServiceTicketByQrcodeToken", params, new HashMap<>());
        Map<String, Object> json = Json.parseSafe(result, Map.class);

        if (json != null && json.get("status").equals(Double.valueOf(2000000))) {
            String serviceTicket = (String) ((Map<String, Object>) ((Map<String, Object>) json.get("data")).get("members")).get("service_ticket");

            // 获取用户cookie
            OkResult userInfoResult = OkHttp.get("https://pan.quark.cn/account/info?st=" + serviceTicket + "&lw=scan", new HashMap<>(), new HashMap<>());
            Map userInfo = Json.parseSafe(userInfoResult.getBody(), Map.class);

            if (userInfo != null && userInfo.get("success").equals(Boolean.TRUE)) {
                List<String> cookies = userInfoResult.getResp().get("set-Cookie");
                List<String> cookieList = new ArrayList<>();
                for (String cookie : cookies) {
                    cookieList.add(cookie.split(";")[0]);
                }
                String cookieStr = StringUtils.join(cookieList, ";");

                // 保存到本地缓存
                cache.setUser(User.objectFrom(cookieStr));
                cache.save();

                SpiderDebug.log("Quark Token获取成功: " + cookieStr);
                Util.notify("Quark Token获取成功,请重启应用");

                // 停止服务并关闭对话框
                isScanning = false;
                stopService();
            }
        }
    }


    /**
     * 停止服务
     */
    private void stopService() {
        if (service != null) {
            SpiderDebug.log("quark线程池关闭");
            service.shutdownNow();
            try {
                if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
                    service.shutdownNow();
                }
            } catch (InterruptedException e) {
                service.shutdownNow();
            }
            service = null;
        }

        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
    }


    /**
     * 取消扫码
     */
    public void cancelScan() {
        isScanning = false;
        qrCodeToken = null;
        stopService();
    }

}
