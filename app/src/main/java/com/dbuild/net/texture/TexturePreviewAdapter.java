package com.dbuild.net.texture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dbuild.net.R;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView.Adapter for displaying texture thumbnails in a grid layout.
 * Supports click and long-click callbacks, bitmap caching via LRU,
 * asynchronous thumbnail loading, format filtering, and selection state.
 */
public class TexturePreviewAdapter extends RecyclerView.Adapter<TexturePreviewAdapter.ViewHolder> {

    /** Maximum number of bitmaps to keep in the LRU cache. */
    private static final int CACHE_MAX_ENTRIES = 100;

    private final Context context;
    private final int layoutResourceId;
    private List<TextureManager.TextureInfo> textures;
    private List<TextureManager.TextureInfo> filteredTextures;
    private String currentFilter;
    private int selectedPosition;

    private OnTextureSelectedListener clickListener;
    private OnTextureLongClickListener longClickListener;

    /** LRU cache for decoded thumbnail bitmaps. */
    private final android.util.LruCache<String, Bitmap> bitmapCache;

    // ========== Callback Interfaces ==========

    /**
     * Callback interface for texture selection events.
     */
    public interface OnTextureSelectedListener {
        void onTextureSelected(TextureManager.TextureInfo textureInfo);
    }

    /**
     * Callback interface for texture long-click events.
     */
    public interface OnTextureLongClickListener {
        void onTextureLongClick(TextureManager.TextureInfo textureInfo, int position);
    }

    // ========== ViewHolder ==========

    /**
     * ViewHolder for texture preview items.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView thumbnailImage;
        public TextView textureNameText;
        public TextView textureDimensionsText;
        public View selectionOverlay;

        public ViewHolder(View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.texture_thumbnail);
            textureNameText = itemView.findViewById(R.id.texture_name);
            textureDimensionsText = itemView.findViewById(R.id.texture_dimensions);
            selectionOverlay = itemView.findViewById(R.id.selection_overlay);
        }
    }

    // ========== Constructor ==========

    /**
     * Constructs a TexturePreviewAdapter.
     *
     * @param context          the Android context
     * @param layoutResourceId the layout resource ID for each grid item
     */
    public TexturePreviewAdapter(Context context, int layoutResourceId) {
        this.context = context;
        this.layoutResourceId = layoutResourceId;
        this.textures = new ArrayList<>();
        this.filteredTextures = new ArrayList<>();
        this.currentFilter = null;
        this.selectedPosition = -1;

        this.bitmapCache = new android.util.LruCache<String, Bitmap>(CACHE_MAX_ENTRIES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return 1; // Count entries, not bytes
            }

            @Override
            protected void entryRemoved(boolean evicted, String key,
                                        Bitmap oldValue, Bitmap newValue) {
                if (oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };
    }

    // ========== RecyclerView Adapter Methods ==========

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(layoutResourceId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= filteredTextures.size()) {
            return;
        }

        TextureManager.TextureInfo info = filteredTextures.get(position);
        String textureId = info.getTextureId();

        // Set texture name
        holder.textureNameText.setText(info.getName());

        // Set dimensions
        holder.textureDimensionsText.setText(info.getDimensionsString());

        // Handle selection state
        boolean isSelected = (position == selectedPosition);
        if (holder.selectionOverlay != null) {
            holder.selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        }

        // Load thumbnail
        Bitmap cachedBitmap = bitmapCache.get(textureId);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            holder.thumbnailImage.setImageBitmap(cachedBitmap);
        } else {
            // Set placeholder
            holder.thumbnailImage.setImageResource(android.R.color.darker_gray);
            // Load asynchronously
            loadThumbnailAsync(holder, info);
        }

        // Click listeners
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    int oldPosition = selectedPosition;
                    selectedPosition = adapterPos;
                    if (oldPosition >= 0 && oldPosition < filteredTextures.size()) {
                        notifyItemChanged(oldPosition);
                    }
                    notifyItemChanged(adapterPos);

