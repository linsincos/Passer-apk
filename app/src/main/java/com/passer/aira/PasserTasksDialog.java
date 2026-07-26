package com.passer.aira;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class PasserTasksDialog {
    private static final int BLUE = Color.rgb(91, 92, 226);
    private static final int BLUE_DARK = Color.rgb(66, 68, 199);
    private static final int TEXT = Color.rgb(24, 26, 32);
    private static final int MUTED = Color.rgb(124, 129, 139);
    private static final int SURFACE = Color.rgb(247, 248, 250);
    private static final int BORDER = Color.rgb(232, 234, 240);
    private static final int SOFT_BLUE = Color.rgb(243, 244, 255);
    private static final int RUNNING = Color.rgb(180, 83, 9);
    private static final int SOFT_RUNNING = Color.rgb(255, 247, 237);
    private static final long ACTIVE_REFRESH_MS = 2500L;
    private static final long IDLE_REFRESH_MS = 10000L;

    private final Activity activity;
    private final Runnable pairingAction;
    private final SecureStore secureStore;
    private final Handler monitorHandler;
    private final Runnable monitorRefresh;
    private Dialog dialog;
    private LinearLayout taskList;
    private TextView status;
    private TextView refreshButton;
    private TextView addButton;
    private boolean refreshInFlight;

    PasserTasksDialog(Activity activity, Runnable pairingAction) {
        this.activity = activity;
        this.pairingAction = pairingAction;
        this.secureStore = new SecureStore(activity);
        this.monitorHandler = new Handler(Looper.getMainLooper());
        this.monitorRefresh = () -> refresh(false);
    }

    void show() {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        root.setPadding(dp(14), dp(10), dp(14), dp(14));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = button("×", false);
        close.setTextSize(24);
        close.setOnClickListener(view -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(activity);
        title.setText("电脑任务");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        refreshButton = button("刷新", false);
        refreshButton.setOnClickListener(view -> refresh(true));
        header.addView(refreshButton, new LinearLayout.LayoutParams(dp(64), dp(44)));
        root.addView(header);

        LinearLayout computer = new LinearLayout(activity);
        computer.setOrientation(LinearLayout.HORIZONTAL);
        computer.setGravity(Gravity.CENTER_VERTICAL);
        computer.setPadding(dp(13), dp(10), dp(8), dp(10));
        computer.setBackground(roundRect(Color.WHITE, BORDER, 14));

        status = new TextView(activity);
        status.setTextColor(TEXT);
        status.setTextSize(14);
        status.setMaxLines(3);
        computer.addView(status, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView pair = button("配对电脑", false);
        pair.setOnClickListener(view -> {
            if (pairingAction != null) {
                pairingAction.run();
            }
        });
        computer.addView(pair, new LinearLayout.LayoutParams(dp(92), dp(42)));
        LinearLayout.LayoutParams computerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        computerParams.setMargins(0, dp(6), 0, dp(12));
        root.addView(computer, computerParams);

        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView section = new TextView(activity);
        section.setText("Aira 自动化任务");
        section.setTextColor(TEXT);
        section.setTextSize(17);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(section, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addButton = button("＋ 新建", true);
        addButton.setOnClickListener(view -> showAddTask());
        toolbar.addView(addButton, new LinearLayout.LayoutParams(dp(92), dp(44)));
        root.addView(toolbar);

        taskList = new LinearLayout(activity);
        taskList.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(8), 0, dp(10));
        scroll.addView(taskList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView safety = new TextView(activity);
        safety.setText("手机仅能查看和新增任务；删除、脚本执行和任意文件访问未开放。");
        safety.setTextColor(MUTED);
        safety.setTextSize(12);
        safety.setGravity(Gravity.CENTER);
        safety.setPadding(dp(8), dp(6), dp(8), 0);
        root.addView(safety);

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                );
                window.setStatusBarColor(SURFACE);
                window.setNavigationBarColor(SURFACE);
            }
            refresh(true);
        });
        dialog.setOnDismissListener(ignored -> {
            monitorHandler.removeCallbacks(monitorRefresh);
            refreshInFlight = false;
        });
        dialog.show();
    }

    private void refresh() {
        refresh(true);
    }

    private void refresh(boolean showLoading) {
        monitorHandler.removeCallbacks(monitorRefresh);
        if (refreshInFlight || !showing()) {
            return;
        }
        PasserLinkConfig config = PasserLinkConfig.load(activity, secureStore);
        if (!config.isConfigured()) {
            status.setText("尚未配对电脑\n请先在同一局域网搜索并输入 8 位连接码。");
            renderMessage("配对电脑后即可查看任务。", false);
            addButton.setEnabled(false);
            addButton.setAlpha(0.45f);
            return;
        }
        String name = config.computerName.isEmpty() ? "已配对电脑" : config.computerName;
        refreshInFlight = true;
        if (showLoading) {
            status.setText(name + "\n" + config.host + ":" + config.port + " · 正在连接…");
            setLoading(true, true);
        } else {
            refreshButton.setEnabled(false);
            refreshButton.setAlpha(0.45f);
        }
        new Thread(() -> {
            try {
                JSONObject response = new PasserLinkClient(config)
                        .request("list_tasks", new JSONObject());
                JSONObject desktop = response.optJSONObject("result");
                if (desktop == null) {
                    throw new IllegalStateException("Passer 未返回任务数据。");
                }
                JSONArray tasks = desktop.optJSONArray("tasks");
                int activeCount = Math.max(0, desktop.optInt("active_count", 0));
                long refreshAfter = desktop.optLong(
                        "refresh_after_ms",
                        activeCount > 0 ? ACTIVE_REFRESH_MS : IDLE_REFRESH_MS
                );
                activity.runOnUiThread(() -> {
                    if (!showing()) {
                        return;
                    }
                    refreshInFlight = false;
                    String connectionState = activeCount > 0
                            ? "正在运行 " + activeCount + " 项"
                            : "已连接 · 暂无运行中任务";
                    status.setText(name + "\n" + config.host + ":" + config.port
                            + " · " + connectionState);
                    renderTasks(tasks == null ? new JSONArray() : tasks);
                    setLoading(false, false);
                    scheduleRefresh(refreshAfter);
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!showing()) {
                        return;
                    }
                    refreshInFlight = false;
                    status.setText(name + "\n" + config.host + ":" + config.port + " · 连接失败");
                    renderMessage("无法读取任务：" + readable(error), true);
                    setLoading(false, false);
                    scheduleRefresh(IDLE_REFRESH_MS);
                });
            }
        }, "aira-task-list").start();
    }

    private void renderTasks(JSONArray tasks) {
        taskList.removeAllViews();
        if (tasks.length() == 0) {
            renderMessage("这台电脑还没有自动化任务。", false);
            return;
        }
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) {
                continue;
            }
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(roundRect(Color.WHITE, BORDER, 14));

            LinearLayout first = new LinearLayout(activity);
            first.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(activity);
            title.setText(task.optString("title", "未命名任务"));
            title.setTextColor(TEXT);
            title.setTextSize(16);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(2);
            first.addView(title, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView state = new TextView(activity);
            boolean running = task.optBoolean("running", false);
            boolean enabled = task.optBoolean("enabled", true);
            state.setText(running ? "执行中" : enabled ? "已启用" : "已停用");
            state.setTextColor(running ? RUNNING : enabled ? BLUE_DARK : MUTED);
            state.setTextSize(12);
            state.setPadding(dp(8), dp(4), dp(8), dp(4));
            state.setBackground(roundRect(
                    running ? SOFT_RUNNING : enabled ? SOFT_BLUE : SURFACE,
                    running ? RUNNING : BORDER,
                    10
            ));
            first.addView(state);
            card.addView(first);

            if (running) {
                String stage = task.optString("run_stage", "正在执行");
                int round = Math.max(0, task.optInt("run_round", 0));
                String started = friendlyTime(task.optString("run_started_at", ""));
                String updated = friendlyTime(task.optString("run_updated_at", ""));
                String preview = compact(task.optString("run_preview", ""), 280);
                StringBuilder progressText = new StringBuilder("当前：").append(stage);
                if (round > 0) {
                    progressText.append(String.format(
                            Locale.getDefault(),
                            " · 第 %d 轮",
                            round
                    ));
                }
                progressText.append("\n开始：").append(started)
                        .append(" · 更新：").append(updated);
                if (!preview.isEmpty()) {
                    progressText.append("\n进度：").append(preview);
                }
                TextView progress = new TextView(activity);
                progress.setText(progressText.toString());
                progress.setTextColor(RUNNING);
                progress.setTextSize(13);
                progress.setLineSpacing(dp(2), 1.05f);
                progress.setPadding(dp(10), dp(8), dp(10), dp(8));
                progress.setBackground(roundRect(SOFT_RUNNING, RUNNING, 10));
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                progressParams.setMargins(0, dp(8), 0, 0);
                card.addView(progress, progressParams);
            }

            TextView schedule = new TextView(activity);
            schedule.setText("计划：" + task.optString("schedule", "—")
                    + "\n下次：" + friendlyTime(task.optString("next_run", ""))
                    + "\n指令：" + compact(task.optString("prompt", ""), 240));
            schedule.setTextColor(MUTED);
            schedule.setTextSize(13);
            schedule.setLineSpacing(dp(2), 1.05f);
            schedule.setPadding(0, dp(8), 0, 0);
            card.addView(schedule);

            String lastResult = task.optString("last_result", "").trim();
            if (!lastResult.isEmpty()) {
                TextView result = new TextView(activity);
                result.setText("上次结果：" + compact(lastResult, 240));
                result.setTextColor(TEXT);
                result.setTextSize(13);
                result.setPadding(0, dp(7), 0, 0);
                card.addView(result);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(9));
            taskList.addView(card, params);
        }
    }

    private void showAddTask() {
        PasserLinkConfig config = PasserLinkConfig.load(activity, secureStore);
        if (!config.isConfigured()) {
            Toast.makeText(activity, "请先配对电脑。", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), 0, dp(2), 0);

        content.addView(label("任务名称"));
        EditText title = field("例如：每天工作总结", false);
        content.addView(title, fieldParams());

        content.addView(label("交给 Aira 的任务指令"));
        EditText prompt = field("清楚描述要让电脑自动完成的事情", false);
        prompt.setMinLines(3);
        prompt.setMaxLines(6);
        prompt.setGravity(Gravity.TOP);
        prompt.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        content.addView(prompt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(112)));

        content.addView(label("执行方式"));
        Spinner mode = spinner(new String[]{"一次", "每天", "每隔一段时间"});
        content.addView(mode, fieldParams());

        TextView scheduleLabel = label("执行时间");
        content.addView(scheduleLabel);
        EditText schedule = field("例如 2026-07-27 09:00", false);
        content.addView(schedule, fieldParams());

        TextView unitLabel = label("间隔单位");
        Spinner unit = spinner(new String[]{"分钟", "小时", "天"});
        content.addView(unitLabel);
        content.addView(unit, fieldParams());
        unitLabel.setVisibility(View.GONE);
        unit.setVisibility(View.GONE);

        TextView hint = new TextView(activity);
        hint.setTextColor(MUTED);
        hint.setTextSize(12);
        hint.setPadding(0, dp(6), 0, 0);
        content.addView(hint);

        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    scheduleLabel.setText("执行时间");
                    schedule.setHint("例如 2026-07-27 09:00");
                    hint.setText("一次性任务必须填写未来时间。");
                    unitLabel.setVisibility(View.GONE);
                    unit.setVisibility(View.GONE);
                } else if (position == 1) {
                    scheduleLabel.setText("每天执行时间");
                    schedule.setHint("例如 09:00");
                    hint.setText("每天在指定时间运行。");
                    unitLabel.setVisibility(View.GONE);
                    unit.setVisibility(View.GONE);
                } else {
                    scheduleLabel.setText("间隔数值");
                    schedule.setHint("例如 2");
                    hint.setText("例如“每 2 小时”运行一次。");
                    unitLabel.setVisibility(View.VISIBLE);
                    unit.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // The first mode remains selected.
            }
        });

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        AlertDialog addDialog = new AlertDialog.Builder(activity)
                .setTitle("新建电脑任务")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加任务", null)
                .create();
        addDialog.setOnShowListener(ignored -> addDialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String titleValue = title.getText().toString().trim();
                    String promptValue = prompt.getText().toString().trim();
                    String scheduleValue = schedule.getText().toString().trim();
                    if (titleValue.isEmpty() || promptValue.isEmpty() || scheduleValue.isEmpty()) {
                        Toast.makeText(activity, "请填写完整的任务名称、指令和时间。",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONObject params = new JSONObject();
                    try {
                        params.put("title", titleValue);
                        params.put("prompt", promptValue);
                        if (mode.getSelectedItemPosition() == 0) {
                            params.put("mode", "once");
                            params.put("when", scheduleValue);
                        } else if (mode.getSelectedItemPosition() == 1) {
                            params.put("mode", "daily");
                            params.put("at", scheduleValue);
                        } else {
                            int every = Integer.parseInt(scheduleValue);
                            if (every < 1 || every > 100_000) {
                                throw new IllegalArgumentException("间隔必须在 1–100000 之间。");
                            }
                            params.put("mode", "interval");
                            params.put("every", every);
                            params.put("unit", unit.getSelectedItemPosition() == 0
                                    ? "minutes"
                                    : unit.getSelectedItemPosition() == 1 ? "hours" : "days");
                        }
                    } catch (Exception error) {
                        Toast.makeText(activity, readable(error), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    new Thread(() -> {
                        try {
                            new PasserLinkClient(config).request("add_task", params);
                            activity.runOnUiThread(() -> {
                                if (addDialog.isShowing()) {
                                    addDialog.dismiss();
                                }
                                Toast.makeText(activity, "任务已添加到电脑。",
                                        Toast.LENGTH_SHORT).show();
                                refresh();
                            });
                        } catch (Exception error) {
                            activity.runOnUiThread(() -> {
                                if (addDialog.isShowing()) {
                                    addDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                            .setEnabled(true);
                                }
                                Toast.makeText(activity, "添加失败：" + readable(error),
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    }, "aira-task-add").start();
                }));
        addDialog.show();
    }

    private void renderMessage(String message, boolean error) {
        taskList.removeAllViews();
        TextView text = new TextView(activity);
        text.setText(message);
        text.setTextColor(error ? Color.rgb(185, 28, 28) : MUTED);
        text.setTextSize(15);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(20), dp(64), dp(20), dp(20));
        taskList.addView(text);
    }

    private void setLoading(boolean loading, boolean replaceContent) {
        refreshButton.setEnabled(!loading);
        refreshButton.setAlpha(loading ? 0.45f : 1f);
        addButton.setEnabled(!loading);
        addButton.setAlpha(loading ? 0.45f : 1f);
        if (loading && replaceContent) {
            renderMessage("正在同步电脑任务…", false);
        }
    }

    private void scheduleRefresh(long requestedDelay) {
        if (!showing()) {
            return;
        }
        long delay = Math.max(ACTIVE_REFRESH_MS, Math.min(IDLE_REFRESH_MS, requestedDelay));
        monitorHandler.removeCallbacks(monitorRefresh);
        monitorHandler.postDelayed(monitorRefresh, delay);
    }

    private boolean showing() {
        return dialog != null && dialog.isShowing()
                && !activity.isFinishing() && !activity.isDestroyed();
    }

    private TextView button(String text, boolean primary) {
        TextView value = new TextView(activity);
        value.setText(text);
        value.setTextColor(primary ? Color.WHITE : BLUE_DARK);
        value.setTextSize(14);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        value.setBackground(roundRect(
                primary ? BLUE : Color.WHITE,
                primary ? BLUE : BORDER,
                12
        ));
        return value;
    }

    private TextView label(String text) {
        TextView value = new TextView(activity);
        value.setText(text);
        value.setTextColor(TEXT);
        value.setTextSize(14);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setPadding(0, dp(10), 0, dp(5));
        return value;
    }

    private EditText field(String hint, boolean password) {
        EditText value = new EditText(activity);
        value.setHint(hint);
        value.setTextColor(TEXT);
        value.setHintTextColor(MUTED);
        value.setTextSize(15);
        value.setPadding(dp(12), 0, dp(12), 0);
        value.setBackground(roundRect(Color.WHITE, BORDER, 10));
        value.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return value;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(activity);
        spinner.setAdapter(new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                values
        ));
        spinner.setBackground(roundRect(Color.WHITE, BORDER, 10));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        return spinner;
    }

    private LinearLayout.LayoutParams fieldParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private String friendlyTime(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            return "—";
        }
        return clean.replace('T', ' ').length() > 16
                ? clean.replace('T', ' ').substring(0, 16)
                : clean.replace('T', ' ');
    }

    private String compact(String value, int limit) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            return "—";
        }
        return clean.length() <= limit ? clean : clean.substring(0, limit) + "…";
    }

    private String readable(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
