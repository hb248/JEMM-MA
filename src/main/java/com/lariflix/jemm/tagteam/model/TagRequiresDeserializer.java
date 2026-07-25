package com.lariflix.jemm.tagteam.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Accepts both the legacy single {@code {tree,label}} shape and the new
 * {@code {mode,items}} group for {@link TagRequires}.
 */
public class TagRequiresDeserializer extends JsonDeserializer<TagRequires> {

    @Override
    public TagRequires deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }

        TagRequires out = new TagRequires();
        boolean newShape = node.has("items") || node.has("mode");
        if (newShape) {
            if (node.has("mode") && !node.get("mode").isNull()) {
                out.setMode(RequireMode.fromJson(node.get("mode").asText()));
            } else {
                out.setMode(RequireMode.ANY);
            }
            List<TagRequire> items = new ArrayList<>();
            JsonNode itemsNode = node.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    TagRequire ref = readRef(item);
                    if (ref != null) {
                        items.add(ref);
                    }
                }
            }
            out.setItems(items);
        } else {
            // Legacy: { "tree": "...", "label": "..." }
            TagRequire ref = readRef(node);
            out.setMode(RequireMode.ANY);
            List<TagRequire> items = new ArrayList<>();
            if (ref != null) {
                items.add(ref);
            }
            out.setItems(items);
        }

        return out.isSet() ? out : null;
    }

    private static TagRequire readRef(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String tree = text(node, "tree");
        String label = text(node, "label");
        TagRequire ref = new TagRequire(tree, label);
        return ref.isSet() ? ref : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
