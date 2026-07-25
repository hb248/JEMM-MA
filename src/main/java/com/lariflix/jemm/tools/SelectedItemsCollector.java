package com.lariflix.jemm.tools;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.core.LoadItemMetadata;
import com.lariflix.jemm.core.LoadItems;
import com.lariflix.jemm.dtos.JellyfinItem;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.dtos.JellyfinItems;
import com.lariflix.jemm.utils.JellyfinParameters;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.simple.parser.ParseException;

/**
 * Collects media items under one or more selected library folders, recursively.
 */
public class SelectedItemsCollector {

    private final ConnectJellyfinAPI api;

    public SelectedItemsCollector(ConnectJellyfinAPI api) {
        this.api = api;
    }

    public List<CollectedItem> collectRecursive(List<String> rootFolderIds) throws IOException, ParseException {
        Map<String, CollectedItem> byId = new LinkedHashMap<>();
        Set<String> visitedFolders = new HashSet<>();
        if (rootFolderIds == null) {
            return new ArrayList<>();
        }
        for (String rootId : rootFolderIds) {
            if (rootId == null || rootId.isBlank()) {
                continue;
            }
            collectFolder(rootId, byId, visitedFolders);
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Collects the folder nodes themselves (the selected roots and every subfolder), loading each
     * folder's metadata so callers can edit folder-level fields.
     */
    public List<CollectedItem> collectFolders(List<String> rootFolderIds) throws IOException, ParseException {
        Map<String, CollectedItem> byId = new LinkedHashMap<>();
        Set<String> visitedFolders = new HashSet<>();
        if (rootFolderIds == null) {
            return new ArrayList<>();
        }
        for (String rootId : rootFolderIds) {
            if (rootId == null || rootId.isBlank()) {
                continue;
            }
            collectFolderNode(rootId, byId, visitedFolders);
        }
        return new ArrayList<>(byId.values());
    }

    private void collectFolderNode(String folderId, Map<String, CollectedItem> byId, Set<String> visitedFolders)
            throws IOException, ParseException {
        if (!visitedFolders.add(folderId)) {
            return;
        }
        if (!byId.containsKey(folderId)) {
            LoadItemMetadata metaLoader = new LoadItemMetadata(
                    api.getcBaseURL(),
                    api.getcTokenApi(),
                    api.getAdminUser().getId(),
                    folderId);
            JellyfinItemMetadata metadata = metaLoader.requestItemMetadata();
            byId.put(folderId, new CollectedItem(folderId, folderId, metadata));
        }

        LoadItems folderLoader = new LoadItems(
                api.getcBaseURL(),
                api.getcTokenApi(),
                api.getAdminUser().getId(),
                folderId,
                JellyfinParameters.JUST_SUBFOLDERS);
        JellyfinItems subFolders = folderLoader.requestItems();
        if (subFolders != null && subFolders.getItems() != null) {
            for (JellyfinItem sub : subFolders.getItems()) {
                if (sub != null && sub.getId() != null) {
                    collectFolderNode(sub.getId(), byId, visitedFolders);
                }
            }
        }
    }

    private void collectFolder(String folderId, Map<String, CollectedItem> byId, Set<String> visitedFolders)
            throws IOException, ParseException {
        if (!visitedFolders.add(folderId)) {
            return;
        }

        LoadItems mediaLoader = new LoadItems(
                api.getcBaseURL(),
                api.getcTokenApi(),
                api.getAdminUser().getId(),
                folderId,
                JellyfinParameters.JUST_ITEMS);
        JellyfinItems mediaItems = mediaLoader.requestItems();
        if (mediaItems != null && mediaItems.getItems() != null) {
            for (JellyfinItem item : mediaItems.getItems()) {
                if (item == null || item.getId() == null || byId.containsKey(item.getId())) {
                    continue;
                }
                LoadItemMetadata metaLoader = new LoadItemMetadata(
                        api.getcBaseURL(),
                        api.getcTokenApi(),
                        api.getAdminUser().getId(),
                        item.getId());
                JellyfinItemMetadata metadata = metaLoader.requestItemMetadata();
                byId.put(item.getId(), new CollectedItem(folderId, item.getId(), metadata));
            }
        }

        LoadItems folderLoader = new LoadItems(
                api.getcBaseURL(),
                api.getcTokenApi(),
                api.getAdminUser().getId(),
                folderId,
                JellyfinParameters.JUST_SUBFOLDERS);
        JellyfinItems subFolders = folderLoader.requestItems();
        if (subFolders != null && subFolders.getItems() != null) {
            for (JellyfinItem sub : subFolders.getItems()) {
                if (sub != null && sub.getId() != null) {
                    collectFolder(sub.getId(), byId, visitedFolders);
                }
            }
        }
    }

    public static class CollectedItem {
        private final String parentFolderId;
        private final String itemId;
        private final JellyfinItemMetadata metadata;

        public CollectedItem(String parentFolderId, String itemId, JellyfinItemMetadata metadata) {
            this.parentFolderId = parentFolderId;
            this.itemId = itemId;
            this.metadata = metadata;
        }

        public String getParentFolderId() {
            return parentFolderId;
        }

        public String getItemId() {
            return itemId;
        }

        public JellyfinItemMetadata getMetadata() {
            return metadata;
        }
    }
}
