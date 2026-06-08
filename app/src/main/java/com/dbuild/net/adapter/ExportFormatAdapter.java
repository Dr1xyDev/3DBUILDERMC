package com.dbuild.net.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView.Adapter for displaying export format options.
 * Pre-populated with GLB, GLTF, OBJ, and M3X formats.
 */
public class ExportFormatAdapter extends RecyclerView.Adapter<ExportFormatAdapter.ViewHolder> {

    /**
     * Data class representing an export format option.
     */
    public static class ExportFormat {
        private String name;
        private String extension;
        private String description;
        private int iconRes;

        public ExportFormat(String name, String extension, String description, int iconRes) {
            this.name = name;
            this.extension = extension;
            this.description = description;
            this.iconRes = iconRes;
        }

        public String getName() {
            return name;
        }

        public String getExtension() {
            return extension;
        }

        public String getDescription() {
            return description;
        }

        public int getIconRes() {
            return iconRes;
        }
    }

    /**
     * Callback interface for format selection events.
     */
    public interface OnFormatSelectedListener {
        /**
         * Called when an export format is selected.
         *
         * @param format the selected export format
         * @param position adapter position
         */
        void onFormatSelected(ExportFormat format, int position);
    }

    /**
     * ViewHolder for export format items.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView icon;
        public TextView name;
        public TextView description;

        public ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewWithTag("icon");
            name = itemView.findViewWithTag("name");
            description = itemView.findViewWithTag("description");
        }
    }

    private List<ExportFormat> formats;
    private Context context;
    private OnFormatSelectedListener listener;
    private int selectedPosition = -1;

    /**
     * Creates a new ExportFormatAdapter with pre-populated format options.
     *
     * @param context  activity context
     * @param listener format selection listener
     */
    public ExportFormatAdapter(Context context, OnFormatSelectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.formats = new ArrayList<>();
        populateDefaultFormats();
    }

    /**
     * Populates the adapter with the default export formats: GLB, GLTF, OBJ, M3X.
     */
    private void populateDefaultFormats() {
        int iconGlb = context.getResources().getIdentifier("ic_format_glb", "drawable", context.getPackageName());
        int iconGltf = context.getResources().getIdentifier("ic_format_gltf", "drawable", context.getPackageName());
        int iconObj = context.getResources().getIdentifier("ic_format_obj", "drawable", context.getPackageName());
        int iconM3x = context.getResources().getIdentifier("ic_format_m3x", "drawable", context.getPackageName());

        formats.add(new ExportFormat(
                "GLB",
                ".glb",
                "GL Transmission Format Binary — single-file 3D model with embedded textures and animations. " +
                        "Ideal for sharing and web viewing.",
                iconGlb
        ));

        formats.add(new ExportFormat(
                "GLTF",
                ".gltf",
                "GL Transmission Format — JSON-based 3D model format with separate texture files. " +
                        "Open standard for efficient transmission of 3D scenes.",
                iconGltf
        ));

        formats.add(new ExportFormat(
                "OBJ",
                ".obj",
                "Wavefront OBJ — widely supported mesh format with companion MTL material file. " +
                        "Compatible with most 3D software and game engines.",
                iconObj
        ));

        formats.add(new ExportFormat(
                "M3X",
                ".m3x",
                "Minecraft Model Exchange — optimized format for Minecraft-compatible 3D block models. " +
                        "Preserves block structure, per-face textures, and UV data.",
                iconM3x
        ));
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
        int layoutId = context.getResources().getIdentifier("item_export_format", "layout", context.getPackageName());
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
        layout.setPadding(16, 16, 16, 16);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView iconImg = new ImageView(context);
        iconImg.setTag("icon");
        iconImg.setLayoutParams(new android.widget.LinearLayout.LayoutParams(48, 48));
        iconImg.setBackgroundColor(0xFFE0E0E0);
        layout.addView(iconImg);

        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(context);
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        textContainer.setPadding(16, 0, 0, 0);
        textContainer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView nameView = new TextView(context);
        nameView.setTag("name");
        nameView.setTextSize(16);
        nameView.setTextColor(0xFF111111);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        textContainer.addView(nameView);

        TextView descView = new TextView(context);
        descView.setTag("description");
        descView.setTextSize(12);
        descView.setTextColor(0xFF666666);
        descView.setMaxLines(2);
        textContainer.addView(descView);

        layout.addView(textContainer);
        return layout;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= formats.size()) {
            return;
        }

