package com.dbuild.net;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Splash screen activity for the 3D Builder MC application.
 * Displays the app logo for 2 seconds in fullscreen mode,
 * then transitions to MainActivity and finishes itself
 * so the user cannot navigate back to it.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 2000L;

    private Handler splashHandler;
    private Runnable splashRunnable;
    private boolean navigatedAway = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply fullscreen flags before setting content view so the UI
        // never shows the status/navigation bars during splash
        applyFullscreenFlags();

        setContentView(R.layout.activity_splash);

        splashHandler = new Handler(Looper.getMainLooper());
        splashRunnable = () -> {
            if (!navigatedAway && !isFinishing()) {
                navigateToMainActivity();
            }
        };

        // Post the navigation delayed
        splashHandler.postDelayed(splashRunnable, SPLASH_DURATION_MS);
    }

    /**
     * Hides both the status bar and the navigation bar for a fully
     * immersive splash experience. Works across multiple API levels.
     */
    private void applyFullscreenFlags() {
        // Hide status bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // On API 16+ also hide the navigation bar using system UI visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }

        // On API 28+ also request layout in cutout mode to use full screen area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(layoutParams);
        }
    }

    /**
     * Starts MainActivity and finishes this splash activity
     * so it is removed from the back stack.
     */
    private void navigateToMainActivity() {
        navigatedAway = true;

        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);

        // Apply a fade-out transition for a smooth hand-off
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-apply fullscreen in case the flags were cleared
        // (e.g. when a system dialog appeared on top)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // Remove any pending callbacks to prevent leaks or double-navigation
        if (splashHandler != null && splashRunnable != null) {
            splashHandler.removeCallbacks(splashRunnable);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Do nothing — the user should not be able to dismiss
        // the splash screen by pressing back
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Re-apply immersive mode when the window regains focus
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }
}
