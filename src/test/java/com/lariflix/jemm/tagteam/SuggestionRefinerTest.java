package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.dtos.JellyfinCadPeopleItem;
import com.lariflix.jemm.dtos.JellyfinCadStudioItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;

public class SuggestionRefinerTest {

    private CatalogIndex catalog() {
        JellyfinCadStudioItem studio = new JellyfinCadStudioItem();
        studio.setId("s1");
        studio.setName("StudioX");
        JellyfinCadPeopleItem alice = new JellyfinCadPeopleItem();
        alice.setId("p1");
        alice.setName("Alice");
        alice.setType("Actor");
        return CatalogIndex.from(Arrays.asList(alice), Arrays.asList(studio));
    }

    @Test
    public void movesKnownStudioOutOfActors() {
        FilenameSuggestions raw = new FilenameSuggestions();
        raw.getActors().add("StudioX");
        raw.getActors().add("Alice");
        raw.setTitle("Something");

        FilenameSuggestions refined = new SuggestionRefiner(catalog()).refine(raw, "Folder");
        assertTrue(refined.getStudios().contains("StudioX"));
        assertFalse(refined.getActors().stream().anyMatch(a -> a.equalsIgnoreCase("StudioX")));
        assertTrue(refined.getActors().contains("Alice"));
    }

    @Test
    public void studioDashActorWithoutBrackets() {
        // Mimics parser output for "StudioX - Alice.mp4"
        FilenameSuggestions raw = new FilenameSuggestions();
        raw.getActors().add("StudioX");
        raw.setTitle("Alice");

        FilenameSuggestions refined = new SuggestionRefiner(catalog()).refine(raw, "My Folder");
        assertTrue(refined.getStudios().contains("StudioX"));
        assertTrue(refined.getActors().contains("Alice"));
        // Title was a known person -> empty core; folder "My Folder" has no brackets so
        // its whole name becomes the Videotitel segment.
        assertEquals("My Folder", refined.getTitle());
    }

    @Test
    public void bracketStudioActorFolderDoesNotDuplicateAsCore() {
        // Mimics parser output for folder "[StudioX] Alice"
        FilenameSuggestions raw = new FilenameSuggestions();
        raw.getStudios().add("StudioX");
        raw.setTitle("Alice");

        FilenameSuggestions refined = new SuggestionRefiner(catalog()).refine(raw, "");
        assertTrue(refined.getStudios().contains("StudioX"));
        assertTrue(refined.getActors().contains("Alice"));
        assertEquals("", refined.getTitle());
    }

    @Test
    public void unknownActorAfterStudioStillBecomesCastNotTitle() {
        // Studio known in catalog; person not yet in Jellyfin — still cast, not Videotitel.
        FilenameSuggestions raw = parserStyleBracketNoDash("StudioX", "darsteller");
        FilenameSuggestions refined = new SuggestionRefiner(catalog()).refine(raw, "");
        assertTrue(refined.getStudios().contains("StudioX"));
        assertTrue(refined.getActors().contains("darsteller"));
        assertEquals("", refined.getTitle());

        assertEquals("[StudioX] darsteller", TitleComposer.compose(
                List.of(studio("StudioX")),
                List.of(person("darsteller")),
                refined.getTitle(),
                ""));
    }

    private static FilenameSuggestions parserStyleBracketNoDash(String studio, String actor) {
        FilenameSuggestions raw = new FilenameSuggestions();
        raw.getStudios().add(studio);
        raw.getActors().add(actor);
        raw.setTitle("");
        return raw;
    }

    private static com.lariflix.jemm.dtos.JellyfinStudioItem studio(String name) {
        com.lariflix.jemm.dtos.JellyfinStudioItem s = new com.lariflix.jemm.dtos.JellyfinStudioItem();
        s.setName(name);
        return s;
    }

    private static com.lariflix.jemm.dtos.JellyfinPeopleItem person(String name) {
        com.lariflix.jemm.dtos.JellyfinPeopleItem p = new com.lariflix.jemm.dtos.JellyfinPeopleItem();
        p.setName(name);
        p.setType("Actor");
        return p;
    }

    @Test
    public void lowQualityFileTitleParsesParentFolder() {
        FilenameSuggestions raw = new FilenameSuggestions();
        raw.setTitle("a1b2c3d4e5f67890");
        FilenameSuggestions refined = new SuggestionRefiner(catalog())
                .refine(raw, "[StudioX] Alice");
        assertTrue(refined.getStudios().contains("StudioX"));
        assertTrue(refined.getActors().contains("Alice"));
        // Folder title segment was the actor — no leftover Videotitel.
        assertEquals("", refined.getTitle());
    }

    @Test
    public void lowQualityTitleUsesFolderTitleSegment() {
        FilenameSuggestions raw = new FilenameSuggestions();
        raw.setTitle("a1b2c3d4e5f67890");
        FilenameSuggestions refined = new SuggestionRefiner(catalog()).refine(raw, "Nice Folder");
        assertEquals("Nice Folder", refined.getTitle());
    }

    @Test
    public void catalogSuggestPeopleAndStudios() {
        CatalogIndex idx = catalog();
        assertFalse(idx.suggestPeople("Ali", 5).isEmpty());
        assertEquals("Alice", idx.suggestPeople("Ali", 5).get(0).getName());
        assertFalse(idx.suggestStudios("Stu", 5).isEmpty());
        assertTrue(idx.isKnownStudio("studiox"));
        assertTrue(idx.isKnownPerson("alice"));
    }
}
