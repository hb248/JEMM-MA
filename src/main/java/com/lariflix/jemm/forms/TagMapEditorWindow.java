package com.lariflix.jemm.forms;

import com.lariflix.jemm.tagteam.TagMapLoader;
import com.lariflix.jemm.tagteam.TagMapStore;
import com.lariflix.jemm.tagteam.editor.TagMapTreeModel;
import com.lariflix.jemm.tagteam.editor.TreeGraphPreviewPanel;
import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagRequire;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Hybrid tag-map editor: outline + detail form + read-only graph preview.
 * Edits a deep copy; Save writes via {@link TagMapStore}.
 */
public class TagMapEditorWindow extends JDialog {

    private final TagMapStore store;
    private final TagMapLoader loader = new TagMapLoader();
    private final TagMapTreeModel treeModel;
    private final JTree outline;
    private final TreeGraphPreviewPanel preview = new TreeGraphPreviewPanel();

    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailHost = new JPanel(detailCards);
    private final JPanel emptyDetail = new JPanel(new BorderLayout());
    private final JPanel treeDetail = new JPanel(new GridBagLayout());
    private final JPanel nodeDetail = new JPanel(new BorderLayout(6, 6));

    private final JTextField treeNameField = new JTextField();
    private final JSpinner treeOrderSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 9999, 1));
    private final JCheckBox treeMultiBox = new JCheckBox("Multi-select (several top-level chips)");

    private final JTextField nodeLabelField = new JTextField();
    private final JCheckBox nodeMultiBox = new JCheckBox("Multi-select (several of this node's children)");
    private final JCheckBox nodeExclusiveBox = new JCheckBox("Exclusive (click ends multi-select)");
    private final JComboBox<RequireChoice> requiresCombo = new JComboBox<>();
    private final AssignTableModel assignModel = new AssignTableModel();
    private final JTable assignTable = new JTable(assignModel);

    private final JButton addTreeBtn = new JButton("Add tree");
    private final JButton addChildBtn = new JButton("Add child");
    private final JButton deleteBtn = new JButton("Delete");
    private final JButton moveUpBtn = new JButton("Move up");
    private final JButton moveDownBtn = new JButton("Move down");
    private final JLabel statusLabel = new JLabel(" ");

    private Object selected;
    private boolean dirty;
    private boolean suppressDetailEvents;
    private boolean saved;
    /** Last committed tree name for retargeting {@code requires} on rename. */
    private String renameAnchorTreeName;
    /** Last committed node label / enclosing tree for retargeting {@code requires}. */
    private String renameAnchorNodeLabel;
    private String renameAnchorNodeTree;

    public TagMapEditorWindow(Window owner) {
        this(owner, new TagMapStore());
    }

    public TagMapEditorWindow(Window owner, TagMapStore store) {
        super(owner instanceof Frame ? (Frame) owner : null, "JEMM - Tag Map Editor",
                owner instanceof Frame ? ModalityType.DOCUMENT_MODAL : ModalityType.APPLICATION_MODAL);
        this.store = store == null ? new TagMapStore() : store;

        TagMap working;
        try {
            working = loader.deepCopy(this.store.loadActive());
        } catch (Exception ex) {
            working = new TagMap();
            JOptionPane.showMessageDialog(owner,
                    "Could not load active tag map (starting empty):\n" + ex.getMessage(),
                    "Tag Map Editor", JOptionPane.WARNING_MESSAGE);
        }
        if (working.getVersion() <= 0) {
            working.setVersion(1);
        }
        this.treeModel = new TagMapTreeModel(working);
        this.outline = new JTree(treeModel);

        setSize(980, 680);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        buildUi();
        wireEvents();
        showEmptyDetail();
        updateToolbar();
        refreshPreview();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                tryClose();
            }
        });
    }

    /**
     * @return true when the user saved at least once before closing
     */
    public boolean wasSaved() {
        return saved;
    }

    private void buildUi() {
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(addTreeBtn);
        north.add(addChildBtn);
        north.add(deleteBtn);
        north.add(moveUpBtn);
        north.add(moveDownBtn);
        add(north, BorderLayout.NORTH);

        outline.setRootVisible(false);
        outline.setShowsRootHandles(true);
        outline.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        outline.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                setText(treeModel.displayLabel(value));
                return this;
            }
        });
        JScrollPane treeScroll = new JScrollPane(outline);
        treeScroll.setPreferredSize(new Dimension(280, 400));
        treeScroll.setBorder(BorderFactory.createTitledBorder("Outline"));

        buildTreeDetail();
        buildNodeDetail();
        emptyDetail.add(new JLabel("Select a tree or node, or add a tree."), BorderLayout.CENTER);
        detailHost.add(emptyDetail, "empty");
        detailHost.add(treeDetail, "tree");
        detailHost.add(nodeDetail, "node");
        detailHost.setBorder(BorderFactory.createTitledBorder("Details"));

        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Preview (selected tree)"));
        previewScroll.setPreferredSize(new Dimension(400, 200));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detailHost, previewScroll);
        rightSplit.setResizeWeight(0.55);
        rightSplit.setContinuousLayout(true);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, rightSplit);
        mainSplit.setResizeWeight(0.32);
        mainSplit.setContinuousLayout(true);
        add(mainSplit, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save");
        JButton saveCloseBtn = new JButton("Save & Close");
        JButton closeBtn = new JButton("Close");
        saveBtn.addActionListener(e -> save(false));
        saveCloseBtn.addActionListener(e -> save(true));
        closeBtn.addActionListener(e -> tryClose());
        buttons.add(saveBtn);
        buttons.add(saveCloseBtn);
        buttons.add(closeBtn);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
    }

    private void buildTreeDetail() {
        treeDetail.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        treeDetail.add(new JLabel("Tree name"), c);
        c.gridx = 1;
        c.weightx = 1;
        treeDetail.add(treeNameField, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        treeDetail.add(new JLabel("Order"), c);
        c.gridx = 1;
        c.weightx = 1;
        treeOrderSpinner.setPreferredSize(new Dimension(80, treeOrderSpinner.getPreferredSize().height));
        treeDetail.add(treeOrderSpinner, c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        treeDetail.add(treeMultiBox, c);
        c.gridy = 3;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        treeDetail.add(new JPanel(), c);
    }

    private void buildNodeDetail() {
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        top.add(new JLabel("Label"), c);
        c.gridx = 1;
        c.weightx = 1;
        top.add(nodeLabelField, c);
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        top.add(nodeMultiBox, c);
        c.gridy = 2;
        top.add(nodeExclusiveBox, c);
        c.gridy = 3;
        c.gridwidth = 1;
        c.weightx = 0;
        top.add(new JLabel("Only if selected"), c);
        c.gridx = 1;
        c.weightx = 1;
        requiresCombo.setToolTipText("Hide this chip unless the chosen node was selected in an earlier tree.");
        top.add(requiresCombo, c);

        assignTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assignTable.setFillsViewportHeight(true);
        JComboBox<AssignKind> kindCombo = new JComboBox<>(AssignKind.values());
        kindCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AssignKind) {
                    setText(((AssignKind) value).name().toLowerCase());
                }
                return this;
            }
        });
        assignTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(kindCombo));
        assignTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        assignTable.getColumnModel().getColumn(1).setPreferredWidth(220);

        JPanel assignButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addAssign = new JButton("Add assign");
        JButton removeAssign = new JButton("Remove assign");
        addAssign.addActionListener(e -> {
            if (selected instanceof TagNode) {
                assignModel.addRow();
                markDirty();
            }
        });
        removeAssign.addActionListener(e -> {
            int row = assignTable.getSelectedRow();
            if (row >= 0) {
                assignModel.removeRow(row);
                markDirty();
            }
        });
        assignButtons.add(addAssign);
        assignButtons.add(removeAssign);

        JPanel assignWrap = new JPanel(new BorderLayout());
        assignWrap.setBorder(BorderFactory.createTitledBorder("Assign (empty = traversal only)"));
        assignWrap.add(new JScrollPane(assignTable), BorderLayout.CENTER);
        assignWrap.add(assignButtons, BorderLayout.SOUTH);

        nodeDetail.add(top, BorderLayout.NORTH);
        nodeDetail.add(assignWrap, BorderLayout.CENTER);
    }

    private void wireEvents() {
        outline.addTreeSelectionListener(e -> onSelectionChanged());

        DocumentListener nameListener = simpleDoc(() -> {
            if (suppressDetailEvents || !(selected instanceof TagTree)) {
                return;
            }
            String newName = treeNameField.getText();
            String oldName = renameAnchorTreeName;
            ((TagTree) selected).setName(newName);
            if (oldName != null && !oldName.equals(newName)) {
                retargetRequiresTree(oldName, newName);
                renameAnchorTreeName = newName;
            }
            treeModel.nodeChanged(selected);
            refreshPreview();
            markDirty();
        });
        treeNameField.getDocument().addDocumentListener(nameListener);
        treeOrderSpinner.addChangeListener(e -> {
            if (suppressDetailEvents || !(selected instanceof TagTree)) {
                return;
            }
            ((TagTree) selected).setOrder(((Number) treeOrderSpinner.getValue()).intValue());
            markDirty();
        });
        treeMultiBox.addActionListener(e -> {
            if (suppressDetailEvents || !(selected instanceof TagTree)) {
                return;
            }
            ((TagTree) selected).setMultiSelect(treeMultiBox.isSelected());
            treeModel.nodeChanged(selected);
            refreshPreview();
            markDirty();
        });

        nodeLabelField.getDocument().addDocumentListener(simpleDoc(() -> {
            if (suppressDetailEvents || !(selected instanceof TagNode)) {
                return;
            }
            String newLabel = nodeLabelField.getText();
            String oldLabel = renameAnchorNodeLabel;
            String treeName = renameAnchorNodeTree;
            ((TagNode) selected).setLabel(newLabel);
            if (oldLabel != null && treeName != null && !oldLabel.equals(newLabel)) {
                retargetRequiresNode(treeName, oldLabel, newLabel);
                renameAnchorNodeLabel = newLabel;
            }
            treeModel.nodeChanged(selected);
            refreshPreview();
            markDirty();
        }));
        nodeMultiBox.addActionListener(e -> {
            if (suppressDetailEvents || !(selected instanceof TagNode)) {
                return;
            }
            ((TagNode) selected).setMultiSelect(nodeMultiBox.isSelected());
            treeModel.nodeChanged(selected);
            refreshPreview();
            markDirty();
        });
        nodeExclusiveBox.addActionListener(e -> {
            if (suppressDetailEvents || !(selected instanceof TagNode)) {
                return;
            }
            ((TagNode) selected).setExclusive(nodeExclusiveBox.isSelected());
            treeModel.nodeChanged(selected);
            refreshPreview();
            markDirty();
        });
        requiresCombo.addActionListener(e -> {
            if (suppressDetailEvents || !(selected instanceof TagNode)) {
                return;
            }
            RequireChoice choice = (RequireChoice) requiresCombo.getSelectedItem();
            TagNode node = (TagNode) selected;
            if (choice == null || choice.require == null) {
                node.setRequires(null);
            } else {
                node.setRequires(new TagRequire(choice.require.getTree(), choice.require.getLabel()));
            }
            treeModel.nodeChanged(selected);
            refreshPreview();
            markDirty();
        });
        assignModel.setChangeListener(() -> {
            if (!suppressDetailEvents && selected instanceof TagNode) {
                treeModel.nodeChanged(selected);
                refreshPreview();
                markDirty();
            }
        });

        addTreeBtn.addActionListener(e -> {
            TagTree tree = treeModel.addTree();
            selectObject(tree);
            markDirty();
            statusLabel.setText("Tree added.");
        });
        addChildBtn.addActionListener(e -> {
            Object parent = selected;
            if (parent == null) {
                return;
            }
            TagNode child = treeModel.addChild(parent);
            if (child != null) {
                selectObject(child);
                markDirty();
                statusLabel.setText("Child node added.");
            }
        });
        deleteBtn.addActionListener(e -> {
            if (selected == null) {
                return;
            }
            Object toRemove = selected;
            Object parent = treeModel.findParent(toRemove);
            if (treeModel.remove(toRemove)) {
                if (parent != null && parent != TagMapTreeModel.ROOT) {
                    selectObject(parent);
                } else {
                    outline.clearSelection();
                    selected = null;
                    showEmptyDetail();
                }
                refreshPreview();
                markDirty();
                updateToolbar();
                statusLabel.setText("Deleted.");
            }
        });
        moveUpBtn.addActionListener(e -> {
            Object node = selected;
            if (treeModel.moveUp(node)) {
                selectObject(node);
                refreshPreview();
                markDirty();
            }
        });
        moveDownBtn.addActionListener(e -> {
            Object node = selected;
            if (treeModel.moveDown(node)) {
                selectObject(node);
                refreshPreview();
                markDirty();
            }
        });
    }

    private void onSelectionChanged() {
        TreePath path = outline.getSelectionPath();
        selected = path == null ? null : path.getLastPathComponent();
        suppressDetailEvents = true;
        try {
            if (selected instanceof TagTree) {
                TagTree t = (TagTree) selected;
                renameAnchorTreeName = t.getName() == null ? "" : t.getName();
                treeNameField.setText(renameAnchorTreeName);
                treeOrderSpinner.setValue(t.getOrder());
                treeMultiBox.setSelected(t.isMultiSelect());
                detailCards.show(detailHost, "tree");
            } else if (selected instanceof TagNode) {
                TagNode n = (TagNode) selected;
                TagTree enclosing = treeModel.enclosingTree(n);
                renameAnchorNodeLabel = n.getLabel() == null ? "" : n.getLabel();
                renameAnchorNodeTree = enclosing == null || enclosing.getName() == null
                        ? "" : enclosing.getName();
                nodeLabelField.setText(renameAnchorNodeLabel);
                nodeMultiBox.setSelected(n.isMultiSelect());
                nodeExclusiveBox.setSelected(n.isExclusive());
                assignModel.bind(n);
                populateRequiresCombo(n, enclosing);
                detailCards.show(detailHost, "node");
                if (n.getRequires() != null && n.getRequires().isSet()
                        && !requirementTargetExists(n.getRequires())) {
                    statusLabel.setText("Warning: requires " + n.getRequires().getTree() + "/"
                            + n.getRequires().getLabel() + " — target not found in map.");
                }
            } else {
                showEmptyDetail();
            }
        } finally {
            suppressDetailEvents = false;
        }
        refreshPreview();
        updateToolbar();
    }

    private void showEmptyDetail() {
        detailCards.show(detailHost, "empty");
    }

    private void selectObject(Object node) {
        if (node == null) {
            return;
        }
        TreePath path = new TreePath(treeModel.pathTo(node));
        outline.setSelectionPath(path);
        outline.scrollPathToVisible(path);
    }

    private void refreshPreview() {
        preview.setTree(treeModel.enclosingTree(selected));
    }

    private void updateToolbar() {
        boolean hasSel = selected != null;
        addChildBtn.setEnabled(hasSel);
        deleteBtn.setEnabled(hasSel);
        moveUpBtn.setEnabled(hasSel);
        moveDownBtn.setEnabled(hasSel);
    }

    private void markDirty() {
        dirty = true;
        setTitle("JEMM - Tag Map Editor *");
    }

    private void clearDirty() {
        dirty = false;
        setTitle("JEMM - Tag Map Editor");
    }

    private boolean save(boolean closeAfter) {
        commitEditors();
        TagMap map = treeModel.getMap();
        treeModel.renumberTreeOrders();
        if (!validateForSave(map)) {
            return false;
        }
        try {
            TagMap validated = loader.validateAndSort(loader.deepCopy(map));
            // Copy validated names/order back into working model display
            store.saveActive(validated);
            TagMap refreshed = loader.deepCopy(validated);
            treeModel.getMap().setVersion(refreshed.getVersion());
            treeModel.getMap().setTrees(refreshed.getTrees());
            selected = null;
            outline.clearSelection();
            treeModel.structureChanged();
            showEmptyDetail();
            refreshPreview();
            updateToolbar();
            saved = true;
            clearDirty();
            statusLabel.setText("Saved to " + store.getFile().getAbsolutePath());
            if (closeAfter) {
                dispose();
            }
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                    "Tag Map Editor", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean validateForSave(TagMap map) {
        if (map.getTrees() == null || map.getTrees().isEmpty()) {
            int reply = JOptionPane.showConfirmDialog(this,
                    "The tag map has no trees. Save an empty map?",
                    "Tag Map Editor", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            return reply == JOptionPane.YES_OPTION;
        }
        for (TagTree tree : map.getTrees()) {
            if (tree.getName() == null || tree.getName().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Every tree must have a name.",
                        "Tag Map Editor", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            if (hasBlankNodeLabel(tree.getChildren())) {
                JOptionPane.showMessageDialog(this, "Every node must have a label.",
                        "Tag Map Editor", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        try {
            loader.validateAndSort(loader.deepCopy(map));
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Tag Map Editor", JOptionPane.WARNING_MESSAGE);
            return false;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Validation failed: " + ex.getMessage(),
                    "Tag Map Editor", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private static boolean hasBlankNodeLabel(java.util.List<TagNode> nodes) {
        if (nodes == null) {
            return false;
        }
        for (TagNode n : nodes) {
            if (n.getLabel() == null || n.getLabel().trim().isEmpty()) {
                return true;
            }
            if (hasBlankNodeLabel(n.getChildren())) {
                return true;
            }
        }
        return false;
    }

    private void commitEditors() {
        if (assignTable.isEditing()) {
            assignTable.getCellEditor().stopCellEditing();
        }
    }

    private void tryClose() {
        commitEditors();
        if (dirty) {
            int reply = JOptionPane.showConfirmDialog(this,
                    "Save changes before closing?",
                    "Tag Map Editor",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (reply == JOptionPane.CANCEL_OPTION || reply == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (reply == JOptionPane.YES_OPTION) {
                if (!save(true)) {
                    return;
                }
                return;
            }
        }
        dispose();
    }

    private static DocumentListener simpleDoc(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    private void populateRequiresCombo(TagNode current, TagTree enclosing) {
        requiresCombo.removeAllItems();
        requiresCombo.addItem(RequireChoice.none());
        int maxOrder = enclosing == null ? Integer.MAX_VALUE : enclosing.getOrder();
        List<TagNode> excluded = new ArrayList<>();
        excluded.add(current);
        collectDescendants(current, excluded);

        RequireChoice selectedChoice = RequireChoice.none();
        TagRequire currentReq = current.getRequires();
        for (TagTree tree : treeModel.getMap().getTrees()) {
            if (tree == null || tree.getOrder() >= maxOrder) {
                continue;
            }
            String treeName = tree.getName() == null ? "" : tree.getName();
            addRequireChoices(tree.getChildren(), treeName, excluded, currentReq);
        }
        // Ensure current requires stays selectable even if target is in same/later tree or missing.
        if (currentReq != null && currentReq.isSet()) {
            boolean found = false;
            for (int i = 0; i < requiresCombo.getItemCount(); i++) {
                RequireChoice c = requiresCombo.getItemAt(i);
                if (c != null && c.matches(currentReq)) {
                    selectedChoice = c;
                    found = true;
                    break;
                }
            }
            if (!found) {
                RequireChoice orphan = RequireChoice.of(currentReq.getTree(), currentReq.getLabel());
                requiresCombo.addItem(orphan);
                selectedChoice = orphan;
            }
        }
        requiresCombo.setSelectedItem(selectedChoice);
    }

    private void addRequireChoices(List<TagNode> nodes, String treeName, List<TagNode> excluded,
            TagRequire currentReq) {
        if (nodes == null) {
            return;
        }
        for (TagNode n : nodes) {
            if (n == null) {
                continue;
            }
            if (!excluded.contains(n) && n.getLabel() != null && !n.getLabel().isBlank()) {
                RequireChoice choice = RequireChoice.of(treeName, n.getLabel().trim());
                requiresCombo.addItem(choice);
                if (currentReq != null && choice.matches(currentReq)) {
                    // selection applied by caller
                }
            }
            addRequireChoices(n.getChildren(), treeName, excluded, currentReq);
        }
    }

    private static void collectDescendants(TagNode node, List<TagNode> into) {
        if (node == null || node.getChildren() == null) {
            return;
        }
        for (TagNode child : node.getChildren()) {
            into.add(child);
            collectDescendants(child, into);
        }
    }

    private void retargetRequiresTree(String oldTreeName, String newTreeName) {
        if (oldTreeName == null || newTreeName == null) {
            return;
        }
        for (TagTree tree : treeModel.getMap().getTrees()) {
            retargetRequiresInNodes(tree.getChildren(), oldTreeName, null, newTreeName, null);
        }
        treeModel.structureChanged();
    }

    private void retargetRequiresNode(String treeName, String oldLabel, String newLabel) {
        if (treeName == null || oldLabel == null || newLabel == null) {
            return;
        }
        for (TagTree tree : treeModel.getMap().getTrees()) {
            retargetRequiresInNodes(tree.getChildren(), treeName, oldLabel, treeName, newLabel);
        }
        treeModel.structureChanged();
    }

    private void retargetRequiresInNodes(List<TagNode> nodes, String matchTree, String matchLabel,
            String newTree, String newLabel) {
        if (nodes == null) {
            return;
        }
        for (TagNode n : nodes) {
            TagRequire req = n.getRequires();
            if (req != null && req.isSet()
                    && req.getTree().equalsIgnoreCase(matchTree)
                    && (matchLabel == null || req.getLabel().equalsIgnoreCase(matchLabel))) {
                req.setTree(newTree);
                if (newLabel != null) {
                    req.setLabel(newLabel);
                }
            }
            retargetRequiresInNodes(n.getChildren(), matchTree, matchLabel, newTree, newLabel);
        }
    }

    private boolean requirementTargetExists(TagRequire req) {
        if (req == null || !req.isSet()) {
            return true;
        }
        for (TagTree tree : treeModel.getMap().getTrees()) {
            if (tree.getName() != null && tree.getName().equalsIgnoreCase(req.getTree())
                    && labelExistsIn(tree.getChildren(), req.getLabel())) {
                return true;
            }
        }
        return false;
    }

    private static boolean labelExistsIn(List<TagNode> nodes, String label) {
        if (nodes == null || label == null) {
            return false;
        }
        for (TagNode n : nodes) {
            if (n.getLabel() != null && n.getLabel().equalsIgnoreCase(label)) {
                return true;
            }
            if (labelExistsIn(n.getChildren(), label)) {
                return true;
            }
        }
        return false;
    }

    private static final class RequireChoice {
        private final TagRequire require;
        private final String display;

        private RequireChoice(TagRequire require, String display) {
            this.require = require;
            this.display = display;
        }

        static RequireChoice none() {
            return new RequireChoice(null, "(none)");
        }

        static RequireChoice of(String tree, String label) {
            return new RequireChoice(new TagRequire(tree, label), tree + " / " + label);
        }

        boolean matches(TagRequire req) {
            return require != null && req != null && require.isSet() && req.isSet()
                    && require.getTree().equalsIgnoreCase(req.getTree())
                    && require.getLabel().equalsIgnoreCase(req.getLabel());
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private static final class AssignTableModel extends AbstractTableModel {
        private TagNode node;
        private Runnable changeListener;

        void setChangeListener(Runnable changeListener) {
            this.changeListener = changeListener;
        }

        void bind(TagNode node) {
            this.node = node;
            if (this.node.getAssign() == null) {
                this.node.setAssign(new ArrayList<>());
            }
            fireTableDataChanged();
        }

        void addRow() {
            if (node == null) {
                return;
            }
            node.getAssign().add(new TagAssign(AssignKind.TAG, ""));
            fireTableRowsInserted(node.getAssign().size() - 1, node.getAssign().size() - 1);
            notifyChange();
        }

        void removeRow(int row) {
            if (node == null || row < 0 || row >= node.getAssign().size()) {
                return;
            }
            node.getAssign().remove(row);
            fireTableRowsDeleted(row, row);
            notifyChange();
        }

        @Override
        public int getRowCount() {
            return node == null || node.getAssign() == null ? 0 : node.getAssign().size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Kind" : "Value";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? AssignKind.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TagAssign a = node.getAssign().get(rowIndex);
            if (columnIndex == 0) {
                return a.getKind() == null ? AssignKind.TAG : a.getKind();
            }
            return a.getValue() == null ? "" : a.getValue();
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            TagAssign a = node.getAssign().get(rowIndex);
            if (columnIndex == 0) {
                if (aValue instanceof AssignKind) {
                    a.setKind((AssignKind) aValue);
                } else if (aValue != null) {
                    try {
                        a.setKind(AssignKind.valueOf(aValue.toString().trim().toUpperCase()));
                    } catch (Exception ignore) {
                        a.setKind(AssignKind.TAG);
                    }
                }
            } else {
                a.setValue(aValue == null ? "" : aValue.toString());
            }
            fireTableCellUpdated(rowIndex, columnIndex);
            notifyChange();
        }

        private void notifyChange() {
            if (changeListener != null) {
                changeListener.run();
            }
        }
    }
}
