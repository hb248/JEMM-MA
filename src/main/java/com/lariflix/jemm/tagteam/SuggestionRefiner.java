package com.lariflix.jemm.tagteam;

import java.util.ArrayList;
import java.util.List;

/**
 * Second pass over {@link FilenameSuggestions}: reclassify studio/actor segments using
 * the Jellyfin catalog, and resolve a better core Videotitel (folder name when low quality).
 */
public class SuggestionRefiner {

    private final CatalogIndex catalog;

    public SuggestionRefiner(CatalogIndex catalog) {
        this.catalog = catalog == null ? CatalogIndex.from(null, null) : catalog;
    }

    /**
     * Returns a refined copy of the suggestions. Does not mutate the input.
     *
     * @param raw        parser output
     * @param folderName parent folder display name (may be null)
     */
    public FilenameSuggestions refine(FilenameSuggestions raw, String folderName) {
        FilenameSuggestions out = new FilenameSuggestions();
        if (raw == null) {
            out.setTitle(TitleComposer.resolveCoreTitle("", folderName));
            return out;
        }

        List<String> studios = new ArrayList<>(raw.getStudios());
        List<String> actors = new ArrayList<>();

        for (String candidate : raw.getActors()) {
            if (blank(candidate)) {
                continue;
            }
            boolean knownStudio = catalog.isKnownStudio(candidate);
            boolean knownPerson = catalog.isKnownPerson(candidate);
            if (knownStudio && !knownPerson) {
                if (!containsIgnoreCase(studios, candidate)) {
                    CatalogIndex.StudioEntry se = catalog.findStudio(candidate);
                    studios.add(se != null ? se.getName() : candidate.trim());
                }
            } else {
                CatalogIndex.PersonEntry pe = catalog.findPerson(candidate, "Actor");
                String name = pe != null ? pe.getName() : candidate.trim();
                if (!containsIgnoreCase(actors, name)) {
                    actors.add(name);
                }
            }
        }

        // Canonicalize studio names from catalog when known.
        List<String> canonStudios = new ArrayList<>();
        for (String s : studios) {
            CatalogIndex.StudioEntry se = catalog.findStudio(s);
            String name = se != null ? se.getName() : s.trim();
            if (!containsIgnoreCase(canonStudios, name)) {
                canonStudios.add(name);
            }
        }

        String core = TitleComposer.resolveCoreTitle(raw.getTitle(), folderName);
        // If the parsed "title" is actually a known person (e.g. "Studio - Alice" without brackets),
        // promote it to actors and clear the core so the folder/low-quality path can take over.
        if (!blank(core) && catalog.isKnownPerson(core) && !catalog.isKnownStudio(core)) {
            CatalogIndex.PersonEntry pe = catalog.findPerson(core, "Actor");
            String name = pe != null ? pe.getName() : core.trim();
            if (!containsIgnoreCase(actors, name)) {
                actors.add(name);
            }
            core = TitleComposer.resolveCoreTitle("", folderName);
        }

        out.getStudios().addAll(canonStudios);
        out.getActors().addAll(actors);
        out.setTitle(core);
        out.getDates().addAll(raw.getDates());
        return out;
    }

    /**
     * Heuristic for filenames without brackets shaped like {@code Studio - Actor}
     * or {@code Studio - Title}: if the left of {@code " - "} is a known studio and
     * was parsed as an actor, move it. Handled mainly via {@link #refine}; this helper
     * exists for tests that start from a raw left/right split.
     */
    public void reclassifyDashPair(String left, String right, FilenameSuggestions into) {
        if (into == null) {
            return;
        }
        if (!blank(left) && catalog.isKnownStudio(left) && !catalog.isKnownPerson(left)) {
            CatalogIndex.StudioEntry se = catalog.findStudio(left);
            String name = se != null ? se.getName() : left.trim();
            if (!containsIgnoreCase(into.getStudios(), name)) {
                into.getStudios().add(name);
            }
            into.getActors().removeIf(a -> a != null && a.equalsIgnoreCase(left.trim()));
        } else if (!blank(left)) {
            if (!containsIgnoreCase(into.getActors(), left)) {
                into.getActors().add(left.trim());
            }
        }
        if (!blank(right) && catalog.isKnownPerson(right)) {
            CatalogIndex.PersonEntry pe = catalog.findPerson(right, "Actor");
            String name = pe != null ? pe.getName() : right.trim();
            if (!containsIgnoreCase(into.getActors(), name)) {
                into.getActors().add(name);
            }
            // Don't keep a person name as the title core when it's clearly the actor.
            if (right.trim().equalsIgnoreCase(into.getTitle())) {
                into.setTitle("");
            }
        }
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
