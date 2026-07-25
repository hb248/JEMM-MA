package com.lariflix.jemm.tagteam;

/**
 * Builds Jellyfin web UI deep links so the current item can be opened in a browser.
 */
public final class JellyfinWebLink {

    private JellyfinWebLink() {
    }

    /**
     * Builds a details URL like {@code {base}/web/index.html#/details?id={id}&serverId={serverId}}.
     *
     * @param baseUrl  the Jellyfin base URL (may end with or without a trailing slash)
     * @param serverId the server id (may be null/empty; then it is omitted)
     * @param itemId   the item id
     * @return the deep link, or null when base/item are missing
     */
    public static String detailsUrl(String baseUrl, String serverId, String itemId) {
        if (baseUrl == null || baseUrl.isBlank() || itemId == null || itemId.isBlank()) {
            return null;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        StringBuilder sb = new StringBuilder(base);
        sb.append("/web/index.html#/details?id=").append(itemId);
        if (serverId != null && !serverId.isBlank()) {
            sb.append("&serverId=").append(serverId);
        }
        return sb.toString();
    }
}
