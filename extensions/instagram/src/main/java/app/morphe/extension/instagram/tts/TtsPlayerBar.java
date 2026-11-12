package app.morphe.extension.instagram.tts;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

/** Waveform player bar: rounded bubble, play/pause glyph, 40 waveform bars, mm:ss duration. */
final class TtsPlayerBar extends View {
    private final String key;
    private float[] peaks;
    private final long durationMs;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint restPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius = Dim.dp(18);

    private long positionMs;
    private volatile boolean playing;

    TtsPlayerBar(Context context, String key, float[] peaks, long durationMs) {
        super(context);
        this.key = key;
        this.peaks = peaks == null ? TtsCache.silentPeaks() : peaks;
        this.durationMs = Math.max(0, durationMs);
        setClickable(true);
        setFocusable(true);
        applyTheme();
    }

    String key() {
        return key;
    }

    void setPeaks(float[] newPeaks) {
        if (newPeaks != null) {
            peaks = newPeaks;
            invalidate();
        }
    }

    void setPlaying(boolean isPlaying) {
        if (playing != isPlaying) {
            playing = isPlaying;
            invalidate();
        }
    }

    void setProgress(long positionMillis) {
        long clamped = Math.min(durationMs, Math.max(0, positionMillis));
        if (clamped != positionMs) {
            positionMs = clamped;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, Dim.dp(44));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        rect.set(0, 0, width, height);
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint);

        float glyphSize = Dim.dp(20);
        float glyphX = Dim.dp(16);
        float centerY = height / 2f;
        if (playing) {
            float bar = glyphSize / 3.4f;
            canvas.drawRoundRect(glyphX, centerY - glyphSize / 2, glyphX + bar,
                    centerY + glyphSize / 2, bar / 3, bar / 3, glyphPaint);
            canvas.drawRoundRect(glyphX + bar * 2, centerY - glyphSize / 2, glyphX + bar * 3,
                    centerY + glyphSize / 2, bar / 3, bar / 3, glyphPaint);
        } else {
            android.graphics.Path triangle = new android.graphics.Path();
            triangle.moveTo(glyphX, centerY - glyphSize / 2);
            triangle.lineTo(glyphX, centerY + glyphSize / 2);
            triangle.lineTo(glyphX + glyphSize * 0.85f, centerY);
            triangle.close();
            canvas.drawPath(triangle, glyphPaint);
        }

        String duration = formatDuration(durationMs);
        float durationWidth = measureDuration(duration);
        float waveformLeft = glyphX + glyphSize + Dim.dp(10);
        float waveformRight = width - Dim.dp(12) - durationWidth;
        int count = peaks.length;
        float gap = Dim.dp(2);
        float barWidth = Math.max(Dim.dp(1.5f),
                (waveformRight - waveformLeft - gap * (count - 1)) / count);
        float playedX = waveformLeft
                + (waveformRight - waveformLeft) * (durationMs == 0 ? 0 : (float) positionMs / durationMs);
        float maxBarHeight = Dim.dp(22);
        for (int i = 0; i < count; i++) {
            float left = waveformLeft + i * (barWidth + gap);
            float amplitude = Math.max(0.08f, Math.min(1f, peaks[i]));
            float barHeight = amplitude * maxBarHeight;
            Paint paint = left + barWidth / 2 <= playedX ? fillPaint : restPaint;
            canvas.drawRoundRect(left, centerY - barHeight / 2, left + barWidth,
                    centerY + barHeight / 2, barWidth / 2, barWidth / 2, paint);
        }

        drawCenteredText(canvas, duration, width - Dim.dp(12) - durationWidth, centerY);
    }

    private float measureDuration(String text) {
        glyphPaint.setTextSize(Dim.dp(11));
        return glyphPaint.measureText(text);
    }

    private void drawCenteredText(Canvas canvas, String text, float right, float centerY) {
        glyphPaint.setTextSize(Dim.dp(11));
        float x = right - glyphPaint.measureText(text);
        canvas.drawText(text, x, centerY + glyphPaint.getTextSize() / 3f, glyphPaint);
    }

    private void applyTheme() {
        boolean dark = darkMode();
        int accent = themed("igds_color_link", 0xff0095f6);
        int bubble = themed("igds_color_secondary_background", dark ? 0xff363636 : 0xffefefef);
        int muted = dark ? 0x66ffffff : 0x66000000;
        fillPaint.setColor(accent);
        restPaint.setColor(muted);
        glyphPaint.setColor(accent);
        backgroundPaint.setColor(bubble);
    }

    static boolean darkMode() {
        try {
            return (Utils.getContext().getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ex) {
            return false;
        }
    }

    static int themed(String attribute, int fallback) {
        try {
            android.content.Context context = Utils.getContext();
            TypedValue value = new TypedValue();
            int attrId = ResourceUtils.getAttrIdentifier(attribute);
            if (attrId != 0 && context.getTheme().resolveAttribute(attrId, value, true)) {
                if (value.resourceId != 0) return context.getColor(value.resourceId);
                if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && value.type <= TypedValue.TYPE_LAST_COLOR_INT) return value.data;
            }
            int colorId = ResourceUtils.getColorIdentifier(attribute);
            if (colorId != 0) return context.getColor(colorId);
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    static int linkColor() {
        return themed("igds_color_link", darkMode() ? 0xffe0f1ff : 0xff0095f6);
    }

    static int secondaryTextColor() {
        return themed("igds_color_secondary_text", darkMode() ? 0xffb3b3b3 : 0xff737373);
    }

    static String formatDuration(long millis) {
        long seconds = (millis + 500) / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
