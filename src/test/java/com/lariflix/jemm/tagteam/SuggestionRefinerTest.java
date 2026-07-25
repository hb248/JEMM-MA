package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.dtos.JellyfinCadPeopleItem;
import com.lariflix.jemm.dtos.JellyfinCadStudioItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

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
        // Title was a known person -> core falls back to folder.
        assertEquals("My Folder", refined.getTitle());
    }

    @Test
    public void lowQualityTitleUsesFolder() {
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
