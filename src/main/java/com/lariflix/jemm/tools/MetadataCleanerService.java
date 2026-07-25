package com.lariflix.jemm.tools;

import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import java.util.ArrayList;

/**
 * Clears selected metadata list fields on an item.
 */
public class MetadataCleanerService {

    public boolean clear(JellyfinItemMetadata metadata, boolean tags, boolean people, boolean genres, boolean studios) {
        return clear(metadata, tags, people, genres, studios, false);
    }

    public boolean clear(JellyfinItemMetadata metadata, boolean tags, boolean people, boolean genres, boolean studios,
            boolean preferredLangCountry) {
        return clear(metadata, tags, people, genres, studios, preferredLangCountry, false);
    }

    public boolean clear(JellyfinItemMetadata metadata, boolean tags, boolean people, boolean genres, boolean studios,
            boolean preferredLangCountry, boolean autoTagsOnly) {
        if (metadata == null) {
            return false;
        }
        boolean changed = false;
        if (preferredLangCountry) {
            if (isSet(metadata.getPreferredMetadataLanguage()) || isSet(metadata.getPreferredMetadataCountryCode())) {
                metadata.setPreferredMetadataLanguage(null);
                metadata.setPreferredMetadataCountryCode(null);
                changed = true;
            }
        }
        if (tags && metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            if (autoTagsOnly) {
                ArrayList<String> kept = new ArrayList<>();
                for (String tag : metadata.getTags()) {
                    if (!ManagedAutoTags.isManaged(tag)) {
                        kept.add(tag);
                    }
                }
                if (kept.size() != metadata.getTags().size()) {
                    metadata.setTags(kept);
                    changed = true;
                }
            } else {
                metadata.setTags(new ArrayList<>());
                changed = true;
            }
        }
        if (people && metadata.getPeople() != null && !metadata.getPeople().isEmpty()) {
            metadata.setPeople(new ArrayList<>());
            changed = true;
        }
        if (genres) {
            boolean hadGenres = (metadata.getGenreItems() != null && !metadata.getGenreItems().isEmpty())
                    || (metadata.getGenres() != null && !metadata.getGenres().isEmpty());
            if (hadGenres) {
                metadata.setGenreItems(new ArrayList<>());
                metadata.setGenres(new ArrayList<>());
                changed = true;
            }
        }
        if (studios && metadata.getStudios() != null && !metadata.getStudios().isEmpty()) {
            metadata.setStudios(new ArrayList<>());
            changed = true;
        }
        return changed;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
