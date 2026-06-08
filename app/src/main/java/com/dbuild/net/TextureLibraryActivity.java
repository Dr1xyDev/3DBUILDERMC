package com.dbuild.net;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TextureLibraryActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_IMAGE = 2001;
    private static final int REQUEST_IMPORT_IMAGE = 2002;

    private GridView gridView;
    private EditText searchBar;
    private FloatingActionButton fabImport;
    private Spinner spinnerFilter;
    private LinearLayout emptyStateLayout;
    private TexturePreviewAdapter adapter;
    private TextureManager textureManager;
    private TextureImporter textureImporter;
    private TextureStorage textureStorage;
    private List<TextureInfo> allTextures;
    private List<TextureInfo> filteredTextures;
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_texture_library);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.texture_library_title);
        }

        textureManager = TextureManager.getInstance(this);
        textureImporter = new TextureImporter(this);
        textureStorage = new TextureStorage(this);
        allTextures = new ArrayList<>();
        filteredTextures = new ArrayList<>();

        gridView = findViewById(R.id.grid_textures);
        searchBar = findViewById(R.id.search_bar);
        fabImport = findViewById(R.id.fab_import_texture);
        spinnerFilter = findViewById(R.id.spinner_filter);
        emptyStateLayout = findViewById(R.id.empty_state_layout);

        adapter = new TexturePreviewAdapter(this, filteredTextures, textureManager);
        gridView.setAdapter(adapter);

        setupSearchBar();
        setupFilterSpinner();
        setupFab();
        setupGridItemClickListener();

        loadTextures();
    }

    private void setupSearchBar() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilterAndSearch(s.toString(), currentFilter);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterSpinner() {
        String[] filterOptions = {"ALL", "PNG", "JPG", "WEBP"};
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, filterOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(spinnerAdapter);

        spinnerFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                currentFilter = (String) parent.getItemAtPosition(position);
                applyFilterAndSearch(searchBar.getText().toString(), currentFilter);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupFab() {
        fabImport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            String[] mimeTypes = {"image/png", "image/jpeg", "image/webp"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_texture)),
                    REQUEST_PICK_IMAGE);
        });
    }

    private void setupGridItemClickListener() {
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            TextureInfo texture = filteredTextures.get(position);
            Intent result = new Intent();
            result.putExtra("texture_path", texture.getFilePath());
            result.putExtra("texture_name", texture.getName());
            setResult(RESULT_OK, result);
            finish();
        });

        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            TextureInfo texture = filteredTextures.get(position);
            showTextureContextMenu(texture, view);
            return true;
        });
    }

    private void loadTextures() {
        allTextures.clear();
        List<TextureInfo> loaded = textureManager.getAllTextures();
        if (loaded != null) {
            allTextures.addAll(loaded);
        }
        applyFilterAndSearch(searchBar.getText().toString(), currentFilter);
    }

    private void applyFilterAndSearch(String query, String formatFilter) {
        filteredTextures.clear();
        for (TextureInfo texture : allTextures) {
            boolean matchesFormat = "ALL".equals(formatFilter) ||
                    texture.getFormat().equalsIgnoreCase(formatFilter);
            boolean matchesQuery = query == null || query.trim().isEmpty() ||
                    texture.getName().toLowerCase(Locale.getDefault())
                            .contains(query.toLowerCase(Locale.getDefault()));
            if (matchesFormat && matchesQuery) {
                filteredTextures.add(texture);
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredTextures.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            gridView.setVisibility(View.VISIBLE);
        }
    }

    private void showTextureContextMenu(TextureInfo texture, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_texture_context, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.ctx_rename_texture) {
                showRenameTextureDialog(texture);
            } else if (id == R.id.ctx_delete_texture) {
                showDeleteTextureDialog(texture);
            } else if (id == R.id.ctx_apply_to_face) {
                applyTextureToFace(texture);
            }
            return true;
        });
        popup.show();
    }

    private void showRenameTextureDialog(TextureInfo texture) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.rename_texture);
        final EditText input = new EditText(this);
        input.setText(texture.getName());
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);
        builder.setPositiveButton(R.string.rename, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                textureManager.renameTexture(texture.getId(), newName);
                loadTextures();
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeleteTextureDialog(TextureInfo texture) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_texture_title);
        builder.setMessage(getString(R.string.delete_texture_message, texture.getName()));
        builder.setPositiveButton(R.string.delete, (dialog, which) -> {
            textureManager.deleteTexture(texture.getId());
            textureStorage.deleteTextureFile(texture.getFilePath());
            loadTextures();
            Toast.makeText(this, R.string.texture_deleted, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void applyTextureToFace(TextureInfo texture) {
        Intent result = new Intent();
        result.putExtra("texture_path", texture.getFilePath());
        result.putExtra("texture_name", texture.getName());
        result.putExtra("action", "apply_to_face");
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                importTexture(imageUri);
            }
        }
    }

    private void importTexture(Uri uri) {
        try {
            String fileName = getFileNameFromUri(uri);
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, R.string.texture_import_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            String format = guessFormatFromName(fileName);
            File destFile = textureStorage.createTextureFile(fileName, format);
            FileOutputStream outputStream = new FileOutputStream(destFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();

            TextureInfo imported = textureImporter.processImportedFile(destFile.getAbsolutePath(), fileName, format);
            if (imported != null) {
                textureManager.addTexture(imported);
                loadTextures();
                Toast.makeText(this, R.string.texture_imported, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.texture_import_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.texture_import_error, Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "texture";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        if (fileName.lastIndexOf('.') == -1) {
            fileName += ".png";
        }
        return fileName;
    }

    private String guessFormatFromName(String fileName) {
        String lower = fileName.toLowerCase(Locale.getDefault());
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "JPG";
        if (lower.endsWith(".webp")) return "WEBP";
        return "PNG";
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTextures();
    }

    private static class TexturePreviewAdapter extends BaseAdapter {

        private final Context context;
        private final List<TextureInfo> textures;
        private final LayoutInflater inflater;
        private final TextureManager textureManagerRef;

        TexturePreviewAdapter(Context context, List<TextureInfo> textures, TextureManager tm) {
            this.context = context;
            this.textures = textures;
            this.inflater = LayoutInflater.from(context);
            this.textureManagerRef = tm;
        }

        @Override
        public int getCount() {
            return textures.size();
        }

        @Override
        public TextureInfo getItem(int position) {
            return textures.get(position);
        }

        @Override
        public long getItemId(int position) {
            return textures.get(position).getId().hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_texture, parent, false);
                holder = new ViewHolder();
                holder.imageThumbnail = convertView.findViewById(R.id.image_texture_thumbnail);
                holder.textName = convertView.findViewById(R.id.text_texture_name);
                holder.textDetails = convertView.findViewById(R.id.text_texture_details);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            TextureInfo texture = textures.get(position);
            holder.textName.setText(texture.getName());
            holder.textDetails.setText(context.getString(R.string.texture_details_format,
                    texture.getWidth(), texture.getHeight(), texture.getFormat(),
                    formatFileSize(texture.getFileSize())));

            Bitmap thumbnail = textureManagerRef != null ?
                    textureManagerRef.getThumbnail(texture.getId()) : null;
            if (thumbnail != null) {
                holder.imageThumbnail.setImageBitmap(thumbnail);
            } else {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                Bitmap bm = BitmapFactory.decodeFile(texture.getFilePath(), opts);
                if (bm != null) {
                    holder.imageThumbnail.setImageBitmap(bm);
                } else {
                    holder.imageThumbnail.setImageResource(R.drawable.ic_texture_placeholder);
                }
            }

            return convertView;
        }

        private String formatFileSize(long size) {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
        }

        static class ViewHolder {
            ImageView imageThumbnail;
            TextView textName;
            TextView textDetails;
        }
    }
}
