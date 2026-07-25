package com.lariflix.jemm.tagteam.editor;

import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Swing {@link TreeModel} over a live {@link TagMap}. Root children are trees;
 * deeper levels are {@link TagNode}s.
 */
public class TagMapTreeModel implements TreeModel {

    public static final Object ROOT = new Object() {
        @Override
        public String toString() {
            return "Tag map";
        }
    };

    private final TagMap map;
    private final List<TreeModelListener> listeners = new ArrayList<>();

    public TagMapTreeModel(TagMap map) {
        this.map = map == null ? new TagMap() : map;
        if (this.map.getTrees() == null) {
            this.map.setTrees(new ArrayList<>());
        }
    }

    public TagMap getMap() {
        return map;
    }

    @Override
    public Object getRoot() {
        return ROOT;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (parent == ROOT) {
            return map.getTrees().get(index);
        }
        if (parent instanceof TagTree) {
            return ((TagTree) parent).getChildren().get(index);
        }
        if (parent instanceof TagNode) {
            return ((TagNode) parent).getChildren().get(index);
        }
        return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if (parent == ROOT) {
            return map.getTrees().size();
        }
        if (parent instanceof TagTree) {
            return ((TagTree) parent).getChildren().size();
        }
        if (parent instanceof TagNode) {
            return ((TagNode) parent).getChildren().size();
        }
        return 0;
    }

