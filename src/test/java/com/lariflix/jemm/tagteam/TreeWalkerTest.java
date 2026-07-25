package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TreeWalkerTest {

    private TagNode node(String label, boolean multi, String tagValue, TagNode... children) {
        TagNode n = new TagNode();
        n.setLabel(label);
        n.setMultiSelect(multi);
        if (tagValue != null) {
            n.getAssign().add(new TagAssign(AssignKind.TAG, tagValue));
        }
        n.setChildren(new ArrayList<>(Arrays.asList(children)));
        return n;
    }

    private TagTree tree(String name, boolean multi, TagNode... children) {
        TagTree t = new TagTree();
        t.setName(name);
        t.setMultiSelect(multi);
        t.setChildren(new ArrayList<>(Arrays.asList(children)));
        return t;
    }

    private List<String> values(List<TagAssign> assigns) {
        List<String> out = new ArrayList<>();
        for (TagAssign a : assigns) {
            out.add(a.getValue());
        }
        return out;
    }

    @Test
    public void singleSelectDescendsAndCollects() {
        TagNode leafA = node("Leaf A", false, "leafA");
        TagNode branch = node("Branch", false, "branch", leafA);
        TagTree t = tree("T", false, branch, node("Other", false, "other"));
        TreeWalker walker = new TreeWalker(new ArrayList<>(Arrays.asList(t)));

        assertFalse(walker.isFinished());
        assertEquals("T", walker.currentTreeName());
        walker.selectSingle(branch);
        // Descended into branch's children.
        assertFalse(walker.isFinished());
        assertEquals(1, walker.currentOptions().size());
        walker.selectSingle(leafA);
        // Tree done -> walker finished.
        assertTrue(walker.isFinished());
        assertTrue(values(walker.getCollected()).contains("branch"));
        assertTrue(values(walker.getCollected()).contains("leafA"));
        assertTrue(walker.getWalkedTreeNames().contains("T"));
    }

    @Test
    public void multiSelectQueuesBranchesInOrder() {
        TagNode aChild = node("A child", false, "aChild");
        TagNode a = node("A", false, "a", aChild);
        TagNode b = node("B", false, "b");
        TagTree t = tree("Multi", true, a, b);
        TreeWalker walker = new TreeWalker(new ArrayList<>(Arrays.asList(t)));

        assertTrue(walker.isCurrentMultiSelect());
        walker.confirmMultiSelect(Arrays.asList(a, b));
        // a has a child -> we now walk that child frame.
        assertFalse(walker.isFinished());
        assertEquals(1, walker.currentOptions().size());
        walker.selectSingle(aChild);
        assertTrue(walker.isFinished());
        List<String> vals = values(walker.getCollected());
        assertTrue(vals.contains("a"));
        assertTrue(vals.contains("b"));
        assertTrue(vals.contains("aChild"));
    }

    @Test
    public void skipTreeDoesNotRecordWalked() {
        TagTree t1 = tree("Skipme", false, node("x", false, "x"));
        TagTree t2 = tree("Keep", false, node("y", false, "y"));
        TreeWalker walker = new TreeWalker(new ArrayList<>(Arrays.asList(t1, t2)));

        assertEquals("Skipme", walker.currentTreeName());
        walker.skipCurrentTree();
        assertEquals("Keep", walker.currentTreeName());
        walker.selectSingle(t2.getChildren().get(0));
        assertTrue(walker.isFinished());
        assertFalse(walker.getWalkedTreeNames().contains("Skipme"));
        assertTrue(walker.getWalkedTreeNames().contains("Keep"));
    }

    @Test
    public void emptyTreeIsSkippedAutomatically() {
        TagTree empty = tree("Empty", false);
        TagTree real = tree("Real", false, node("z", false, "z"));
        TreeWalker walker = new TreeWalker(new ArrayList<>(Arrays.asList(empty, real)));
        assertEquals("Real", walker.currentTreeName());
    }
}
