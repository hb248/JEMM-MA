package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagRequire;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-stop state machine that walks the tag map's trees in order.
 *
 * <p>Each frame presents a set of chips (a tree's children, or a chosen node's children).
 * Single-select frames advance on {@link #selectSingle(TagNode)}; multi-select frames collect
 * several children and advance on {@link #confirmMultiSelect(List)}. Choosing a node with
 * children descends into it (depth-first); multi-selected branches are queued and visited in
 * selection order. A tree can be skipped with {@link #skipCurrentTree()} (its owned values are
 * then left untouched). The walker records every {@link TagAssign} chosen and the set of trees
 * that were actually walked (not skipped).</p>
 *
 * <p>Nodes with {@link TagRequire} are hidden unless a matching label was selected in that
 * prerequisite tree earlier on this stop. Frames that become empty after filtering advance
 * automatically; an empty root frame still marks the tree as walked.</p>
 */
public class TreeWalker {

    private final List<TagTree> trees;
    private final List<TagAssign> collected = new ArrayList<>();
    private final Set<String> walkedTreeNames = new LinkedHashSet<>();
    /** Lowercased tree name -> selected node labels (original casing preserved in the set). */
    private final Map<String, Set<String>> selectedByTree = new LinkedHashMap<>();

    private int treeIndex = -1;
    private Frame current;
    private final Deque<Frame> stack = new ArrayDeque<>();

    public TreeWalker(List<TagTree> trees) {
        this.trees = trees == null ? new ArrayList<>() : trees;
        advanceToNextTree();
    }

    private void advanceToNextTree() {
        stack.clear();
        current = null;
        treeIndex++;
        while (treeIndex < trees.size()) {
            TagTree tree = trees.get(treeIndex);
            List<TagNode> options = filterOptions(tree.getChildren());
            if (options.isEmpty()) {
                // Nothing visible (empty tree or all chips gated); count as walked.
                walkedTreeNames.add(tree.getName());
                treeIndex++;
                continue;
            }
            current = new Frame(tree.getName(), options, tree.isMultiSelect());
            return;
        }
    }

    private void finishCurrentTree() {
        if (treeIndex >= 0 && treeIndex < trees.size()) {
            walkedTreeNames.add(trees.get(treeIndex).getName());
        }
        advanceToNextTree();
    }

    /**
     * Skips the current tree without recording it as walked (its owned values stay untouched).
     * Selections from this tree are not recorded, so later {@code requires} cannot use them.
     */
    public void skipCurrentTree() {
        advanceToNextTree();
    }

    /**
     * Picks a single child of the current single-select frame.
     */
    public void selectSingle(TagNode node) {
        if (current == null || current.multiSelect || node == null) {
            return;
        }
        process(Collections.singletonList(node));
    }

    /**
     * Confirms the chosen children of the current multi-select frame (may be empty).
     */
    public void confirmMultiSelect(List<TagNode> nodes) {
        if (current == null || !current.multiSelect) {
            return;
        }
        process(nodes == null ? Collections.emptyList() : nodes);
    }

    private void process(List<TagNode> selected) {
        String treeName = currentTreeName();
        for (TagNode node : selected) {
            if (node == null) {
                continue;
            }
            recordSelection(treeName, node.getLabel());
            if (node.getAssign() != null) {
                collected.addAll(node.getAssign());
            }
        }
        List<Frame> childFrames = new ArrayList<>();
        for (TagNode node : selected) {
            if (node != null && node.hasChildren()) {
                childFrames.add(new Frame(treeName, node.getChildren(), node.isMultiSelect()));
            }
        }
        for (int i = childFrames.size() - 1; i >= 0; i--) {
            stack.push(childFrames.get(i));
        }
        advanceFrame();
    }

    private void advanceFrame() {
        while (!stack.isEmpty()) {
            Frame next = stack.pop();
            List<TagNode> filtered = filterOptions(next.options);
            if (!filtered.isEmpty()) {
                current = new Frame(next.treeName, filtered, next.multiSelect);
                return;
            }
        }
        finishCurrentTree();
    }

    private void recordSelection(String treeName, String label) {
        if (treeName == null || treeName.isBlank() || label == null || label.isBlank()) {
            return;
        }
        String key = treeName.trim().toLowerCase(Locale.ROOT);
        selectedByTree.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(label.trim());
    }

    private List<TagNode> filterOptions(List<TagNode> options) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        List<TagNode> out = new ArrayList<>();
        for (TagNode node : options) {
            if (node != null && isRequirementSatisfied(node)) {
                out.add(node);
            }
        }
        return out;
    }

    private boolean isRequirementSatisfied(TagNode node) {
        TagRequire req = node.getRequires();
        if (req == null || !req.isSet()) {
            return true;
        }
        Set<String> labels = selectedByTree.get(req.getTree().trim().toLowerCase(Locale.ROOT));
        if (labels == null || labels.isEmpty()) {
            return false;
        }
        String want = req.getLabel().trim();
        for (String selected : labels) {
            if (selected != null && selected.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    // --- state queries ------------------------------------------------------

    /**
     * @return true once every tree has been walked or skipped
     */
    public boolean isFinished() {
        return current == null;
    }

    public String currentTreeName() {
        return current == null ? null : current.treeName;
    }

    public List<TagNode> currentOptions() {
        return current == null ? Collections.emptyList() : current.options;
    }

    public boolean isCurrentMultiSelect() {
        return current != null && current.multiSelect;
    }

    /**
     * @return 1-based index of the tree currently being walked (0 when finished)
     */
    public int currentTreeNumber() {
        return current == null ? trees.size() : treeIndex + 1;
    }

    public int treeCount() {
        return trees.size();
    }

    public List<TagAssign> getCollected() {
        return collected;
    }

    /**
     * @return names of trees that were actually walked (never the skipped ones)
     */
    public Set<String> getWalkedTreeNames() {
        return walkedTreeNames;
    }

    private static class Frame {
        private final String treeName;
        private final List<TagNode> options;
        private final boolean multiSelect;

        Frame(String treeName, List<TagNode> options, boolean multiSelect) {
            this.treeName = treeName;
            this.options = options == null ? Collections.emptyList() : options;
            this.multiSelect = multiSelect;
        }
    }
}
