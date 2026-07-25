package com.lariflix.jemm.tools;

import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MetadataCleanerServiceTest {

    private final MetadataCleanerService cleaner = new MetadataCleanerService();

    @Test
    public void autoTagsOnlyRemovesManagedKeepsManual() {
        JellyfinItemMetadata metadata = new JellyfinItemMetadata();
        metadata.setTags(new ArrayList<>(Arrays.asList(
                "my series", "vertical", "FULL HD", "QR3", "No Audio", "favourite")));

        boolean changed = cleaner.clear(metadata, true, false, false, false, false, true);

        assertTrue(changed);
        assertEquals(Arrays.asList("my series", "favourite"), metadata.getTags());
    }

    @Test
    public void autoTagsOnlyNoManagedTagsIsNoChange() {
        JellyfinItemMetadata metadata = new JellyfinItemMetadata();
        metadata.setTags(new ArrayList<>(Arrays.asList("my series", "favourite")));

        boolean changed = cleaner.clear(metadata, true, false, false, false, false, true);

        assertFalse(changed);
        assertEquals(Arrays.asList("my series", "favourite"), metadata.getTags());
    }

    @Test
    public void fullClearRemovesEverythingWhenNotAutoOnly() {
        JellyfinItemMetadata metadata = new JellyfinItemMetadata();
        metadata.setTags(new ArrayList<>(Arrays.asList("my series", "vertical", "FULL HD")));

        boolean changed = cleaner.clear(metadata, true, false, false, false, false, false);

        assertTrue(changed);
        assertTrue(metadata.getTags().isEmpty());
    }

    @Test
    public void ratingsResetToZero() {
        JellyfinItemMetadata metadata = new JellyfinItemMetadata();
        metadata.setCommunityRating(8);
        metadata.setCriticRating(90);

        boolean changed = cleaner.clear(metadata, false, false, false, false, false, false, true);

        assertTrue(changed);
        assertEquals(0, metadata.getCommunityRating());
        assertEquals(0, metadata.getCriticRating());
    }

    @Test
    public void ratingsAlreadyZeroIsNoChange() {
        JellyfinItemMetadata metadata = new JellyfinItemMetadata();
        metadata.setCommunityRating(0);
        metadata.setCriticRating(0);

        boolean changed = cleaner.clear(metadata, false, false, false, false, false, false, true);

        assertFalse(changed);
    }
}
