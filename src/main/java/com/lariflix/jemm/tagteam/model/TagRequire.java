package com.lariflix.jemm.tagteam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Optional prerequisite for a {@link TagNode}: the chip is only shown during a Tag-Team
 * walk when a node with {@code label} was selected in the tree named {@code tree}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagRequire {

    private String tree;
    private String label;

    public TagRequire() {
    }

    public TagRequire(String tree, String label) {
        this.tree = tree;
        this.label = label;
    }

    public String getTree() {
        return tree;
    }

    public void setTree(String tree) {
        this.tree = tree;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isSet() {
        return tree != null && !tree.trim().isEmpty()
                && label != null && !label.trim().isEmpty();
    }
}
