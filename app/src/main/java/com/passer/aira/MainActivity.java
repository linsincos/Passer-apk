package com.passer.aira;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String CRASH_PREFS = "aira_crash";
    private static final String LAST_CRASH = "last_crash";
    private static final int REQUEST_ATTACHMENTS = 9104;
    private static final int MAX_ATTACHMENTS = 3;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 512 * 1024;
    private static boolean crashRecorderInstalled;

    private static final int BLUE = Color.rgb(91, 92, 226);
    private static final int BLUE_DARK = Color.rgb(66, 68, 199);
    private static final int TEXT = Color.rgb(24, 26, 32);
    private static final int MUTED = Color.rgb(124, 129, 139);
    private static final int SURFACE = Color.rgb(247, 248, 250);
    private static final int BORDER = Color.rgb(232, 234, 240);
    private static final int USER_BUBBLE = Color.rgb(236, 238, 250);
    private static final int SOFT_BLUE = Color.rgb(243, 244, 255);

    private final List<ChatMessage> history = new ArrayList<>();
    private final List<AttachmentData> pendingAttachments = new ArrayList<>();

    private AppStorage storage;
    private AgentRunner runner;
    private ScrollView scrollView;
    private LinearLayout messageList;
    private EditText input;
    private TextView sendButton;
    private TextView statusText;
    private TextView attachmentButton;
    private TextView attachmentView;
    private String currentConversationId;
    private String currentConversationTitle;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashRecorder();
        String previousCrash = getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
                .getString(LAST_CRASH, "");
        if (previousCrash != null && !previousCrash.trim().isEmpty()) {
            showStartupFailureDetail(previousCrash.trim());
            return;
        }
        try {
            initializeApp();
        } catch (RuntimeException | LinkageError error) {
            showStartupFailure(error);
        }
    }

    private void initializeApp() {
        configureSystemBars();
        storage = new AppStorage(this);
        runner = new AgentRunner(new AgentTools(this, storage), storage);
        Conversation active = storage.loadActiveConversation();
        currentConversationId = active.id;
        currentConversationTitle = active.title;
        history.addAll(active.messages);
        View content = buildUi();
        setContentView(content);
        applySystemInsets(content);
        renderHistory();
        updateProviderStatus();
        if (!storage.disclosureAccepted()) {
            showDisclosure();
        } else if (storage.loadConfig().apiKey.isEmpty()) {
            input.postDelayed(this::safeShowSettings, 350);
        }
    }

    private void showStartupFailure(Throwable error) {
        Log.e("AiraStartup", "Aira startup failed", error);
        if (runner != null) {
            runner.close();
            runner = null;
        }

        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String detail = cause.getClass().getSimpleName();
        if (cause.getMessage() != null && !cause.getMessage().trim().isEmpty()) {
            detail += "：\n" + cause.getMessage().trim();
        }
        showStartupFailureDetail(detail);
    }

    private void showStartupFailureDetail(String detail) {
        String visibleDetail = detail == null ? "" : detail.trim();
        if (visibleDetail.length() > 6000) {
            visibleDetail = visibleDetail.substring(0, 6000);
        }

        LinearLayout fallback = new LinearLayout(this);
        fallback.setOrientation(LinearLayout.VERTICAL);
        fallback.setGravity(Gravity.CENTER);
        fallback.setFitsSystemWindows(true);
        fallback.setPadding(dp(28), dp(48), dp(28), dp(32));
        fallback.setBackgroundColor(SURFACE);

        TextView title = new TextView(this);
        title.setText(getString(R.string.startup_failed));
        title.setTextColor(TEXT);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        fallback.addView(title);

        TextView message = new TextView(this);
        message.setText(detail);
        message.setTextColor(TEXT);
        message.setTextSize(16);
        message.setTextIsSelectable(true);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(22), 0, dp(22));
        ScrollView details = new ScrollView(this);
        details.setFillViewport(true);
        details.addView(message, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        fallback.addView(details, detailParams);

        TextView retry = new TextView(this);
        retry.setText(getString(R.string.retry));
        retry.setTextColor(Color.WHITE);
        retry.setTextSize(16);
        retry.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(roundRect(BLUE_DARK, BLUE_DARK, 22));
        retry.setOnClickListener(view -> {
            getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
                    .edit()
                    .remove(LAST_CRASH)
                    .apply();
            recreate();
        });
        fallback.addView(retry, new LinearLayout.LayoutParams(dp(150), dp(48)));

        setContentView(fallback);
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    private void installCrashRecorder() {
        synchronized (MainActivity.class) {
            if (crashRecorderInstalled) {
                return;
            }
            Context application = getApplicationContext();
            Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                try {
                    StringWriter value = new StringWriter();
                    error.printStackTrace(new PrintWriter(value));
                    String trace = value.toString();
                    if (trace.length() > 8000) {
                        trace = trace.substring(0, 8000);
                    }
                    application.getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
                            .edit()
                            .putString(LAST_CRASH, trace)
                            .commit();
                } catch (RuntimeException ignored) {
                    // Preserve the platform crash path even if diagnostic persistence fails.
                } finally {
                    if (previous != null) {
                        previous.uncaughtException(thread, error);
                    } else {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                }
            });
            crashRecorderInstalled = true;
        }
    }

    @Override
    protected void onDestroy() {
        if (runner != null) {
            runner.close();
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ATTACHMENTS || resultCode != RESULT_OK || data == null) {
            return;
        }

        List<Uri> selected = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                selected.add(clipData.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            selected.add(data.getData());
        }

        for (Uri uri : selected) {
            if (pendingAttachments.size() >= MAX_ATTACHMENTS) {
                Toast.makeText(this, "最多添加 " + MAX_ATTACHMENTS + " 个附件。",
                        Toast.LENGTH_SHORT).show();
                break;
            }
            try {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (SecurityException ignored) {
                    // Some document providers only grant access for the current activity.
                }
                pendingAttachments.add(readAttachment(uri));
            } catch (IOException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        updateAttachmentView();
    }

    @SuppressWarnings("deprecation")
    private void openAttachmentPicker() {
        if (busy) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_ATTACHMENTS);
        } catch (Exception error) {
            Toast.makeText(this, "手机上没有可用的文件选择器。", Toast.LENGTH_LONG).show();
        }
    }

    private AttachmentData readAttachment(Uri uri) throws IOException {
        String name = "附件";
        long declaredSize = -1;
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                    name = cursor.getString(nameColumn);
                }
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    declaredSize = cursor.getLong(sizeColumn);
                }
            }
        } catch (SecurityException error) {
            throw new IOException("无法读取这个附件。");
        }

        String mime = getContentResolver().getType(uri);
        if (mime == null || mime.trim().isEmpty()) {
            mime = "application/octet-stream";
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        boolean image = "image/jpeg".equals(mime)
                || "image/png".equals(mime)
                || "image/webp".equals(mime)
                || "image/gif".equals(mime);
        boolean text = mime.startsWith("text/")
                || "application/json".equals(mime)
                || "application/xml".equals(mime)
                || "application/javascript".equals(mime)
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".txt")
                || lowerName.endsWith(".csv")
                || lowerName.endsWith(".json")
                || lowerName.endsWith(".xml")
                || lowerName.endsWith(".java")
                || lowerName.endsWith(".kt")
                || lowerName.endsWith(".py")
                || lowerName.endsWith(".js")
                || lowerName.endsWith(".html")
                || lowerName.endsWith(".css");
        if (!image && !text) {
            throw new IOException("“" + name + "”暂不支持；请选择图片或文本文件。");
        }

        int limit = image ? MAX_IMAGE_BYTES : MAX_TEXT_BYTES;
        if (declaredSize > limit) {
            throw new IOException("“" + name + "”过大，图片上限 5 MB，文本上限 512 KB。");
        }
        byte[] bytes;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("无法打开“" + name + "”。");
            }
            bytes = readLimited(stream, limit, name);
        } catch (SecurityException error) {
            throw new IOException("没有读取“" + name + "”的权限。");
        }

        if (image) {
            return AttachmentData.image(
                    name,
                    mime,
                    bytes.length,
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
            );
        }
        return AttachmentData.text(
                name,
                mime,
                bytes.length,
                new String(bytes, StandardCharsets.UTF_8)
        );
    }

    private byte[] readLimited(InputStream stream, int limit, String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = stream.read(buffer)) != -1) {
            total += count;
            if (total > limit) {
                throw new IOException("“" + name + "”过大，图片上限 5 MB，文本上限 512 KB。");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);

        View header = buildHeader();
        header.setMinimumHeight(dp(56));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        int pagePadding = compactWidth() ? dp(10) : dp(16);
        scrollView.setPadding(pagePadding, dp(10), pagePadding, dp(10));
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setGravity(Gravity.TOP);
        scrollView.addView(messageList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        statusText = new TextView(this);
        statusText.setTextColor(BLUE_DARK);
        statusText.setTextSize(14);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(18), dp(7), dp(18), dp(3));
        statusText.setMaxLines(2);
        statusText.setVisibility(View.GONE);
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
        header.setPadding(dp(8), dp(4), dp(8), dp(4));
        header.setBackgroundColor(SURFACE);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(19);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView historyButton = iconButton(getString(R.string.history_symbol));
        historyButton.setContentDescription(getString(R.string.conversation_history));
        historyButton.setOnClickListener(view -> showHistoryDrawer());
        header.addView(historyButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = iconButton(getString(R.string.settings_symbol));
        settings.setContentDescription(getString(R.string.settings_label));
        settings.setOnClickListener(view -> safeShowSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private View buildComposer() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int sidePadding = compactWidth() ? dp(8) : dp(12);
        wrapper.setPadding(sidePadding, dp(5), sidePadding, dp(10));
        wrapper.setBackgroundColor(SURFACE);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(8), dp(5), dp(8), dp(7));
        composer.setBackground(roundRect(Color.WHITE, BORDER, 24));
        composer.setElevation(dp(5));

        attachmentView = new TextView(this);
        attachmentView.setTextColor(BLUE_DARK);
        attachmentView.setTextSize(14);
        attachmentView.setSingleLine(true);
        attachmentView.setGravity(Gravity.CENTER_VERTICAL);
        attachmentView.setPadding(dp(12), dp(8), dp(12), dp(8));
        attachmentView.setBackground(roundRect(SOFT_BLUE, SOFT_BLUE, 15));
        attachmentView.setVisibility(View.GONE);
        attachmentView.setOnClickListener(view -> clearAttachments());
        LinearLayout.LayoutParams attachmentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        attachmentParams.setMargins(dp(4), dp(3), dp(4), dp(3));
        composer.addView(attachmentView, attachmentParams);

        input = new EditText(this);
        input.setHint(getString(R.string.message_hint));
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setPadding(dp(10), dp(8), dp(10), dp(4));
        input.setBackgroundColor(Color.TRANSPARENT);
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
        composer.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        attachmentButton = smallRoundButton(getString(R.string.add_symbol));
        attachmentButton.setContentDescription(getString(R.string.add_attachment));
        attachmentButton.setOnClickListener(view -> openAttachmentPicker());
        actions.addView(attachmentButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, dp(1), 1f));

        sendButton = new TextView(this);
        sendButton.setText(getString(R.string.send_symbol));
        sendButton.setTextColor(Color.WHITE);
        sendButton.setTextSize(23);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sendButton.setBackground(gradientRect(
                new int[]{Color.rgb(108, 110, 239), BLUE_DARK}, 21));
        sendButton.setOnClickListener(view -> {
            if (busy) {
                runner.cancel();
                setActivityStatus(getString(R.string.stopping_status));
            } else {
                send();
            }
        });
        actions.addView(sendButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        composer.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        wrapper.addView(composer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
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
            safeShowSettings();
            return;
        }
        if (ModelConfig.DEEPSEEK.equals(config.provider) && hasPendingImage()) {
            Toast.makeText(
                    this,
                    "DeepSeek 当前配置不支持图片附件，请改用 OpenAI/Anthropic 或移除图片。",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        input.setText("");
        ChatMessage userMessage = new ChatMessage(
                "user",
                text,
                new ArrayList<>(pendingAttachments)
        );
        clearAttachments();
        history.add(userMessage);
        saveCurrentConversation();
        addBubble(userMessage);
        setBusy(true);

        runner.run(config, new ArrayList<>(history), new AgentRunner.Listener() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> setActivityStatus(status));
            }

            @Override
            public void onComplete(String answer) {
                runOnUiThread(() -> {
                    ChatMessage message = new ChatMessage("assistant", answer);
                    history.add(message);
                    saveCurrentConversation();
                    addBubble(message);
                    setBusy(false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    ChatMessage error = new ChatMessage("assistant", "处理未完成：" + message);
                    history.add(error);
                    saveCurrentConversation();
                    addBubble(error);
                    setBusy(false);
                });
            }
        });
    }

    private void saveCurrentConversation() {
        if (Conversation.DEFAULT_TITLE.equals(currentConversationTitle)) {
            currentConversationTitle = Conversation.titleFromMessages(history);
        }
        storage.saveConversation(
                currentConversationId,
                currentConversationTitle,
                history
        );
    }

    private void setBusy(boolean value) {
        busy = value;
        input.setEnabled(!value);
        attachmentButton.setEnabled(!value);
        attachmentButton.setAlpha(value ? 0.45f : 1f);
        sendButton.setText(value
                ? getString(R.string.stop_symbol)
                : getString(R.string.send_symbol));
        if (!value) {
            setActivityStatus("");
            input.requestFocus();
        }
    }

    private void renderHistory() {
        messageList.removeAllViews();
        if (history.isEmpty()) {
            messageList.setGravity(Gravity.CENTER);
            View welcome = buildWelcome();
            welcome.setTag("welcome");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, dp(12), 0, dp(12));
            messageList.addView(welcome, params);
            return;
        }
        messageList.setGravity(Gravity.TOP);
        for (ChatMessage message : history) {
            addBubble(message);
        }
    }

    private void addBubble(ChatMessage message) {
        if (messageList.getChildCount() == 1
                && "welcome".equals(messageList.getChildAt(0).getTag())) {
            messageList.removeAllViews();
            messageList.setGravity(Gravity.TOP);
        }
        boolean user = "user".equals(message.role);
        TextView bubble = new TextView(this);
        bubble.setText(displayText(message));
        bubble.setTextColor(TEXT);
        bubble.setTextSize(16);
        bubble.setTextIsSelectable(true);
        bubble.setLineSpacing(dp(2), 1.1f);
        bubble.setPadding(
                user ? dp(14) : dp(10),
                dp(10),
                user ? dp(14) : dp(6),
                dp(10));
        if (user) {
            bubble.setBackground(roundRect(USER_BUBBLE, USER_BUBBLE, 20));
        } else {
            bubble.setBackgroundColor(Color.TRANSPARENT);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(user ? Gravity.END : Gravity.START);
        if (!user) {
            TextView avatar = avatarView(32);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            avatarParams.setMargins(0, dp(7), dp(3), 0);
            row.addView(avatar, avatarParams);
        }
        row.addView(bubble, new LinearLayout.LayoutParams(
                user ? LinearLayout.LayoutParams.WRAP_CONTENT : 0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                user ? 0f : 1f));
        if (!user) {
            View endSpace = new View(this);
            row.addView(endSpace, new LinearLayout.LayoutParams(
                    dp(24),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(user ? dp(52) : 0, dp(7), user ? 0 : dp(24), dp(7));
        messageList.addView(row, rowParams);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private String displayText(ChatMessage message) {
        if (message.attachments.isEmpty()) {
            return message.content;
        }
        StringBuilder value = new StringBuilder(message.content);
        for (AttachmentData attachment : message.attachments) {
            value.append("\n\n📎 ").append(attachment.name);
        }
        return value.toString();
    }

    private boolean hasPendingImage() {
        for (AttachmentData attachment : pendingAttachments) {
            if (attachment.isImage()) {
                return true;
            }
        }
        return false;
    }

    private void clearAttachments() {
        pendingAttachments.clear();
        updateAttachmentView();
    }

    private void updateAttachmentView() {
        if (attachmentView == null) {
            return;
        }
        if (pendingAttachments.isEmpty()) {
            attachmentView.setText("");
            attachmentView.setVisibility(View.GONE);
            return;
        }
        String label = pendingAttachments.size() == 1
                ? pendingAttachments.get(0).name
                : pendingAttachments.get(0).name + " 等 " + pendingAttachments.size() + " 个";
        attachmentView.setText(getString(R.string.attachment_chip, label));
        attachmentView.setContentDescription(
                "已添加附件：" + label + "。点击移除全部附件。"
        );
        attachmentView.setVisibility(View.VISIBLE);
    }

    private View buildWelcome() {
        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(0, dp(18), 0, dp(18));

        TextView avatar = avatarView(68);
        avatar.setTextSize(30);
        welcome.addView(avatar, new LinearLayout.LayoutParams(dp(68), dp(68)));

        TextView title = new TextView(this);
        title.setText(getString(R.string.welcome_title));
        title.setTextColor(TEXT);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(18), 0, dp(24));
        welcome.addView(title, titleParams);

        if (compactWidth()) {
            welcome.addView(suggestionColumn());
        } else {
            LinearLayout firstRow = suggestionRow(
                    promptCard("☀", getString(R.string.suggestion_plan)),
                    promptCard("✉", getString(R.string.suggestion_email))
            );
            welcome.addView(firstRow);
            LinearLayout secondRow = suggestionRow(
                    promptCard("◷", getString(R.string.suggestion_alarm)),
                    promptCard("✦", getString(R.string.suggestion_ideas))
            );
            LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            secondParams.setMargins(0, dp(8), 0, 0);
            welcome.addView(secondRow, secondParams);
        }
        return welcome;
    }

    private LinearLayout suggestionColumn() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        String[] symbols = {"☀", "✉", "◷", "✦"};
        int[] prompts = {
                R.string.suggestion_plan,
                R.string.suggestion_email,
                R.string.suggestion_alarm,
                R.string.suggestion_ideas
        };
        for (int i = 0; i < prompts.length; i++) {
            View card = promptCard(symbols[i], getString(prompts[i]));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.setMargins(0, dp(8), 0, 0);
            }
            column.addView(card, params);
        }
        return column;
    }

    private LinearLayout suggestionRow(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        leftParams.setMargins(0, 0, dp(4), 0);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rightParams.setMargins(dp(4), 0, 0, 0);
        row.addView(left, leftParams);
        row.addView(right, rightParams);
        return row;
    }

    private View promptCard(String symbol, String prompt) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), 0, dp(10), 0);
        card.setBackground(roundRect(Color.WHITE, BORDER, 17));
        card.setElevation(dp(1));
        card.setMinimumHeight(dp(64));

        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextColor(BLUE);
        icon.setTextSize(18);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(SOFT_BLUE, SOFT_BLUE, 12));
        card.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView text = new TextView(this);
        text.setText(prompt);
        text.setTextColor(TEXT);
        text.setTextSize(15);
        text.setMaxLines(3);
        text.setPadding(dp(9), 0, 0, 0);
        card.addView(text, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.setOnClickListener(view -> {
            input.setText(prompt);
            input.setSelection(input.length());
            input.requestFocus();
        });
        return card;
    }

    private void safeShowSettings() {
        try {
            showSettings();
        } catch (RuntimeException | LinkageError error) {
            showStartupFailure(error);
        }
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
                        safeShowSettings();
                    }
                })
                .show();
    }

    private void showHistoryDrawer() {
        Dialog drawer = new Dialog(this);
        drawer.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFitsSystemWindows(true);
        panel.setPadding(dp(12), dp(8), dp(12), dp(12));
        panel.setBackgroundColor(SURFACE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(getString(R.string.conversations));
        title.setTextColor(TEXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(
                0,
                dp(56),
                1f
        ));

        TextView add = iconButton(getString(R.string.add_symbol));
        add.setContentDescription(getString(R.string.new_chat));
        add.setBackground(roundRect(Color.WHITE, BORDER, 20));
        add.setOnClickListener(view -> {
            if (requestNewConversation()) {
                drawer.dismiss();
            }
        });
        top.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(top);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        List<Conversation> conversations = storage.loadConversations();
        for (Conversation conversation : conversations) {
            list.addView(buildConversationRow(conversation, drawer));
        }
        listScroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(listScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        TextView settings = drawerAction(getString(R.string.settings_label));
        settings.setOnClickListener(view -> {
            drawer.dismiss();
            safeShowSettings();
        });
        panel.addView(settings);

        TextView privacy = drawerAction(getString(R.string.privacy_label));
        privacy.setOnClickListener(view -> {
            drawer.dismiss();
            showPrivacyDialog();
        });
        panel.addView(privacy);

        drawer.setContentView(panel);
        drawer.setOnShowListener(ignored -> {
            Window window = drawer.getWindow();
            if (window == null) {
                return;
            }
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.START);
            window.setDimAmount(0.32f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(
                    Math.min(dp(360), Math.round(screenWidth * 0.88f)),
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        });
        drawer.show();
    }

    private View buildConversationRow(Conversation conversation, Dialog drawer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(4), dp(4));
        row.setBackground(roundRect(
                conversation.id.equals(currentConversationId) ? SOFT_BLUE : SURFACE,
                conversation.id.equals(currentConversationId) ? SOFT_BLUE : SURFACE,
                14
        ));

        TextView label = new TextView(this);
        label.setText(conversation.title);
        label.setTextColor(TEXT);
        label.setTextSize(16);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(8), 0, dp(8), 0);
        label.setOnClickListener(view -> loadConversation(conversation, drawer));
        row.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1f));

        TextView more = iconButton(getString(R.string.more_symbol));
        more.setTextSize(24);
        more.setContentDescription(getString(R.string.conversation_actions));
        more.setOnClickListener(view -> showConversationActions(view, conversation, drawer));
        row.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(params);
        return row;
    }

    private TextView drawerAction(String label) {
        TextView action = new TextView(this);
        action.setText(label);
        action.setTextColor(TEXT);
        action.setTextSize(16);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(dp(16), 0, dp(16), 0);
        action.setMinHeight(dp(48));
        action.setBackgroundColor(Color.TRANSPARENT);
        return action;
    }

    private void showConversationActions(
            View anchor,
            Conversation conversation,
            Dialog drawer
    ) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String rename = getString(R.string.rename);
        String delete = getString(R.string.delete);
        menu.getMenu().add(rename);
        menu.getMenu().add(delete);
        menu.setOnMenuItemClickListener(item -> {
            if (busy) {
                Toast.makeText(this, "请先停止当前任务。", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (rename.contentEquals(item.getTitle())) {
                renameConversation(conversation, drawer);
            } else {
                confirmDeleteConversation(conversation, drawer);
            }
            return true;
        });
        menu.show();
    }

    private void renameConversation(Conversation conversation, Dialog drawer) {
        EditText value = field(conversation.title, false);
        value.selectAll();
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.rename_conversation))
                .setView(value)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String title = Conversation.normalizeTitle(
                            value.getText().toString()
                    );
                    storage.renameConversation(conversation.id, title);
                    if (conversation.id.equals(currentConversationId)) {
                        currentConversationTitle = title;
                    }
                    drawer.dismiss();
                    showHistoryDrawer();
                })
                .show();
    }

    private void confirmDeleteConversation(Conversation conversation, Dialog drawer) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_conversation))
                .setMessage(conversation.title)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean deletingCurrent =
                            conversation.id.equals(currentConversationId);
                    storage.deleteConversation(conversation.id);
                    if (deletingCurrent) {
                        applyConversation(storage.loadActiveConversation());
                    }
                    drawer.dismiss();
                    showHistoryDrawer();
                })
                .show();
    }

    private void loadConversation(Conversation conversation, Dialog drawer) {
        if (busy) {
            Toast.makeText(this, "请先停止当前任务。", Toast.LENGTH_SHORT).show();
            return;
        }
        storage.setActiveConversation(conversation.id);
        applyConversation(conversation);
        drawer.dismiss();
    }

    private void applyConversation(Conversation conversation) {
        currentConversationId = conversation.id;
        currentConversationTitle = conversation.title;
        history.clear();
        history.addAll(conversation.messages);
        input.setText("");
        clearAttachments();
        renderHistory();
    }

    private boolean requestNewConversation() {
        if (busy) {
            Toast.makeText(this, "请先停止当前任务。", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (history.isEmpty()) {
            input.setText("");
            input.requestFocus();
            return true;
        }
        saveCurrentConversation();
        applyConversation(storage.startConversation());
        input.requestFocus();
        return true;
    }

    private void confirmClearConversation() {
        new AlertDialog.Builder(this)
                .setTitle("清空本机对话？")
                .setMessage("长期记忆和 API Key 不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> clearConversation())
                .show();
    }

    private void clearConversation() {
        history.clear();
        currentConversationTitle = Conversation.DEFAULT_TITLE;
        storage.saveConversation(
                currentConversationId,
                currentConversationTitle,
                history
        );
        input.setText("");
        clearAttachments();
        renderHistory();
    }

    private void showPrivacyDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.privacy_label))
                .setMessage("对话仅保存在本机，但模型请求会发送给你选择的服务商。"
                        + "API Key 由 Android Keystore 加密。Aira 不读取短信、联系人、"
                        + "相册、通知或其他 App 屏幕，也不会自动发送或提交外部内容。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void updateProviderStatus() {
        ModelConfig config = storage.loadConfig();
        String provider = ModelConfig.displayName(config.provider);
        input.setContentDescription(config.apiKey.isEmpty()
                ? getString(R.string.configure_model)
                : getString(R.string.provider_status, provider, config.model, ""));
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

    private TextView iconButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setTextSize(20);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private TextView smallRoundButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setTextSize(21);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundRect(Color.rgb(246, 247, 250), BORDER, 19));
        return button;
    }

    private TextView avatarView(int sizeDp) {
        TextView avatar = new TextView(this);
        avatar.setText(getString(R.string.aira_mark));
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(Math.max(15, sizeDp / 3f));
        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(gradientRect(
                new int[]{
                        Color.rgb(110, 190, 255),
                        Color.rgb(106, 102, 239),
                        Color.rgb(180, 112, 232)
                },
                sizeDp / 2
        ));
        avatar.setElevation(dp(2));
        return avatar;
    }

    private void setActivityStatus(String value) {
        String text = value == null ? "" : value.trim();
        statusText.setText(text);
        statusText.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(SURFACE);
        window.setNavigationBarColor(SURFACE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    @SuppressWarnings("deprecation")
    private void applySystemInsets(View root) {
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom()
            );
            return insets;
        });
        root.requestApplyInsets();
    }

    private boolean compactWidth() {
        return getResources().getConfiguration().screenWidthDp < 360;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private GradientDrawable gradientRect(int[] colors, int radiusDp) {
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                colors
        );
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
