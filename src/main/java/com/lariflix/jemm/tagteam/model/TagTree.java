package com.lariflix.jemm.tagteam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * A named decision tree with an explicit ordering. Acts as a virtual root: its
 * {@code children} are the first options presented, and {@code multiSelect} controls
 * whether several of them may be picked at once.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagTree {

    private String name;
    private int order;
    private boolean multiSelect;
    private List<TagNode> children = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public void setMultiSelect(boolean multiSelect) {
        this.multiSelect = multiSelect;
    }

    public List<TagNode> getChildren() {
        return children;
    }

    public void setChildren(List<TagNode> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }
}
