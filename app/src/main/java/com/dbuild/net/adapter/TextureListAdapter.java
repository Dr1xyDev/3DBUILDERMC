package com.dbuild.net.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.dbuild.net.texture.TextureManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * BaseAdapter for displaying textures in a GridView.
 * Supports LRU caching for thumbnails, async loading, and a selection listener.
 */
public class TextureListAdapter extends BaseAdapter {

    private List<TextureManager.TextureInfo> textures;
    private Context context;
    private OnTextureSelectedListener listener;
    private LruThumbnailCache thumbnailCache;
    private int selectedPosition = -1;

    /**
     * Callback interface for texture selection events.
     */
    public interface OnTextureSelectedListener {
        /**
         * Called when a texture is selected.
         *
         * @param textureInfo the selected texture info
         * @param position    the position in the list
         */
        void onTextureSelected(TextureManager.TextureInfo textureInfo, int position);
    }

    /**
     * LRU cache for bitmap thumbnails to avoid repeated disk I/O.
     */
    private static class LruThumbnailCache {
        private final android.util.LruCache<String, Bitmap> cache;

        LruThumbnailCache(int maxSizeBytes) {
            cache = new android.util.LruCache<String, Bitmap>(maxSizeBytes) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
        }

        public Bitmap get(String key) {
            return cache.get(key);
        }

        public void put(String key, Bitmap bitmap) {
            if (key != null && bitmap != null) {
                cache.put(key, bitmap);
            }
        }

        public void evictAll() {
            cache.evict();
        }
    }

    /**
     * ViewHolder pattern for GridView items.
     */
    private static class ViewHolder {
        ImageView thumbnail;
        TextView name;
        TextView dimensions;
    }

    /**
     * Creates a new TextureListAdapter.
     *
     * @param context  activity context
     * @param listener texture selection listener
     */
    public TextureListAdapter(Context context, OnTextureSelectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.textures = new ArrayList<>();
        // Use 1/8 of available memory for thumbnail cache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        this.thumbnailCache = new LruThumbnailCache(cacheSize * 1024);
    }

    @Override
    public int getCount() {
        return textures.size();
    }

    @Override
    public TextureManager.TextureInfo getItem(int position) {
        if (position >= 0 && position < textures.size()) {
            return textures.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = createItemView(parent);
            holder = new ViewHolder();
            holder.thumbnail = convertView.findViewWithTag("thumbnail");
            holder.name = convertView.findViewWithTag("name");
            holder.dimensions = convertView.findViewWithTag("dimensions");

            if (holder.thumbnail == null || holder.name == null || holder.dimensions == null) {
                // Fallback: create views programmatically
                convertView = createFallbackView(parent);
                holder.thumbnail = (ImageView) convertView.getChildAt(0);
                android.widget.LinearLayout textContainer =
                        (android.widget.LinearLayout) ((android.widget.LinearLayout) convertView).getChildAt(1);
                holder.name = (TextView) textContainer.getChildAt(0);
                holder.dimensions = (TextView) textContainer.getChildAt(1);
            }

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        TextureManager.TextureInfo textureInfo = textures.get(position);

        // Set name
        holder.name.setText(textureInfo.getName());

        // Set dimensions
        String dimText = textureInfo.getWidth() + " x " + textureInfo.getHeight();
        holder.dimensions.setText(dimText);

        // Load thumbnail (async with cache)
        loadThumbnail(textureInfo, holder.thumbnail);

        // Highlight selected
        if (position == selectedPosition) {
            convertView.setBackgroundColor(0x330000FF);
        } else {
            convertView.setBackgroundColor(0x00000000);
        }

        // Click listener
        final int pos = position;
        convertView.setOnClickListener(v -> {
            selectedPosition = pos;
            notifyDataSetChanged();
            if (listener != null) {
                listener.onTextureSelected(textures.get(pos), pos);
            }
        });

        return convertView;
    }

    /**
     * Creates an item view from the layout resource.
     */
    private View createItemView(ViewGroup parent) {
        int layoutId = context.getResources().getIdentifier("item_texture", "layout", context.getPackageName());
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
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(8, 8, 8, 8);
        layout.setLayoutParams(new GridView.LayoutParams(
                GridView.LayoutParams.MATCH_PARENT, GridView.LayoutParams.WRAP_CONTENT));

        ImageView thumb = new ImageView(context);
        thumb.setTag("thumbnail");
        thumb.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 120));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(0xFFEEEEEE);
        layout.addView(thumb);

        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(context);
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);

        TextView nameView = new TextView(context);
        nameView.setTag("name");
        nameView.setTextSize(12);
        nameView.setTextColor(0xFF333333);
        nameView.setMaxLines(1);
        textContainer.addView(nameView);

        TextView dimView = new TextView(context);
        dimView.setTag("dimensions");
        dimView.setTextSize(10);
        dimView.setTextColor(0xFF999999);
        textContainer.addView(dimView);

        layout.addView(textContainer);
        return layout;
    }

    /**
     * Loads a texture thumbnail asynchronously, using the LRU cache.
     *
     * @param textureInfo texture information
     * @param imageView   target ImageView
     */
    private void loadThumbnail(TextureManager.TextureInfo textureInfo, ImageView imageView) {
        if (imageView == null) return;

        String cacheKey = textureInfo.getPath();
        Bitmap cached = thumbnailCache.get(cacheKey);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        // Show placeholder
        imageView.setImageResource(
                context.getResources().getIdentifier("ic_texture_placeholder", "drawable", context.getPackageName()));

        // Async load
        new ThumbnailAsyncTask(imageView, thumbnailCache).execute(textureInfo.getPath());
    }

    /**
     * AsyncTask for loading texture thumbnails from disk.
     */
    private static class ThumbnailAsyncTask extends AsyncTask<String, Void, Bitmap> {
        private WeakReference<ImageView> imageViewRef;
        private LruThumbnailCache cache;
        private String path;
        private static final int THUMB_SIZE = 128;

        ThumbnailAsyncTask(ImageView imageView, LruThumbnailCache cache) {
            this.imageViewRef = new WeakReference<>(imageView);
            this.cache = cache;
        }

        @Override
        protected Bitmap doInBackground(String... paths) {
            if (paths == null || paths.length == 0) return null;
            path = paths[0];

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            options.inSampleSize = calculateSampleSize(options, THUMB_SIZE, THUMB_SIZE);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeFile(path, options);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                cache.put(path, bitmap);
                ImageView imageView = imageViewRef.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }

        private int calculateSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                while ((halfHeight / inSampleSize) >= reqHeight
                        && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            return inSampleSize;
        }
    }

    /**
     * Sets the list of textures to display.
     *
     * @param textures new list of texture info
     */
    public void setTextures(List<TextureManager.TextureInfo> textures) {
        this.textures = textures != null ? new ArrayList<>(textures) : new ArrayList<>();
        this.selectedPosition = -1;
        notifyDataSetChanged();
    }

    /**
     * Gets the currently selected position.
     *
     * @return selected position, or -1 if none
     */
    public int getSelectedPosition() {
        return selectedPosition;
    }

    /**
     * Gets the currently selected texture info.
     *
     * @return selected TextureInfo, or null
     */
    public TextureManager.TextureInfo getSelectedTexture() {
        if (selectedPosition >= 0 && selectedPosition < textures.size()) {
            return textures.get(selectedPosition);
        }
        return null;
    }

    /**
     * Clears the thumbnail cache to free memory.
     */
    public void clearCache() {
        thumbnailCache.evictAll();
    }
}
