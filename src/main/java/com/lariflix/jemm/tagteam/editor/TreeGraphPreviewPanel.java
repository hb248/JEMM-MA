package com.lariflix.jemm.tagteam.editor;

import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * Read-only auto-layout preview of one {@link TagTree}: boxes for the tree root and
 * each chip, with parent→child arrows. No user placement or free edges.
 */
public class TreeGraphPreviewPanel extends JPanel {

    private static final int H_GAP = 28;
    private static final int V_GAP = 48;
    private static final int PAD = 24;
    private static final int BOX_H = 36;
    private static final int MIN_BOX_W = 72;
    private static final int TEXT_PAD = 12;

    private TagTree tree;
    private final List<Box> boxes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private int contentW = 200;
    private int contentH = 120;

    public TreeGraphPreviewPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(400, 180));
    }

    /**
     * Rebuilds the layout for the given tree (or clears when null).
     */
    public void setTree(TagTree tree) {
        this.tree = tree;
        relayout();
        revalidate();
        repaint();
    }

    public TagTree getTree() {
        return tree;
    }

    private void relayout() {
        boxes.clear();
        edges.clear();
        if (tree == null) {
            contentW = 200;
            contentH = 120;
            setPreferredSize(new Dimension(contentW, contentH));
            return;
        }

        LayoutNode root = new LayoutNode(null, treeLabel(tree), true);
        for (TagNode child : tree.getChildren()) {
            root.children.add(build(child));
        }
        measure(root, getFontMetrics(getFont()));
        place(root, PAD, PAD);
        collect(root, null);

        contentW = Math.max(200, (int) Math.ceil(root.subtreeWidth) + PAD * 2);
        contentH = Math.max(120, maxBottom(root) + PAD);
        setPreferredSize(new Dimension(contentW, contentH));
    }

    private LayoutNode build(TagNode node) {
        LayoutNode ln = new LayoutNode(node, nodeLabel(node), false);
        if (node.getChildren() != null) {
            for (TagNode child : node.getChildren()) {
                ln.children.add(build(child));
            }
        }
        return ln;
    }

    private void measure(LayoutNode node, FontMetrics fm) {
        int textW = fm.stringWidth(node.label) + TEXT_PAD * 2;
        node.boxW = Math.max(MIN_BOX_W, textW);
        node.boxH = BOX_H;
        if (node.children.isEmpty()) {
            node.subtreeWidth = node.boxW;
            return;
        }
        double childrenWidth = 0;
        for (int i = 0; i < node.children.size(); i++) {
            LayoutNode c = node.children.get(i);
            measure(c, fm);
            childrenWidth += c.subtreeWidth;
            if (i > 0) {
                childrenWidth += H_GAP;
            }
        }
        node.subtreeWidth = Math.max(node.boxW, childrenWidth);
    }

    private void place(LayoutNode node, double left, double top) {
        node.x = left + (node.subtreeWidth - node.boxW) / 2.0;
        node.y = top;
        if (node.children.isEmpty()) {
            return;
        }
        double childTop = top + node.boxH + V_GAP;
        double cursor = left + (node.subtreeWidth - childrenSpan(node)) / 2.0;
        for (LayoutNode c : node.children) {
            place(c, cursor, childTop);
            cursor += c.subtreeWidth + H_GAP;
        }
    }

    private double childrenSpan(LayoutNode node) {
        double w = 0;
        for (int i = 0; i < node.children.size(); i++) {
            w += node.children.get(i).subtreeWidth;
            if (i > 0) {
                w += H_GAP;
            }
        }
        return w;
    }

    private void collect(LayoutNode node, LayoutNode parent) {
        boxes.add(new Box(node.x, node.y, node.boxW, node.boxH, node.label, node.root));
        if (parent != null) {
            edges.add(new Edge(
                    parent.x + parent.boxW / 2.0,
                    parent.y + parent.boxH,
                    node.x + node.boxW / 2.0,
                    node.y));
        }
        for (LayoutNode c : node.children) {
            collect(c, node);
        }
    }

    private int maxBottom(LayoutNode node) {
        int bottom = (int) Math.ceil(node.y + node.boxH);
        for (LayoutNode c : node.children) {
            bottom = Math.max(bottom, maxBottom(c));
        }
        return bottom;
    }

    private static String treeLabel(TagTree tree) {
        String name = tree.getName() == null || tree.getName().isBlank() ? "(tree)" : tree.getName().trim();
        return tree.isMultiSelect() ? name + " ★" : name;
    }

    private static String nodeLabel(TagNode node) {
        String label = node.getLabel() == null || node.getLabel().isBlank() ? "(node)" : node.getLabel().trim();
        if (node.isMultiSelect()) {
            label = label + " ★";
        }
        if (node.assignsAnything()) {
            label = label + " ·";
        }
        return label;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (tree == null) {
                g2.setColor(Color.GRAY);
                g2.drawString("Select a tree to preview its structure.", PAD, PAD + 16);
                return;
            }
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(new Color(120, 120, 120));
            for (Edge e : edges) {
                drawArrow(g2, e.x1, e.y1, e.x2, e.y2);
            }
            for (Box b : boxes) {
                int x = (int) Math.round(b.x);
                int y = (int) Math.round(b.y);
                int w = (int) Math.round(b.w);
                int h = (int) Math.round(b.h);
                g2.setColor(b.root ? new Color(230, 240, 255) : new Color(245, 245, 245));
                g2.fillRoundRect(x, y, w, h, 8, 8);
                g2.setColor(b.root ? new Color(60, 100, 160) : new Color(80, 80, 80));
                g2.drawRoundRect(x, y, w, h, 8, 8);
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (w - fm.stringWidth(b.label)) / 2;
                int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(b.label, tx, ty);
            }
        } finally {
            g2.dispose();
        }
    }

    private static void drawArrow(Graphics2D g2, double x1, double y1, double x2, double y2) {
        g2.draw(new Line2D.Double(x1, y1, x2, y2));
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double len = 8;
        double a1 = angle - Math.PI / 7;
        double a2 = angle + Math.PI / 7;
        g2.draw(new Line2D.Double(x2, y2, x2 - len * Math.cos(a1), y2 - len * Math.sin(a1)));
        g2.draw(new Line2D.Double(x2, y2, x2 - len * Math.cos(a2), y2 - len * Math.sin(a2)));
    }

    /** Exposed for tests: number of laid-out boxes after {@link #setTree}. */
    int boxCountForTests() {
        return boxes.size();
    }

    /** Exposed for tests: number of parent→child edges. */
    int edgeCountForTests() {
        return edges.size();
    }

    private static final class LayoutNode {
        final String label;
        final boolean root;
        final List<LayoutNode> children = new ArrayList<>();
        double x;
        double y;
        double boxW;
        double boxH;
        double subtreeWidth;

        LayoutNode(TagNode ignored, String label, boolean root) {
            this.label = label;
            this.root = root;
        }
    }

    private static final class Box {
        final double x;
        final double y;
        final double w;
        final double h;
        final String label;
        final boolean root;

        Box(double x, double y, double w, double h, String label, boolean root) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.root = root;
        }
    }

    private static final class Edge {
        final double x1;
        final double y1;
        final double x2;
        final double y2;

        Edge(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }
}
