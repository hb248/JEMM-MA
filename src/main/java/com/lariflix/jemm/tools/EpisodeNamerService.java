package com.lariflix.jemm.tools;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.dtos.JellyfinFolderMetadata;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.tools.SelectedItemsCollector.CollectedItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.parser.ParseException;

/**
 * Assigns sequential episode-style names ({@code "<base> - EP01"}, {@code "EP02"}, ...) to media
 * items. Numbering restarts per parent folder and follows the order the items are returned by
 * Jellyfin. This is the opt-in Tools counterpart of the naming logic that used to run implicitly
 * on "Apply for Library and Content".
 */
public class EpisodeNamerService {

    private final ConnectJellyfinAPI api;
    private final Map<String, String> folderNameCache = new HashMap<>();

    public EpisodeNamerService(ConnectJellyfinAPI api) {
        this.api = api;
    }

    /**
     * Runtime options controlling how the names are generated.
     */
    public static class Config {
        /** Use each item's parent folder name as the base title. */
        public boolean useFolderName = true;
        /** Base title used when {@link #useFolderName} is false. */
        public String customPrefix = "";
        /** Set the item Name. */
        public boolean setName = true;
        /** Set OriginalTitle (and, through the save path, the sort name) to the episode name too. */
        public boolean setOriginalAndSort = false;
        /** Minimum digits for the episode number (2 -&gt; EP01). */
        public int minDigits = 2;
        /** Text placed between base title and number. */
        public String separator = " - EP";
    }

    /**
     * Builds the episode name for a single item.
     */
    public static String buildName(String base, int number, int minDigits, String separator) {
        String safeBase = base == null ? "" : base.trim();
        String safeSeparator = separator == null ? "" : separator;
        String num = Integer.toString(number);
        int digits = Math.max(1, minDigits);
        while (num.length() < digits) {
            num = "0".concat(num);
        }
        return safeBase.concat(safeSeparator).concat(num);
    }

    /**
     * Mutates the metadata of the collected items in place and returns the ones that changed so the
     * caller can persist them.
     */
    public List<JellyfinItemMetadata> apply(List<CollectedItem> items, Config config)
            throws IOException, ParseException {
        List<JellyfinItemMetadata> changed = new ArrayList<>();
        if (items == null || config == null) {
            return changed;
        }

        Map<String, List<CollectedItem>> byFolder = new LinkedHashMap<>();
        for (CollectedItem item : items) {
            if (item == null) {
                continue;
            }
            byFolder.computeIfAbsent(item.getParentFolderId(), k -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<String, List<CollectedItem>> entry : byFolder.entrySet()) {
            String base = config.useFolderName ? resolveFolderName(entry.getKey()) : config.customPrefix;
            int index = 0;
            for (CollectedItem item : entry.getValue()) {
                index++;
                JellyfinItemMetadata metadata = item.getMetadata();
                if (metadata == null) {
                    continue;
                }
                String episodeName = buildName(base, index, config.minDigits, config.separator);
                boolean itemChanged = false;
                if (config.setName && !episodeName.equals(metadata.getName())) {
                    metadata.setName(episodeName);
                    itemChanged = true;
                }
                if (config.setOriginalAndSort && !episodeName.equals(metadata.getOriginalTitle())) {
                    metadata.setOriginalTitle(episodeName);
                    itemChanged = true;
                }
                if (itemChanged) {
                    changed.add(metadata);
                }
            }
        }
        return changed;
    }

    private String resolveFolderName(String folderId) throws IOException, ParseException {
        if (folderId == null) {
            return "";
        }
        if (folderNameCache.containsKey(folderId)) {
            return folderNameCache.get(folderId);
        }
        String name = "";
        try {
            JellyfinFolderMetadata metadata = api.getFolderMetadata(folderId);
            if (metadata != null && metadata.getName() != null) {
                name = metadata.getName().trim();
            }
        } catch (java.net.MalformedURLException ex) {
            name = "";
        }
        folderNameCache.put(folderId, name);
        return name;
    }
}
