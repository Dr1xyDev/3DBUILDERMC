package com.dbuild.net;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "dbuild_settings";
    private static final String KEY_UI_SIZE = "ui_size";
    private static final String KEY_THEME = "theme";
    private static final String KEY_AUTOSAVE_INTERVAL = "autosave_interval";
    private static final String KEY_CAMERA_TYPE = "camera_type";
    private static final String KEY_GRID_VISIBLE = "grid_visible";

    private SharedPreferences prefs;

    private RadioGroup radioGroupUiSize;
    private RadioGroup radioGroupTheme;
    private Spinner spinnerAutoSave;
    private Spinner spinnerCameraType;
    private Switch switchGridVisible;
    private TextView textVersion;
    private TextView textBuildInfo;
    private TextView textResetDefaults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        loadSettings();
        setupListeners();
        populateAboutSection();
    }

    private void initViews() {
        radioGroupUiSize = findViewById(R.id.radio_group_ui_size);
        radioGroupTheme = findViewById(R.id.radio_group_theme);
        spinnerAutoSave = findViewById(R.id.spinner_autosave);
        spinnerCameraType = findViewById(R.id.spinner_camera_type);
        switchGridVisible = findViewById(R.id.switch_grid_visible);
        textVersion = findViewById(R.id.text_version);
        textBuildInfo = findViewById(R.id.text_build_info);
        textResetDefaults = findViewById(R.id.text_reset_defaults);
    }

    private void loadSettings() {
        loadUiSizeSetting();
        loadThemeSetting();
        loadAutoSaveSetting();
        loadCameraTypeSetting();
        loadGridVisibilitySetting();
    }

    private void loadUiSizeSetting() {
        String uiSize = prefs.getString(KEY_UI_SIZE, "medium");
        radioGroupUiSize.clearCheck();
        int radioButtonId;
        switch (uiSize) {
            case "small":
                radioButtonId = R.id.radio_ui_small;
                break;
            case "large":
                radioButtonId = R.id.radio_ui_large;
                break;
            case "xlarge":
                radioButtonId = R.id.radio_ui_xlarge;
                break;
            default:
                radioButtonId = R.id.radio_ui_medium;
                break;
        }
        radioGroupUiSize.check(radioButtonId);
    }

    private void loadThemeSetting() {
        String theme = prefs.getString(KEY_THEME, "dark");
        radioGroupTheme.clearCheck();
        int radioButtonId = "dark".equals(theme) ? R.id.radio_theme_dark : R.id.radio_theme_white;
        radioGroupTheme.check(radioButtonId);
    }

    private void loadAutoSaveSetting() {
        String[] intervals = {"30", "60", "120", "300"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, intervals);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAutoSave.setAdapter(adapter);

        int savedInterval = prefs.getInt(KEY_AUTOSAVE_INTERVAL, 60);
        int position = 1;
        for (int i = 0; i < intervals.length; i++) {
            if (Integer.parseInt(intervals[i]) == savedInterval) {
                position = i;
                break;
            }
        }
        spinnerAutoSave.setSelection(position);
    }

    private void loadCameraTypeSetting() {
        String[] cameraTypes = {"Orbit", "First Person", "Fly"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, cameraTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCameraType.setAdapter(adapter);

        String savedCamera = prefs.getString(KEY_CAMERA_TYPE, "Orbit");
        int position = 0;
        for (int i = 0; i < cameraTypes.length; i++) {
            if (cameraTypes[i].equals(savedCamera)) {
                position = i;
                break;
            }
        }
        spinnerCameraType.setSelection(position);
    }

    private void loadGridVisibilitySetting() {
        boolean gridVisible = prefs.getBoolean(KEY_GRID_VISIBLE, true);
        switchGridVisible.setChecked(gridVisible);
    }

    private void setupListeners() {
        radioGroupUiSize.setOnCheckedChangeListener((group, checkedId) -> {
            String uiSize;
            if (checkedId == R.id.radio_ui_small) {
                uiSize = "small";
            } else if (checkedId == R.id.radio_ui_large) {
                uiSize = "large";
            } else if (checkedId == R.id.radio_ui_xlarge) {
                uiSize = "xlarge";
            } else {
                uiSize = "medium";
            }
            saveSetting(KEY_UI_SIZE, uiSize);
            UISizeManager.applyUISize(this, uiSize);
            Toast.makeText(this, R.string.ui_size_applied, Toast.LENGTH_SHORT).show();
        });

        radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
            String theme;
            if (checkedId == R.id.radio_theme_dark) {
                theme = "dark";
            } else {
                theme = "white";
            }
            saveSetting(KEY_THEME, theme);
            ThemeManager.applyTheme(this, theme);
            recreate();
        });

        spinnerAutoSave.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                int intervalSeconds = Integer.parseInt(selected);
                prefs.edit().putInt(KEY_AUTOSAVE_INTERVAL, intervalSeconds).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerCameraType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String cameraType = (String) parent.getItemAtPosition(position);
                prefs.edit().putString(KEY_CAMERA_TYPE, cameraType).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        switchGridVisible.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_GRID_VISIBLE, isChecked).apply();
        });

        textResetDefaults.setOnClickListener(v -> showResetConfirmation());
    }

    private void saveSetting(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    private void populateAboutSection() {
        String versionName = BuildConfig.VERSION_NAME;
        int versionCode = BuildConfig.VERSION_CODE;
        textVersion.setText(getString(R.string.version_format, versionName, versionCode));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String buildDate = sdf.format(new Date(BuildConfig.BUILD_TIME));
        String buildType = BuildConfig.BUILD_TYPE;
        textBuildInfo.setText(getString(R.string.build_info_format, buildType, buildDate));
    }

    private void showResetConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.reset_defaults_title);
        builder.setMessage(R.string.reset_defaults_message);
        builder.setPositiveButton(R.string.reset, (dialog, which) -> {
            resetToDefaults();
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void resetToDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        UISizeManager.applyUISize(this, "medium");
        ThemeManager.applyTheme(this, "dark");

        loadSettings();
        Toast.makeText(this, R.string.settings_reset, Toast.LENGTH_SHORT).show();
        recreate();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static String getUISize(SharedPreferences prefs) {
        return prefs.getString(KEY_UI_SIZE, "medium");
    }

    public static String getTheme(SharedPreferences prefs) {
        return prefs.getString(KEY_THEME, "dark");
    }

    public static int getAutoSaveInterval(SharedPreferences prefs) {
        return prefs.getInt(KEY_AUTOSAVE_INTERVAL, 60);
    }

    public static String getCameraType(SharedPreferences prefs) {
        return prefs.getString(KEY_CAMERA_TYPE, "Orbit");
    }

    public static boolean isGridVisible(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_GRID_VISIBLE, true);
    }
}