                    if (clickListener != null) {
                        clickListener.onTextureSelected(filteredTextures.get(adapterPos));
                    }
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION && longClickListener != null) {
                    longClickListener.onTextureLongClick(
                            filteredTextures.get(adapterPos), adapterPos);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredTextures.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.thumbnailImage.setImageBitmap(null);
    }

    // ========== Data Management ==========

    /**
     * Sets the texture list and updates the display.
     *
     * @param newTextures the new list of textures to display
     */
    public void setTextures(List<TextureManager.TextureInfo> newTextures) {
        if (newTextures == null) {
            newTextures = Collections.emptyList();
        }

        this.textures = new ArrayList<>(newTextures);
        applyFilter();
        this.selectedPosition = -1;
        notifyDataSetChanged();
    }

    /**
     * Filters the displayed textures by format.
     * Pass null to clear the filter and show all textures.
     *
     * @param format the format string to filter by (e.g., "PNG", "JPG"), or null for all
     */
    public void filterByFormat(String format) {
        this.currentFilter = format;
        applyFilter();
        this.selectedPosition = -1;
        notifyDataSetChanged();
    }

    /**
     * Applies the current format filter to the texture list.
     */
    private void applyFilter() {
        filteredTextures.clear();

        if (currentFilter == null || currentFilter.isEmpty()) {
            filteredTextures.addAll(textures);
        } else {
            String upperFilter = currentFilter.toUpperCase(Locale.US);
            for (TextureManager.TextureInfo info : textures) {
                if (upperFilter.equalsIgnoreCase(info.getFormat())) {
                    filteredTextures.add(info);
                }
            }
        }
    }

    /**
     * Returns the currently displayed (possibly filtered) list of textures.
     *
     * @return list of displayed TextureInfo objects
     */
    public List<TextureManager.TextureInfo> getDisplayedTextures() {
        return Collections.unmodifiableList(new ArrayList<>(filteredTextures));
    }

    /**
     * Gets the TextureInfo at a given position in the displayed list.
     *
     * @param position the position
     * @return the TextureInfo, or null if position is out of range
     */
    public TextureManager.TextureInfo getTextureAt(int position) {
        if (position < 0 || position >= filteredTextures.size()) {
            return null;
        }
        return filteredTextures.get(position);
    }

    /**
     * Returns the currently selected position.
     *
     * @return the selected position, or -1 if none selected
     */
    public int getSelectedPosition() {
        return selectedPosition;
    }

    /**
     * Gets the currently selected texture.
     *
     * @return the selected TextureInfo, or null if none selected
     */
    public TextureManager.TextureInfo getSelectedTexture() {
        if (selectedPosition >= 0 && selectedPosition < filteredTextures.size()) {
            return filteredTextures.get(selectedPosition);
        }
        return null;
    }

    /**
     * Clears the current selection.
     */
    public void clearSelection() {
        int oldPos = selectedPosition;
        selectedPosition = -1;
        if (oldPos >= 0 && oldPos < filteredTextures.size()) {
            notifyItemChanged(oldPos);
        }
    }

    // ========== Listener Setters ==========

    /**
     * Sets the listener for texture selection events.
     *
     * @param listener the click listener
     */
    public void setOnTextureSelectedListener(OnTextureSelectedListener listener) {
        this.clickListener = listener;
    }

    /**
     * Sets the listener for texture long-click events.
     *
     * @param listener the long-click listener
     */
    public void setOnTextureLongClickListener(OnTextureLongClickListener listener) {
        this.longClickListener = listener;
    }

    // ========== Cache Management ==========

    /**
     * Clears the bitmap cache, recycling all cached bitmaps.
     */
    public void clearCache() {
        bitmapCache.evictAll();
    }

    /**
     * Removes a specific texture's thumbnail from the cache.
     *
     * @param textureId the texture ID to evict
     */
    public void evictFromCache(String textureId) {
        if (textureId != null) {
            bitmapCache.remove(textureId);
        }
    }

    /**
     * Pre-caches all thumbnails for the current texture list.
     * Should be called on a background thread.
     */
    public void preCacheAll() {
        for (TextureManager.TextureInfo info : textures) {
            String textureId = info.getTextureId();
            if (bitmapCache.get(textureId) == null) {
                Bitmap thumb = loadThumbnailSync(info);
                if (thumb != null) {
                    bitmapCache.put(textureId, thumb);
                }
            }
        }
        notifyDataSetChanged();
    }

    // ========== Async Thumbnail Loading ==========

    /**
     * Asynchronously loads a thumbnail for a ViewHolder.
     * Cancels previous loading tasks associated with the ViewHolder.
     */
    private void loadThumbnailAsync(ViewHolder holder, TextureManager.TextureInfo info) {
        // Cancel any existing task for this holder
        if (holder.thumbnailImage.getTag() instanceof ThumbnailLoadTask) {
            ThumbnailLoadTask existingTask = (ThumbnailLoadTask) holder.thumbnailImage.getTag();
            existingTask.cancel(true);
        }

        ThumbnailLoadTask task = new ThumbnailLoadTask(holder, info);
        holder.thumbnailImage.setTag(task);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * AsyncTask for loading thumbnail bitmaps off the main thread.
     */
    private class ThumbnailLoadTask extends AsyncTask<Void, Void, Bitmap> {
        private final WeakReference<ViewHolder> holderRef;
        private final TextureManager.TextureInfo info;
        private final String textureId;

        ThumbnailLoadTask(ViewHolder holder, TextureManager.TextureInfo info) {
            this.holderRef = new WeakReference<>(holder);
            this.info = info;
            this.textureId = info.getTextureId();
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            // Check cache again (might have been cached by another task)
            Bitmap cached = bitmapCache.get(textureId);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }

            // Load the thumbnail
            return loadThumbnailSync(info);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled() || bitmap == null || bitmap.isRecycled()) {
                return;
            }

            // Cache the bitmap
            bitmapCache.put(textureId, bitmap);

            // Update the ViewHolder if it's still valid
            ViewHolder holder = holderRef.get();
            if (holder != null) {
                Object tag = holder.thumbnailImage.getTag();
                if (tag == this) {
                    holder.thumbnailImage.setImageBitmap(bitmap);
                    holder.thumbnailImage.setTag(null);
                }
            }
        }
    }

    /**
     * Synchronously loads a thumbnail bitmap for a texture.
     * Tries the thumbnail file first, then falls back to the full texture.
     *
     * @param info the TextureInfo to load
     * @return the thumbnail Bitmap, or null on failure
     */
    private Bitmap loadThumbnailSync(TextureManager.TextureInfo info) {
        String textureId = info.getTextureId();

        // Try to get from TextureManager's cache/storage
        try {
            TextureManager manager = TextureManager.getInstance(context);
            Bitmap thumb = manager.getTextureThumbnail(textureId);
            if (thumb != null && !thumb.isRecycled()) {
                return thumb;
            }
        } catch (Exception e) {
            // Fall through to file-based loading
        }

        // Try loading from thumbnail file directly
        String filePath = info.getFilePath();
        if (filePath != null && !filePath.isEmpty()) {
            // Derive thumbnail path
            File textureFile = new File(filePath);
            File thumbDir = textureFile.getParentFile();
            if (thumbDir != null) {
                File parentDir = new File(thumbDir, "thumbnails");
                File thumbFile = new File(parentDir, textureFile.getName());
                if (thumbFile.exists()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap bitmap = BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), options);
                    if (bitmap != null) {
                        return bitmap;
                    }
                }
            }

            // Fall back: load full image and scale down
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = 4; // Load at reduced size for efficiency
            Bitmap full = BitmapFactory.decodeFile(filePath, options);
            if (full != null) {
                if (full.getWidth() > 128 || full.getHeight() > 128) {
                    Bitmap scaled = Bitmap.createScaledBitmap(full, 128, 128, true);
                    if (full != scaled && !full.isRecycled()) {
                        full.recycle();
                    }
                    return scaled;
                }
                return full;
            }
        }

        return null;
    }

    // ========== Utility ==========

    /**
     * Returns the current filter string.
     *
     * @return the current format filter, or null if no filter is applied
     */
    public String getCurrentFilter() {
        return currentFilter;
    }

    /**
     * Returns the total number of textures (unfiltered).
     *
     * @return total texture count
     */
    public int getTotalTextureCount() {
        return textures.size();
    }

    /**
     * Returns the number of currently displayed textures.
     *
     * @return displayed texture count
     */
    public int getDisplayedTextureCount() {
        return filteredTextures.size();
    }

    /**
     * Checks if a filter is currently applied.
     *
     * @return true if a filter is active
     */
    public boolean isFiltered() {
        return currentFilter != null && !currentFilter.isEmpty();
    }
}
