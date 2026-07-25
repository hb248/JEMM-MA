package com.lariflix.jemm.tools;

import java.io.File;

/**
 * Decides which input string to hand to ffprobe for a given item: the local
 * file path when it is accessible on this machine, otherwise the Jellyfin
 * download URL so ffprobe can read the media over HTTP.
 */
public class MediaInputResolver {

    private final String baseUrl;
    private final String token;

    public MediaInputResolver(String baseUrl, String token) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.token = token == null ? "" : token;
    }

    /**
     * Resolves the ffprobe input for an item.
     *
     * @param localPath the item's stored path (may be null/blank or unreachable)
     * @param itemId    the Jellyfin item id, used to build the download URL fallback
     * @return a local path or download URL, or null when neither can be built
     */
    public String resolve(String localPath, String itemId) {
        if (localPath != null && !localPath.isBlank()) {
            File file = new File(localPath.trim());
            if (file.isFile()) {
                return file.getAbsolutePath();
            }
        }
        return buildDownloadUrl(itemId);
    }

    /**
     * Builds the Jellyfin download URL for an item.
     *
     * @param itemId the Jellyfin item id
     * @return the download URL, or null when it cannot be built
     */
    public String buildDownloadUrl(String itemId) {
        if (itemId == null || itemId.isBlank() || baseUrl.isBlank()) {
            return null;
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        StringBuilder url = new StringBuilder(normalized)
                .append("Items/")
                .append(itemId.trim())
                .append("/Download");
        if (!token.isBlank()) {
            url.append("?api_key=").append(token.trim());
        }
        return url.toString();
    }
}
