package com.dbuild.net.adapter;

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

import com.dbuild.net.project.ProjectMetadata;
import com.dbuild.net.project.ProjectThumbnailGenerator;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView.Adapter for displaying a list of project metadata in the project list screen.
 * Supports filtering by name, sorting by name/date/size, and click/long-click callbacks.
 */
public class ProjectListAdapter extends RecyclerView.Adapter<ProjectListAdapter.ViewHolder> {

    private List<ProjectMetadata> projects;
    private List<ProjectMetadata> filteredProjects;
    private Context context;
    private OnProjectClickListener listener;
    private String currentFilter;
    private SimpleDateFormat dateFormat;

    /**
     * Callback interface for project item click events.
     */
    public interface OnProjectClickListener {
        /**
         * Called when a project is clicked.
         *
         * @param projectId the ID of the clicked project
         */
        void onProjectClick(String projectId);

        /**
         * Called when a project is long-clicked.
         *
         * @param projectId the ID of the long-clicked project
         * @param position  the adapter position
         */
        void onProjectLongClick(String projectId, int position);
    }

    /**
     * ViewHolder for project list items.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView thumbnail;
        public TextView name;
        public TextView modifiedDate;
        public TextView blockCount;

        public ViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(
                    itemView.getResources().getIdentifier("thumbnail", "id", itemView.getContext().getPackageName()));
            name = itemView.findViewById(
                    itemView.getResources().getIdentifier("project_name", "id", itemView.getContext().getPackageName()));
            modifiedDate = itemView.findViewById(
                    itemView.getResources().getIdentifier("modified_date", "id", itemView.getContext().getPackageName()));
            blockCount = itemView.findViewById(
                    itemView.getResources().getIdentifier("block_count", "id", itemView.getContext().getPackageName()));
        }
    }

    /**
     * Creates a new ProjectListAdapter.
     *
     * @param context  activity context
     * @param listener click event listener
     */
    public ProjectListAdapter(Context context, OnProjectClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.projects = new ArrayList<>();
        this.filteredProjects = new ArrayList<>();
        this.currentFilter = "";
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = context.getResources().getIdentifier("item_project", "layout", context.getPackageName());
        View view;
        if (layoutId != 0) {
            view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        } else {
            // Fallback: create a simple layout programmatically
            view = createFallbackView(parent);
        }
        return new ViewHolder(view);
    }

    /**
     * Creates a fallback item view when the layout resource is not found.
     */
    private View createFallbackView(ViewGroup parent) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setPadding(16, 16, 16, 16);

        ImageView thumb = new ImageView(context);
        thumb.setId(View.generateViewId());
        thumb.setLayoutParams(new android.widget.LinearLayout.LayoutParams(120, 120));
        thumb.setBackgroundColor(0xFFCCCCCC);
        layout.addView(thumb);

        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(context);
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        textContainer.setPadding(16, 0, 0, 0);

        TextView nameView = new TextView(context);
        nameView.setId(View.generateViewId());
        nameView.setTextSize(16);
        nameView.setTextColor(0xFF000000);
        textContainer.addView(nameView);

        TextView dateView = new TextView(context);
        dateView.setId(View.generateViewId());
        dateView.setTextSize(12);
        dateView.setTextColor(0xFF666666);
        textContainer.addView(dateView);

        TextView countView = new TextView(context);
        countView.setId(View.generateViewId());
        countView.setTextSize(12);
        countView.setTextColor(0xFF999999);
        textContainer.addView(countView);

