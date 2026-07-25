package com.lariflix.jemm.tagteam.editor;

import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TreeGraphPreviewPanelTest {

    @Test
    public void laysOutRootAndChildrenWithEdges() {
        TagTree tree = new TagTree();
        tree.setName("Type");
        TagNode a = new TagNode();
        a.setLabel("Solo");
        TagNode b = new TagNode();
        b.setLabel("Duo");
        TagNode b1 = new TagNode();
        b1.setLabel("Mixed");
        b.setChildren(new ArrayList<>(Arrays.asList(b1)));
        tree.setChildren(new ArrayList<>(Arrays.asList(a, b)));

        TreeGraphPreviewPanel panel = new TreeGraphPreviewPanel();
        panel.setTree(tree);

        // root + Solo + Duo + Mixed
        assertEquals(4, panel.boxCountForTests());
        // Type->Solo, Type->Duo, Duo->Mixed
        assertEquals(3, panel.edgeCountForTests());
    }

    @Test
    public void nullTreeClearsLayout() {
        TreeGraphPreviewPanel panel = new TreeGraphPreviewPanel();
        panel.setTree(new TagTree());
        panel.setTree(null);
        assertEquals(0, panel.boxCountForTests());
        assertEquals(0, panel.edgeCountForTests());
    }
}
