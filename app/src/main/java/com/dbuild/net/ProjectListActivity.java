package com.dbuild.net;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectListActivity extends AppCompatActivity {

    private static final int REQUEST_NEW_PROJECT = 1001;
    private static final int REQUEST_OPEN_PROJECT = 1002;

    private RecyclerView recyclerView;
    private EditText searchBar;
    private FloatingActionButton fabNewProject;
    private ImageButton btnSort;
    private LinearLayout emptyStateLayout;
    private ProjectListAdapter adapter;
    private ProjectManager projectManager;
    private List<ProjectInfo> allProjects;
    private List<ProjectInfo> filteredProjects;
    private int sortMode = 0; // 0=date, 1=name, 2=size

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_project_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.project_list_title);
        }

        projectManager = ProjectManager.getInstance(this);
        allProjects = new ArrayList<>();
        filteredProjects = new ArrayList<>();

        recyclerView = findViewById(R.id.recycler_projects);
        searchBar = findViewById(R.id.search_bar);
        fabNewProject = findViewById(R.id.fab_new_project);
        btnSort = findViewById(R.id.btn_sort);
        emptyStateLayout = findViewById(R.id.empty_state_layout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectListAdapter(this, filteredProjects);
        recyclerView.setAdapter(adapter);

        setupSearchBar();
        setupSortButton();
        setupFab();

        loadProjects();
    }

    private void setupSearchBar() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProjects(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSortButton() {
        btnSort.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenuInflater().inflate(R.menu.menu_sort_options, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.sort_by_date) {
                    sortMode = 0;
                } else if (id == R.id.sort_by_name) {
                    sortMode = 1;
                } else if (id == R.id.sort_by_size) {
                    sortMode = 2;
                }
                sortProjects();
                return true;
            });
            popup.show();
        });
    }

    private void setupFab() {
        fabNewProject.setOnClickListener(v -> {
            showNewProjectDialog();
        });
    }

    private void loadProjects() {
        allProjects.clear();
        List<ProjectInfo> loaded = projectManager.getAllProjects();
        if (loaded != null) {
            allProjects.addAll(loaded);
        }
        sortProjects();
        updateEmptyState();
    }

    private void filterProjects(String query) {
        filteredProjects.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredProjects.addAll(allProjects);
        } else {
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            for (ProjectInfo project : allProjects) {
                if (project.getName().toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                    filteredProjects.add(project);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void sortProjects() {
        Comparator<ProjectInfo> comparator;
        switch (sortMode) {
            case 1:
                comparator = (a, b) -> a.getName().compareToIgnoreCase(b.getName());
                break;
            case 2:
                comparator = (a, b) -> Long.compare(b.getBlockCount(), a.getBlockCount());
                break;
            default:
                comparator = (a, b) -> Long.compare(b.getLastModified(), a.getLastModified());
                break;
        }
        Collections.sort(allProjects, comparator);
        String currentQuery = searchBar.getText().toString();
        filterProjects(currentQuery);
    }

    private void updateEmptyState() {
        if (filteredProjects.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showNewProjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.new_project_title);
        final EditText input = new EditText(this);
        input.setHint(R.string.project_name_hint);
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);
        builder.setPositiveButton(R.string.create, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.project_name_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            String projectId = projectManager.createProject(name);
            Intent result = new Intent();
            result.putExtra("project_id", projectId);
            result.putExtra("project_name", name);
            setResult(RESULT_OK, result);
            finish();
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showContextMenu(ProjectInfo project, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_project_context, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.ctx_open) {
                openProject(project);
            } else if (id == R.id.ctx_rename) {
                showRenameDialog(project);
            } else if (id == R.id.ctx_delete) {
                showDeleteConfirmDialog(project);
            } else if (id == R.id.ctx_share) {
                shareProject(project);
            } else if (id == R.id.ctx_export) {
                exportProject(project);
            }
            return true;
        });
        popup.show();
    }

    private void openProject(ProjectInfo project) {
        Intent result = new Intent();
        result.putExtra("project_id", project.getId());
        result.putExtra("project_name", project.getName());
        setResult(RESULT_OK, result);
        finish();
    }

    private void showRenameDialog(ProjectInfo project) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.rename_project);
        final EditText input = new EditText(this);
        input.setText(project.getName());
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);
        builder.setPositiveButton(R.string.rename, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                projectManager.renameProject(project.getId(), newName);
                loadProjects();
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeleteConfirmDialog(ProjectInfo project) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_project_title);
        builder.setMessage(getString(R.string.delete_project_message, project.getName()));
        builder.setPositiveButton(R.string.delete, (dialog, which) -> {
            projectManager.deleteProject(project.getId());
            loadProjects();
            Toast.makeText(this, R.string.project_deleted, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void shareProject(ProjectInfo project) {
        Intent shareIntent = new Intent(this, ProjectShareActivity.class);
        shareIntent.putExtra("project_id", project.getId());
        startActivity(shareIntent);
    }

    private void exportProject(ProjectInfo project) {
        Intent exportIntent = new Intent(this, ExportActivity.class);
        exportIntent.putExtra("project_id", project.getId());
        startActivity(exportIntent);
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
        loadProjects();
    }

    private static class ProjectListAdapter extends RecyclerView.Adapter<ProjectListAdapter.ViewHolder> {

        private final Context context;
        private final List<ProjectInfo> projects;
        private final SimpleDateFormat dateFormat;

        ProjectListAdapter(Context context, List<ProjectInfo> projects) {
            this.context = context;
            this.projects = projects;
            this.dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = View.inflate(context, R.layout.item_project, null);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectInfo project = projects.get(position);
            holder.textName.setText(project.getName());
            holder.textDate.setText(dateFormat.format(new Date(project.getLastModified())));
            holder.textBlockCount.setText(context.getString(R.string.block_count_format, project.getBlockCount()));

            Bitmap thumbnail = project.getThumbnail();
            if (thumbnail != null) {
                holder.imageThumbnail.setImageBitmap(thumbnail);
            } else {
                holder.imageThumbnail.setImageResource(R.drawable.ic_project_placeholder);
            }

            holder.itemView.setOnClickListener(v -> {
                ProjectListActivity activity = (ProjectListActivity) context;
                activity.openProject(project);
            });

            holder.itemView.setOnLongClickListener(v -> {
                ProjectListActivity activity = (ProjectListActivity) context;
                activity.showContextMenu(project, v);
                return true;
            });

            holder.btnMore.setOnClickListener(v -> {
                ProjectListActivity activity = (ProjectListActivity) context;
                activity.showContextMenu(project, v);
            });
        }

        @Override
        public int getItemCount() {
            return projects.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageThumbnail;
            TextView textName;
            TextView textDate;
            TextView textBlockCount;
            ImageView btnMore;

            ViewHolder(View itemView) {
                super(itemView);
                imageThumbnail = itemView.findViewById(R.id.image_thumbnail);
                textName = itemView.findViewById(R.id.text_name);
                textDate = itemView.findViewById(R.id.text_date);
                textBlockCount = itemView.findViewById(R.id.text_block_count);
                btnMore = itemView.findViewById(R.id.btn_more);
            }
        }
    }
}
