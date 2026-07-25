package com.lariflix.jemm.tagteam;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggestions parsed from a filename: studios, actors, a title and a ranked list of
 * date candidates. Any field may be empty when nothing could be inferred.
 */
public class FilenameSuggestions {

    private final List<String> studios = new ArrayList<>();
    private final List<String> actors = new ArrayList<>();
    private String title = "";
    private final List<DateCandidate> dates = new ArrayList<>();

    public List<String> getStudios() {
        return studios;
    }

    public List<String> getActors() {
        return actors;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public List<DateCandidate> getDates() {
        return dates;
    }
}
