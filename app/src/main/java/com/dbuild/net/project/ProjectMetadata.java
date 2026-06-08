package com.dbuild.net.project;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Model class representing metadata for a 3D Builder MC project.
 * Contains project identification, timestamps, statistics, and serialization support.
 */
public class ProjectMetadata {

    private static final String KEY_PROJECT_ID = "projectId";
    private static final String KEY_PROJECT_NAME = "projectName";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_MODIFIED_AT = "modifiedAt";
    private static final String KEY_BLOCK_COUNT = "blockCount";
    private static final String KEY_THUMBNAIL_PATH = "thumbnailPath";
    private static final String KEY_FILE_SIZE = "fileSize";
    private static final String KEY_VERSION = "version";

    public static final String CURRENT_VERSION = "1.0.0";

    private String projectId;
    private String projectName;
    private String description;
    private long createdAt;
    private long modifiedAt;
    private int blockCount;
    private String thumbnailPath;
    private long fileSize;
    private String version;

    /**
     * Default constructor. All fields are uninitialized.
     */
    public ProjectMetadata() {
    }

    /**
     * Full constructor with all fields.
     */
    public ProjectMetadata(String projectId, String projectName, String description,
                           long createdAt, long modifiedAt, int blockCount,
                           String thumbnailPath, long fileSize, String version) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.description = description;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.blockCount = blockCount;
        this.thumbnailPath = thumbnailPath;
        this.fileSize = fileSize;
        this.version = version;
    }

    /**
     * Factory method that creates a new ProjectMetadata with a generated UUID,
     * current timestamps, and default values.
     *
     * @param name the name of the new project
     * @return a new ProjectMetadata instance
     */
    public static ProjectMetadata createNew(String name) {
        long now = System.currentTimeMillis();
        ProjectMetadata metadata = new ProjectMetadata();
        metadata.setProjectId(UUID.randomUUID().toString());
        metadata.setProjectName(name);
        metadata.setDescription("");
        metadata.setCreatedAt(now);
        metadata.setModifiedAt(now);
        metadata.setBlockCount(0);
        metadata.setThumbnailPath("");
        metadata.setFileSize(0L);
        metadata.setVersion(CURRENT_VERSION);
        return metadata;
    }

    /**
     * Factory method that constructs a ProjectMetadata from a JSONObject.
     *
     * @param json the JSON object to read from
     * @return a populated ProjectMetadata instance
     * @throws JSONException if required keys are missing or have wrong types
     */
    public static ProjectMetadata fromJSON(JSONObject json) throws JSONException {
        ProjectMetadata metadata = new ProjectMetadata();
        metadata.setProjectId(json.getString(KEY_PROJECT_ID));
        metadata.setProjectName(json.getString(KEY_PROJECT_NAME));
        metadata.setDescription(json.optString(KEY_DESCRIPTION, ""));
        metadata.setCreatedAt(json.getLong(KEY_CREATED_AT));
        metadata.setModifiedAt(json.getLong(KEY_MODIFIED_AT));
        metadata.setBlockCount(json.optInt(KEY_BLOCK_COUNT, 0));
        metadata.setThumbnailPath(json.optString(KEY_THUMBNAIL_PATH, ""));
        metadata.setFileSize(json.optLong(KEY_FILE_SIZE, 0L));
        metadata.setVersion(json.optString(KEY_VERSION, CURRENT_VERSION));
        return metadata;
    }

    /**
     * Serializes this metadata to a JSONObject.
     *
     * @return a JSONObject representation of this metadata
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put(KEY_PROJECT_ID, projectId);
            json.put(KEY_PROJECT_NAME, projectName);
            json.put(KEY_DESCRIPTION, description);
            json.put(KEY_CREATED_AT, createdAt);
            json.put(KEY_MODIFIED_AT, modifiedAt);
            json.put(KEY_BLOCK_COUNT, blockCount);
            json.put(KEY_THUMBNAIL_PATH, thumbnailPath);
            json.put(KEY_FILE_SIZE, fileSize);
            json.put(KEY_VERSION, version);
        } catch (JSONException e) {
            // JSONObject.put should not throw for these primitive types,
            // but handle it defensively by wrapping in a RuntimeException.
            throw new RuntimeException("Failed to serialize ProjectMetadata to JSON", e);
        }
        return json;
    }

    /**
     * Updates the modifiedAt timestamp to the current time.
     */
    public void touchModified() {
        this.modifiedAt = System.currentTimeMillis();
    }

    /**
     * Returns true if this metadata has a valid projectId and projectName.
     *
     * @return true if the metadata is valid
     */
    public boolean isValid() {
        return projectId != null && !projectId.isEmpty()
                && projectName != null && !projectName.isEmpty();
    }

    /**
     * Returns a human-readable summary of the project.
     *
     * @return a summary string
     */
    public String getSummary() {
        return projectName + " (" + blockCount + " blocks, "
                + formatFileSize(fileSize) + ")";
    }

    /**
     * Formats a file size in bytes to a human-readable string.
     *
     * @param bytes the size in bytes
     * @return a formatted string like "1.5 MB"
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    // --- Getters and Setters ---

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ProjectMetadata{"
                + "projectId='" + projectId + '\''
                + ", projectName='" + projectName + '\''
                + ", blockCount=" + blockCount
                + ", fileSize=" + fileSize
                + ", version='" + version + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectMetadata that = (ProjectMetadata) o;
        return projectId != null ? projectId.equals(that.projectId) : that.projectId == null;
    }

    @Override
    public int hashCode() {
        return projectId != null ? projectId.hashCode() : 0;
    }
}
