package app.morphe.extension.instagram.tts;

import android.app.Activity;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

/**
 * Per-message injection entry point, called from the text-message binder hook with
 * (recyclerHolder, messageModel). Adds an "Écouter le message" link above the bubble and, after
 * first tap, a voice-message-like player bar. Every failure path logs and returns: never crash
 * Instagram.
 */
@SuppressWarnings("unused")
public final class MessageTtsView {
    private static final String LINK_TAG = "dmtts_link";
    private static final String PLAYER_TAG = "dmtts_player";
    /** Bubble resource id; discovered against Instagram 452.1.0.53.64 (see Fingerprints.kt). */
    private static final String BUBBLE_ID = "message_content_text";

    private static final Map<String, String> textByKey = new ConcurrentHashMap<>();
    private static final Map<String, String> threadByMessage = new ConcurrentHashMap<>();
    private static final List<WeakReference<View>> attachedLinks = new CopyOnWriteArrayList<>();
    private static final Runnable TICKER = new Runnable() {
        @Override
        public void run() {
            TtsPlayer.get().pushProgress();
            Utils.runOnMainThreadDelayed(this, 250);
        }
    };
    private static volatile boolean tickerStarted;

    private MessageTtsView() { }

    /** Called from smali at the top of the text-message bind method with (p1, p2). */
    public static void onBind(Object holder, Object model) {
        try {
            bind(holder, model);
        } catch (Throwable ex) {
            Logger.printException(() -> "MessageTtsView.onBind failed", ex);
        }
    }

    private static void bind(Object holder, Object model) {
        View bubble = firstViewOf(holder, BUBBLE_ID);
        if (!(bubble instanceof TextView)) return;
        String text = ((TextView) bubble).getText() == null
                ? "" : ((TextView) bubble).getText().toString();

        ViewGroup row = linkSibling(bubble);
        if (TextUtils.isEmpty(text)) {
            // Row recycled onto a media/blank message: drop any widgets riding on this bubble.
            if (row != null) setRow(row, false, false);
            return;
        }

        String itemId = stringGetter(model, "getItemId", "itemId");
        String key = itemId != null ? "i" + itemId : "t" + Integer.toHexString(text.hashCode());
        String threadId = directThreadKeyString(model);
        if (threadId != null) threadByMessage.put(key, threadId);

        textByKey.put(key, text);
        TtsPlayer.MessageState state = TtsPlayer.state(key);
        if (TtsPrefs.isHidden(key)) {
            if (row != null) setRow(row, false, false);
            return;
        }

        boolean generating = state.status == TtsPlayer.Status.GENERATING;
        boolean showPlayer = state.status == TtsPlayer.Status.PLAYING
                || state.status == TtsPlayer.Status.PAUSED
                || state.status == TtsPlayer.Status.DONE;
        if (row == null) row = insertRow(bubble);
        render(row, key, generating, showPlayer);
    }

    /** Re-renders the row currently attached to {@code key}, if any. */
    static void refresh(String key, TtsPlayer.MessageState state) {
        String text = textByKey.get(key);
        if (text == null) return;
        Utils.runOnMainThread(() -> {
            for (WeakReference<View> reference : attachedLinks) {
                View link = reference.get();
                if (link == null) continue;
                if (!key.equals(link.getTag(PLAYER_TAG.hashCode()))) continue;
                boolean generating = state.status == TtsPlayer.Status.GENERATING;
                boolean showPlayer = state.status == TtsPlayer.Status.PLAYING
                        || state.status == TtsPlayer.Status.PAUSED
                        || state.status == TtsPlayer.Status.DONE;
                ViewGroup row = (ViewGroup) link.getParent();
                if (row == null) continue;
                render(row, key, generating, showPlayer);
                return;
            }
        });
    }

