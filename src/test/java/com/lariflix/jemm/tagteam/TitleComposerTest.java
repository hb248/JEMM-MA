package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.dtos.JellyfinPeopleItem;
import com.lariflix.jemm.dtos.JellyfinStudioItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;

public class TitleComposerTest {

    @Test
    public void composesFullCanonicalTitle() {
        List<JellyfinStudioItem> studios = Arrays.asList(studio("Studio A"), studio("Studio B"));
        List<JellyfinPeopleItem> people = Arrays.asList(
                person("Alice", "Actor"),
                person("Bob", "Actor"),
                person("Carol", "Director"));
        String title = TitleComposer.compose(studios, people, "My Video", "2024-05-11");
        assertEquals("[Studio A | Studio B] Alice, Bob - My Video (2024-05-11)", title);
    }

    @Test
    public void omitsEmptyParts() {
        assertEquals("Only Title", TitleComposer.compose(null, null, "Only Title", ""));
        assertEquals("(2020)", TitleComposer.compose(null, null, "", "2020"));
        assertEquals("[S] - Title", TitleComposer.compose(
                Arrays.asList(studio("S")), null, "Title", ""));
    }

    @Test
    public void onlyActorsInCastSegment() {
        List<JellyfinPeopleItem> people = Arrays.asList(
                person("Dir", "Director"),
                person("Act", "Actor"));
        String title = TitleComposer.compose(null, people, "T", "2021-01-02");
        assertEquals("Act - T (2021-01-02)", title);
    }

    @Test
    public void lowQualityTitleDetection() {
        assertTrue(TitleComposer.isLowQualityTitle("a1b2c3d4e5f6"));
        assertTrue(TitleComposer.isLowQualityTitle("123456789012"));
        assertTrue(TitleComposer.isLowQualityTitle(""));
        assertFalse(TitleComposer.isLowQualityTitle("A Great Title"));
    }

    @Test
    public void resolveCorePrefersFolderWhenLowQuality() {
        assertEquals("Folder Name", TitleComposer.resolveCoreTitle("deadbeefcafebabe", "Folder Name"));
        assertEquals("Nice Title", TitleComposer.resolveCoreTitle("Nice Title", "Folder Name"));
    }

    @Test
    public void sanitizeStripsStudiosAndActorsAlreadyInPanels() {
        assertEquals("", TitleComposer.sanitizeCoreTitle(
                "[StudioX] Alice",
                Arrays.asList("StudioX"),
                Arrays.asList("Alice")));
        assertEquals("[StudioX] Alice (2024-01-02)", TitleComposer.compose(
                Arrays.asList(studio("StudioX")),
                Arrays.asList(person("Alice", "Actor")),
                "[StudioX] Alice",
                "2024-01-02"));
    }

    private JellyfinStudioItem studio(String name) {
        JellyfinStudioItem s = new JellyfinStudioItem();
        s.setName(name);
        return s;
    }

    private JellyfinPeopleItem person(String name, String type) {
        JellyfinPeopleItem p = new JellyfinPeopleItem();
        p.setName(name);
        p.setType(type);
        return p;
    }
}
