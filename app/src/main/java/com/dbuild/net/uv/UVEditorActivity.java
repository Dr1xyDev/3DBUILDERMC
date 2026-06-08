package com.dbuild.net.uv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dbuild.net.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.util.Locale;

/**
 * Activity for editing UV mapping of a selected block face.
 * Shows a UVEditorView, EditText fields for precise control,
 * and provides Apply/Reset/Save actions.
 */
public class UVEditorActivity extends Activity {

    public static final String EXTRA_BLOCK_ID = "block_id";
    public static final String EXTRA_FACE_NAME = "face_name";
    public static final String EXTRA_UV_DATA = "uv_data";
    public static final String EXTRA_TEXTURE_PATH = "texture_path";
    public static final String EXTRA_RESULT_UV_DATA = "result_uv_data";

    private static final String STATE_UV_DATA = "state_uv_data";

    private UVEditorView uvEditorView;
    private EditText etU;
    private EditText etV;
    private EditText etUScale;
    private EditText etVScale;
    private EditText etRotation;
    private TextView tvFaceName;
    private ImageView ivTextureThumb;
    private Button btnApply;
    private Button btnReset;

    private UVData currentUVData;
    private String faceName;
    private String texturePath;
    private long blockId;
    private boolean updatingFromView = false;
    private boolean updatingFromText = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse intent extras
        Intent intent = getIntent();
        blockId = intent.getLongExtra(EXTRA_BLOCK_ID, -1);
        faceName = intent.getStringExtra(EXTRA_FACE_NAME);
        if (faceName == null) faceName = "Unknown";
        texturePath = intent.getStringExtra(EXTRA_TEXTURE_PATH);

