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
}
