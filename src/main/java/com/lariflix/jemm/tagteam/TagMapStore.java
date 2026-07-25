package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.utils.JemmSettingsStore;
import java.io.File;
import java.io.IOException;

/**
 * Manages the single active Tag-Team tag map. The active map lives at a fixed
 * app-owned location (remembered in {@link JemmSettingsStore}); external JSON files
 * can be brought in via {@link #importFrom(File)} and written out via {@link #exportTo(File)}.
 */
public class TagMapStore {

    private final TagMapLoader loader = new TagMapLoader();
    private final File file;

    public TagMapStore() {
        this(new File(new JemmSettingsStore().getTagMapPath()));
    }

    public TagMapStore(File file) {
        this.file = file;
    }

    /**
     * @return the file backing the active tag map
     */
    public File getFile() {
        return file;
    }

    /**
     * @return true when the active tag map file exists on disk
     */
    public boolean exists() {
        return file != null && file.isFile();
    }

    /**
     * Loads the active tag map, or an empty map when none has been saved yet.
     */
    public TagMap loadActive() throws IOException {
        if (!exists()) {
            return new TagMap();
        }
        return loader.load(file);
    }

    /**
     * Persists the given map as the active map.
     */
    public void saveActive(TagMap map) throws IOException {
        loader.save(map, file);
    }

    /**
     * Loads a tag map from an external file and makes it the active map.
     *
     * @return the imported (and now active) map
     */
    public TagMap importFrom(File source) throws IOException {
        TagMap map = loader.load(source);
        saveActive(map);
        return map;
    }

    /**
     * Writes the current active map to an external file.
     */
    public void exportTo(File dest) throws IOException {
        TagMap map = loadActive();
        loader.save(map, dest);
    }
}