        // Restore or parse UV data
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_UV_DATA)) {
            try {
                currentUVData = UVData.fromJSON(new JSONObject(savedInstanceState.getString(STATE_UV_DATA)));
            } catch (JSONException e) {
                currentUVData = parseUVFromIntent(intent);
            }
        } else {
            currentUVData = parseUVFromIntent(intent);
        }

        // Build UI programmatically
        buildUI();

        // Sync initial state
        syncEditTextsFromUVData();
        uvEditorView.setUVData(currentUVData);
        loadTextureThumbnail();

        // Set up toolbar-like title
        CharSequence title = "UV Editor - " + getDisplayFaceName(faceName);
        setTitle(title);
    }

    private UVData parseUVFromIntent(Intent intent) {
        String uvJson = intent.getStringExtra(EXTRA_UV_DATA);
        if (uvJson != null && !uvJson.isEmpty()) {
            try {
                return UVData.fromJSON(new JSONObject(uvJson));
            } catch (JSONException e) {
                return new UVData();
            }
        }
        return new UVData();
    }

    private void buildUI() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(16, 16, 16, 16);

        float density = getResources().getDisplayMetrics().density;

        // Face name label
        tvFaceName = new TextView(this);
        tvFaceName.setText("Face: " + getDisplayFaceName(faceName));
        tvFaceName.setTextSize(18);
        tvFaceName.setPadding(0, 0, 0, (int) (8 * density));
        rootLayout.addView(tvFaceName);

        // Texture thumbnail
        ivTextureThumb = new ImageView(this);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(
                (int) (64 * density), (int) (64 * density));
        thumbParams.setMargins(0, 0, 0, (int) (8 * density));
        ivTextureThumb.setLayoutParams(thumbParams);
        ivTextureThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivTextureThumb.setBackgroundColor(0xFF333333);
        rootLayout.addView(ivTextureThumb);

        // UV Editor View
        uvEditorView = new UVEditorView(this);
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        editorParams.setMargins(0, 0, 0, (int) (12 * density));
        uvEditorView.setLayoutParams(editorParams);
        uvEditorView.setTexturePath(texturePath);
        rootLayout.addView(uvEditorView);

        uvEditorView.setUVChangeListener(new UVEditorView.UVChangeListener() {
            @Override
            public void onUVChanged(UVData newData) {
                if (updatingFromText) return;
                updatingFromView = true;
                currentUVData = new UVData(newData);
                syncEditTextsFromUVData();
                updatingFromView = false;
            }
        });

        // EditText fields in a ScrollView for small screens
        ScrollView fieldsScroll = new ScrollView(this);
        LinearLayout fieldsContainer = new LinearLayout(this);
        fieldsContainer.setOrientation(LinearLayout.VERTICAL);
        fieldsContainer.setPadding(0, 0, 0, (int) (8 * density));

        // Create field rows
        LinearLayout row1 = createFieldRow("U:", createUField());
        LinearLayout row2 = createFieldRow("V:", createVField());
        LinearLayout row3 = createFieldRow("U Scale:", createUScaleField());
        LinearLayout row4 = createFieldRow("V Scale:", createVScaleField());
        LinearLayout row5 = createFieldRow("Rotation:", createRotationField());

        fieldsContainer.addView(row1);
        fieldsContainer.addView(row2);
        fieldsContainer.addView(row3);
        fieldsContainer.addView(row4);
        fieldsContainer.addView(row5);

        fieldsScroll.addView(fieldsContainer);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.setMargins(0, 0, 0, (int) (12 * density));
        fieldsScroll.setLayoutParams(scrollParams);
        rootLayout.addView(fieldsScroll);

        // Buttons row
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, (int) (8 * density), 0, 0);

        btnReset = new Button(this);
        btnReset.setText("Reset");
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentUVData.reset();
                uvEditorView.setUVData(currentUVData);
                syncEditTextsFromUVData();
            }
        });
        buttonRow.addView(btnReset, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (8 * density), LinearLayout.LayoutParams.WRAP_CONTENT));
        buttonRow.addView(spacer);

        btnApply = new Button(this);
        btnApply.setText("Apply");
        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyAndReturn();
            }
        });
        buttonRow.addView(btnApply, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        rootLayout.addView(buttonRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView outerScroll = new ScrollView(this);
        outerScroll.addView(rootLayout);
        setContentView(outerScroll);
    }

    private LinearLayout createFieldRow(String label, EditText field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, 0);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setMinWidth((int) (80 * getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        tv.setLayoutParams(labelParams);
        row.addView(tv);

        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        field.setLayoutParams(fieldParams);
        row.addView(field);

        return row;
    }

    private EditText createUField() {
        etU = new EditText(this);
        etU.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etU.setHint("0.0 - 1.0");
        addTextWatcher(etU, new FieldUpdater() {
            @Override
            public void update(float value) {
                currentUVData.setU(value);
            }
        });
        return etU;
    }

    private EditText createVField() {
        etV = new EditText(this);
        etV.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etV.setHint("0.0 - 1.0");
        addTextWatcher(etV, new FieldUpdater() {
            @Override
            public void update(float value) {
                currentUVData.setV(value);
            }
        });
        return etV;
    }

    private EditText createUScaleField() {
        etUScale = new EditText(this);
        etUScale.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etUScale.setHint("> 0");
        addTextWatcher(etUScale, new FieldUpdater() {
            @Override
            public void update(float value) {
                currentUVData.setUScale(value);
            }
        });
        return etUScale;
    }

    private EditText createVScaleField() {
        etVScale = new EditText(this);
        etVScale.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etVScale.setHint("> 0");
        addTextWatcher(etVScale, new FieldUpdater() {
            @Override
            public void update(float value) {
                currentUVData.setVScale(value);
            }
        });
        return etVScale;
    }

    private EditText createRotationField() {
        etRotation = new EditText(this);
        etRotation.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etRotation.setHint("0 - 360");
        addTextWatcher(etRotation, new FieldUpdater() {
            @Override
            public void update(float value) {
                currentUVData.setRotation(value);
            }
        });
        return etRotation;
    }

    private interface FieldUpdater {
        void update(float value);
    }

    private void addTextWatcher(EditText editText, final FieldUpdater updater) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingFromView) return;
                String text = s.toString().trim();
                if (text.isEmpty()) return;

                try {
                    float value = Float.parseFloat(text);
                    updatingFromText = true;
                    updater.update(value);
                    currentUVData.clampValues();
                    uvEditorView.setUVData(currentUVData);
                    updatingFromText = false;
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });
    }

    private void syncEditTextsFromUVData() {
        if (currentUVData == null) return;
        updatingFromView = true;
        etU.setText(String.format(Locale.US, "%.4f", currentUVData.getU()));
        etV.setText(String.format(Locale.US, "%.4f", currentUVData.getV()));
        etUScale.setText(String.format(Locale.US, "%.4f", currentUVData.getUScale()));
        etVScale.setText(String.format(Locale.US, "%.4f", currentUVData.getVScale()));
        etRotation.setText(String.format(Locale.US, "%.1f", currentUVData.getRotation()));
        updatingFromView = false;
    }

    private void loadTextureThumbnail() {
        if (texturePath == null || texturePath.isEmpty()) return;
        try {
            FileInputStream fis = new FileInputStream(texturePath);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap thumb = BitmapFactory.decodeStream(fis, null, opts);
            fis.close();
            if (thumb != null) {
                ivTextureThumb.setImageBitmap(thumb);
            }
        } catch (Exception e) {
            // Thumbnail not available
        }
    }

    private void applyAndReturn() {
        currentUVData.clampValues();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_RESULT_UV_DATA, currentUVData.toJSON().toString());
        resultIntent.putExtra(EXTRA_BLOCK_ID, blockId);
        resultIntent.putExtra(EXTRA_FACE_NAME, faceName);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentUVData != null) {
            outState.putString(STATE_UV_DATA, currentUVData.toJSON().toString());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey(STATE_UV_DATA)) {
            try {
                currentUVData = UVData.fromJSON(new JSONObject(savedInstanceState.getString(STATE_UV_DATA)));
                uvEditorView.setUVData(currentUVData);
                syncEditTextsFromUVData();
            } catch (JSONException e) {
                // Keep current data
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            applyAndReturn();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        applyAndReturn();
    }

    /**
     * Returns a human-readable name for a face identifier.
     */
    public static String getDisplayFaceName(String faceName) {
        if (faceName == null) return "Unknown";
        switch (faceName.toLowerCase(Locale.US)) {
            case "top":
            case "up":
            case "y+":
                return "Top";
            case "bottom":
            case "down":
            case "y-":
                return "Bottom";
            case "north":
            case "front":
            case "z-":
                return "North";
            case "south":
            case "back":
            case "z+":
                return "South";
            case "east":
            case "right":
            case "x+":
                return "East";
            case "west":
            case "left":
            case "x-":
                return "West";
            default:
                return faceName;
        }
    }
}
