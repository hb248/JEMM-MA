package com.lariflix.jemm.utils;

import com.lariflix.jemm.dtos.JellyfinGenreItem;
import com.lariflix.jemm.dtos.JellyfinPeopleItem;
import com.lariflix.jemm.dtos.JellyfinStudioItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Merge and deduplicate People, Genres, Studios and Tags lists.
 */
public final class MetadataListMerge {

    private MetadataListMerge() {
    }

    public static String peopleKey(JellyfinPeopleItem person) {
        if (person == null) {
            return "";
        }
        String id = safe(person.getId());
        if (!id.isEmpty()) {
            return "id:" + id.toLowerCase(Locale.ROOT);
        }
        return "nt:" + safe(person.getName()).toLowerCase(Locale.ROOT) + "|"
                + safe(person.getType()).toLowerCase(Locale.ROOT);
    }

    public static String genreKey(JellyfinGenreItem genre) {
        if (genre == null) {
            return "";
        }
        String id = safe(genre.getId());
        if (!id.isEmpty()) {
            return "id:" + id.toLowerCase(Locale.ROOT);
        }
        return "n:" + safe(genre.getName()).toLowerCase(Locale.ROOT);
    }

    public static String studioKey(JellyfinStudioItem studio) {
        if (studio == null) {
            return "";
        }
        String id = safe(studio.getId());
        if (!id.isEmpty()) {
            return "id:" + id.toLowerCase(Locale.ROOT);
        }
        return "n:" + safe(studio.getName()).toLowerCase(Locale.ROOT);
    }

    public static String tagKey(String tag) {
        return safe(tag).toLowerCase(Locale.ROOT);
    }