    /** A row = [link + hide] / [player], all children tagged, sitting directly above the bubble. */
    private static ViewGroup linkSibling(View bubble) {
        View previous = previousSibling(bubble);
        return previous instanceof ViewGroup && LINK_TAG.equals(previous.getTag()) ? (ViewGroup) previous : null;
    }

    private static View previousSibling(View bubble) {
        if (!(bubble.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) bubble.getParent();
        int index = parent.indexOfChild(bubble);
        return index <= 0 ? null : parent.getChildAt(index - 1);
    }

    private static ViewGroup insertRow(View bubble) {
        ViewGroup parent;
        View anchor = bubble;
        if (bubble.getParent() instanceof ViewGroup) {
            parent = (ViewGroup) bubble.getParent();
        } else {
            return null;
        }
        LinearLayout row = new LinearLayout(Utils.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setTag(LINK_TAG);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = Dim.dp(2);
        int index = Math.max(0, parent.indexOfChild(anchor));
        parent.addView(row, index, rowParams);
        return row;
    }

    private static void render(ViewGroup row, String key, boolean generating, boolean showPlayer) {
        row.removeAllViews();
        row.setTag(PLAYER_TAG.hashCode(), key);

        TextView link = new TextView(row.getContext());
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        link.setTypeface(android.graphics.Typeface.DEFAULT);
        link.setTextColor(TtsPlayerBar.linkColor());
        link.setPadding(Dim.dp(4), Dim.dp(4), Dim.dp(4), Dim.dp(4));
        link.setText(generating ? TtsStr.GENERATING : TtsStr.LISTEN);
        if (!generating) {
            String text = textByKey.get(key);
            link.setOnClickListener(v -> {
                String threadId = threadByMessage.get(key);
                TtsCoordinator.listen(key, text, threadId);
            });
        } else {
            link.setOnClickListener(null);
        }
        row.addView(link, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (generating) {
            ProgressBar progress = new ProgressBar(row.getContext(), null,
                    android.R.attr.progressBarStyleSmall);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Dim.dp(14), Dim.dp(14));
            params.leftMargin = Dim.dp(6);
            row.addView(progress, params);
        }

        if (showPlayer) {
            TtsPlayer.MessageState state = TtsPlayer.state(key);
            TtsCache.Entry entry = state.entry != null ? state.entry : TtsCache.get(key);
            TtsPlayerBar bar = new TtsPlayerBar(row.getContext(), key,
                    entry == null ? null : entry.peaks,
                    entry == null ? 0 : entry.durationMs);
            bar.setPlaying(state.status == TtsPlayer.Status.PLAYING);
            if (entry != null) {
                bar.setProgress(state.positionSamples * 1000L / entry.sampleRate);
            }
            bar.setOnClickListener(v -> TtsCoordinator.togglePlay(key));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    0, Dim.dp(44), 1f);
            barParams.leftMargin = Dim.dp(8);
            row.addView(bar, barParams);
            TtsPlayer.get().attach(bar, key);
            startTicker();
        } else {
            TtsPlayer.get().attach(null, null);
        }

        TextView hide = new TextView(row.getContext());
        hide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hide.setText(TtsStr.HIDE);
        hide.setTextColor(TtsPlayerBar.secondaryTextColor());
        hide.setPadding(Dim.dp(8), Dim.dp(4), Dim.dp(4), Dim.dp(4));
        hide.setVisibility(showPlayer || generating ? View.VISIBLE : View.GONE);
        hide.setOnClickListener(v -> {
            TtsPrefs.hide(key);
            TtsPlayer.get().stopAll();
            ViewGroup parentRow = (ViewGroup) link.getParent();
            if (parentRow != null) setRow(parentRow, false, false);
        });
        row.addView(hide, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setRow(row, true, showPlayer);
        track(row);
    }

    private static void setRow(View row, boolean visible, boolean player) {
        row.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private static void track(ViewGroup row) {
        prune();
        for (WeakReference<View> reference : attachedLinks) {
            if (reference.get() == row) return;
        }
        attachedLinks.add(new WeakReference<>(row));
    }

    private static void prune() {
        attachedLinks.removeIf(reference -> reference.get() == null);
    }

    private static void startTicker() {
        if (tickerStarted) return;
        tickerStarted = true;
        Utils.runOnMainThreadDelayed(TICKER, 250);
    }

    // ---------------------------------------------------------------- reflection helpers

    /** First View field (any depth ≤3) whose resource id name is {@code resourceName}. */
    private static View firstViewOf(Object root, String resourceName) {
        try {
            Resources resources = Utils.getResources();
            int id = resources.getIdentifier(resourceName, "id",
                    Utils.getContext().getPackageName());
            if (id == 0) return null;
            return findViewById(root, id, 0);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static View findViewById(Object root, int id, int depth) {
        if (root == null || depth > 3) return null;
        for (Field field : fieldsOf(root.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Object value;
            try {
                field.setAccessible(true);
                value = field.get(root);
            } catch (Throwable ignored) {
                continue;
            }
            if (value == null || value == root) continue;
            if (value instanceof View && ((View) value).getId() == id) return (View) value;
            String type = field.getType().getName();
            if (!type.startsWith("android.") && !type.startsWith("java.")
                    && !type.startsWith("kotlin.") && !type.startsWith("libcore.")
                    && !(value instanceof Number) && !(value instanceof CharSequence)
                    && !(value instanceof Boolean)) {
                View found = findViewById(value, id, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Invokes no-arg String getters by name or matching single field, walking nested models. */
    private static String stringGetter(Object root, String methodName, String fieldName) {
        try {
            return (String) nestedGetter(root, methodName, fieldName, 0);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Object nestedGetter(Object root, String methodName, String fieldName, int depth) {
        if (root == null || depth > 3) return null;
        Method method = findMethod(root.getClass(), methodName);
        if (method != null && method.getReturnType() == String.class) {
            try {
                method.setAccessible(true);
                return method.invoke(root);
            } catch (Throwable ignored) {
            }
        }
        for (Field field : fieldsOf(root.getClass())) {
            if (field.getName().equalsIgnoreCase(fieldName) && field.getType() == String.class) {
                try {
                    field.setAccessible(true);
                    return field.get(root);
                } catch (Throwable ignored) {
                }
            }
        }
        for (Field field : fieldsOf(root.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            String type = field.getType().getName();
            if (!type.startsWith("com.instagram") && !type.startsWith("instagram.")) continue;
            try {
                field.setAccessible(true);
                Object nested = field.get(root);
                Object found = nestedGetter(nested, methodName, fieldName, depth + 1);
                if (found != null) return found;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** DirectThreadKey.toString() of the message's thread key, when reachable. */
    /** DirectThreadKey.toString() of an arbitrary DM model, or null. */
    static String threadKeyOf(Object model) {
        return directThreadKeyString(model);
    }

    private static String directThreadKeyString(Object model) {
        try {
            Object key = nestedObjectByTypeName(model, "DirectThreadKey", 0);
            return key == null ? null : String.valueOf(key);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Object nestedObjectByTypeName(Object root, String simpleTypeName, int depth) {
        if (root == null || depth > 3) return null;
        for (Field field : fieldsOf(root.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!field.getType().getName().endsWith(simpleTypeName)) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(root);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }
        for (Field field : fieldsOf(root.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            String type = field.getType().getName();
            if (!type.startsWith("com.instagram") && !type.startsWith("instagram.")) continue;
            try {
                field.setAccessible(true);
                Object found = nestedObjectByTypeName(field.get(root), simpleTypeName, depth + 1);
                if (found != null) return found;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == 0) {
                    return method;
                }
            }
        }
        return null;
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            java.util.Collections.addAll(fields, current.getDeclaredFields());
        }
        return fields;
    }

    static Activity activity() {
        return Utils.getActivity();
    }
}
