package com.lariflix.jemm.tagteam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single metadata assignment produced by a tag-map node: either a tag or a genre value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagAssign {

    private AssignKind kind = AssignKind.TAG;
    private String value;

    public TagAssign() {
    }

    public TagAssign(AssignKind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    public AssignKind getKind() {
        return kind;
    }

    public void setKind(AssignKind kind) {
        this.kind = kind;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
