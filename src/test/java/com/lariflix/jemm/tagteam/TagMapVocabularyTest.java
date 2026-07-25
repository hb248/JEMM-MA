package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;

public class TagMapVocabularyTest {

    private TagNode node(String label, AssignKind kind, String value, TagNode... children) {
        TagNode n = new TagNode();
        n.setLabel(label);
        if (value != null) {
            n.getAssign().add(new TagAssign(kind, value));
        }
        n.setChildren(Arrays.asList(children));
        return n;
    }

    private TagTree tree(String name, TagNode... children) {
        TagTree t = new TagTree();
        t.setName(name);
        t.setChildren(Arrays.asList(children));
        return t;
    }

    @Test
    public void collectsOwnedTagsAndGenresPerTree() {
        TagTree t1 = tree("Type",
                node("Solo", AssignKind.TAG, "solo"),
                node("Duo", AssignKind.GENRE, "Duo Scene"));
        TagTree t2 = tree("Mood",
                node("Happy", AssignKind.TAG, "happy",
                        node("Very", AssignKind.TAG, "very-happy")));
        TagMap map = new TagMap();
        map.setTrees(Arrays.asList(t1, t2));

        TagMapVocabulary vocab = TagMapVocabulary.from(map);

        assertTrue(vocab.isOwnedTag("solo"));
        assertTrue(vocab.isOwnedTag("SOLO")); // case-insensitive
        assertTrue(vocab.isOwnedGenre("Duo Scene"));
        assertTrue(vocab.isOwnedTag("very-happy"));

        assertTrue(vocab.ownedTagsForTree("Type").contains("solo"));
        assertFalse(vocab.ownedTagsForTree("Type").contains("happy"));
        assertTrue(vocab.ownedTagsForTree("Mood").contains("happy"));
        assertTrue(vocab.ownedTagsForTree("Mood").contains("very-happy"));
        assertTrue(vocab.ownedGenresForTree("Type").contains("duo scene"));
    }

    @Test
    public void ownedForTreesUnion() {
        TagTree t1 = tree("A", node("x", AssignKind.TAG, "ax"));
        TagTree t2 = tree("B", node("y", AssignKind.TAG, "by"));
        TagMap map = new TagMap();
        map.setTrees(Arrays.asList(t1, t2));
        TagMapVocabulary vocab = TagMapVocabulary.from(map);

        assertTrue(vocab.ownedTagsForTrees(Arrays.asList("A", "B")).contains("ax"));
        assertTrue(vocab.ownedTagsForTrees(Arrays.asList("A", "B")).contains("by"));
        assertFalse(vocab.ownedTagsForTrees(Collections.singletonList("A")).contains("by"));
    }
}
