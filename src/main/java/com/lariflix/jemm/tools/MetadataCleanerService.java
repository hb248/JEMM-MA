package com.lariflix.jemm.tools;

import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import java.util.ArrayList;

/**
 * Clears selected metadata list fields on an item.
 */
public class MetadataCleanerService {

    public boolean clear(JellyfinItemMetadata metadata, boolean tags, boolean people, boolean genres, boolean studios) {
        if (metadata == null) {
            return false;
        }
        boolean changed = false;
        if (tags && metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            metadata.setTags(new ArrayList<>());
            changed = true;
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
}
