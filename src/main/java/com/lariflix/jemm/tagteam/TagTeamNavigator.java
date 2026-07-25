package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.core.LoadItemMetadata;
import com.lariflix.jemm.core.LoadItems;
import com.lariflix.jemm.dtos.JellyfinItem;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.dtos.JellyfinItems;
import com.lariflix.jemm.utils.JellyfinParameters;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.simple.parser.ParseException;

/**
 * Produces the ordered list of "stops" Tag-Team mode walks: for every selected library,
 * the folder itself, then its files, then recursively its subfolders. The stop list is
 * built up-front from cheap listing calls (no per-item metadata); full metadata is loaded
 * on demand via {@link #loadMetadata(String)}.
 *
 * <p>Supports next/prev, jump-to, and the skip semantics: skip file (advance one), skip
 * folder (advance past the folder stop to its files) and skip rest of folder (jump past the
 * whole subtree of the current stop's enclosing folder). A media-type filter controls which
 * files become stops; folders are always included.</p>
 */
public class TagTeamNavigator {

    public enum StopType { FOLDER, ITEM }

    public enum MediaKind { VIDEO, IMAGE, OTHER }

    private final ConnectJellyfinAPI api;
    private final List<String> rootFolderIds;
    private final boolean includeVideos;
    private final boolean includeImages;
    private final boolean includeOther;

    private final List<Stop> stops = new ArrayList<>();
    private final Map<String, Integer> folderStopIndex = new HashMap<>();
    private final Map<String, JellyfinItemMetadata> metadataCache = new HashMap<>();
    private final Set<String> dirtyIds = new HashSet<>();
    private String adminUserId;
    private int currentIndex = 0;

    public TagTeamNavigator(ConnectJellyfinAPI api, List<String> rootFolderIds,
            boolean includeVideos, boolean includeImages, boolean includeOther) {
        this.api = api;
        this.rootFolderIds = rootFolderIds;
        this.includeVideos = includeVideos;
        this.includeImages = includeImages;
        this.includeOther = includeOther;
    }

    /**
     * Lists all folders/items under the selected roots and builds the ordered stop list.
     */
    public void build() throws IOException, ParseException {
        stops.clear();
        folderStopIndex.clear();
        metadataCache.clear();
        dirtyIds.clear();
        adminUserId = api.getAdminUser().getId();
        Set<String> visited = new HashSet<>();
        if (rootFolderIds != null) {
            for (String rootId : rootFolderIds) {
                if (rootId == null || rootId.isBlank()) {
                    continue;
                }
                buildFolder(rootId, "", rootId, visited);
            }
        }
        currentIndex = 0;
    }

    /**
     * Preloads full metadata for every stop into the local cache.
     *
     * @param progress optional callback receiving (done, total); may be null
     */
    public void preloadAll(ProgressCallback progress) throws IOException, ParseException {
        int total = stops.size();
        int done = 0;
        for (Stop stop : stops) {
            if (!metadataCache.containsKey(stop.getId())) {
                JellyfinItemMetadata meta = loadMetadata(stop.getId());
                metadataCache.put(stop.getId(), meta);
                if (meta != null && meta.getName() != null && !meta.getName().isBlank()) {
                    stop.setDisplayName(meta.getName());
                }
            }
            done++;
            if (progress != null) {
                progress.onProgress(done, total);
            }
        }
    }

    /**
     * Returns cached metadata, loading from the API once if missing.
     */
    public JellyfinItemMetadata getCachedMetadata(String id) throws IOException, ParseException {
        if (id == null) {
            return null;
        }
        JellyfinItemMetadata cached = metadataCache.get(id);
        if (cached != null) {
            return cached;
        }
        JellyfinItemMetadata loaded = loadMetadata(id);
        metadataCache.put(id, loaded);
        return loaded;
    }

    public void putCachedMetadata(String id, JellyfinItemMetadata meta) {
        if (id != null && meta != null) {
            metadataCache.put(id, meta);
        }
    }

