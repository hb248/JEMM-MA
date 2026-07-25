package com.lariflix.jemm.forms;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.dtos.JellyfinInstanceDetails;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.dtos.JellyfinPeopleItem;
import com.lariflix.jemm.dtos.JellyfinStudioItem;
import com.lariflix.jemm.tagteam.CatalogIndex;
import com.lariflix.jemm.tagteam.DateCandidate;
import com.lariflix.jemm.tagteam.FilenameMetadataParser;
import com.lariflix.jemm.tagteam.FilenameSuggestions;
import com.lariflix.jemm.tagteam.JellyfinWebLink;
import com.lariflix.jemm.tagteam.SuggestionRefiner;
import com.lariflix.jemm.tagteam.TagMapVocabulary;
import com.lariflix.jemm.tagteam.TagTeamApplier;
import com.lariflix.jemm.tagteam.TagTeamNavigator;
import com.lariflix.jemm.tagteam.TagTeamSelection;
import com.lariflix.jemm.tagteam.TitleComposer;
import com.lariflix.jemm.tagteam.TreeWalker;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.utils.JemmSettingsStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/**
 * Guided Tag-Team workflow: walks tag trees with always-on Actors/Studios/Date panels.
 * Metadata is preloaded; applies stay in memory until Finish &amp; Close (or Save on close).
 */
public class TagTeamModeWindow extends JDialog {

    private static final Color SUGGESTION_COLOR = new Color(0x1565C0);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");
    private static final Pattern FULL_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern YEAR_ONLY = Pattern.compile("^(\\d{4})$");

    private final ConnectJellyfinAPI api;
    private final JellyfinInstanceDetails instance;
    private final TagTeamNavigator navigator;
    private final TagMap tagMap;
    private final CatalogIndex catalog;
    private final SuggestionRefiner refiner;
    private final TagTeamApplier applier;
    private final FilenameMetadataParser parser = new FilenameMetadataParser();
    private final JemmSettingsStore settings = new JemmSettingsStore();

    private TagTeamNavigator.Stop currentStop;
    private JellyfinItemMetadata currentMeta;
    private TreeWalker walker;
    private FilenameSuggestions suggestions;
    private String coreTitle = "";
    private String folderNameForTitle = "";
    private final List<JellyfinPeopleItem> actors = new ArrayList<>();
    private final List<JellyfinStudioItem> studios = new ArrayList<>();
    private List<TagNode> currentOptions = new ArrayList<>();
    private final List<AbstractButton> chipButtons = new ArrayList<>();
    private boolean currentMulti;
    private boolean busy;
    private boolean allowClose;

    private final JTextField titleView = new JTextField();
    private final JButton openInJellyfin = new JButton("Open in Jellyfin");
    private final JPanel titleSuggestionWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JButton titleSuggestionButton = new JButton();
    private final JButton folderTitleSuggestionButton = new JButton();
    private final JTextField titleInput = new JTextField();
    private final JLabel headerInfo = new JLabel();

    private final JList<String> stopsList = new JList<>();
    private final DefaultListModel<String> stopsModel = new DefaultListModel<>();
    private final JComboBox<String> personTypeBox = new JComboBox<>(
            new String[]{"Actor", "Director", "Writer", "Producer", "GuestStar"});
    private final JTextField actorInput = new JTextField();
    private final JPanel actorListPanel = new JPanel();
    private final JTextField studioInput = new JTextField();
    private final JPanel studioListPanel = new JPanel();
    private final JTextField dateInput = new JTextField();
    private final JPanel dateCandidatesPanel = new JPanel();
    private final JLabel tagTreeLabel = new JLabel();
    private final JPanel chipsPanel = new JPanel();
    private final JButton confirmMultiButton = new JButton("Confirm selection (Enter)");
    private final JButton skipTreeButton = new JButton("Skip this tree");
    private final JButton skipFileButton = new JButton("Skip file");
    private final JButton skipFolderButton = new JButton("Skip folder");
    private final JButton skipRestButton = new JButton("Skip rest of folder");
    private final JCheckBox cascadeBox = new JCheckBox("Also apply to nested subfolders");
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar flushProgress = new JProgressBar();

    private KeyEventDispatcher keyDispatcher;

