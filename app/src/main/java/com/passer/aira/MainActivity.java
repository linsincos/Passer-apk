package com.passer.aira;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int TEXT = Color.rgb(15, 23, 42);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int SURFACE = Color.rgb(248, 250, 252);
    private static final int BORDER = Color.rgb(226, 232, 240);

    private final List<ChatMessage> history = new ArrayList<>();

    private AppStorage storage;
    private AgentRunner runner;
    private ScrollView scrollView;
    private LinearLayout messageList;
    private EditText input;
    private Button sendButton;
    private TextView statusText;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storage = new AppStorage(this);
        runner = new AgentRunner(new AgentTools(this, storage), storage);
        history.addAll(storage.loadHistory());
        setContentView(buildUi());
        renderHistory();
        updateProviderStatus();
        if (!storage.disclosureAccepted()) {
            showDisclosure();
        } else if (storage.loadConfig().apiKey.isEmpty()) {
            input.postDelayed(this::showSettings, 350);
        }
    }

    @Override
    protected void onDestroy() {
        if (runner != null) {
            runner.close();
        }
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(dp(12), dp(12), dp(12), dp(12));
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setGravity(Gravity.BOTTOM);
        scrollView.addView(messageList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        statusText = new TextView(this);
        statusText.setTextColor(MUTED);
        statusText.setTextSize(12);
        statusText.setPadding(dp(18), dp(2), dp(18), dp(5));
        statusText.setSingleLine(true);
        root.addView(statusText);

        root.addView(buildComposer(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(8), dp(8), dp(8));
        header.setBackgroundColor(Color.WHITE);
        header.setElevation(dp(2));

        TextView mark = new TextView(this);
        mark.setText("A");
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(Color.WHITE);
        mark.setTextSize(19);
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mark.setBackground(roundRect(BLUE, BLUE, 14));
        header.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(20);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.agent_subtitle));
        subtitle.setTextSize(12);
        subtitle.setTextColor(MUTED);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button menu = compactButton("⋮");
        menu.setContentDescription("更多");
        menu.setOnClickListener(this::showMenu);
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(44)));

        Button settings = compactButton("设置");
        settings.setOnClickListener(view -> showSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(68), dp(44)));
        return header;
    }

    private View buildComposer() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.BOTTOM);
        wrapper.setPadding(dp(12), dp(8), dp(12), dp(12));
        wrapper.setBackgroundColor(Color.WHITE);

        input = new EditText(this);
        input.setHint(getString(R.string.message_hint));
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setBackground(roundRect(Color.WHITE, BORDER, 18));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || (enter && !event.isShiftPressed())) {
                send();
                return true;
            }
            return false;
        });
        wrapper.addView(input, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = new Button(this);
        sendButton.setText(getString(R.string.send));
        sendButton.setTextColor(Color.WHITE);
        sendButton.setTextSize(14);
        sendButton.setAllCaps(false);
        sendButton.setBackground(roundRect(BLUE, BLUE, 18));
        sendButton.setOnClickListener(view -> {
            if (busy) {
                runner.cancel();
                statusText.setText("正在安全停止…");
            } else {
                send();
            }
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(76), dp(48));
        sendParams.setMargins(dp(8), 0, 0, 0);
        wrapper.addView(sendButton, sendParams);
        return wrapper;
    }

    private void send() {
        if (busy) {
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        ModelConfig config = storage.loadConfig();
        if (config.apiKey.isEmpty()) {
            Toast.makeText(this, "请先设置模型和 API Key。", Toast.LENGTH_SHORT).show();
            showSettings();
            return;
        }

        input.setText("");
        ChatMessage userMessage = new ChatMessage("user", text);
        history.add(userMessage);
        storage.saveHistory(history);
        addBubble(userMessage);
        setBusy(true);

        runner.run(config, new ArrayList<>(history), new AgentRunner.Listener() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> statusText.setText(status));
            }

            @Override
            public void onComplete(String answer) {
                runOnUiThread(() -> {
                    ChatMessage message = new ChatMessage("assistant", answer);
                    history.add(message);
                    storage.saveHistory(history);
                    addBubble(message);
                    setBusy(false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    ChatMessage error = new ChatMessage("assistant", "处理未完成：" + message);
                    history.add(error);
                    storage.saveHistory(history);
                    addBubble(error);
                    setBusy(false);
                });
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        input.setEnabled(!value);
        sendButton.setText(value ? getString(R.string.stop) : getString(R.string.send));
        if (!value) {
            statusText.setText("");
            input.requestFocus();
        }
    }

    private void renderHistory() {
        messageList.removeAllViews();
        if (history.isEmpty()) {
            TextView welcome = new TextView(this);
            welcome.setText(getString(R.string.welcome_message));
            welcome.setTextColor(TEXT);
            welcome.setTextSize(16);
            welcome.setLineSpacing(0, 1.18f);
            welcome.setPadding(dp(18), dp(16), dp(18), dp(16));
            welcome.setBackground(roundRect(Color.WHITE, BORDER, 18));
            welcome.setTag("welcome");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, dp(18), 0, dp(12));
            messageList.addView(welcome, params);
            return;
        }
        for (ChatMessage message : history) {
            addBubble(message);
        }
    }

    private void addBubble(ChatMessage message) {
        if (messageList.getChildCount() == 1
                && "welcome".equals(messageList.getChildAt(0).getTag())) {
            messageList.removeAllViews();
        }
        boolean user = "user".equals(message.role);
        TextView bubble = new TextView(this);
        bubble.setText(message.content);
        bubble.setTextColor(user ? Color.WHITE : TEXT);
        bubble.setTextSize(16);
        bubble.setTextIsSelectable(true);
        bubble.setLineSpacing(0, 1.12f);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(roundRect(user ? BLUE : Color.WHITE, user ? BLUE : BORDER, 18));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.addView(bubble, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(user ? dp(44) : 0, dp(5), user ? 0 : dp(44), dp(5));
        messageList.addView(row, rowParams);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void showSettings() {
        ModelConfig current = storage.loadConfig();
        LinearLayout content = dialogContent();

        TextView providerLabel = fieldLabel("模型服务商");
        content.addView(providerLabel);
        Spinner provider = new Spinner(this);
        String[] providerNames = {"DeepSeek", "OpenAI", "Anthropic"};
        provider.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, providerNames));
        provider.setSelection(ModelConfig.OPENAI.equals(current.provider)
                ? 1 : ModelConfig.ANTHROPIC.equals(current.provider) ? 2 : 0);
        content.addView(provider, fieldParams());

        content.addView(fieldLabel("模型名称"));
        EditText model = field(current.model, false);
        content.addView(model, fieldParams());

        content.addView(fieldLabel("API Key"));
        EditText apiKey = field("", true);
        apiKey.setHint(current.apiKey.isEmpty() ? "请输入 API Key" : "已安全保存；留空则不修改");
        content.addView(apiKey, fieldParams());

        TextView note = new TextView(this);
        note.setText(getString(R.string.key_storage_note));
        note.setTextColor(MUTED);
        note.setTextSize(12);
        note.setPadding(0, dp(4), 0, 0);
        content.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Aira 设置")
                .setView(content)
                .setNegativeButton("取消", null)
                .setNeutralButton("清除 Key", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                storage.clearApiKey();
                apiKey.setText("");
                apiKey.setHint("API Key 已清除");
                updateProviderStatus();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String providerId = provider.getSelectedItemPosition() == 1
                        ? ModelConfig.OPENAI
                        : provider.getSelectedItemPosition() == 2
                        ? ModelConfig.ANTHROPIC : ModelConfig.DEEPSEEK;
                String modelValue = model.getText().toString().trim();
                String keyValue = apiKey.getText().toString().trim();
                try {
                    storage.saveConfig(providerId, modelValue, keyValue, !keyValue.isEmpty());
                    updateProviderStatus();
                    dialog.dismiss();
                } catch (Exception error) {
                    Toast.makeText(this, "保存 Key 失败：" + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.show();
    }

    private void showDisclosure() {
        new AlertDialog.Builder(this)
                .setTitle("Aira 如何使用手机能力")
                .setMessage("Aira 会把你的对话直接发送给你选择的模型服务商。API Key 使用 "
                        + "Android Keystore 加密并仅保存在本机。\n\n"
                        + "Aira 不使用无障碍服务，不会后台读取或操控其他 App。打开网页、分享、"
                        + "邮件、闹钟、日程和地图前会显示确认；发送、保存等最终动作仍由系统 App "
                        + "交给你完成。\n\n请勿在对话中发送密码、验证码或其他秘密。")
                .setCancelable(false)
                .setNegativeButton("退出", (dialog, which) -> finish())
                .setPositiveButton("了解并继续", (dialog, which) -> {
                    storage.acceptDisclosure();
                    if (storage.loadConfig().apiKey.isEmpty()) {
                        showSettings();
                    }
                })
                .show();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("清空本机对话");
        menu.getMenu().add("查看隐私说明");
        menu.setOnMenuItemClickListener(item -> {
            if ("清空本机对话".contentEquals(item.getTitle())) {
                new AlertDialog.Builder(this)
                        .setTitle("清空本机对话？")
                        .setMessage("长期记忆和 API Key 不受影响。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("清空", (dialog, which) -> {
                            history.clear();
                            storage.clearHistory();
                            renderHistory();
                        })
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("隐私说明")
                        .setMessage("对话仅保存在本机，但模型请求会发送给你选择的服务商。"
                                + "API Key 由 Android Keystore 加密。Aira 不读取短信、联系人、"
                                + "相册、通知或其他 App 屏幕，也不会自动发送或提交外部内容。")
                        .setPositiveButton("知道了", null)
                        .show();
            }
            return true;
        });
        menu.show();
    }

    private void updateProviderStatus() {
        if (statusText == null) {
            return;
        }
        ModelConfig config = storage.loadConfig();
        statusText.setText(getString(
                R.string.provider_status,
                ModelConfig.displayName(config.provider),
                config.model,
                config.apiKey.isEmpty() ? getString(R.string.key_missing_suffix) : ""
        ));
    }

    private LinearLayout dialogContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(24);
        content.setPadding(horizontal, dp(6), horizontal, 0);
        return content;
    }

    private TextView fieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(TEXT);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(10), 0, dp(4));
        return label;
    }

    private EditText field(String value, boolean password) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setTextColor(TEXT);
        field.setSingleLine(true);
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setBackground(roundRect(Color.WHITE, BORDER, 10));
        if (password) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return field;
    }

    private LinearLayout.LayoutParams fieldParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(BLUE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