    public void markDirty(String id) {
        if (id != null && !id.isBlank()) {
            dirtyIds.add(id);
        }
    }

    public Set<String> getDirtyIds() {
        return dirtyIds;
    }

    public boolean hasDirty() {
        return !dirtyIds.isEmpty();
    }

    public void clearDirty() {
        dirtyIds.clear();
    }

    /**
     * Display name of the parent folder stop, or empty when unknown.
     */
    public String parentFolderName(Stop stop) {
        if (stop == null) {
            return "";
        }
        if (stop.isFolder()) {
            return stop.getDisplayName() == null ? "" : stop.getDisplayName();
        }
        String parentId = stop.getParentFolderId();
        if (parentId == null || parentId.isBlank()) {
            return "";
        }
        Integer idx = folderStopIndex.get(parentId);
        if (idx == null || idx < 0 || idx >= stops.size()) {
            return "";
        }
        String name = stops.get(idx).getDisplayName();
        return name == null ? "" : name;
    }

    /**
     * Direct child item/folder stop IDs under a folder (not recursive).
     */
    public List<String> directChildIds(String folderId) {
        List<String> ids = new ArrayList<>();
        Integer idx = folderStopIndex.get(folderId);
        if (idx == null) {
            return ids;
        }
        Stop folder = stops.get(idx);
        int end = folder.subtreeEndExclusive;
        for (int i = idx + 1; i < end && i < stops.size(); i++) {
            Stop s = stops.get(i);
            if (folderId.equals(s.getParentFolderId())) {
                ids.add(s.getId());
            }
        }
        return ids;
    }

