package com.dbuild.net;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CustomBlocksActivity extends AppCompatActivity {

    private static final int REQUEST_SELECT_TEXTURE = 3001;

    private RecyclerView recyclerView;
    private FloatingActionButton fabNewBlock;
    private LinearLayout emptyStateLayout;
    private BlockListAdapter adapter;
    private CustomBlockManager blockManager;
    private BlockPreviewGenerator previewGenerator;
    private List<CustomBlock> allBlocks;

    private String pendingEditBlockId = null;
    private String pendingFaceKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_custom_blocks);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.custom_blocks_title);
        }

        blockManager = CustomBlockManager.getInstance(this);
        previewGenerator = new BlockPreviewGenerator(this);
        allBlocks = new ArrayList<>();

        recyclerView = findViewById(R.id.recycler_blocks);
        fabNewBlock = findViewById(R.id.fab_new_block);
        emptyStateLayout = findViewById(R.id.empty_state_layout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BlockListAdapter(this, allBlocks);
        recyclerView.setAdapter(adapter);

        setupFab();
        loadBlocks();
    }

    private void setupFab() {
        fabNewBlock.setOnClickListener(v -> showCreateBlockDialog());
    }

    private void loadBlocks() {
        allBlocks.clear();
        List<CustomBlock> loaded = blockManager.getAllBlocks();
        if (loaded != null) {
            allBlocks.addAll(loaded);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (allBlocks.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showCreateBlockDialog() {
        CustomBlock newBlock = new CustomBlock();
        newBlock.setId(UUID.randomUUID().toString());
        newBlock.setName("Custom Block");
        newBlock.setFaceTextures(new HashMapOfString());

        showBlockEditorDialog(newBlock, true);
    }

    private void showBlockEditorDialog(CustomBlock block, boolean isNew) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isNew ? R.string.create_block : R.string.edit_block);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        final EditText inputName = new EditText(this);
        inputName.setHint(R.string.block_name_hint);
        inputName.setText(block.getName());
        layout.addView(inputName);

        TextView labelFaces = new TextView(this);
        labelFaces.setText(R.string.face_textures_label);
        labelFaces.setPadding(0, 24, 0, 8);
        labelFaces.setTextSize(16);
        labelFaces.setTextColor(Color.WHITE);
        layout.addView(labelFaces);

        String[] faceNames = {"Top", "Bottom", "North", "South", "East", "West"};
        final LinearLayout faceButtonsLayout = new LinearLayout(this);
        faceButtonsLayout.setOrientation(LinearLayout.VERTICAL);

        for (String face : faceNames) {
            LinearLayout faceRow = new LinearLayout(this);
            faceRow.setOrientation(LinearLayout.HORIZONTAL);
            faceRow.setPadding(0, 4, 0, 4);

            TextView faceLabel = new TextView(this);
            faceLabel.setText(face + ": ");
            faceLabel.setTextSize(14);
            faceRow.addView(faceLabel);

            TextView faceTexture = new TextView(this);
            String currentTexture = block.getFaceTextures().get(face);
            faceTexture.setText(currentTexture != null ? currentTexture : getString(R.string.no_texture));
            faceTexture.setTextSize(14);
            faceTexture.setTextColor(currentTexture != null ? Color.CYAN : Color.GRAY);
            faceRow.addView(faceTexture);

            TextView changeBtn = new TextView(this);
            changeBtn.setText("  [" + getString(R.string.change) + "]");
            changeBtn.setTextSize(14);
            changeBtn.setTextColor(Color.YELLOW);
            changeBtn.setClickable(true);
            changeBtn.setOnClickListener(v -> {
                pendingEditBlockId = block.getId();
                pendingFaceKey = face;
                Intent intent = new Intent(CustomBlocksActivity.this, TextureLibraryActivity.class);
                intent.putExtra("select_for_face", true);
                startActivityForResult(intent, REQUEST_SELECT_TEXTURE);
            });
            faceRow.addView(changeBtn);

            faceButtonsLayout.addView(faceRow);
        }
        layout.addView(faceButtonsLayout);

        builder.setView(layout);
        builder.setPositiveButton(isNew ? R.string.create : R.string.save, (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.block_name_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            block.setName(name);
            if (isNew) {
                blockManager.addBlock(block);
            } else {
                blockManager.updateBlock(block);
            }
            loadBlocks();
            Toast.makeText(this, isNew ? R.string.block_created : R.string.block_updated,
                    Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showBlockContextMenu(CustomBlock block, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_block_context, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.ctx_duplicate_block) {
                duplicateBlock(block);
            } else if (id == R.id.ctx_delete_block) {
                showDeleteBlockDialog(block);
            } else if (id == R.id.ctx_export_block) {
                exportBlock(block);
            }
            return true;
        });
        popup.show();
    }

    private void duplicateBlock(CustomBlock block) {
        CustomBlock copy = new CustomBlock();
        copy.setId(UUID.randomUUID().toString());
        copy.setName(block.getName() + " (copy)");
        HashMapOfString faceTextures = new HashMapOfString();
        for (String key : block.getFaceTextures().keySet()) {
            faceTextures.put(key, block.getFaceTextures().get(key));
        }
        copy.setFaceTextures(faceTextures);
        blockManager.addBlock(copy);
        loadBlocks();
        Toast.makeText(this, R.string.block_duplicated, Toast.LENGTH_SHORT).show();
    }

    private void showDeleteBlockDialog(CustomBlock block) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_block_title);
        builder.setMessage(getString(R.string.delete_block_message, block.getName()));
        builder.setPositiveButton(R.string.delete, (dialog, which) -> {
            blockManager.deleteBlock(block.getId());
            loadBlocks();
            Toast.makeText(this, R.string.block_deleted, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void exportBlock(CustomBlock block) {
        Intent exportIntent = new Intent(this, ExportActivity.class);
        exportIntent.putExtra("block_id", block.getId());
        exportIntent.putExtra("export_type", "block");
        startActivity(exportIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_TEXTURE && resultCode == RESULT_OK && data != null) {
            String texturePath = data.getStringExtra("texture_path");
            String textureName = data.getStringExtra("texture_name");
            if (texturePath != null && pendingEditBlockId != null && pendingFaceKey != null) {
                CustomBlock block = blockManager.getBlockById(pendingEditBlockId);
                if (block != null) {
                    block.getFaceTextures().put(pendingFaceKey, texturePath);
                    blockManager.updateBlock(block);
                    loadBlocks();
                    Toast.makeText(this, getString(R.string.texture_applied_to_face,
                            textureName, pendingFaceKey), Toast.LENGTH_SHORT).show();
                }
                pendingEditBlockId = null;
                pendingFaceKey = null;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent result = new Intent();
            setResult(RESULT_CANCELED, result);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBlocks();
    }

    private class BlockListAdapter extends RecyclerView.Adapter<BlockListAdapter.ViewHolder> {

        private final Context context;
        private final List<CustomBlock> blocks;

        BlockListAdapter(Context context, List<CustomBlock> blocks) {
            this.context = context;
            this.blocks = blocks;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_custom_block, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CustomBlock block = blocks.get(position);
            holder.textBlockName.setText(block.getName());

            int faceCount = 0;
            for (String key : block.getFaceTextures().keySet()) {
                if (block.getFaceTextures().get(key) != null) {
                    faceCount++;
                }
            }
            holder.textFaceCount.setText(context.getString(R.string.face_count_format, faceCount));

            Bitmap preview = previewGenerator.generatePreview(block);
            if (preview != null) {
                holder.imagePreview.setImageBitmap(preview);
            } else {
                holder.imagePreview.setImageResource(R.drawable.ic_block_placeholder);
            }

            holder.itemView.setOnClickListener(v -> {
                showBlockEditorDialog(block, false);
            });

            holder.itemView.setOnLongClickListener(v -> {
                showBlockContextMenu(block, v);
                return true;
            });

            holder.btnMore.setOnClickListener(v -> {
                showBlockContextMenu(block, v);
            });
        }

        @Override
        public int getItemCount() {
            return blocks.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imagePreview;
            TextView textBlockName;
            TextView textFaceCount;
            ImageView btnMore;

            ViewHolder(View itemView) {
                super(itemView);
                imagePreview = itemView.findViewById(R.id.image_block_preview);
                textBlockName = itemView.findViewById(R.id.text_block_name);
                textFaceCount = itemView.findViewById(R.id.text_face_count);
                btnMore = itemView.findViewById(R.id.btn_more);
            }
        }
    }
}