        ExportFormat format = formats.get(position);

        // Set name with extension
        if (holder.name != null) {
            holder.name.setText(format.getName() + " (" + format.getExtension() + ")");
        }

        // Set description
        if (holder.description != null) {
            holder.description.setText(format.getDescription());
        }

        // Set icon
        if (holder.icon != null) {
            if (format.getIconRes() != 0) {
                holder.icon.setImageResource(format.getIconRes());
            } else {
                // Use a colored background as fallback icon
                int color = getFormatColor(format.getExtension());
                holder.icon.setBackgroundColor(color);
            }
        }

        // Highlight selected
        if (position == selectedPosition) {
            holder.itemView.setBackgroundColor(0x220000FF);
        } else {
            holder.itemView.setBackgroundColor(0x00000000);
        }

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            int prevSelected = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            if (prevSelected >= 0) {
                notifyItemChanged(prevSelected);
            }
            notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onFormatSelected(formats.get(selectedPosition), selectedPosition);
            }
        });
    }

    /**
     * Returns a representative color for each format extension.
     *
     * @param extension file extension string
     * @return ARGB color int
     */
    private int getFormatColor(String extension) {
        switch (extension) {
            case ".glb":
                return 0xFF4CAF50; // Green
            case ".gltf":
                return 0xFF2196F3; // Blue
            case ".obj":
                return 0xFFFF9800; // Orange
            case ".m3x":
                return 0xFF9C27B0; // Purple
            default:
                return 0xFF9E9E9E; // Grey
        }
    }

    @Override
    public int getItemCount() {
        return formats.size();
    }

    /**
     * Gets the format at the given position.
     *
     * @param position adapter position
     * @return ExportFormat or null
     */
    public ExportFormat getItem(int position) {
        if (position >= 0 && position < formats.size()) {
            return formats.get(position);
        }
        return null;
    }

    /**
     * Gets the currently selected format.
     *
     * @return selected ExportFormat, or null if none selected
     */
    public ExportFormat getSelectedFormat() {
        if (selectedPosition >= 0 && selectedPosition < formats.size()) {
            return formats.get(selectedPosition);
        }
        return null;
    }

    /**
     * Gets the selected position.
     *
     * @return selected position, or -1 if none
     */
    public int getSelectedPosition() {
        return selectedPosition;
    }

    /**
     * Sets the selected position programmatically.
     *
     * @param position position to select
     */
    public void setSelectedPosition(int position) {
        int prevSelected = selectedPosition;
        selectedPosition = position;
        if (prevSelected >= 0) {
            notifyItemChanged(prevSelected);
        }
        if (selectedPosition >= 0) {
            notifyItemChanged(selectedPosition);
        }
    }

    /**
     * Adds a custom export format to the list.
     *
     * @param format the format to add
     */
    public void addFormat(ExportFormat format) {
        if (format != null) {
            formats.add(format);
            notifyItemInserted(formats.size() - 1);
        }
    }

    /**
     * Removes an export format by position.
     *
     * @param position position to remove
     */
    public void removeFormat(int position) {
        if (position >= 0 && position < formats.size()) {
            formats.remove(position);
            notifyItemRemoved(position);
            if (selectedPosition == position) {
                selectedPosition = -1;
            } else if (selectedPosition > position) {
                selectedPosition--;
            }
        }
    }
}