    public static ArrayList<JellyfinPeopleItem> dedupePeople(ArrayList<JellyfinPeopleItem> source) {
        Map<String, JellyfinPeopleItem> map = new LinkedHashMap<>();
        if (source != null) {
            for (JellyfinPeopleItem person : source) {
                String key = peopleKey(person);
                if (!key.isEmpty() && !map.containsKey(key)) {
                    map.put(key, person);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    public static ArrayList<JellyfinGenreItem> dedupeGenres(ArrayList<JellyfinGenreItem> source) {
        Map<String, JellyfinGenreItem> map = new LinkedHashMap<>();
        if (source != null) {
            for (JellyfinGenreItem genre : source) {
                String key = genreKey(genre);
                if (!key.isEmpty() && !map.containsKey(key)) {
                    map.put(key, genre);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    public static ArrayList<JellyfinStudioItem> dedupeStudios(ArrayList<JellyfinStudioItem> source) {
        Map<String, JellyfinStudioItem> map = new LinkedHashMap<>();
        if (source != null) {
            for (JellyfinStudioItem studio : source) {
                String key = studioKey(studio);
                if (!key.isEmpty() && !map.containsKey(key)) {
                    map.put(key, studio);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    public static ArrayList<String> dedupeTags(ArrayList<String> source) {
        Map<String, String> map = new LinkedHashMap<>();
        if (source != null) {
            for (String tag : source) {
                String key = tagKey(tag);
                if (!key.isEmpty() && !map.containsKey(key)) {
                    map.put(key, tag.trim());
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    public static ArrayList<JellyfinPeopleItem> mergePeople(
            ArrayList<JellyfinPeopleItem> existing,
            ArrayList<JellyfinPeopleItem> incoming) {
        Map<String, JellyfinPeopleItem> map = new LinkedHashMap<>();
        for (JellyfinPeopleItem person : dedupePeople(existing)) {
            map.put(peopleKey(person), person);
        }
        for (JellyfinPeopleItem person : dedupePeople(incoming)) {
            String key = peopleKey(person);
            if (!map.containsKey(key)) {
                map.put(key, person);
            }
        }
        return new ArrayList<>(map.values());
    }

    public static ArrayList<JellyfinGenreItem> mergeGenres(
            ArrayList<JellyfinGenreItem> existing,
            ArrayList<JellyfinGenreItem> incoming) {
        Map<String, JellyfinGenreItem> map = new LinkedHashMap<>();
        for (JellyfinGenreItem genre : dedupeGenres(existing)) {
            map.put(genreKey(genre), genre);
        }
        for (JellyfinGenreItem genre : dedupeGenres(incoming)) {
            String key = genreKey(genre);
            if (!map.containsKey(key)) {
                map.put(key, genre);
            }
        }
        return new ArrayList<>(map.values());
    }

    public static ArrayList<JellyfinStudioItem> mergeStudios(
            ArrayList<JellyfinStudioItem> existing,
            ArrayList<JellyfinStudioItem> incoming) {
        Map<String, JellyfinStudioItem> map = new LinkedHashMap<>();
        for (JellyfinStudioItem studio : dedupeStudios(existing)) {
            map.put(studioKey(studio), studio);
        }
        for (JellyfinStudioItem studio : dedupeStudios(incoming)) {
            String key = studioKey(studio);
            if (!map.containsKey(key)) {
                map.put(key, studio);
            }
        }
        return new ArrayList<>(map.values());
    }

    public static ArrayList<String> mergeTags(ArrayList<String> existing, ArrayList<String> incoming) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String tag : dedupeTags(existing)) {
            map.put(tagKey(tag), tag);
        }
        for (String tag : dedupeTags(incoming)) {
            String key = tagKey(tag);
            if (!map.containsKey(key)) {
                map.put(key, tag.trim());
            }
        }
        return new ArrayList<>(map.values());
    }

    public static boolean peopleContains(ArrayList<JellyfinPeopleItem> list, JellyfinPeopleItem candidate) {
        String key = peopleKey(candidate);
        if (key.isEmpty() || list == null) {
            return false;
        }
        for (JellyfinPeopleItem person : list) {
            if (key.equals(peopleKey(person))) {
                return true;
            }
        }
        return false;
    }

    public static boolean genreContains(ArrayList<JellyfinGenreItem> list, JellyfinGenreItem candidate) {
        String key = genreKey(candidate);
        if (key.isEmpty() || list == null) {
            return false;
        }
        for (JellyfinGenreItem genre : list) {
            if (key.equals(genreKey(genre))) {
                return true;
            }
        }
        return false;
    }

    public static boolean studioContains(ArrayList<JellyfinStudioItem> list, JellyfinStudioItem candidate) {
        String key = studioKey(candidate);
        if (key.isEmpty() || list == null) {
            return false;
        }
        for (JellyfinStudioItem studio : list) {
            if (key.equals(studioKey(studio))) {
                return true;
            }
        }
        return false;
    }

    public static boolean tagContains(ArrayList<String> list, String candidate) {
        String key = tagKey(candidate);
        if (key.isEmpty() || list == null) {
            return false;
        }
        for (String tag : list) {
            if (key.equals(tagKey(tag))) {
                return true;
            }
        }
        return false;
    }

    public static boolean tableModelContainsPeople(javax.swing.table.TableModel model, String id, String name, String type) {
        JellyfinPeopleItem candidate = new JellyfinPeopleItem();
        candidate.setId(id);
        candidate.setName(name);
        candidate.setType(type);
        String key = peopleKey(candidate);
        for (int i = 0; i < model.getRowCount(); i++) {
            JellyfinPeopleItem row = new JellyfinPeopleItem();
            row.setId(cell(model, i, 0));
            row.setName(cell(model, i, 1));
            row.setType(cell(model, i, 2));
            if (key.equals(peopleKey(row))) {
                return true;
            }
        }
        return false;
    }

    public static boolean tableModelContainsIdOrName(javax.swing.table.TableModel model, int idCol, int nameCol, String id, String name) {
        String idKey = safe(id).toLowerCase(Locale.ROOT);
        String nameKey = safe(name).toLowerCase(Locale.ROOT);
        for (int i = 0; i < model.getRowCount(); i++) {
            String rowId = cell(model, i, idCol).toLowerCase(Locale.ROOT);
            String rowName = cell(model, i, nameCol).toLowerCase(Locale.ROOT);
            if (!idKey.isEmpty() && idKey.equals(rowId)) {
                return true;
            }
            if (idKey.isEmpty() && !nameKey.isEmpty() && nameKey.equals(rowName)) {
                return true;
            }
            if (!idKey.isEmpty() && !nameKey.isEmpty() && (idKey.equals(rowId) || nameKey.equals(rowName))) {
                return true;
            }
        }
        return false;
    }

    public static boolean tableModelContainsTag(javax.swing.table.TableModel model, int tagCol, String tag) {
        String key = tagKey(tag);
        for (int i = 0; i < model.getRowCount(); i++) {
            if (key.equals(tagKey(cell(model, i, tagCol)))) {
                return true;
            }
        }
        return false;
    }

    private static String cell(javax.swing.table.TableModel model, int row, int col) {
        Object value = model.getValueAt(row, col);
        return value == null ? "" : value.toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
