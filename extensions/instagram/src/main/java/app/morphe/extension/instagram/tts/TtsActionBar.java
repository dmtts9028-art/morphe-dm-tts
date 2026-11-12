package app.morphe.extension.instagram.tts;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

/** Adds the DM TTS settings button to the inbox action bar. */
final class TtsActionBar {
    private static final String ICON_DRAWABLE = "instagram_settings_pano_filled_24";
    private static final String BUTTON_TAG = "dmtts_action_button";

    private TtsActionBar() { }

    /** Called from smali with the inflated IgdsActionBar content view. */
    public static void addButton(ViewGroup actionBar) {
        TtsRuntime.init();
        try {
            if (actionBar == null || Utils.getContext() == null) return;
            if (actionBar.findViewWithTag(BUTTON_TAG) != null) return;
            ImageView button = new ImageView(actionBar.getContext());
            button.setTag(BUTTON_TAG);
            Drawable icon = ResourceUtils.getDrawable(ICON_DRAWABLE);
            if (icon == null) {
                Logger.printDebug(() -> "TtsActionBar: icon " + ICON_DRAWABLE + " missing");
                return;
            }
            button.setImageDrawable(icon);
            button.setColorFilter(new PorterDuffColorFilter(
                    TtsPlayerBar.themed("igds_color_primary_icon", 0xffffffff),
                    PorterDuff.Mode.SRC_ATOP));
            button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            button.setPadding(Dim.dp(12), Dim.dp(12), Dim.dp(12), Dim.dp(12));
            button.setBackgroundResource(android.R.attr.selectableItemBackgroundBorderless);
            button.setOnClickListener(v -> TtsSettingsDialog.show());
            button.setContentDescription(TtsStr.SETTINGS);

            int size = Dim.dp(48);
            if (actionBar instanceof android.widget.LinearLayout) {
                android.widget.LinearLayout.LayoutParams params =
                        new android.widget.LinearLayout.LayoutParams(size, size);
                params.gravity = Gravity.CENTER_VERTICAL;
                actionBar.addView(button, params);
            } else if (actionBar instanceof android.widget.FrameLayout) {
                android.widget.FrameLayout.LayoutParams params =
                        new android.widget.FrameLayout.LayoutParams(size, size);
                params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                actionBar.addView(button, params);
            } else if (actionBar instanceof android.widget.RelativeLayout) {
                android.widget.RelativeLayout.LayoutParams params =
                        new android.widget.RelativeLayout.LayoutParams(size, size);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
                params.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
                actionBar.addView(button, params);
            } else {
                // Unknown container: place it with generic layout params.
                ViewGroup.LayoutParams params =
                        new ViewGroup.LayoutParams(size, size);
                actionBar.addView(button, params);
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "TtsActionBar.addButton failed", ex);
        }
    }
}