    public TagTeamModeWindow(java.awt.Frame owner, ConnectJellyfinAPI api, JellyfinInstanceDetails instance,
            TagTeamNavigator navigator, TagMap tagMap) {
        this(owner, api, instance, navigator, tagMap, CatalogIndex.from(null, null));
    }

    public TagTeamModeWindow(java.awt.Frame owner, ConnectJellyfinAPI api, JellyfinInstanceDetails instance,
            TagTeamNavigator navigator, TagMap tagMap, CatalogIndex catalog) {
        super(owner, "JEMM - Tag-Team Mode", true);
        this.api = api;
        this.instance = instance;
        this.navigator = navigator;
        this.tagMap = tagMap;
        this.catalog = catalog == null ? CatalogIndex.from(null, null) : catalog;
        this.refiner = new SuggestionRefiner(this.catalog);
        this.applier = new TagTeamApplier(api, TagMapVocabulary.from(tagMap));

        setSize(1180, 720);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        cascadeBox.setSelected(settings.isTagTeamCascadeSubfolders());
        buildStopsList();
        installKeyboard();
        installCloseHandler();
        wireLiveCompose();
        loadStop();
    }

    // --- UI construction ----------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(6, 8, 6, 8));

        JPanel row1 = new JPanel(new BorderLayout(6, 0));
        titleView.setEditable(false);
        titleView.setFont(titleView.getFont().deriveFont(Font.BOLD, 15f));
        row1.add(new JLabel("Title: "), BorderLayout.WEST);
        row1.add(titleView, BorderLayout.CENTER);
        openInJellyfin.addActionListener(e -> openCurrentInBrowser());
        row1.add(openInJellyfin, BorderLayout.EAST);
        row1.setAlignmentX(0f);
        header.add(row1);

        JPanel row2 = new JPanel(new BorderLayout(6, 0));
        titleSuggestionWrap.setOpaque(false);
        titleSuggestionWrap.add(new JLabel("Suggested title:"));
        titleSuggestionButton.setForeground(SUGGESTION_COLOR);
        titleSuggestionButton.setBorderPainted(false);
        titleSuggestionButton.setContentAreaFilled(false);
        titleSuggestionButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        titleSuggestionButton.setHorizontalAlignment(SwingConstants.LEFT);
        titleSuggestionButton.addActionListener(e -> titleInput.setText(titleSuggestionButton.getText()));
        titleSuggestionWrap.add(titleSuggestionButton);
        folderTitleSuggestionButton.setForeground(SUGGESTION_COLOR);
        folderTitleSuggestionButton.setBorderPainted(false);
        folderTitleSuggestionButton.setContentAreaFilled(false);
        folderTitleSuggestionButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        folderTitleSuggestionButton.addActionListener(e -> {
            coreTitle = folderTitleSuggestionButton.getClientProperty("core") instanceof String
                    ? (String) folderTitleSuggestionButton.getClientProperty("core")
                    : folderTitleSuggestionButton.getText();
            refreshTitleSuggestion();
            titleInput.setText(titleSuggestionButton.getText());
        });
        titleSuggestionWrap.add(folderTitleSuggestionButton);
        titleSuggestionWrap.setVisible(false);
        row2.add(titleSuggestionWrap, BorderLayout.WEST);
        JPanel setWrap = new JPanel(new BorderLayout(4, 0));
        setWrap.add(new JLabel("Set title: "), BorderLayout.WEST);
        setWrap.add(titleInput, BorderLayout.CENTER);
        row2.add(setWrap, BorderLayout.CENTER);
        row2.setAlignmentX(0f);
        header.add(row2);

