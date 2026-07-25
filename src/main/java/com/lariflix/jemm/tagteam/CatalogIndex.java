package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.dtos.JellyfinCadPeopleItem;
import com.lariflix.jemm.dtos.JellyfinCadStudioItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory index of Jellyfin people and studios catalogs for Tag-Team suggestions.
 * Loaded once per session; lookups are case-insensitive.
 */
public class CatalogIndex {

    public static class PersonEntry {
        private final String id;
        private final String name;
        private final String type;

        public PersonEntry(String id, String name, String type) {
            this.id = id;
            this.name = name;
            this.type = type == null || type.isBlank() ? "Actor" : type;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    public static class StudioEntry {
        private final String id;
        private final String name;

        public StudioEntry(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    private final Map<String, List<PersonEntry>> peopleByName = new LinkedHashMap<>();
    private final Map<String, StudioEntry> studiosByName = new LinkedHashMap<>();
    private final List<PersonEntry> allPeople = new ArrayList<>();
    private final List<StudioEntry> allStudios = new ArrayList<>();

    public static CatalogIndex from(List<JellyfinCadPeopleItem> people, List<JellyfinCadStudioItem> studios) {
        CatalogIndex index = new CatalogIndex();
        if (people != null) {
            for (JellyfinCadPeopleItem p : people) {
                if (p == null || blank(p.getName())) {
                    continue;
                }
                PersonEntry entry = new PersonEntry(p.getId(), p.getName().trim(), p.getType());
                index.allPeople.add(entry);
                index.peopleByName.computeIfAbsent(key(entry.getName()), k -> new ArrayList<>()).add(entry);
            }
        }
        if (studios != null) {
            for (JellyfinCadStudioItem s : studios) {
                if (s == null || blank(s.getName())) {
                    continue;
                }
                StudioEntry entry = new StudioEntry(s.getId(), s.getName().trim());
                index.allStudios.add(entry);
                index.studiosByName.putIfAbsent(key(entry.getName()), entry);
            }
        }
        return index;
    }

    public boolean isKnownStudio(String name) {
        return !blank(name) && studiosByName.containsKey(key(name));
    }

    public boolean isKnownPerson(String name) {
        return !blank(name) && peopleByName.containsKey(key(name));
    }

    public StudioEntry findStudio(String name) {
        if (blank(name)) {
            return null;
        }
        return studiosByName.get(key(name));
    }

    /**
     * Returns people matching the name (any type). Prefer exact name match.
     */
    public List<PersonEntry> findPeople(String name) {
        if (blank(name)) {
            return Collections.emptyList();
        }
        List<PersonEntry> found = peopleByName.get(key(name));
        return found == null ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    public PersonEntry findPerson(String name, String preferredType) {
        List<PersonEntry> found = findPeople(name);
        if (found.isEmpty()) {
            return null;
        }
        if (preferredType != null) {
            for (PersonEntry p : found) {
                if (preferredType.equalsIgnoreCase(p.getType())) {
                    return p;
                }
            }
        }
        return found.get(0);
    }

    /**
     * Prefix/contains matches for live autocomplete (case-insensitive), capped.
     */
    public List<PersonEntry> suggestPeople(String query, int limit) {
        if (blank(query) || limit <= 0) {
            return Collections.emptyList();
        }
        String q = key(query);
        List<PersonEntry> out = new ArrayList<>();
        for (PersonEntry p : allPeople) {
            String n = key(p.getName());
            if (n.startsWith(q) || n.contains(q)) {
                out.add(p);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    public List<StudioEntry> suggestStudios(String query, int limit) {
        if (blank(query) || limit <= 0) {
            return Collections.emptyList();
        }
        String q = key(query);
        List<StudioEntry> out = new ArrayList<>();
        for (StudioEntry s : allStudios) {
            String n = key(s.getName());
            if (n.startsWith(q) || n.contains(q)) {
                out.add(s);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
