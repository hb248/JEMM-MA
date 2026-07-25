package com.lariflix.jemm.tagteam;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@link TagMap} JSON documents. Uses the same Jackson
 * {@code ObjectMapper} the rest of the project relies on. Validates and normalizes
 * the map (non-null lists, trimmed names, trees sorted by {@code order}).
 */
public class TagMapLoader {

    private final ObjectMapper mapper;

    public TagMapLoader() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    /**
     * Loads and validates a tag map from a file.
     */
    public TagMap load(File file) throws IOException {
        TagMap map = mapper.readValue(file, TagMap.class);
        return validateAndSort(map);
    }

    /**
     * Parses and validates a tag map from a JSON string (handy for tests).
     */
    public TagMap parse(String json) throws IOException {
        TagMap map = mapper.readValue(json, TagMap.class);
        return validateAndSort(map);
    }

    /**
     * Writes a tag map to a file using pretty printing.
     */
    public void save(TagMap map, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, map);
    }

    /**
     * Serializes a tag map to a pretty-printed JSON string.
     */
    public String toJson(TagMap map) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map == null ? new TagMap() : map);
    }

    /**
     * Deep-copies a tag map via JSON round-trip (validates/normalizes like {@link #parse}).
     */
    public TagMap deepCopy(TagMap map) throws IOException {
        return parse(toJson(map));
    }

    /**
     * Normalizes the map in place: replaces null lists with empty ones, trims names,
     * validates that every tree has a non-blank name, and sorts trees by {@code order}
     * (stable, keeping declaration order for ties). Throws {@link IllegalArgumentException}
     * if a tree name is missing or duplicated.
     */
    public TagMap validateAndSort(TagMap map) {
        if (map == null) {
            return new TagMap();
        }
        if (map.getTrees() == null) {
            map.setTrees(new ArrayList<>());
        }

        List<String> seenNames = new ArrayList<>();
        for (TagTree tree : map.getTrees()) {
            String name = tree.getName() == null ? "" : tree.getName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Every tree in the tag map must have a name.");
            }
            tree.setName(name);
            String lower = name.toLowerCase();
            if (seenNames.contains(lower)) {
                throw new IllegalArgumentException("Duplicate tree name in tag map: " + name);
            }
            seenNames.add(lower);
            normalizeNodes(tree.getChildren());
        }

        // Stable sort by order.
        map.getTrees().sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        return map;
    }

    private void normalizeNodes(List<TagNode> nodes) {
        if (nodes == null) {
            return;
        }
        for (TagNode node : nodes) {
            if (node.getLabel() != null) {
                node.setLabel(node.getLabel().trim());
            }
            if (node.getAssign() == null) {
                node.setAssign(new ArrayList<>());
            }
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            normalizeNodes(node.getChildren());
        }
    }
}
