package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
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
 */
public class TreeWalker {

    private final List<TagTree> trees;
    private final List<TagAssign> collected = new ArrayList<>();
    private final Set<String> walkedTreeNames = new LinkedHashSet<>();

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
            List<TagNode> options = tree.getChildren();
            if (options == null || options.isEmpty()) {
                // Nothing to pick; count as walked (owns nothing meaningful) and move on.
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
        for (TagNode node : selected) {
            if (node != null && node.getAssign() != null) {
                collected.addAll(node.getAssign());
            }
        }
        // Queue children of selected nodes depth-first, preserving selection order.
        List<Frame> childFrames = new ArrayList<>();
        for (TagNode node : selected) {
            if (node != null && node.hasChildren()) {
                childFrames.add(new Frame(currentTreeName(), node.getChildren(), node.isMultiSelect()));
            }
        }
        for (int i = childFrames.size() - 1; i >= 0; i--) {
            stack.push(childFrames.get(i));
        }
        advanceFrame();
    }

    private void advanceFrame() {
        if (!stack.isEmpty()) {
            current = stack.pop();
        } else {
            finishCurrentTree();
        }
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
