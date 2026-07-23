package com.lariflix.jemm.utils;

import com.lariflix.jemm.dtos.JellyfinPeopleItem;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MetadataListMergeTest {

    @Test
    public void mergePeopleDoesNotDuplicateById() {
        JellyfinPeopleItem existing = person("1", "Alice", "Actor");
        JellyfinPeopleItem incoming = person("1", "Alice", "Actor");

        ArrayList<JellyfinPeopleItem> left = new ArrayList<>();
        left.add(existing);
        ArrayList<JellyfinPeopleItem> right = new ArrayList<>();
        right.add(incoming);

        ArrayList<JellyfinPeopleItem> merged = MetadataListMerge.mergePeople(left, right);
        assertEquals(1, merged.size());
    }

    @Test
    public void mergePeopleAddsMissingPerson() {
        ArrayList<JellyfinPeopleItem> left = new ArrayList<>();
        left.add(person("1", "Alice", "Actor"));
        ArrayList<JellyfinPeopleItem> right = new ArrayList<>();
        right.add(person("2", "Bob", "Actor"));

        ArrayList<JellyfinPeopleItem> merged = MetadataListMerge.mergePeople(left, right);
        assertEquals(2, merged.size());
    }

    @Test
    public void mergeTagsIgnoreCase() {
        ArrayList<String> left = new ArrayList<>();
        left.add("HD");
        ArrayList<String> right = new ArrayList<>();
        right.add("hd");
        right.add("vertical");

        ArrayList<String> merged = MetadataListMerge.mergeTags(left, right);
        assertEquals(2, merged.size());
    }

    private JellyfinPeopleItem person(String id, String name, String type) {
        JellyfinPeopleItem person = new JellyfinPeopleItem();
        person.setId(id);
        person.setName(name);
        person.setType(type);
        person.setRole("");
        return person;
    }
}
