package com.lariflix.jemm.tagteam.editor;

import com.lariflix.jemm.tagteam.TagMapLoader;
import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.io.File;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TagMapTreeModelTest {

    @Test
    public void addRemoveMoveAndRoundTrip() throws Exception {
        TagMapLoader loader = new TagMapLoader();
        File example = new File("tagmap.example.json");
        assertTrue(example.isFile(), "tagmap.example.json should exist at project root");
        TagMap map = loader.deepCopy(loader.load(example));
        TagMapTreeModel model = new TagMapTreeModel(map);

        assertTrue(model.getChildCount(TagMapTreeModel.ROOT) >= 1);
        TagTree first = (TagTree) model.getChild(TagMapTreeModel.ROOT, 0);
        String firstName = first.getName();

        TagNode child = model.addChild(first);
        assertNotNull(child);
        child.setLabel("EditorTestNode");
        child.getAssign().add(new TagAssign(AssignKind.TAG, "editor-test"));
        model.nodeChanged(child);

        assertTrue(model.moveDown(first) || model.getChildCount(TagMapTreeModel.ROOT) == 1);
        model.renumberTreeOrders();
        for (int i = 0; i < map.getTrees().size(); i++) {
            assertEquals(i + 1, map.getTrees().get(i).getOrder());
        }

        // Round-trip keeps the added node and its assign.
        String json = loader.toJson(map);
        TagMap again = loader.parse(json);
        TagTree found = null;
        for (TagTree t : again.getTrees()) {
            if (firstName.equals(t.getName())) {
                found = t;
                break;
            }
        }
        assertNotNull(found);
        assertTrue(found.getChildren().stream().anyMatch(n ->
                "EditorTestNode".equals(n.getLabel())
                        && n.getAssign().stream().anyMatch(a -> "editor-test".equals(a.getValue()))));

        assertTrue(model.remove(child));
        assertFalse(first.getChildren().stream().anyMatch(n -> "EditorTestNode".equals(n.getLabel())));
    }

    @Test
    public void deepCopyIsIndependent() throws Exception {
        TagMapLoader loader = new TagMapLoader();
        TagMap original = loader.load(new File("tagmap.example.json"));
        TagMap copy = loader.deepCopy(original);
        copy.getTrees().get(0).setName("Mutated");
        assertNotEquals("Mutated", original.getTrees().get(0).getName());
    }

    @Test
    public void addTreeCreatesNamedRoot() {
        TagMapTreeModel model = new TagMapTreeModel(new TagMap());
        TagTree tree = model.addTree();
        assertEquals(1, model.getChildCount(TagMapTreeModel.ROOT));
        assertEquals("New tree", tree.getName());
        assertEquals(1, tree.getOrder());
    }
}