    /**
     * All descendant stop IDs under a folder (items and nested folders), excluding the folder itself.
     */
    public List<String> descendantIds(String folderId) {
        List<String> ids = new ArrayList<>();
        Integer idx = folderStopIndex.get(folderId);
        if (idx == null) {
            return ids;
        }
        Stop folder = stops.get(idx);
        for (int i = idx + 1; i < folder.subtreeEndExclusive && i < stops.size(); i++) {
            ids.add(stops.get(i).getId());
        }
        return ids;
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int done, int total);
    }

    private void buildFolder(String folderId, String parentFolderId, String displayName, Set<String> visited)
            throws IOException, ParseException {
        if (!visited.add(folderId)) {
            return;
        }
        int folderStop = stops.size();
        Stop folder = new Stop(StopType.FOLDER, folderId, parentFolderId, displayName, null);
        stops.add(folder);
        folderStopIndex.put(folderId, folderStop);

        // Files first.
        JellyfinItems items = listChildren(folderId, JellyfinParameters.JUST_ITEMS);
        if (items != null && items.getItems() != null) {
            for (JellyfinItem item : items.getItems()) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                MediaKind kind = mediaKind(item);
                if (!isIncluded(kind)) {
                    continue;
                }
                Stop stop = new Stop(StopType.ITEM, item.getId(), folderId,
                        item.getName() != null ? item.getName() : item.getId(), kind);
                stops.add(stop);
            }
        }

        // Then subfolders (naming the folder stop from its listing entry).
        JellyfinItems subFolders = listChildren(folderId, JellyfinParameters.JUST_SUBFOLDERS);
        if (subFolders != null && subFolders.getItems() != null) {
            for (JellyfinItem sub : subFolders.getItems()) {
                if (sub != null && sub.getId() != null) {
                    String subName = sub.getName() != null ? sub.getName() : sub.getId();
                    buildFolder(sub.getId(), folderId, subName, visited);
                }
            }
        }

        stops.get(folderStop).subtreeEndExclusive = stops.size();
    }

    private JellyfinItems listChildren(String folderId, JellyfinParameters type)
            throws IOException, ParseException {
        LoadItems loader = new LoadItems(
                api.getcBaseURL(), api.getcTokenApi(), adminUserId, folderId, type);
        return loader.requestItems();
    }

    /**
     * Loads full metadata for the given item/folder id.
     */
    public JellyfinItemMetadata loadMetadata(String id) throws IOException, ParseException {
        LoadItemMetadata loader = new LoadItemMetadata(
                api.getcBaseURL(), api.getcTokenApi(), adminUserId, id);
        return loader.requestItemMetadata();
    }

    private boolean isIncluded(MediaKind kind) {
        switch (kind) {
            case VIDEO:
                return includeVideos;
            case IMAGE:
                return includeImages;
            default:
                return includeOther;
        }
    }

    /**
     * Classifies a listing entry into a media kind for filtering.
     */
    public static MediaKind mediaKind(JellyfinItem item) {
        if (item == null) {
            return MediaKind.OTHER;
        }
        String mediaType = item.getMediaType() == null ? "" : item.getMediaType().toLowerCase(Locale.ROOT);
        String type = item.getType() == null ? "" : item.getType().toLowerCase(Locale.ROOT);
        if (mediaType.contains("photo") || mediaType.contains("image")
                || type.contains("photo") || type.contains("image")) {
            return MediaKind.IMAGE;
        }
        if (mediaType.contains("video")
                || type.contains("movie") || type.contains("episode")
                || type.contains("video") || type.contains("trailer")) {
            return MediaKind.VIDEO;
        }
        return MediaKind.OTHER;
    }

    // --- navigation ---------------------------------------------------------

    public List<Stop> getStops() {
        return stops;
    }

    public int size() {
        return stops.size();
    }

    public boolean isEmpty() {
        return stops.isEmpty();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Stop current() {
        if (currentIndex < 0 || currentIndex >= stops.size()) {
            return null;
        }
        return stops.get(currentIndex);
    }

    public boolean hasNext() {
        return currentIndex < stops.size() - 1;
    }

    public boolean hasPrevious() {
        return currentIndex > 0;
    }

    public Stop next() {
        if (currentIndex < stops.size()) {
            currentIndex++;
        }
        return current();
    }

    public Stop previous() {
        if (currentIndex > 0) {
            currentIndex--;
        }
        return current();
    }

    public Stop jumpTo(int index) {
        if (index >= 0 && index < stops.size()) {
            currentIndex = index;
        }
        return current();
    }

    /**
     * @return true once the walk has moved past the last stop
     */
    public boolean isFinished() {
        return currentIndex >= stops.size();
    }

    /** Skip the current file: advance one stop. */
    public Stop skipFile() {
        return next();
    }

    /** Skip the current folder: advance to its first file (or whatever is next). */
    public Stop skipFolder() {
        return next();
    }

    /**
     * Skip the rest of the current stop's enclosing folder: jump past that folder's whole
     * subtree to the next sibling/ancestor stop.
     */
    public Stop skipRestOfFolder() {
        Stop cur = current();
        if (cur == null) {
            return null;
        }
        String folderId = cur.type == StopType.FOLDER ? cur.id : cur.parentFolderId;
        Integer idx = folderId == null ? null : folderStopIndex.get(folderId);
        if (idx == null) {
            return next();
        }
        int end = stops.get(idx).subtreeEndExclusive;
        currentIndex = end;
        return current();
    }

    /**
     * A single stop in the walk.
     */
    public static class Stop {
        private final StopType type;
        private final String id;
        private final String parentFolderId;
        private String displayName;
        private final MediaKind mediaKind;
        private int subtreeEndExclusive;

        public Stop(StopType type, String id, String parentFolderId, String displayName, MediaKind mediaKind) {
            this.type = type;
            this.id = id;
            this.parentFolderId = parentFolderId;
            this.displayName = displayName;
            this.mediaKind = mediaKind;
        }

        public StopType getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public String getParentFolderId() {
            return parentFolderId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public MediaKind getMediaKind() {
            return mediaKind;
        }

        public boolean isFolder() {
            return type == StopType.FOLDER;
        }
    }
}
