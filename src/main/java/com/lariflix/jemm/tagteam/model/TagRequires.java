package com.lariflix.jemm.tagteam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prerequisite group for a {@link TagNode}: one or more {@link TagRequire} refs combined
 * with {@link RequireMode#ANY} (OR) or {@link RequireMode#ALL} (AND).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = TagRequiresDeserializer.class)
public class TagRequires {

    private RequireMode mode = RequireMode.ANY;
    private List<TagRequire> items = new ArrayList<>();

    public TagRequires() {
    }

    public TagRequires(RequireMode mode, List<TagRequire> items) {
        this.mode = mode == null ? RequireMode.ANY : mode;
        this.items = items == null ? new ArrayList<>() : items;
    }

    public static TagRequires single(String tree, String label) {
        TagRequires r = new TagRequires();
        r.setMode(RequireMode.ANY);
        r.getItems().add(new TagRequire(tree, label));
        return r;
    }

    public RequireMode getMode() {
        return mode == null ? RequireMode.ANY : mode;
    }

    public void setMode(RequireMode mode) {
        this.mode = mode == null ? RequireMode.ANY : mode;
    }

    public List<TagRequire> getItems() {
        return items;
    }

    public void setItems(List<TagRequire> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public boolean isSet() {
        if (items == null) {
            return false;
        }
        for (TagRequire r : items) {
            if (r != null && r.isSet()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Human-readable summary for the editor / outline.
     */
    public String summary() {
        if (!isSet()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getMode() == RequireMode.ALL ? "all of: " : "any of: ");
        boolean first = true;
        for (TagRequire r : items) {
            if (r == null || !r.isSet()) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(r.getTree().trim()).append('/').append(r.getLabel().trim());
        }
        return sb.toString();
    }

    /**
     * Compact outline marker, e.g. {@code any(Type/Duo, Setting/Indoor)}.
     */
    public String outlineMarker() {
        if (!isSet()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getMode() == RequireMode.ALL ? "all(" : "any(");
        boolean first = true;
        for (TagRequire r : items) {
            if (r == null || !r.isSet()) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(r.getTree().trim()).append('/').append(r.getLabel().trim());
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * @param selectedByTree lowercased tree name → selected labels
     */
    public boolean isSatisfiedBy(Map<String, Set<String>> selectedByTree) {
        if (!isSet()) {
            return true;
        }
        if (getMode() == RequireMode.ALL) {
            for (TagRequire r : items) {
                if (r != null && r.isSet() && !itemSatisfied(r, selectedByTree)) {
                    return false;
                }
            }
            return true;
        }
        for (TagRequire r : items) {
            if (r != null && r.isSet() && itemSatisfied(r, selectedByTree)) {
                return true;
            }
        }
        return false;
    }

    private static boolean itemSatisfied(TagRequire r, Map<String, Set<String>> selectedByTree) {
        if (selectedByTree == null) {
            return false;
        }
        Set<String> labels = selectedByTree.get(r.getTree().trim().toLowerCase(Locale.ROOT));
        if (labels == null || labels.isEmpty()) {
            return false;
        }
        String want = r.getLabel().trim();
        for (String selected : labels) {
            if (selected != null && selected.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }
}
