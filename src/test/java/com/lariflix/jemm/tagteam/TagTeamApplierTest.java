package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.dtos.JellyfinGenreItem;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.dtos.JellyfinPeopleItem;
import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class TagTeamApplierTest {

    private TagNode node(String label, AssignKind kind, String value) {
        TagNode n = new TagNode();
        n.setLabel(label);
        n.getAssign().add(new TagAssign(kind, value));
        return n;
    }

    private TagTree tree(String name, TagNode... children) {
        TagTree t = new TagTree();
        t.setName(name);
        t.setChildren(new ArrayList<>(Arrays.asList(children)));
        return t;
    }

    private TagMapVocabulary buildVocab() {
        TagTree res = tree("Res",
                node("HD", AssignKind.TAG, "HD"),
                node("4K", AssignKind.TAG, "4K"),
                node("GenreX", AssignKind.GENRE, "GenX"),
                node("GenreY", AssignKind.GENRE, "GenY"));
        TagTree mood = tree("Mood", node("Happy", AssignKind.TAG, "happy"));
        TagMap map = new TagMap();
        map.setTrees(Arrays.asList(res, mood));
        return TagMapVocabulary.from(map);
    }

    private TagTeamApplier applier(TagMapVocabulary vocab) {
        return new TagTeamApplier(new ConnectJellyfinAPI(), vocab);
    }

    private boolean tagsContain(List<String> tags, String value) {
        for (String t : tags) {
            if (t.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean genresContain(List<JellyfinGenreItem> genres, String value) {
        for (JellyfinGenreItem g : genres) {
            if (g.getName() != null && g.getName().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void overwriteReplacesOwnedTagsKeepsManualAndSkippedTrees() {
        TagMapVocabulary vocab = buildVocab();
        JellyfinItemMetadata meta = new JellyfinItemMetadata();
        meta.setTags(new ArrayList<>(Arrays.asList("Manual", "HD", "keepme", "happy")));

        TagTeamSelection sel = new TagTeamSelection();
        sel.getWalkedTreeNames().add("Res"); // Mood was skipped
        sel.getAssigns().add(new TagAssign(AssignKind.TAG, "4K"));

        applier(vocab).applyToMetadata(meta, sel, true);

        assertTrue(tagsContain(meta.getTags(), "Manual"));
        assertTrue(tagsContain(meta.getTags(), "keepme"));
        assertTrue(tagsContain(meta.getTags(), "4K"));
        assertFalse(tagsContain(meta.getTags(), "HD")); // owned by walked tree, replaced
        assertTrue(tagsContain(meta.getTags(), "happy")); // owned by skipped tree, preserved
    }

    @Test
    public void overwriteReplacesOwnedGenres() {
        TagMapVocabulary vocab = buildVocab();
        JellyfinItemMetadata meta = new JellyfinItemMetadata();
        ArrayList<JellyfinGenreItem> genres = new ArrayList<>();
        JellyfinGenreItem gx = new JellyfinGenreItem();
        gx.setName("GenX");
        JellyfinGenreItem manual = new JellyfinGenreItem();
        manual.setName("ManualGenre");
        genres.add(gx);
        genres.add(manual);
        meta.setGenreItems(genres);

        TagTeamSelection sel = new TagTeamSelection();
        sel.getWalkedTreeNames().add("Res");
        sel.getAssigns().add(new TagAssign(AssignKind.GENRE, "GenY"));

        applier(vocab).applyToMetadata(meta, sel, true);

        assertFalse(genresContain(meta.getGenreItems(), "GenX"));
        assertTrue(genresContain(meta.getGenreItems(), "ManualGenre"));
        assertTrue(genresContain(meta.getGenreItems(), "GenY"));
    }

    @Test
    public void peopleAuthoritativeReplacesButMergeAdds() {
        TagMapVocabulary vocab = buildVocab();

        JellyfinItemMetadata meta = new JellyfinItemMetadata();
        ArrayList<JellyfinPeopleItem> existing = new ArrayList<>();
        JellyfinPeopleItem bob = new JellyfinPeopleItem();
        bob.setName("Bob");
        bob.setType("Actor");
        existing.add(bob);
        meta.setPeople(existing);

        TagTeamSelection sel = new TagTeamSelection();
        ArrayList<JellyfinPeopleItem> people = new ArrayList<>();
        JellyfinPeopleItem alice = new JellyfinPeopleItem();
        alice.setName("Alice");
        alice.setType("Actor");
        people.add(alice);
        sel.setPeople(people);

        applier(vocab).applyToMetadata(meta, sel, true);
        assertEquals(1, meta.getPeople().size());
        assertEquals("Alice", meta.getPeople().get(0).getName());

        // Merge path keeps existing and adds new.
        JellyfinItemMetadata meta2 = new JellyfinItemMetadata();
        ArrayList<JellyfinPeopleItem> existing2 = new ArrayList<>();
        existing2.add(bob);
        meta2.setPeople(existing2);
        applier(vocab).applyToMetadata(meta2, sel, false);
        assertEquals(2, meta2.getPeople().size());
    }

    @Test
    public void dateSetAuthoritativeButOnlyFillsWhenMerging() {
        TagMapVocabulary vocab = buildVocab();
        Calendar cal = new GregorianCalendar(2022, Calendar.MARCH, 3);

        // Authoritative: always sets.
        JellyfinItemMetadata meta = new JellyfinItemMetadata();
        Calendar existing = new GregorianCalendar(2000, Calendar.JANUARY, 1);
        meta.setPremiereDate(existing.getTime());
        meta.setProductionYear(2000);
        TagTeamSelection sel = new TagTeamSelection();
        sel.setPremiereDate(cal.getTime());
        sel.setProductionYear(2022);
        applier(vocab).applyToMetadata(meta, sel, true);
        assertEquals(2022, meta.getProductionYear());

        // Merge: does not overwrite an existing date/year.
        JellyfinItemMetadata meta2 = new JellyfinItemMetadata();
        meta2.setPremiereDate(existing.getTime());
        meta2.setProductionYear(2000);
        applier(vocab).applyToMetadata(meta2, sel, false);
        assertEquals(2000, meta2.getProductionYear());

        // Merge into empty: fills.
        JellyfinItemMetadata meta3 = new JellyfinItemMetadata();
        applier(vocab).applyToMetadata(meta3, sel, false);
        assertEquals(2022, meta3.getProductionYear());
        assertNotNull(meta3.getPremiereDate());
    }

    @Test
    public void tagMapLoaderParsesJsonCaseInsensitiveEnums() throws Exception {
        String json = "{\n"
                + "  \"version\": 1,\n"
                + "  \"trees\": [\n"
                + "    { \"name\": \"Type\", \"order\": 2, \"children\": [\n"
                + "      { \"label\": \"Solo\", \"assign\": [ {\"kind\": \"tag\", \"value\": \"solo\"} ] }\n"
                + "    ]},\n"
                + "    { \"name\": \"Style\", \"order\": 1, \"multiSelect\": true, \"children\": [\n"
                + "      { \"label\": \"Artsy\", \"assign\": [ {\"kind\": \"genre\", \"value\": \"Art\"} ] }\n"
                + "    ]}\n"
                + "  ]\n"
                + "}";
        TagMap map = new TagMapLoader().parse(json);
        // Sorted by order: Style (1) before Type (2).
        assertEquals("Style", map.getTrees().get(0).getName());
        assertEquals("Type", map.getTrees().get(1).getName());
        assertEquals(AssignKind.GENRE,
                map.getTrees().get(0).getChildren().get(0).getAssign().get(0).getKind());
        assertEquals(AssignKind.TAG,
                map.getTrees().get(1).getChildren().get(0).getAssign().get(0).getKind());
    }
}
