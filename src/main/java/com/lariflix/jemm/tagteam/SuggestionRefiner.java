package com.lariflix.jemm.tagteam;

import java.util.ArrayList;
import java.util.List;

/**
 * Second pass over {@link FilenameSuggestions}: reclassify studio/actor segments using
 * the Jellyfin catalog, and resolve a better core Videotitel (parsed folder name when
 * the file title is low quality — never the raw folder string as a whole).
 */
public class SuggestionRefiner {

    private final CatalogIndex catalog;
    private final FilenameMetadataParser parser = new FilenameMetadataParser();

    public SuggestionRefiner(CatalogIndex catalog) {
        this.catalog = catalog == null ? CatalogIndex.from(null, null) : catalog;
    }

    /**
     * Returns a refined copy of the suggestions. Does not mutate the input.
     *
     * @param raw        parser output
     * @param folderName parent folder display name (may be null); for folder stops pass empty
     *                   so the stop's own name is not reused as a title fallback
     */
    public FilenameSuggestions refine(FilenameSuggestions raw, String folderName) {
        FilenameSuggestions out = new FilenameSuggestions();
        if (raw == null) {
            mergeFromFolderFallback(out, new ArrayList<>(), new ArrayList<>(), "", folderName);
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

        List<String> canonStudios = canonicalizeStudios(studios);

        String core = raw.getTitle() == null ? "" : raw.getTitle().trim();
        // Parsed "title" is actually a known person → promote; do not fall back to the raw
        // folder string (that reintroduces [Studio] Actor and causes duplicate composition).
        if (!blank(core) && catalog.isKnownPerson(core) && !catalog.isKnownStudio(core)) {
            CatalogIndex.PersonEntry pe = catalog.findPerson(core, "Actor");
            String name = pe != null ? pe.getName() : core.trim();
            if (!containsIgnoreCase(actors, name)) {
                actors.add(name);
            }
            core = "";
        }

        if (TitleComposer.isLowQualityTitle(core)) {
            mergeFromFolderFallback(out, canonStudios, actors, core, folderName);
        } else {
            out.getStudios().addAll(canonStudios);
            out.getActors().addAll(actors);
            out.setTitle(core);
        }
        out.getDates().addAll(raw.getDates());
        return out;
    }

    /**
     * When the file/item title is empty or junk, parse the parent folder name and merge
     * its studio/actor/title pieces — using only the folder's refined title segment as core.
     */
    private void mergeFromFolderFallback(FilenameSuggestions out, List<String> studios,
            List<String> actors, String currentCore, String folderName) {
        List<String> mergedStudios = new ArrayList<>(studios);
        List<String> mergedActors = new ArrayList<>(actors);
        String core = currentCore == null ? "" : currentCore.trim();

        if (!blank(folderName)) {
            FilenameSuggestions folderRaw = parser.parse(folderName);
            for (String s : folderRaw.getStudios()) {
                CatalogIndex.StudioEntry se = catalog.findStudio(s);
                String name = se != null ? se.getName() : s.trim();
                if (!containsIgnoreCase(mergedStudios, name)) {
                    mergedStudios.add(name);
                }
            }
            for (String a : folderRaw.getActors()) {
                if (catalog.isKnownStudio(a) && !catalog.isKnownPerson(a)) {
                    CatalogIndex.StudioEntry se = catalog.findStudio(a);
                    String name = se != null ? se.getName() : a.trim();
                    if (!containsIgnoreCase(mergedStudios, name)) {
                        mergedStudios.add(name);
                    }
                } else {
                    CatalogIndex.PersonEntry pe = catalog.findPerson(a, "Actor");
                    String name = pe != null ? pe.getName() : a.trim();
                    if (!containsIgnoreCase(mergedActors, name)) {
                        mergedActors.add(name);
                    }
                }
            }
            String folderTitle = folderRaw.getTitle() == null ? "" : folderRaw.getTitle().trim();
            if (!blank(folderTitle) && catalog.isKnownPerson(folderTitle) && !catalog.isKnownStudio(folderTitle)) {
                CatalogIndex.PersonEntry pe = catalog.findPerson(folderTitle, "Actor");
                String name = pe != null ? pe.getName() : folderTitle;
                if (!containsIgnoreCase(mergedActors, name)) {
                    mergedActors.add(name);
                }
                folderTitle = "";
            }
            if (!TitleComposer.isLowQualityTitle(folderTitle)) {
                core = folderTitle;
            } else if (TitleComposer.isLowQualityTitle(core)) {
                // Folder had no usable title segment either (e.g. only [Studio] Actor).
                core = "";
            }
            if (out.getDates().isEmpty() && !folderRaw.getDates().isEmpty()) {
                out.getDates().addAll(folderRaw.getDates());
            }
        } else if (TitleComposer.isLowQualityTitle(core)) {
            core = "";
        }

        out.getStudios().addAll(canonicalizeStudios(mergedStudios));
        out.getActors().addAll(mergedActors);
        out.setTitle(core);
    }

    private List<String> canonicalizeStudios(List<String> studios) {
        List<String> canonStudios = new ArrayList<>();
        for (String s : studios) {
            CatalogIndex.StudioEntry se = catalog.findStudio(s);
            String name = se != null ? se.getName() : s.trim();
            if (!containsIgnoreCase(canonStudios, name)) {
                canonStudios.add(name);
            }
        }
        return canonStudios;
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