        headerInfo.setForeground(Color.GRAY);
        headerInfo.setAlignmentX(0f);
        header.add(headerInfo);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new GridLayout(1, 5, 8, 8));
        body.setBorder(new EmptyBorder(0, 8, 0, 8));
        body.add(buildStopsColumn());
        body.add(buildActorsColumn());
        body.add(buildStudiosColumn());
        body.add(buildDateColumn());
        body.add(buildTagsColumn());
        return body;
    }

    private JPanel buildStopsColumn() {
        JPanel panel = titledColumn("Library");
        stopsList.setModel(stopsModel);
        stopsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stopsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !busy) {
                int idx = stopsList.getSelectedIndex();
                if (idx >= 0 && idx != navigator.getCurrentIndex()) {
                    navigator.jumpTo(idx);
                    loadStop();
                }
            }
        });
        panel.add(new JScrollPane(stopsList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildActorsColumn() {
        JPanel panel = titledColumn("Actors");
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        personTypeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, personTypeBox.getPreferredSize().height));
        personTypeBox.setAlignmentX(0f);
        top.add(personTypeBox);
        actorInput.setAlignmentX(0f);
        actorInput.addActionListener(e -> {
            addActor(actorInput.getText(), (String) personTypeBox.getSelectedItem(), null);
            actorInput.setText("");
            personTypeBox.setSelectedItem("Actor");
        });
        actorInput.getDocument().addDocumentListener(simpleListener(this::rebuildActors));
        top.add(actorInput);
        panel.add(top, BorderLayout.NORTH);
        actorListPanel.setLayout(new BoxLayout(actorListPanel, BoxLayout.Y_AXIS));
        panel.add(new JScrollPane(actorListPanel), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStudiosColumn() {
        JPanel panel = titledColumn("Studios");
        studioInput.addActionListener(e -> {
            addStudio(studioInput.getText(), null);
            studioInput.setText("");
        });
        studioInput.getDocument().addDocumentListener(simpleListener(this::rebuildStudios));
        panel.add(studioInput, BorderLayout.NORTH);
        studioListPanel.setLayout(new BoxLayout(studioListPanel, BoxLayout.Y_AXIS));
        panel.add(new JScrollPane(studioListPanel), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDateColumn() {
        JPanel panel = titledColumn("Release date");
        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("yyyy-MM-dd or yyyy:"), BorderLayout.NORTH);
        top.add(dateInput, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        dateCandidatesPanel.setLayout(new BoxLayout(dateCandidatesPanel, BoxLayout.Y_AXIS));
        panel.add(new JScrollPane(dateCandidatesPanel), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildTagsColumn() {
        JPanel panel = titledColumn("Tags");
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        tagTreeLabel.setAlignmentX(0f);
        tagTreeLabel.setFont(tagTreeLabel.getFont().deriveFont(Font.BOLD));
        top.add(tagTreeLabel);
        panel.add(top, BorderLayout.NORTH);

        chipsPanel.setLayout(new BoxLayout(chipsPanel, BoxLayout.Y_AXIS));
        panel.add(new JScrollPane(chipsPanel), BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        confirmMultiButton.setAlignmentX(0f);
        confirmMultiButton.addActionListener(e -> onConfirmMulti());
        controls.add(confirmMultiButton);
        skipTreeButton.setAlignmentX(0f);
        skipTreeButton.addActionListener(e -> {
            if (walker != null && !walker.isFinished()) {
                walker.skipCurrentTree();
                refreshAfterWalk();
            }
        });
        controls.add(skipTreeButton);
        cascadeBox.setAlignmentX(0f);
        controls.add(cascadeBox);
        JPanel skips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        skipFileButton.addActionListener(e -> skipWithoutApply());
        skipFolderButton.addActionListener(e -> skipWithoutApply());
        skipRestButton.addActionListener(e -> {
            navigator.skipRestOfFolder();
            loadStop();
        });
        skips.add(skipFileButton);
        skips.add(skipFolderButton);
        skips.add(skipRestButton);
        skips.setAlignmentX(0f);
        controls.add(skips);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(4, 8, 6, 8));
        JPanel left = new JPanel(new BorderLayout());
        left.add(statusLabel, BorderLayout.NORTH);
        flushProgress.setStringPainted(true);
        flushProgress.setVisible(false);
        left.add(flushProgress, BorderLayout.SOUTH);
        footer.add(left, BorderLayout.CENTER);
        JButton finishButton = new JButton("Finish & Close");
        finishButton.addActionListener(e -> finishAndClose());
        footer.add(finishButton, BorderLayout.EAST);
        return footer;
    }

    private JPanel titledColumn(String title) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private void buildStopsList() {
        stopsModel.clear();
        for (TagTeamNavigator.Stop stop : navigator.getStops()) {
            String prefix = stop.isFolder() ? "[Folder] " : "    ";
            stopsModel.addElement(prefix + stop.getDisplayName());
        }
    }

    private void wireLiveCompose() {
        dateInput.getDocument().addDocumentListener(simpleListener(this::refreshTitleSuggestion));
        dateInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                refreshTitleSuggestion();
            }
        });
    }

    private DocumentListener simpleListener(Runnable r) {
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

    // --- keyboard / close ---------------------------------------------------

    private void installKeyboard() {
        getRootPane().setDefaultButton(confirmMultiButton);
        keyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || busy) {
                return false;
            }
            Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focus instanceof JTextComponent) {
                return false;
            }
            int code = e.getKeyCode();
            if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) {
                activateChip(code - KeyEvent.VK_1);
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }

    private void installCloseHandler() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (allowClose) {
                    dispose();
                    return;
                }
                if (!navigator.hasDirty()) {
                    allowClose = true;
                    dispose();
                    return;
                }
                Object[] options = {"Save", "Discard", "Cancel"};
                int choice = JOptionPane.showOptionDialog(TagTeamModeWindow.this,
                        "You have unsaved Tag-Team changes. Save before closing?",
                        "Tag-Team Mode",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null, options, options[0]);
                if (choice == 0) {
                    flushThenDispose();
                } else if (choice == 1) {
                    allowClose = true;
                    dispose();
                }
            }
        });
    }

    @Override
    public void dispose() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
        settings.setTagTeamCascadeSubfolders(cascadeBox.isSelected());
        super.dispose();
    }

    // --- stop loading -------------------------------------------------------

    private void loadStop() {
        currentStop = navigator.current();
        if (currentStop == null) {
            finishWalkUi();
            return;
        }
        stopsList.setSelectedIndex(navigator.getCurrentIndex());
        stopsList.ensureIndexIsVisible(navigator.getCurrentIndex());
        try {
            currentMeta = navigator.getCachedMetadata(currentStop.getId());
        } catch (Exception ex) {
            statusLabel.setText("Failed to load item: " + ex.getMessage());
            return;
        }
        populateForCurrent();
    }

    private void populateForCurrent() {
        walker = new TreeWalker(tagMap.getTrees());
        String name = currentMeta.getName() != null ? currentMeta.getName() : currentStop.getDisplayName();
        titleView.setText(name);
        titleView.setCaretPosition(0);
        titleInput.setText("");

        // For folder stops, do not use the folder's own name as a title fallback — that
        // reintroduces "[Studio] Actor" as the core and duplicates studios in the compose.
        folderNameForTitle = currentStop.isFolder() ? "" : navigator.parentFolderName(currentStop);
        String source = currentMeta.getPath() != null && !currentMeta.getPath().isEmpty()
                ? currentMeta.getPath() : name;
        FilenameSuggestions raw = parser.parse(source);
        suggestions = refiner.refine(raw, folderNameForTitle);
        coreTitle = suggestions.getTitle() == null ? "" : suggestions.getTitle();

        actors.clear();
        if (currentMeta.getPeople() != null) {
            actors.addAll(currentMeta.getPeople());
        }
        studios.clear();
        if (currentMeta.getStudios() != null) {
            studios.addAll(currentMeta.getStudios());
        }
        rebuildActors();
        rebuildStudios();

        if (currentMeta.getPremiereDate() != null) {
            dateInput.setText(DATE_FMT.format(currentMeta.getPremiereDate()));
        } else if (currentMeta.getProductionYear() > 0) {
            dateInput.setText(String.valueOf(currentMeta.getProductionYear()));
        } else if (suggestions != null && !suggestions.getDates().isEmpty()) {
            dateInput.setText(suggestions.getDates().get(0).getDisplay());
        } else {
            dateInput.setText("");
        }
        rebuildDateCandidates();
        refreshTitleSuggestion();

        boolean folder = currentStop.isFolder();
        cascadeBox.setVisible(folder);
        skipFolderButton.setEnabled(folder);
        skipFileButton.setEnabled(!folder);

        String kind = folder ? "Folder" : (currentStop.getMediaKind() == null ? "Item"
                : currentStop.getMediaKind().name());
        headerInfo.setText(kind + "  -  stop " + (navigator.getCurrentIndex() + 1) + " of " + navigator.size()
                + (navigator.hasDirty() ? "  (" + navigator.getDirtyIds().size() + " pending save)" : ""));
        openInJellyfin.setEnabled(webLinkForCurrent() != null);

        refreshTagsPanel();
        statusLabel.setText("Ready. Changes stay in memory until Finish & Close.");
    }

    private void refreshTitleSuggestion() {
        // Preview includes filename suggestions not yet clicked into the panels, so a folder
        // like "[Studio]Actor" still suggests "[Studio] Actor" when Studio is only in catalog
        // / suggestions and Actor is not yet on the item.
        String composed = TitleComposer.compose(
                mergeStudiosForTitlePreview(),
                mergeActorsForTitlePreview(),
                coreTitle,
                dateInput.getText());
        boolean hasCompose = composed != null && !composed.isBlank();
        titleSuggestionButton.setText(hasCompose ? composed : "");
        titleSuggestionButton.setVisible(hasCompose);

        boolean showFolderChip = folderNameForTitle != null && !folderNameForTitle.isBlank()
                && !folderNameForTitle.equalsIgnoreCase(coreTitle);
        folderTitleSuggestionButton.setVisible(showFolderChip);
        if (showFolderChip) {
            folderTitleSuggestionButton.setText("Folder: " + folderNameForTitle);
            folderTitleSuggestionButton.putClientProperty("core", folderNameForTitle);
        }

        titleSuggestionWrap.setVisible(hasCompose || showFolderChip);
        titleSuggestionWrap.revalidate();
        titleSuggestionWrap.getParent().revalidate();
    }

    private List<JellyfinStudioItem> mergeStudiosForTitlePreview() {
        List<JellyfinStudioItem> merged = new ArrayList<>(studios);
        if (suggestions == null) {
            return merged;
        }
        for (String name : suggestions.getStudios()) {
            if (name == null || name.isBlank()) {
                continue;
            }
            boolean exists = false;
            for (JellyfinStudioItem s : merged) {
                if (s.getName() != null && s.getName().equalsIgnoreCase(name.trim())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                CatalogIndex.StudioEntry se = catalog.findStudio(name);
                JellyfinStudioItem item = new JellyfinStudioItem();
                item.setName(se != null ? se.getName() : name.trim());
                item.setId(se != null ? se.getId() : null);
                merged.add(item);
            }
        }
        return merged;
    }

    private List<JellyfinPeopleItem> mergeActorsForTitlePreview() {
        List<JellyfinPeopleItem> merged = new ArrayList<>(actors);
        if (suggestions == null) {
            return merged;
        }
        for (String name : suggestions.getActors()) {
            if (name == null || name.isBlank()) {
                continue;
            }
            boolean exists = false;
            for (JellyfinPeopleItem p : merged) {
                if (p.getName() != null && p.getName().equalsIgnoreCase(name.trim())
                        && typesEqual(p.getType(), "Actor")) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                CatalogIndex.PersonEntry pe = catalog.findPerson(name, "Actor");
                JellyfinPeopleItem person = new JellyfinPeopleItem();
                person.setName(pe != null ? pe.getName() : name.trim());
                person.setType("Actor");
                person.setId(pe != null ? pe.getId() : null);
                merged.add(person);
            }
        }
        return merged;
    }

    // --- tag walking --------------------------------------------------------

    private void refreshTagsPanel() {
        chipButtons.clear();
        chipsPanel.removeAll();
        if (walker == null || walker.isFinished()) {
            tagTreeLabel.setText("All trees done - applying in memory...");
            chipsPanel.revalidate();
            chipsPanel.repaint();
            return;
        }
        currentOptions = walker.currentOptions();
        currentMulti = walker.isCurrentMultiSelect();
        tagTreeLabel.setText("<html>Tree " + walker.currentTreeNumber() + "/" + walker.treeCount()
                + ": <b>" + escape(walker.currentTreeName()) + "</b>"
                + (currentMulti ? " (multi-select)" : "") + "</html>");
        int n = 1;
        for (TagNode node : currentOptions) {
            String label = (n <= 9 ? n + ". " : "") + (node.getLabel() == null ? "" : node.getLabel());
            AbstractButton button = currentMulti ? new JToggleButton(label) : new JButton(label);
            button.setAlignmentX(0f);
            final TagNode captured = node;
            if (!currentMulti) {
                button.addActionListener(e -> {
                    walker.selectSingle(captured);
                    refreshAfterWalk();
                });
            }
            chipButtons.add(button);
            chipsPanel.add(button);
            n++;
        }
        confirmMultiButton.setVisible(currentMulti);
        chipsPanel.revalidate();
        chipsPanel.repaint();
    }

    private void onConfirmMulti() {
        if (walker == null || walker.isFinished() || !currentMulti) {
            return;
        }
        List<TagNode> selected = new ArrayList<>();
        for (int i = 0; i < chipButtons.size(); i++) {
            if (chipButtons.get(i).isSelected()) {
                selected.add(currentOptions.get(i));
            }
        }
        walker.confirmMultiSelect(selected);
        refreshAfterWalk();
    }

    private void activateChip(int index) {
        if (index < 0 || index >= chipButtons.size() || walker == null || walker.isFinished()) {
            return;
        }
        if (currentMulti) {
            AbstractButton b = chipButtons.get(index);
            b.setSelected(!b.isSelected());
        } else {
            walker.selectSingle(currentOptions.get(index));
            refreshAfterWalk();
        }
    }

    private void refreshAfterWalk() {
        if (walker != null && walker.isFinished()) {
            applyInMemoryAndAdvance();
        } else {
            refreshTagsPanel();
        }
    }

    // --- apply (in memory) --------------------------------------------------

    private TagTeamSelection buildSelection() {
        TagTeamSelection sel = new TagTeamSelection();
        sel.getAssigns().addAll(walker.getCollected());
        sel.getWalkedTreeNames().addAll(walker.getWalkedTreeNames());
        sel.setPeople(new ArrayList<>(actors));
        sel.setStudios(new ArrayList<>(studios));
        applyDateToSelection(sel);
        return sel;
    }

    private void applyDateToSelection(TagTeamSelection sel) {
        String text = dateInput.getText() == null ? "" : dateInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        Matcher full = FULL_DATE.matcher(text);
        Matcher year = YEAR_ONLY.matcher(text);
        try {
            if (full.matches()) {
                sel.setPremiereDate(DATE_FMT.parse(text));
                sel.setProductionYear(Integer.parseInt(full.group(1)));
            } else if (year.matches()) {
                int y = Integer.parseInt(year.group(1));
                sel.setProductionYear(y);
                sel.setPremiereDate(new DateCandidate(y).toDate());
            }
        } catch (Exception ignore) {
            // Leave date unset when it cannot be parsed.
        }
    }

    private void applyInMemoryAndAdvance() {
        if (currentMeta == null || walker == null) {
            skipWithoutApply();
            return;
        }
        final TagTeamSelection sel = buildSelection();
        final String newTitle = titleInput.getText() == null ? "" : titleInput.getText().trim();
        if (!newTitle.isEmpty()) {
            currentMeta.setName(newTitle);
        }
        try {
            if (currentStop.isFolder()) {
                applier.applyFolderStopInMemory(navigator, currentStop.getId(), sel, cascadeBox.isSelected());
            } else {
                applier.applyToMetadata(currentMeta, sel, true);
                navigator.markDirty(currentStop.getId());
            }
        } catch (Exception ex) {
            statusLabel.setText("Apply failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Apply failed: " + ex.getMessage(),
                    "Tag-Team Mode", JOptionPane.ERROR_MESSAGE);
            return;
        }
        statusLabel.setText("Queued (" + navigator.getDirtyIds().size() + " pending). Moving on...");
        navigator.next();
        loadStop();
    }

    private void skipWithoutApply() {
        navigator.next();
        loadStop();
    }

    private void finishWalkUi() {
        statusLabel.setText("Done walking. Use Finish & Close to save "
                + navigator.getDirtyIds().size() + " pending item(s).");
        titleView.setText("");
        titleInput.setText("");
        titleSuggestionWrap.setVisible(false);
        chipsPanel.removeAll();
        chipButtons.clear();
        tagTreeLabel.setText("Finished");
        chipsPanel.revalidate();
        chipsPanel.repaint();
        setControlsEnabled(false);
    }

    private void finishAndClose() {
        if (!navigator.hasDirty()) {
            allowClose = true;
            dispose();
            return;
        }
        flushThenDispose();
    }

    private void flushThenDispose() {
        setBusy(true);
        flushProgress.setVisible(true);
        flushProgress.setIndeterminate(true);
        statusLabel.setText("Saving " + navigator.getDirtyIds().size() + " item(s)...");
        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return applier.flushDirty(navigator);
            }

            @Override
            protected void done() {
                setBusy(false);
                flushProgress.setIndeterminate(false);
                try {
                    int saved = get();
                    statusLabel.setText("Saved " + saved + " item(s).");
                    allowClose = true;
                    dispose();
                } catch (Exception ex) {
                    flushProgress.setVisible(false);
                    statusLabel.setText("Save failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(TagTeamModeWindow.this,
                            "Save failed: " + ex.getMessage(), "Tag-Team Mode", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // --- actor / studio panels ---------------------------------------------

    private void addActor(String name, String type, String id) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String trimmed = name.trim();
        String useType = type == null || type.isEmpty() ? "Actor" : type;
        // Prefer catalog Id / canonical name.
        CatalogIndex.PersonEntry pe = catalog.findPerson(trimmed, useType);
        if (pe != null) {
            trimmed = pe.getName();
            if (id == null) {
                id = pe.getId();
            }
            if (pe.getType() != null && !pe.getType().isBlank()
                    && (type == null || "Actor".equalsIgnoreCase(type))) {
                // keep requested type from dropdown when user picked one
            }
        }
        for (JellyfinPeopleItem p : actors) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(trimmed)
                    && typesEqual(p.getType(), useType)) {
                return;
            }
        }
        JellyfinPeopleItem person = new JellyfinPeopleItem();
        person.setName(trimmed);
        person.setType(useType);
        person.setId(id);
        actors.add(person);
        rebuildActors();
        refreshTitleSuggestion();
    }

    private void addStudio(String name, String id) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String trimmed = name.trim();
        CatalogIndex.StudioEntry se = catalog.findStudio(trimmed);
        if (se != null) {
            trimmed = se.getName();
            if (id == null) {
                id = se.getId();
            }
        }
        for (JellyfinStudioItem s : studios) {
            if (s.getName() != null && s.getName().equalsIgnoreCase(trimmed)) {
                return;
            }
        }
        JellyfinStudioItem studio = new JellyfinStudioItem();
        studio.setName(trimmed);
        studio.setId(id);
        studios.add(studio);
        rebuildStudios();
        refreshTitleSuggestion();
    }

    private static boolean typesEqual(String a, String b) {
        String aa = a == null || a.isBlank() ? "Actor" : a.trim();
        String bb = b == null || b.isBlank() ? "Actor" : b.trim();
        return aa.equalsIgnoreCase(bb);
    }

    private void rebuildActors() {
        actorListPanel.removeAll();
        for (JellyfinPeopleItem p : actors) {
            final JellyfinPeopleItem captured = p;
            String type = p.getType() == null || p.getType().isBlank() ? "Actor" : p.getType();
            String label = p.getName() + " (" + type + ")";
            actorListPanel.add(entryRow(label, () -> {
                actors.remove(captured);
                rebuildActors();
                refreshTitleSuggestion();
            }));
        }
        if (suggestions != null) {
            for (String s : suggestions.getActors()) {
                if (!actorPresent(s, "Actor")) {
                    final String name = s;
                    CatalogIndex.PersonEntry pe = catalog.findPerson(name, "Actor");
                    String label = pe != null
                            ? pe.getName() + " (" + pe.getType() + ")"
                            : name + " (Actor)";
                    actorListPanel.add(suggestionRow(label, () ->
                            addActor(name, "Actor", pe != null ? pe.getId() : null)));
                }
            }
        }
        String query = actorInput.getText();
        if (query != null && query.trim().length() >= 1) {
            for (CatalogIndex.PersonEntry pe : catalog.suggestPeople(query.trim(), 8)) {
                if (!actorPresent(pe.getName(), pe.getType())) {
                    final CatalogIndex.PersonEntry captured = pe;
                    actorListPanel.add(suggestionRow(
                            pe.getName() + " (" + pe.getType() + ")",
                            () -> {
                                addActor(captured.getName(), captured.getType(), captured.getId());
                                actorInput.setText("");
                                personTypeBox.setSelectedItem("Actor");
                            }));
                }
            }
        }
        actorListPanel.revalidate();
        actorListPanel.repaint();
    }

    private void rebuildStudios() {
        studioListPanel.removeAll();
        for (JellyfinStudioItem s : studios) {
            final JellyfinStudioItem captured = s;
            studioListPanel.add(entryRow(s.getName(), () -> {
                studios.remove(captured);
                rebuildStudios();
                refreshTitleSuggestion();
            }));
        }
        if (suggestions != null) {
            for (String s : suggestions.getStudios()) {
                if (!studioPresent(s)) {
                    final String name = s;
                    CatalogIndex.StudioEntry se = catalog.findStudio(name);
                    studioListPanel.add(suggestionRow(name, () ->
                            addStudio(name, se != null ? se.getId() : null)));
                }
            }
        }
        String query = studioInput.getText();
        if (query != null && query.trim().length() >= 1) {
            for (CatalogIndex.StudioEntry se : catalog.suggestStudios(query.trim(), 8)) {
                if (!studioPresent(se.getName())) {
                    final CatalogIndex.StudioEntry captured = se;
                    studioListPanel.add(suggestionRow(se.getName(), () -> {
                        addStudio(captured.getName(), captured.getId());
                        studioInput.setText("");
                    }));
                }
            }
        }
        studioListPanel.revalidate();
        studioListPanel.repaint();
    }

    private void rebuildDateCandidates() {
        dateCandidatesPanel.removeAll();
        if (suggestions != null) {
            for (DateCandidate c : suggestions.getDates()) {
                final String display = c.getDisplay();
                dateCandidatesPanel.add(suggestionRow(display, () -> {
                    dateInput.setText(display);
                    refreshTitleSuggestion();
                }));
            }
        }
        dateCandidatesPanel.revalidate();
        dateCandidatesPanel.repaint();
    }

    private boolean actorPresent(String name, String type) {
        for (JellyfinPeopleItem p : actors) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(name)
                    && typesEqual(p.getType(), type)) {
                return true;
            }
        }
        return false;
    }

    private boolean studioPresent(String name) {
        for (JellyfinStudioItem s : studios) {
            if (s.getName() != null && s.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private JPanel entryRow(String label, Runnable onRemove) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.add(new JLabel(label), BorderLayout.CENTER);
        JButton remove = new JButton("-");
        remove.setMargin(new java.awt.Insets(0, 6, 0, 6));
        remove.addActionListener(e -> onRemove.run());
        row.add(remove, BorderLayout.EAST);
        return row;
    }

    private JPanel suggestionRow(String label, Runnable onClick) {
        JPanel row = new JPanel(new BorderLayout());
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        JButton button = new JButton(label + "  (+)");
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(SUGGESTION_COLOR);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> onClick.run());
        row.add(button, BorderLayout.CENTER);
        return row;
    }

    // --- misc ---------------------------------------------------------------

    private String webLinkForCurrent() {
        if (currentStop == null) {
            return null;
        }
        String serverId = instance != null && instance.getServerInfo() != null
                ? instance.getServerInfo().getId() : null;
        return JellyfinWebLink.detailsUrl(api.getcBaseURL(), serverId, currentStop.getId());
    }

    private void openCurrentInBrowser() {
        String url = webLinkForCurrent();
        if (url == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ex) {
            statusLabel.setText("Could not open browser: " + ex.getMessage());
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        setCursor(Cursor.getPredefinedCursor(value ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        setControlsEnabled(!value);
    }

    private void setControlsEnabled(boolean enabled) {
        for (AbstractButton b : chipButtons) {
            b.setEnabled(enabled);
        }
        confirmMultiButton.setEnabled(enabled && currentMulti);
        skipTreeButton.setEnabled(enabled);
        skipFileButton.setEnabled(enabled && currentStop != null && !currentStop.isFolder());
        skipFolderButton.setEnabled(enabled && currentStop != null && currentStop.isFolder());
        skipRestButton.setEnabled(enabled);
        actorInput.setEnabled(enabled);
        studioInput.setEnabled(enabled);
        dateInput.setEnabled(enabled);
        titleInput.setEnabled(enabled);
        personTypeBox.setEnabled(enabled);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
