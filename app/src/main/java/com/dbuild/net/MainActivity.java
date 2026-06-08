package com.dbuild.net;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dbuild.net.project.ProjectManager;
import com.dbuild.net.project.ProjectInfo;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Main screen of the 3D Builder MC application.
 * Displays the project list with search filtering, and provides
 * navigation to the editor, import, and settings screens.
 */
public class MainActivity extends AppCompatActivity implements ProjectListAdapter.OnProjectClickListener {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final String PREFS_NAME = "dbuild_prefs";
    private static final String KEY_LAST_PROJECT_ID = "last_project_id";

    private Toolbar toolbar;
    private EditText searchEditText;
    private RecyclerView recyclerViewProjects;
    private FloatingActionButton fabNewProject;
    private View buttonImportProject;

    private ProjectManager projectManager;
    private ProjectListAdapter projectListAdapter;
    private List<ProjectInfo> allProjects;
    private List<ProjectInfo> filteredProjects;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        projectManager = ProjectManager.getInstance(this);

        initViews();
        setupToolbar();
        setupSearchBar();
        setupRecyclerView();
        setupButtons();
        requestStoragePermissions();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        searchEditText = findViewById(R.id.edit_text_search);
        recyclerViewProjects = findViewById(R.id.recycler_view_projects);
        fabNewProject = findViewById(R.id.fab_new_project);
        buttonImportProject = findViewById(R.id.button_import_project);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }
    }

    private void setupSearchBar() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed before text changes
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProjects(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed after text changes
            }
        });
    }

    private void setupRecyclerView() {
        allProjects = new ArrayList<>();
        filteredProjects = new ArrayList<>();

        projectListAdapter = new ProjectListAdapter(filteredProjects, this);
        recyclerViewProjects.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewProjects.setAdapter(projectListAdapter);

        // Item decoration: spacing between items
        int spacing = getResources().getDimensionPixelSize(R.dimen.project_item_spacing);
        recyclerViewProjects.addItemDecoration(new ProjectItemDecoration(spacing));
    }

    private void setupButtons() {
        fabNewProject.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, EditorActivity.class);
            startActivity(intent);
        });

        buttonImportProject.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProjectImportActivity.class);
            startActivity(intent);
        });
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_STORAGE_PERMISSION
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProjectList();
    }

    private void loadProjectList() {
        allProjects = projectManager.getAllProjects();
        String currentQuery = searchEditText.getText().toString();
        filterProjects(currentQuery);
    }

    private void filterProjects(String query) {
        filteredProjects.clear();

        if (query == null || query.trim().isEmpty()) {
            filteredProjects.addAll(allProjects);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (ProjectInfo project : allProjects) {
                if (project.getName().toLowerCase().contains(lowerQuery)) {
                    filteredProjects.add(project);
                }
            }
        }

        projectListAdapter.updateData(filteredProjects);
    }

    @Override
    public void onProjectClick(ProjectInfo project) {
        saveLastOpenedProject(project.getId());

        Intent intent = new Intent(MainActivity.this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_ID, project.getId());
        startActivity(intent);
    }

    @Override
    public void onProjectLongClick(ProjectInfo project) {
        String[] options = {getString(R.string.open), getString(R.string.rename),
                getString(R.string.duplicate), getString(R.string.delete)};

        new AlertDialog.Builder(this)
                .setTitle(project.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Open
                            onProjectClick(project);
                            break;
                        case 1: // Rename
                            showRenameDialog(project);
                            break;
                        case 2: // Duplicate
                            projectManager.duplicateProject(project.getId());
                            loadProjectList();
                            Toast.makeText(this, R.string.project_duplicated, Toast.LENGTH_SHORT).show();
                            break;
                        case 3: // Delete
                            showDeleteConfirmDialog(project);
                            break;
                    }
                })
                .show();
    }

    private void showRenameDialog(final ProjectInfo project) {
        final EditText input = new EditText(this);
        input.setText(project.getName());
        input.setSelection(project.getName().length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_project)
                .setView(input)
                .setPositiveButton(R.string.rename, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        projectManager.renameProject(project.getId(), newName);
                        loadProjectList();
                        Toast.makeText(this, R.string.project_renamed, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteConfirmDialog(final ProjectInfo project) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_project)
                .setMessage(getString(R.string.delete_project_confirm, project.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    projectManager.deleteProject(project.getId());
                    loadProjectList();
                    Toast.makeText(this, R.string.project_deleted, Toast.LENGTH_SHORT).show();

                    // Clear last opened project if it was deleted
                    String lastId = sharedPreferences.getString(KEY_LAST_PROJECT_ID, "");
                    if (project.getId().equals(lastId)) {
                        sharedPreferences.edit().remove(KEY_LAST_PROJECT_ID).apply();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveLastOpenedProject(String projectId) {
        sharedPreferences.edit().putString(KEY_LAST_PROJECT_ID, projectId).apply();
    }

    private String getLastOpenedProjectId() {
        return sharedPreferences.getString(KEY_LAST_PROJECT_ID, "");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(getString(R.string.about_message,
                        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
