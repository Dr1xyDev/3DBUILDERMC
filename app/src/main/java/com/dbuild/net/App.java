package com.dbuild.net;

import android.app.Application;
import com.dbuild.net.blocks.BlockRegistry;
import com.dbuild.net.engine.RustBridge;
import com.dbuild.net.util.ThemeManager;
import com.dbuild.net.util.UISizeManager;

public class App extends Application {

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize theme
        ThemeManager themeManager = ThemeManager.getInstance(this);
        themeManager.applyTheme(null);

        // Initialize UI size
        UISizeManager uiSizeManager = UISizeManager.getInstance(this);

        // Initialize native engine
        RustBridge.load();

        // Initialize default blocks
        BlockRegistry registry = BlockRegistry.getInstance();
        registry.initializeDefaultBlocks();
    }

    public static App getInstance() {
        return instance;
    }
}
