package com.dbuild.net.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class ThemeManager {

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";

    public static final String THEME_DARK = "dark";
    public static final String THEME_WHITE = "white";

    private static final int DARK_BACKGROUND = Color.parseColor("#1E1E1E");
    private static final int DARK_TEXT = Color.parseColor("#E0E0E0");
    private static final int DARK_PANEL = Color.parseColor("#2D2D2D");
    private static final int DARK_ACCENT = Color.parseColor("#4FC3F7");
    private static final int DARK_DIVIDER = Color.parseColor("#3E3E3E");
    private static final int DARK_BUTTON = Color.parseColor("#3A3A3A");
    private static final int DARK_CARD = Color.parseColor("#333333");

    private static final int WHITE_BACKGROUND = Color.parseColor("#FAFAFA");
    private static final int WHITE_TEXT = Color.parseColor("#212121");
    private static final int WHITE_PANEL = Color.parseColor("#FFFFFF");
    private static final int WHITE_ACCENT = Color.parseColor("#1976D2");
    private static final int WHITE_DIVIDER = Color.parseColor("#E0E0E0");
    private static final int WHITE_BUTTON = Color.parseColor("#E3F2FD");
    private static final int WHITE_CARD = Color.parseColor("#FFFFFF");

    private static volatile ThemeManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private String currentTheme;

    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.currentTheme = prefs.getString(KEY_THEME, THEME_DARK);
    }

    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ThemeManager.class) {
                if (instance == null) {
                    instance = new ThemeManager(context);
                }
            }
        }
        return instance;
    }

    public void setTheme(String theme) {
        if (theme == null) {
            theme = THEME_DARK;
        }
        if (!THEME_DARK.equals(theme) && !THEME_WHITE.equals(theme)) {
            theme = THEME_DARK;
        }
        this.currentTheme = theme;
        prefs.edit().putString(KEY_THEME, theme).apply();
    }

    public String getTheme() {
        return currentTheme;
    }

    public boolean isDarkTheme() {
        return THEME_DARK.equals(currentTheme);
    }

    public void applyTheme(Activity activity) {
        if (activity == null) {
            return;
        }
        if (isDarkTheme()) {
            activity.setTheme(android.R.style.Theme_DeviceDefault);
        } else {
            activity.setTheme(android.R.style.Theme_DeviceDefault_Light);
        }
    }

    public int getBackgroundColor() {
        return isDarkTheme() ? DARK_BACKGROUND : WHITE_BACKGROUND;
    }

    public int getTextColor() {
        return isDarkTheme() ? DARK_TEXT : WHITE_TEXT;
    }

    public int getPanelColor() {
        return isDarkTheme() ? DARK_PANEL : WHITE_PANEL;
    }

    public int getAccentColor() {
        return isDarkTheme() ? DARK_ACCENT : WHITE_ACCENT;
    }

    public int getDividerColor() {
        return isDarkTheme() ? DARK_DIVIDER : WHITE_DIVIDER;
    }

    public int getButtonColor() {
        return isDarkTheme() ? DARK_BUTTON : WHITE_BUTTON;
    }

    public int getCardColor() {
        return isDarkTheme() ? DARK_CARD : WHITE_CARD;
    }

    public int getResourceColor(String colorName) {
        switch (colorName) {
            case "background":
                return getBackgroundColor();
            case "text":
                return getTextColor();
            case "panel":
                return getPanelColor();
            case "accent":
                return getAccentColor();
            case "divider":
                return getDividerColor();
            case "button":
                return getButtonColor();
            case "card":
                return getCardColor();
            default:
                return getBackgroundColor();
        }
    }

    public void toggleTheme() {
        if (isDarkTheme()) {
            setTheme(THEME_WHITE);
        } else {
            setTheme(THEME_DARK);
        }
    }

    public String getThemeDisplayName() {
        return isDarkTheme() ? "Dark" : "White";
    }

    public float getBackgroundLuminance() {
        if (isDarkTheme()) {
            return 0.117f;
        } else {
            return 0.960f;
        }
    }

    public boolean shouldUseLightStatusBar() {
        return !isDarkTheme();
    }

    public int getStatusBarColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#141414");
        } else {
            return Color.parseColor("#E0E0E0");
        }
    }

    public int getNavigationBarColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#1A1A1A");
        } else {
            return Color.parseColor("#F5F5F5");
        }
    }

    public int getHintTextColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#757575");
        } else {
            return Color.parseColor("#9E9E9E");
        }
    }

    public int getSecondaryTextColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#BDBDBD");
        } else {
            return Color.parseColor("#757575");
        }
    }

    public int getErrorColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#EF5350");
        } else {
            return Color.parseColor("#D32F2F");
        }
    }

    public int getSuccessColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#66BB6A");
        } else {
            return Color.parseColor("#388E3C");
        }
    }

    public int getWarningColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#FFA726");
        } else {
            return Color.parseColor("#F57C00");
        }
    }

    public int getOverlayColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#CC000000");
        } else {
            return Color.parseColor("#CCFFFFFF");
        }
    }

    public int getRippleColor() {
        if (isDarkTheme()) {
            return Color.parseColor("#33FFFFFF");
        } else {
            return Color.parseColor("#1F000000");
        }
    }
}
