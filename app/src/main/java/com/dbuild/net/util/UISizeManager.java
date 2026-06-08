package com.dbuild.net.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class UISizeManager {

    private static final String PREFS_NAME = "ui_size_prefs";
    private static final String KEY_UI_SIZE = "selected_ui_size";

    public static final String SIZE_SMALL = "small";
    public static final String SIZE_MEDIUM = "medium";
    public static final String SIZE_LARGE = "large";
    public static final String SIZE_XLARGE = "xlarge";

    private static volatile UISizeManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DisplayMetrics displayMetrics;
    private String currentSize;

    private UISizeManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.currentSize = prefs.getString(KEY_UI_SIZE, SIZE_MEDIUM);
        this.displayMetrics = this.context.getResources().getDisplayMetrics();
    }

    public static UISizeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UISizeManager.class) {
                if (instance == null) {
                    instance = new UISizeManager(context);
                }
            }
        }
        return instance;
    }

    public void setUISize(String size) {
        if (size == null) {
            size = SIZE_MEDIUM;
        }
        if (!SIZE_SMALL.equals(size) && !SIZE_MEDIUM.equals(size) &&
                !SIZE_LARGE.equals(size) && !SIZE_XLARGE.equals(size)) {
            size = SIZE_MEDIUM;
        }
        this.currentSize = size;
        prefs.edit().putString(KEY_UI_SIZE, size).apply();
    }

    public String getUISize() {
        return currentSize;
    }

    public float getScaleFactor() {
        switch (currentSize) {
            case SIZE_SMALL:
                return 0.8f;
            case SIZE_MEDIUM:
                return 1.0f;
            case SIZE_LARGE:
                return 1.2f;
            case SIZE_XLARGE:
                return 1.5f;
            default:
                return 1.0f;
        }
    }

    public int getScaledFontSize(int baseSp) {
        float scaled = baseSp * getScaleFactor();
        return Math.round(scaled);
    }

    public int getScaledDimension(int baseDp) {
        float scaled = baseDp * getScaleFactor();
        return dpToPx(Math.round(scaled));
    }

    public int getScaledPadding(int baseDp) {
        float scaled = baseDp * getScaleFactor();
        return dpToPx(Math.round(scaled));
    }

    public int getScaledMargin(int baseDp) {
        float scaled = baseDp * getScaleFactor();
        return dpToPx(Math.round(scaled));
    }

    public int getScaledIconSize(int baseDp) {
        float scaled = baseDp * getScaleFactor();
        return dpToPx(Math.round(scaled));
    }

    public int getScaledButtonHeight(int baseDp) {
        float scaled = baseDp * getScaleFactor();
        return dpToPx(Math.round(scaled));
    }

    public void applyUISize(Activity activity) {
        if (activity == null) {
            return;
        }
        View rootView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView != null) {
            scaleTextViews(rootView);
            scaleButtons(rootView);
        }
    }

    public void scaleTextViews(View rootView) {
        if (rootView instanceof TextView) {
            TextView textView = (TextView) rootView;
            float currentSize = textView.getTextSize();
            float currentSp = pxToSp(currentSize);
            float scaledSp = currentSp * getScaleFactor();
            textView.setTextSize(scaledSp);
        }
        if (rootView instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) rootView;
            for (int i = 0; i < group.getChildCount(); i++) {
                scaleTextViews(group.getChildAt(i));
            }
        }
    }

    public void scaleButtons(View rootView) {
        if (rootView instanceof Button) {
            Button button = (Button) rootView;
            float currentSize = button.getTextSize();
            float currentSp = pxToSp(currentSize);
            float scaledSp = currentSp * getScaleFactor();
            button.setTextSize(scaledSp);
            scalePadding(button);
        }
        if (rootView instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) rootView;
            for (int i = 0; i < group.getChildCount(); i++) {
                scaleButtons(group.getChildAt(i));
            }
        }
    }

    public void scalePadding(View view) {
        if (view == null) return;
        int paddingLeft = view.getPaddingLeft();
        int paddingTop = view.getPaddingTop();
        int paddingRight = view.getPaddingRight();
        int paddingBottom = view.getPaddingBottom();

        float scale = getScaleFactor();
        int leftPx = Math.round(spToPx(paddingLeft) * scale);
        int topPx = Math.round(spToPx(paddingTop) * scale);
        int rightPx = Math.round(spToPx(paddingRight) * scale);
        int bottomPx = Math.round(spToPx(paddingBottom) * scale);

        view.setPadding(leftPx, topPx, rightPx, bottomPx);
    }

    public int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics));
    }

    public int spToPx(int sp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, sp, displayMetrics));
    }

    public float pxToSp(float px) {
        float scaledDensity = displayMetrics.scaledDensity;
        if (scaledDensity == 0) return 0;
        return px / scaledDensity;
    }

    public float pxToDp(float px) {
        float density = displayMetrics.density;
        if (density == 0) return 0;
        return px / density;
    }

    public String getUISizeDisplayName() {
        switch (currentSize) {
            case SIZE_SMALL:
                return "Small";
            case SIZE_MEDIUM:
                return "Medium";
            case SIZE_LARGE:
                return "Large";
            case SIZE_XLARGE:
                return "Extra Large";
            default:
                return "Medium";
        }
    }

    public int getScaledCornerRadius(int baseDp) {
        float scaled = baseDp * getScaleFactor();
        return dpToPx(Math.round(scaled));
    }

    public int getScaledElevation(int baseDp) {
        float scaled = baseDp * getScaleFactor();
        return dpToPx(Math.round(scaled));
    }

    public int getScaledLineHeight(int baseSp) {
        float scaled = baseSp * getScaleFactor() * 1.5f;
        return dpToPx(Math.round(scaled));
    }

    public int getScaledMinTouchTarget() {
        int baseDp = 48;
        float scaled = baseDp * getScaleFactor();
        return dpToPx(Math.round(scaled));
    }

    public float getScaledTextScaleX() {
        return getScaleFactor();
    }

    public int getScaledStrokeWidth(int baseDp) {
        float scaled = baseDp * getScaleFactor();
        return Math.max(1, dpToPx(Math.round(scaled)));
    }

    public void resetToDefault() {
        setUISize(SIZE_MEDIUM);
    }

    public String[] getAvailableSizes() {
        return new String[]{SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE, SIZE_XLARGE};
    }

    public String[] getAvailableSizeDisplayNames() {
        return new String[]{"Small", "Medium", "Large", "Extra Large"};
    }

    public int getSizeIndex() {
        switch (currentSize) {
            case SIZE_SMALL:
                return 0;
            case SIZE_MEDIUM:
                return 1;
            case SIZE_LARGE:
                return 2;
            case SIZE_XLARGE:
                return 3;
            default:
                return 1;
        }
    }

    public void setSizeByIndex(int index) {
        switch (index) {
            case 0:
                setUISize(SIZE_SMALL);
                break;
            case 1:
                setUISize(SIZE_MEDIUM);
                break;
            case 2:
                setUISize(SIZE_LARGE);
                break;
            case 3:
                setUISize(SIZE_XLARGE);
                break;
            default:
                setUISize(SIZE_MEDIUM);
                break;
        }
    }
}
