package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.RequireMode;
import com.lariflix.jemm.tagteam.model.TagRequire;
import com.lariflix.jemm.tagteam.model.TagRequires;
import com.lariflix.jemm.tagteam.model.TagTree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    @Test
    public void requiresHidesChipUntilPrerequisiteSelected() {
        TagNode duo = node("Duo", false, "duo");
        TagNode solo = node("Solo", false, "solo");
        TagTree type = tree("Type", false, solo, duo);

        TagNode calm = node("Calm", false, "calm");
        TagNode duoOnly = node("Duo Mood", false, "duoMood");
        duoOnly.setRequires(TagRequires.single("Type", "Duo"));
        TagTree mood = tree("Mood", false, calm, duoOnly);

        TreeWalker walker = new TreeWalker(new ArrayList<>(Arrays.asList(type, mood)));
        walker.selectSingle(solo);
        assertEquals("Mood", walker.currentTreeName());
        List<String> labels = walker.currentOptions().stream()
                .map(TagNode::getLabel).collect(Collectors.toList());
        assertTrue(labels.contains("Calm"));
        assertFalse(labels.contains("Duo Mood"));
        walker.selectSingle(calm);
        assertTrue(walker.isFinished());
    }

    @Test
    public void requiresShowsChipWhenPrerequisiteSelected() {
        TagNode duo = node("Duo", false, "duo");
        TagTree type = tree("Type", false, duo);

        TagNode duoOnly = node("Duo Mood", false, "duoMood");
        duoOnly.setRequires(TagRequires.single("Type", "Duo"));
        TagTree mood = tree("Mood", false, duoOnly);

        TreeWalker walker = new TreeWalker(new ArrayList<>(Arrays.asList(type, mood)));
        walker.selectSingle(duo);
        assertEquals("Mood", walker.currentTreeName());
        assertEquals(1, walker.currentOptions().size());
        assertEquals("Duo Mood", walker.currentOptions().get(0).getLabel());
        walker.selectSingle(duoOnly);
        assertTrue(values(walker.getCollected()).contains("duoMood"));
    }

    @Test
    public void allGatedRootChipsMarksTreeWalkedAndAdvances() {
        TagNode gated = node("Gated", false, "gated");
        gated.setRequires(TagRequires.single("Type", "Duo"));
        TagTree mood = tree("Mood", false, gated);
        TagTree after = tree("After", false, node("z", false, "z"));

        TreeWalker walker = new TreeWalker(new ArrayList<>(Arrays.asList(mood, after)));
        // Type never walked → Mood has no visible chips → auto-walked, land on After.
        assertEquals("After", walker.currentTreeName());
        assertTrue(walker.getWalkedTreeNames().contains("Mood"));
    }

    @Test
    public void skipPrerequisiteTreeLeavesRequiresUnmet() {
        TagNode duo = node("Duo", false, "duo");
        TagTree type = tree("Type", false, duo);
        TagNode duoOnly = node("Duo Mood", false, "duoMood");
        duoOnly.setRequires(TagRequires.single("Type", "Duo"));
        TagTree mood = tree("Mood", false, duoOnly, node("Always", false, "always"));

        TreeWalker walker = new TreeWalker(new ArrayList<>(Arrays.asList(type, mood)));
        walker.skipCurrentTree();
        assertEquals("Mood", walker.currentTreeName());
        List<String> labels = walker.currentOptions().stream()
                .map(TagNode::getLabel).collect(Collectors.toList());
        assertEquals(Collections.singletonList("Always"), labels);
    }

    @Test
    public void requiresAnyOrAllSemantics() {
        TagNode duo = node("Duo", false, "duo");
        TagNode indoor = node("Indoor", false, "indoor");
        TagNode outdoor = node("Outdoor", false, "outdoor");
        TagTree type = tree("Type", false, duo);
        TagTree setting = tree("Setting", true, indoor, outdoor);

        TagNode anyChip = node("AnyChip", false, "any");
        anyChip.setRequires(new TagRequires(RequireMode.ANY, new ArrayList<>(Arrays.asList(
                new TagRequire("Type", "Duo"),
                new TagRequire("Setting", "Indoor")))));

        TagNode allChip = node("AllChip", false, "all");
        allChip.setRequires(new TagRequires(RequireMode.ALL, new ArrayList<>(Arrays.asList(
                new TagRequire("Type", "Duo"),
                new TagRequire("Setting", "Indoor")))));

        TagTree mood = tree("Mood", false, anyChip, allChip);

        // Duo only → ANY visible, ALL hidden
        TreeWalker w1 = new TreeWalker(new ArrayList<>(Arrays.asList(type, setting, mood)));
        w1.selectSingle(duo);
        w1.confirmMultiSelect(Collections.emptyList()); // Setting: pick nothing
        List<String> labels1 = w1.currentOptions().stream()
                .map(TagNode::getLabel).collect(Collectors.toList());
        assertTrue(labels1.contains("AnyChip"));
        assertFalse(labels1.contains("AllChip"));

        // Duo + Indoor → both visible
        TreeWalker w2 = new TreeWalker(new ArrayList<>(Arrays.asList(type, setting, mood)));
        w2.selectSingle(duo);
        w2.confirmMultiSelect(Collections.singletonList(indoor));
        List<String> labels2 = w2.currentOptions().stream()
                .map(TagNode::getLabel).collect(Collectors.toList());
        assertTrue(labels2.contains("AnyChip"));
        assertTrue(labels2.contains("AllChip"));
    }
}
