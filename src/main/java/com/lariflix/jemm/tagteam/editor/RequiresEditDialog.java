package com.lariflix.jemm.tagteam.editor;

import com.lariflix.jemm.tagteam.model.RequireMode;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagRequire;
import com.lariflix.jemm.tagteam.model.TagRequires;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

/**
 * Modal editor for a node's {@link TagRequires}: Any/All mode + checkbox list of candidates.
 */
public class RequiresEditDialog extends JDialog {

    private final JRadioButton anyRadio = new JRadioButton("Match any (OR)", true);
    private final JRadioButton allRadio = new JRadioButton("Match all (AND)");
    private final JPanel checksPanel = new JPanel();
    private final Map<String, JCheckBox> boxesByKey = new LinkedHashMap<>();
    private TagRequires result;
    private boolean accepted;

    public RequiresEditDialog(Window owner, TagRequires current, List<Candidate> candidates) {
        super(owner, "Edit requires", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(420, 360);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBorder(BorderFactory.createTitledBorder("Mode"));
        ButtonGroup group = new ButtonGroup();
        group.add(anyRadio);
        group.add(allRadio);
        modePanel.add(anyRadio);
        modePanel.add(allRadio);
        if (current != null && current.getMode() == RequireMode.ALL) {
            allRadio.setSelected(true);
        } else {
            anyRadio.setSelected(true);
        }
        add(modePanel, BorderLayout.NORTH);

        checksPanel.setLayout(new BoxLayout(checksPanel, BoxLayout.Y_AXIS));
        Map<String, Boolean> prechecked = new LinkedHashMap<>();
        if (current != null && current.getItems() != null) {
            for (TagRequire r : current.getItems()) {
                if (r != null && r.isSet()) {
                    prechecked.put(key(r.getTree(), r.getLabel()), true);
                }
            }
        }

        if (candidates != null) {
            for (Candidate c : candidates) {
                if (c == null || c.tree == null || c.label == null) {
                    continue;
                }
                String k = key(c.tree, c.label);
                JCheckBox box = new JCheckBox(c.tree + " / " + c.label);
                box.setSelected(Boolean.TRUE.equals(prechecked.remove(k)));
                boxesByKey.put(k, box);
                checksPanel.add(box);
            }
        }
        // Orphans: current refs not in the candidate list (broken / later-tree refs).
        for (Map.Entry<String, Boolean> orphan : prechecked.entrySet()) {
            String k = orphan.getKey();
            int slash = k.indexOf('\u0001');
            String tree = slash < 0 ? k : k.substring(0, slash);
            String label = slash < 0 ? "" : k.substring(slash + 1);
            JCheckBox box = new JCheckBox(tree + " / " + label + " (missing)");
            box.setSelected(true);
            boxesByKey.put(k, box);
            checksPanel.add(box);
        }
        if (boxesByKey.isEmpty()) {
            checksPanel.add(new JLabel("No earlier-tree nodes available."));
        }

        JScrollPane scroll = new JScrollPane(checksPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Required nodes (earlier trees)"));
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton("Clear all");
        clearBtn.addActionListener(e -> {
            for (JCheckBox box : boxesByKey.values()) {
                box.setSelected(false);
            }
        });
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            result = buildResult();
            accepted = true;
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        buttons.add(clearBtn);
        buttons.add(ok);
        buttons.add(cancel);
        add(buttons, BorderLayout.SOUTH);
    }

    public boolean wasAccepted() {
        return accepted;
    }

    /**
     * @return selected requires, or {@code null} when none selected
     */
    public TagRequires getResult() {
        return result;
    }

    private TagRequires buildResult() {
        List<TagRequire> items = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> e : boxesByKey.entrySet()) {
            if (!e.getValue().isSelected()) {
                continue;
            }
            String k = e.getKey();
            int slash = k.indexOf('\u0001');
            String tree = slash < 0 ? k : k.substring(0, slash);
            String label = slash < 0 ? "" : k.substring(slash + 1);
            TagRequire ref = new TagRequire(tree, label);
            if (ref.isSet()) {
                items.add(ref);
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        return new TagRequires(allRadio.isSelected() ? RequireMode.ALL : RequireMode.ANY, items);
    }

    private static String key(String tree, String label) {
        return (tree == null ? "" : tree.trim()) + '\u0001' + (label == null ? "" : label.trim());
    }

    public static final class Candidate {
        public final String tree;
        public final String label;

        public Candidate(String tree, String label) {
            this.tree = tree;
            this.label = label;
        }
    }
}
