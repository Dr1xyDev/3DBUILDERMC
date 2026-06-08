package com.dbuild.net;

import org.junit.Test;
import static org.junit.Assert.*;

import com.dbuild.net.model.Scene;
import com.dbuild.net.model.Block3D;
import com.dbuild.net.uv.UVData;
import com.dbuild.net.project.ProjectMetadata;

public class ExampleUnitTest {

    @Test
    public void testSceneCreation() {
        Scene scene = Scene.createNew("Test Scene");
        assertNotNull(scene);
        assertEquals("Test Scene", scene.getSceneName());
    }

    @Test
    public void testBlockCreation() {
        Block3D block = new Block3D(0, 0, 0);
        assertNotNull(block);
        assertEquals(0, block.getX());
        assertEquals(0, block.getY());
        assertEquals(0, block.getZ());
    }

    @Test
    public void testUVDataDefaults() {
        UVData uv = new UVData();
        assertEquals(0.0f, uv.getU(), 0.001f);
        assertEquals(0.0f, uv.getV(), 0.001f);
        assertEquals(1.0f, uv.getUScale(), 0.001f);
        assertEquals(1.0f, uv.getVScale(), 0.001f);
    }

    @Test
    public void testProjectMetadataCreation() {
        ProjectMetadata meta = ProjectMetadata.createNew("Test Project");
        assertNotNull(meta);
        assertEquals("Test Project", meta.getProjectName());
        assertNotNull(meta.getProjectId());
    }

    @Test
    public void testBlockPositionKey() {
        String key = Block3D.makePositionKey(5, 10, 15);
        assertEquals("5,10,15", key);
    }

    @Test
    public void testUVDataClamping() {
        UVData uv = new UVData(-0.5f, 1.5f, 0.0f, 3.0f, 0.0f);
        uv.clampValues();
        assertTrue(uv.getU() >= 0.0f);
        assertTrue(uv.getV() >= 0.0f);
        assertTrue(uv.getUScale() > 0.0f);
        assertTrue(uv.getVScale() > 0.0f);
    }
}
