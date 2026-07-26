package com.passer.aira;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.CalendarContract;

import org.json.JSONObject;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AgentTools {
    private static final int MAX_SHARED_TEXT = 12_000;

    private final Activity activity;
    private final AppStorage storage;
    private final SecureStore secureStore;

    AgentTools(Activity activity, AppStorage storage) {
        this.activity = activity;
        this.storage = storage;
        this.secureStore = new SecureStore(activity);
    }

    boolean hasPasserConnection() {
        return PasserLinkConfig.load(activity, secureStore).isConfigured();
    }

    JSONObject execute(JSONObject spec) {
        String action = spec.optString("action").trim().toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "current_time":
                    return success(action, new JSONObject()
                            .put("iso", ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
                            .put("timezone", ZonedDateTime.now().getZone().getId()));
                case "device_status":
                    return deviceStatus(action);
                case "remember":
                    return remember(action, spec);
                case "clear_memory":
                    return clearMemory(action);
                case "open_url":
                    return openUrl(action, spec);
                case "share_text":
                    return shareText(action, spec);
                case "compose_email":
                    return composeEmail(action, spec);
                case "set_alarm":
                    return setAlarm(action, spec);
                case "add_calendar_event":
                    return addCalendarEvent(action, spec);
                case "open_maps":
                    return openMaps(action, spec);
                case "passer_status":
                    return desktopAction(action, "status", spec, false);
                case "summon":
                case "list_tools":
                case "list_items":
                case "search":
                case "open_item":
                case "open_tool":
                case "start_screenshot":
                case "clear_search":
                    return desktopAction(
                            action,
                            action,
                            spec,
                            "summon".equals(action)
                                    || "open_item".equals(action)
                                    || "open_tool".equals(action)
                                    || "start_screenshot".equals(action)
                    );
                case "list_tasks":
                    return desktopAction(action, action, spec, false);
                case "add_task":
                    return desktopAction(action, action, spec, true);
                default:
                    return failure(action, "不支持该手机动作。");
            }
        } catch (Exception error) {
            return failure(action, error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage());
        }
    }

    private JSONObject deviceStatus(String action) throws Exception {
        BatteryManager battery = (BatteryManager) activity.getSystemService(Activity.BATTERY_SERVICE);
        int percent = battery == null
                ? -1
                : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        JSONObject value = new JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("brand", Build.BRAND)
                .put("model", Build.MODEL)
                .put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("locale", Locale.getDefault().toLanguageTag());
        if (percent >= 0) {
            value.put("battery_percent", percent);
        }
        return success(action, value);
    }

    private JSONObject remember(String action, JSONObject spec) throws Exception {
        String note = required(spec, "note");
        String lower = note.toLowerCase(Locale.ROOT);
        if (lower.contains("api key") || lower.contains("apikey") || lower.contains("密码")
                || lower.contains("验证码") || lower.contains("token")) {
            return failure(action, "拒绝把密码、验证码、Token 或 API Key 写入长期记忆。");
        }
        if (!confirm("保存 Aira 记忆", preview(note))) {
            return denied(action);
        }
        storage.appendMemory(note);
        return success(action, new JSONObject().put("saved", true));
    }

    private JSONObject clearMemory(String action) throws Exception {
        if (!confirm("清空 Aira 的全部长期记忆？", "该操作无法撤销。")) {
            return denied(action);
        }
        storage.clearMemory();
        return success(action, new JSONObject().put("cleared", true));
    }

    private JSONObject openUrl(String action, JSONObject spec) throws Exception {
        String raw = required(spec, "url");
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return failure(action, "只允许打开 http/https 链接。");
        }
        if (!confirm("允许 Aira 打开网页？", preview(raw))) {
            return denied(action);
        }
        launch(new Intent(Intent.ACTION_VIEW, uri));
        return success(action, new JSONObject().put("opened", raw));
    }

    private JSONObject shareText(String action, JSONObject spec) throws Exception {
        String text = required(spec, "text");
        if (text.length() > MAX_SHARED_TEXT) {
            return failure(action, "分享文字过长。");
        }
        if (!confirm("允许 Aira 打开系统分享面板？", preview(text))) {
            return denied(action);
        }
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        String title = spec.optString("title", "Aira 分享");
        launch(Intent.createChooser(intent, title));
        return success(action, new JSONObject().put("share_sheet_opened", true));
    }

    private JSONObject composeEmail(String action, JSONObject spec) throws Exception {
        String to = spec.optString("to", "").trim();
        String subject = spec.optString("subject", "");
        String body = spec.optString("body", "");
        if (to.contains("\n") || to.contains("\r")) {
            return failure(action, "收件人格式无效。");
        }
        String detail = "收件人：" + (to.isEmpty() ? "未填写" : to)
                + "\n主题：" + preview(subject)
                + "\n\n邮件只会进入编辑页，不会自动发送。";
        if (!confirm("允许 Aira 打开邮件编辑页？", detail)) {
            return denied(action);
        }
        Uri uri = Uri.parse("mailto:" + Uri.encode(to));
        Intent intent = new Intent(Intent.ACTION_SENDTO, uri)
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body);
        launch(intent);
        return success(action, new JSONObject().put("composer_opened", true).put("sent", false));
    }

    private JSONObject setAlarm(String action, JSONObject spec) throws Exception {
        int hour = spec.optInt("hour", -1);
        int minute = spec.optInt("minute", -1);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return failure(action, "hour 必须为 0–23，minute 必须为 0–59。");
        }
        String label = spec.optString("label", "Aira");
        if (!confirm("允许 Aira 打开闹钟确认页？",
                String.format(Locale.CHINA, "%02d:%02d  %s", hour, minute, label))) {
            return denied(action);
        }
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        launch(intent);
        return success(action, new JSONObject().put("alarm_ui_opened", true));
    }

    private JSONObject addCalendarEvent(String action, JSONObject spec) throws Exception {
        String title = required(spec, "title");
        long startMillis = spec.optLong("start_millis", -1);
        long endMillis = spec.optLong("end_millis", startMillis > 0 ? startMillis + 3_600_000 : -1);
        if (startMillis <= 0 || endMillis < startMillis) {
            return failure(action, "需要有效的 start_millis，end_millis 不得早于开始时间。");
        }
        if (!confirm("允许 Aira 打开日程编辑页？",
                preview(title) + "\n系统日历仍会要求你确认保存。")) {
            return denied(action);
        }
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                .putExtra(CalendarContract.Events.DESCRIPTION, spec.optString("description", ""))
                .putExtra(CalendarContract.Events.EVENT_LOCATION, spec.optString("location", ""));
        launch(intent);
        return success(action, new JSONObject().put("calendar_editor_opened", true).put("saved", false));
    }

    private JSONObject openMaps(String action, JSONObject spec) throws Exception {
        String query = required(spec, "query");
        if (!confirm("允许 Aira 打开地图搜索？", preview(query))) {
            return denied(action);
        }
        launch(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))));
        return success(action, new JSONObject().put("map_opened", true).put("query", query));
    }

    private JSONObject desktopAction(
            String localAction,
            String desktopAction,
            JSONObject spec,
            boolean needsConfirmation
    ) throws Exception {
        PasserLinkConfig config = PasserLinkConfig.load(activity, secureStore);
        if (!config.isConfigured()) {
            return failure(localAction, "尚未配置 Passer 连接。");
        }
        JSONObject params = new JSONObject(spec.toString());
        params.remove("action");
        if (needsConfirmation && !confirm(
                "允许 Aira 操作 Passer？",
                "桌面动作：" + desktopAction + "\n参数：" + preview(params.toString())
        )) {
            return denied(localAction);
        }
        JSONObject result = new PasserLinkClient(config).request(desktopAction, params);
        return success(localAction, result);
    }

    private void launch(Intent intent) throws Exception {
        AtomicBoolean launched = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(intent);
                launched.set(true);
            } catch (ActivityNotFoundException ignored) {
                launched.set(false);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(15, TimeUnit.SECONDS) || !launched.get()) {
            throw new IllegalStateException("手机上没有可处理该操作的 App。");
        }
    }

    private boolean confirm(String title, String message) throws InterruptedException {
        AtomicBoolean approved = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                latch.countDown();
                return;
            }
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setNegativeButton("拒绝", (dialog, which) -> {
                        approved.set(false);
                        latch.countDown();
                    })
                    .setPositiveButton("允许本次", (dialog, which) -> {
                        approved.set(true);
                        latch.countDown();
                    })
                    .setOnCancelListener(dialog -> latch.countDown())
                    .show();
        });
        return latch.await(120, TimeUnit.SECONDS) && approved.get();
    }

    private String required(JSONObject spec, String key) {
        String value = spec.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("缺少 " + key + "。");
        }
        return value;
    }

    private String preview(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= 240 ? clean : clean.substring(0, 240) + "…";
    }

    private JSONObject success(String action, JSONObject value) throws Exception {
        return new JSONObject().put("action", action).put("ok", true).put("result", value);
    }

    private JSONObject failure(String action, String message) {
        try {
            return new JSONObject().put("action", action).put("ok", false).put("error", message);
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private JSONObject denied(String action) {
        return failure(action, "用户拒绝了本次操作。");
    }
}
