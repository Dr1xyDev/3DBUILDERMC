package com.dbuild.net.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dbuild.net.blocks.BlockPreviewGenerator;
import com.dbuild.net.blocks.CustomBlock;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * RecyclerView.Adapter for displaying a list of custom blocks.
 * Supports click callbacks, async preview generation, and sorting.
 */
public class BlockListAdapter extends RecyclerView.Adapter<BlockListAdapter.ViewHolder> {

    private List<CustomBlock> blocks;
    private Context context;
    private OnBlockClickListener listener;

    /**
     * Callback interface for block click events.
     */
    public interface OnBlockClickListener {
        /**
         * Called when a block is clicked.
         *
         * @param block    the clicked custom block
         * @param position adapter position
         */
        void onBlockClick(CustomBlock block, int position);

        /**
         * Called when a block is long-clicked.
         *
         * @param block    the long-clicked custom block
         * @param position adapter position
         */
        void onBlockLongClick(CustomBlock block, int position);
    }

    /**
     * ViewHolder for block list items.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView preview;
        public TextView name;
        public TextView category;

        public ViewHolder(View itemView) {
            super(itemView);
            preview = itemView.findViewWithTag("preview");
            name = itemView.findViewWithTag("name");
            category = itemView.findViewWithTag("category");
        }
    }

    /**
     * Creates a new BlockListAdapter.
     *
     * @param context  activity context
     * @param listener block click listener
     */
    public BlockListAdapter(Context context, OnBlockClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.blocks = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = createItemView(parent);
        return new ViewHolder(view);
    }

    /**
     * Creates the item view, trying the layout resource first, then fallback.
     */
    private View createItemView(ViewGroup parent) {
        int layoutId = context.getResources().getIdentifier("item_block", "layout", context.getPackageName());
        if (layoutId != 0) {
            return LayoutInflater.from(context).inflate(layoutId, parent, false);
        }
        return createFallbackView(parent);
    }

    /**
     * Creates a fallback item view programmatically.
     */
    private View createFallbackView(ViewGroup parent) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setPadding(12, 12, 12, 12);

        ImageView previewImg = new ImageView(context);
        previewImg.setTag("preview");
        previewImg.setLayoutParams(new android.widget.LinearLayout.LayoutParams(80, 80));
        previewImg.setBackgroundColor(0xFFDDDDDD);
        layout.addView(previewImg);

        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(context);
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        textContainer.setPadding(12, 0, 0, 0);

        TextView nameView = new TextView(context);
        nameView.setTag("name");
        nameView.setTextSize(14);
        nameView.setTextColor(0xFF222222);
        textContainer.addView(nameView);

        TextView catView = new TextView(context);
        catView.setTag("category");
        catView.setTextSize(11);
        catView.setTextColor(0xFF888888);
        textContainer.addView(catView);

        layout.addView(textContainer);
        return layout;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= blocks.size()) {
            return;
        }

        CustomBlock block = blocks.get(position);

        // Set name
        if (holder.name != null) {
            holder.name.setText(block.getName());
        }

        // Set category
        if (holder.category != null) {
            holder.category.setText(block.getCategory());
        }

        // Generate and load preview
        loadPreview(block, holder.preview);

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onBlockClick(blocks.get(pos), pos);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onBlockLongClick(blocks.get(pos), pos);
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    /**
     * Loads or generates a preview for a custom block.
     *
     * @param block     the custom block
     * @param imageView target ImageView
     */
    private void loadPreview(CustomBlock block, ImageView imageView) {
        if (imageView == null) return;

        // Check if block has a cached preview path
        String previewPath = block.getPreviewPath();
        if (previewPath != null && !previewPath.isEmpty()) {
            new PreviewLoadTask(imageView).execute(previewPath);
        } else {
            // Generate preview
            new PreviewGenerateTask(imageView, block).execute();
        }
    }

    /**
     * AsyncTask to load a preview image from file.
     */
    private static class PreviewLoadTask extends AsyncTask<String, Void, Bitmap> {
        private WeakReference<ImageView> imageViewRef;

        PreviewLoadTask(ImageView imageView) {
            this.imageViewRef = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... paths) {
            if (paths == null || paths.length == 0) return null;
            String path = paths[0];
            java.io.File file = new java.io.File(path);
            if (!file.exists()) return null;
            return android.graphics.BitmapFactory.decodeFile(path);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                ImageView iv = imageViewRef.get();
                if (iv != null) {
                    iv.setImageBitmap(bitmap);
                }
            }
        }
    }

    /**
     * AsyncTask to generate a preview using BlockPreviewGenerator.
     */
    private class PreviewGenerateTask extends AsyncTask<Void, Void, Bitmap> {
        private WeakReference<ImageView> imageViewRef;
        private CustomBlock block;

        PreviewGenerateTask(ImageView imageView, CustomBlock block) {
            this.imageViewRef = new WeakReference<>(imageView);
            this.block = block;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            return BlockPreviewGenerator.generatePreview(context, block);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                ImageView iv = imageViewRef.get();
                if (iv != null) {
                    iv.setImageBitmap(bitmap);
                }
                // Save for future use
                String previewPath = block.getPreviewPath();
                if (previewPath != null) {
                    BlockPreviewGenerator.savePreview(bitmap, previewPath);
                }
            }
        }
    }

    /**
     * Sets the list of blocks to display.
     *
     * @param blocks new list of custom blocks
     */
    public void setBlocks(List<CustomBlock> blocks) {
        this.blocks = blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Gets the block at the given position.
     *
     * @param position adapter position
     * @return CustomBlock or null
     */
    public CustomBlock getItem(int position) {
        if (position >= 0 && position < blocks.size()) {
            return blocks.get(position);
        }
        return null;
    }

    /**
     * Sorts blocks by name alphabetically.
     */
    public void sortByName() {
        Collections.sort(blocks, (b1, b2) -> b1.getName().compareToIgnoreCase(b2.getName()));
        notifyDataSetChanged();
    }

    /**
     * Sorts blocks by category.
     */
    public void sortByCategory() {
        Collections.sort(blocks, (b1, b2) -> b1.getCategory().compareToIgnoreCase(b2.getCategory()));
        notifyDataSetChanged();
    }

    /**
     * Adds a single block to the list.
     *
     * @param block block to add
     */
    public void addBlock(CustomBlock block) {
        if (block != null) {
            blocks.add(block);
            notifyItemInserted(blocks.size() - 1);
        }
    }

    /**
     * Removes a block from the list.
     *
     * @param block block to remove
     */
    public void removeBlock(CustomBlock block) {
        if (block != null) {
            int index = blocks.indexOf(block);
            if (index >= 0) {
                blocks.remove(index);
                notifyItemRemoved(index);
            }
        }
    }
}