        layout.addView(textContainer);
        return layout;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= filteredProjects.size()) {
            return;
        }

        ProjectMetadata project = filteredProjects.get(position);

        // Set project name
        if (holder.name != null) {
            holder.name.setText(project.getName());
        }

        // Set modified date
        if (holder.modifiedDate != null) {
            String dateStr = dateFormat.format(new Date(project.getModifiedAt()));
            holder.modifiedDate.setText(dateStr);
        }

        // Set block count
        if (holder.blockCount != null) {
            holder.blockCount.setText(project.getBlockCount() + " blocks");
        }

        // Load thumbnail
        loadThumbnail(project, holder.thumbnail);

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProjectClick(project.getProjectId());
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onProjectLongClick(project.getProjectId(), holder.getAdapterPosition());
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return filteredProjects.size();
    }

    /**
     * Updates the full project list and refreshes the display.
     *
     * @param projects new list of projects
     */
    public void setProjects(List<ProjectMetadata> projects) {
        this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
        applyFilter();
        notifyDataSetChanged();
    }

    /**
     * Filters the project list by name. An empty or null query shows all projects.
     *
     * @param query search query to filter by
     */
    public void filter(String query) {
        currentFilter = query != null ? query.toLowerCase(Locale.getDefault()).trim() : "";
        applyFilter();
        notifyDataSetChanged();
    }

    /**
     * Applies the current filter to the project list.
     */
    private void applyFilter() {
        filteredProjects.clear();
        if (currentFilter.isEmpty()) {
            filteredProjects.addAll(projects);
        } else {
            for (ProjectMetadata project : projects) {
                if (project.getName().toLowerCase(Locale.getDefault()).contains(currentFilter)) {
                    filteredProjects.add(project);
                }
            }
        }
    }

    /**
     * Sorts the filtered projects by name alphabetically.
     */
    public void sortByName() {
        Collections.sort(filteredProjects, (p1, p2) ->
                p1.getName().compareToIgnoreCase(p2.getName()));
        notifyDataSetChanged();
    }

    /**
     * Sorts the filtered projects by modification date (newest first).
     */
    public void sortByDate() {
        Collections.sort(filteredProjects, (p1, p2) ->
                Long.compare(p2.getModifiedAt(), p1.getModifiedAt()));
        notifyDataSetChanged();
    }

    /**
     * Sorts the filtered projects by block count (largest first).
     */
    public void sortBySize() {
        Collections.sort(filteredProjects, (p1, p2) ->
                Integer.compare(p2.getBlockCount(), p1.getBlockCount()));
        notifyDataSetChanged();
    }

    /**
     * Loads the thumbnail for a project. If no thumbnail exists, generates one
     * using the ProjectThumbnailGenerator.
     *
     * @param project   the project metadata
     * @param imageView the ImageView to load into
     */
    public void loadThumbnail(ProjectMetadata project, ImageView imageView) {
        if (imageView == null) return;

        String thumbnailPath = project.getThumbnailPath();
        if (thumbnailPath != null && !thumbnailPath.isEmpty()) {
            File thumbFile = new File(thumbnailPath);
            if (thumbFile.exists()) {
                new ThumbnailLoadTask(imageView).execute(thumbnailPath);
                return;
            }
        }

        // Generate thumbnail if missing
        new ThumbnailGenerateTask(imageView, project).execute();
    }

    /**
     * AsyncTask to load a thumbnail from file.
     */
    private class ThumbnailLoadTask extends AsyncTask<String, Void, Bitmap> {
        private ImageView imageView;
        private int targetWidth = 120;
        private int targetHeight = 120;

        ThumbnailLoadTask(ImageView imageView) {
            this.imageView = imageView;
        }

        @Override
        protected Bitmap doInBackground(String... paths) {
            if (paths == null || paths.length == 0) return null;
            String path = paths[0];

            // First decode with inJustDecodeBounds to get dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeFile(path, options);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    /**
     * AsyncTask to generate a missing thumbnail.
     */
    private class ThumbnailGenerateTask extends AsyncTask<Void, Void, Bitmap> {
        private ImageView imageView;
        private ProjectMetadata project;

        ThumbnailGenerateTask(ImageView imageView, ProjectMetadata project) {
            this.imageView = imageView;
            this.project = project;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            Bitmap thumbnail = ProjectThumbnailGenerator.generateThumbnail(context, project);
            if (thumbnail != null) {
                // Save the generated thumbnail for future use
                ProjectThumbnailGenerator.saveThumbnail(thumbnail, project.getThumbnailPath());
            }
            return thumbnail;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    /**
     * Calculates a sample size for bitmap decoding that is a power of two
     * and results in an image close to the requested width and height.
     *
     * @param options   BitmapFactory.Options with outWidth and outHeight set
     * @param reqWidth  requested width
     * @param reqHeight requested height
     * @return sample size
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
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

    /**
     * Gets the project at the given adapter position.
     *
     * @param position adapter position
     * @return ProjectMetadata or null
     */
    public ProjectMetadata getItem(int position) {
        if (position >= 0 && position < filteredProjects.size()) {
            return filteredProjects.get(position);
        }
        return null;
    }
}
