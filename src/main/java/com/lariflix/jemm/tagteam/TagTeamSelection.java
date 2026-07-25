package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.dtos.JellyfinPeopleItem;
import com.lariflix.jemm.dtos.JellyfinStudioItem;
import com.lariflix.jemm.tagteam.model.TagAssign;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The user's choices for a single stop, handed from the UI to {@link TagTeamApplier}.
 *
 * <p>{@code assigns} and {@code walkedTreeNames} come from the {@link TreeWalker}. People
 * and studios are the authoritative lists shown in the side panels (edits and removals
 * included); a {@code null} list means "leave untouched". Date/year are applied when non-null.</p>
 */
public class TagTeamSelection {

    private final List<TagAssign> assigns = new ArrayList<>();
    private final Set<String> walkedTreeNames = new LinkedHashSet<>();
    private ArrayList<JellyfinPeopleItem> people;
    private ArrayList<JellyfinStudioItem> studios;
    private Date premiereDate;
    private Integer productionYear;

    public List<TagAssign> getAssigns() {
        return assigns;
    }

    public Set<String> getWalkedTreeNames() {
        return walkedTreeNames;
    }

    public ArrayList<JellyfinPeopleItem> getPeople() {
        return people;
    }

    public void setPeople(ArrayList<JellyfinPeopleItem> people) {
        this.people = people;
    }

    public ArrayList<JellyfinStudioItem> getStudios() {
        return studios;
    }

    public void setStudios(ArrayList<JellyfinStudioItem> studios) {
        this.studios = studios;
    }

    public Date getPremiereDate() {
        return premiereDate;
    }

    public void setPremiereDate(Date premiereDate) {
        this.premiereDate = premiereDate;
    }

    public Integer getProductionYear() {
        return productionYear;
    }

    public void setProductionYear(Integer productionYear) {
        this.productionYear = productionYear;
    }
}
