package com.lariflix.jemm.tagteam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * A node (chip) in a tag tree. It carries an optional list of assignments (tags/genres);
 * an empty {@code assign} list makes the node traversal-only. {@code multiSelect} controls
 * whether the user may pick several of this node's children at once.
 * Optional {@link #requires} hides the chip unless that prerequisite was selected earlier.
 * Optional {@link #exclusive}: in a multi-select frame, clicking the chip confirms only
 * that chip and advances immediately (no combining with other toggles).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagNode {

    private String label;
    private List<TagAssign> assign = new ArrayList<>();
    private boolean multiSelect;
    private boolean exclusive;
    private TagRequire requires;
    private List<TagNode> children = new ArrayList<>();

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<TagAssign> getAssign() {
        return assign;
    }

    public void setAssign(List<TagAssign> assign) {
        this.assign = assign == null ? new ArrayList<>() : assign;
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public void setMultiSelect(boolean multiSelect) {
        this.multiSelect = multiSelect;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public TagRequire getRequires() {
        return requires;
    }

    public void setRequires(TagRequire requires) {
        this.requires = requires;
    }

    public List<TagNode> getChildren() {
        return children;
    }

    public void setChildren(List<TagNode> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public boolean assignsAnything() {
        return assign != null && !assign.isEmpty();
    }
}
