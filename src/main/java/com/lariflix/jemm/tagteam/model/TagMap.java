package com.lariflix.jemm.tagteam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * The single active tag map: a versioned collection of named decision trees.
 * Authored externally as JSON (visual editor is a later phase).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagMap {

    private int version = 1;
    private List<TagTree> trees = new ArrayList<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<TagTree> getTrees() {
        return trees;
    }

    public void setTrees(List<TagTree> trees) {
        this.trees = trees == null ? new ArrayList<>() : trees;
    }

    public boolean isEmpty() {
        return trees == null || trees.isEmpty();
    }
}