    @Override
    public boolean isLeaf(Object node) {
        return getChildCount(node) == 0;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // Labels edited via the detail panel; outline refreshes via nodeChanged.
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == null || child == null) {
            return -1;
        }
        int count = getChildCount(parent);
        for (int i = 0; i < count; i++) {
            if (getChild(parent, i) == child) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    public String displayLabel(Object node) {
        if (node instanceof TagTree) {
            TagTree t = (TagTree) node;
            String name = t.getName() == null || t.getName().isBlank() ? "(unnamed tree)" : t.getName();
            return t.isMultiSelect() ? name + " [multi]" : name;
        }
        if (node instanceof TagNode) {
            TagNode n = (TagNode) node;
            String label = n.getLabel() == null || n.getLabel().isBlank() ? "(unnamed)" : n.getLabel();
            if (n.isMultiSelect()) {
                label = label + " [multi]";
            }
            if (n.isExclusive()) {
                label = label + " [excl]";
            }
            if (n.assignsAnything()) {
                label = label + " (" + n.getAssign().size() + ")";
            }
            if (n.getRequires() != null && n.getRequires().isSet()) {
                label = label + " ⇢ " + n.getRequires().outlineMarker();
            }
            return label;
        }
        return String.valueOf(node);
    }

    public TagTree addTree() {
        TagTree tree = new TagTree();
        tree.setName("New tree");
        tree.setOrder(nextTreeOrder());
        tree.setMultiSelect(false);
        tree.setChildren(new ArrayList<>());
        int index = map.getTrees().size();
        map.getTrees().add(tree);
        fireInserted(new Object[]{ROOT}, new int[]{index}, new Object[]{tree});
        return tree;
    }

    public TagNode addChild(Object parent) {
        if (parent == null || parent == ROOT) {
            return null;
        }
        TagNode child = new TagNode();
        child.setLabel("New node");
        child.setMultiSelect(false);
        child.setAssign(new ArrayList<>());
        child.setChildren(new ArrayList<>());
        List<TagNode> siblings = childrenOf(parent);
        if (siblings == null) {
            return null;
        }
        int index = siblings.size();
        siblings.add(child);
        fireInserted(pathTo(parent), new int[]{index}, new Object[]{child});
        return child;
    }

    public boolean remove(Object node) {
        if (node == null || node == ROOT) {
            return false;
        }
        if (node instanceof TagTree) {
            int index = map.getTrees().indexOf(node);
            if (index < 0) {
                return false;
            }
            map.getTrees().remove(index);
            renumberTreeOrders();
            fireRemoved(new Object[]{ROOT}, new int[]{index}, new Object[]{node});
            return true;
        }
        if (node instanceof TagNode) {
            Object parent = findParent(node);
            if (parent == null) {
                return false;
            }
            List<TagNode> siblings = childrenOf(parent);
            int index = siblings.indexOf(node);
            if (index < 0) {
                return false;
            }
            siblings.remove(index);
            fireRemoved(pathTo(parent), new int[]{index}, new Object[]{node});
            return true;
        }
        return false;
    }

    public boolean moveUp(Object node) {
        return move(node, -1);
    }

    public boolean moveDown(Object node) {
        return move(node, 1);
    }

    private boolean move(Object node, int delta) {
        if (node == null || node == ROOT) {
            return false;
        }
        if (node instanceof TagTree) {
            int index = map.getTrees().indexOf(node);
            int target = index + delta;
            if (index < 0 || target < 0 || target >= map.getTrees().size()) {
                return false;
            }
            TagTree item = map.getTrees().remove(index);
            map.getTrees().add(target, item);
            renumberTreeOrders();
            fireStructureChanged(ROOT);
            return true;
        }
        if (node instanceof TagNode) {
            Object parent = findParent(node);
            if (parent == null) {
                return false;
            }
            List<TagNode> siblings = childrenOf(parent);
            int index = siblings.indexOf(node);
            int target = index + delta;
            if (index < 0 || target < 0 || target >= siblings.size()) {
                return false;
            }
            TagNode item = siblings.remove(index);
            siblings.add(target, item);
            fireStructureChanged(parent);
            return true;
        }
        return false;
    }

    public void nodeChanged(Object node) {
        if (node == null || node == ROOT) {
            fireStructureChanged(ROOT);
            return;
        }
        Object parent = node instanceof TagTree ? ROOT : findParent(node);
        if (parent == null) {
            fireStructureChanged(ROOT);
            return;
        }
        int index = getIndexOfChild(parent, node);
        if (index < 0) {
            fireStructureChanged(parent);
            return;
        }
        TreeModelEvent event = new TreeModelEvent(this, pathTo(parent), new int[]{index}, new Object[]{node});
        for (TreeModelListener l : new ArrayList<>(listeners)) {
            l.treeNodesChanged(event);
        }
    }

    public void structureChanged() {
        fireStructureChanged(ROOT);
    }

    public Object findParent(Object node) {
        if (node instanceof TagTree) {
            return ROOT;
        }
        if (!(node instanceof TagNode)) {
            return null;
        }
        for (TagTree tree : map.getTrees()) {
            if (tree.getChildren().contains(node)) {
                return tree;
            }
            Object nested = findParentInNodes(tree.getChildren(), (TagNode) node);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private Object findParentInNodes(List<TagNode> nodes, TagNode target) {
        for (TagNode n : nodes) {
            if (n.getChildren().contains(target)) {
                return n;
            }
            Object nested = findParentInNodes(n.getChildren(), target);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    public TagTree enclosingTree(Object node) {
        if (node instanceof TagTree) {
            return (TagTree) node;
        }
        Object current = node;
        while (current != null && current != ROOT) {
            if (current instanceof TagTree) {
                return (TagTree) current;
            }
            current = findParent(current);
        }
        return null;
    }

    public Object[] pathTo(Object node) {
        if (node == null || node == ROOT) {
            return new Object[]{ROOT};
        }
        List<Object> path = new ArrayList<>();
        Object current = node;
        while (current != null && current != ROOT) {
            path.add(0, current);
            current = findParent(current);
        }
        path.add(0, ROOT);
        return path.toArray();
    }

    public void renumberTreeOrders() {
        List<TagTree> trees = map.getTrees();
        for (int i = 0; i < trees.size(); i++) {
            trees.get(i).setOrder(i + 1);
        }
    }

    private int nextTreeOrder() {
        int max = 0;
        for (TagTree t : map.getTrees()) {
            if (t.getOrder() > max) {
                max = t.getOrder();
            }
        }
        return max + 1;
    }

    private List<TagNode> childrenOf(Object parent) {
        if (parent instanceof TagTree) {
            TagTree t = (TagTree) parent;
            if (t.getChildren() == null) {
                t.setChildren(new ArrayList<>());
            }
            return t.getChildren();
        }
        if (parent instanceof TagNode) {
            TagNode n = (TagNode) parent;
            if (n.getChildren() == null) {
                n.setChildren(new ArrayList<>());
            }
            return n.getChildren();
        }
        return null;
    }

    private void fireInserted(Object[] path, int[] indices, Object[] children) {
        TreeModelEvent event = new TreeModelEvent(this, path, indices, children);
        for (TreeModelListener l : new ArrayList<>(listeners)) {
            l.treeNodesInserted(event);
        }
    }

    private void fireRemoved(Object[] path, int[] indices, Object[] children) {
        TreeModelEvent event = new TreeModelEvent(this, path, indices, children);
        for (TreeModelListener l : new ArrayList<>(listeners)) {
            l.treeNodesRemoved(event);
        }
    }

    private void fireStructureChanged(Object node) {
        TreeModelEvent event = new TreeModelEvent(this, pathTo(node));
        for (TreeModelListener l : new ArrayList<>(listeners)) {
            l.treeStructureChanged(event);
        }
    }
}
