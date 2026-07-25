package com.lariflix.jemm.forms;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.dtos.JellyfinCadPeopleItem;
import com.lariflix.jemm.dtos.JellyfinCadPeopleItems;
import com.lariflix.jemm.dtos.JellyfinCadStudioItem;
import com.lariflix.jemm.dtos.JellyfinCadStudioItems;
import com.lariflix.jemm.dtos.JellyfinFolder;
import com.lariflix.jemm.dtos.JellyfinFolders;
import com.lariflix.jemm.dtos.JellyfinInstanceDetails;
import com.lariflix.jemm.tagteam.CatalogIndex;
import com.lariflix.jemm.tagteam.TagMapStore;
import com.lariflix.jemm.tagteam.TagTeamNavigator;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.utils.JemmSettingsStore;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Entry dialog for Tag-Team mode: shows the single active tag map, lets the user
 * open the Tag Map Editor, pick which media types to walk, and start the guided
 * window after preloading stop metadata and people/studios catalogs.
 * JSON Import/Export live under Tools (next to Metadata CSV).
 */
public class TagTeamStartDialog extends JDialog {

    private final Frame owner;
    private final ConnectJellyfinAPI api;
    private final JellyfinInstanceDetails instance;
    private final List<String> selectedFolderIds;
    private final JemmSettingsStore settings = new JemmSettingsStore();
    private final TagMapStore tagMapStore = new TagMapStore();

    private final JLabel mapInfoLabel = new JLabel();
    private final JCheckBox videosBox = new JCheckBox("Videos");
    private final JCheckBox imagesBox = new JCheckBox("Images");
    private final JCheckBox otherBox = new JCheckBox("Other files");
    private final JButton startButton = new JButton("Start");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel(" ");

    private TagMap activeMap;

    public TagTeamStartDialog(Frame owner, ConnectJellyfinAPI api, JellyfinInstanceDetails instance,
            int[] selectedFolderIndexes) {
        super(owner, "JEMM - Tag-Team Mode", true);
        this.owner = owner;
        this.api = api;
        this.instance = instance;
        this.selectedFolderIds = resolveFolderIds(instance, selectedFolderIndexes);

        setSize(560, 360);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        add(buildCenter(), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(4, 4));
        progressBar.setStringPainted(true);
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(progressBar, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        startButton.addActionListener(e -> onStart());
        buttons.add(startButton);
        buttons.add(closeButton);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        videosBox.setSelected(settings.isTagTeamIncludes("videos"));
        imagesBox.setSelected(settings.isTagTeamIncludes("images"));
        otherBox.setSelected(settings.isTagTeamIncludes("other"));

        reloadMapInfo();
        if (selectedFolderIds.isEmpty()) {
            statusLabel.setText("Select one or more libraries in the left list first.");
            startButton.setEnabled(false);
        }
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel intro = new JLabel("<html>Guided, keyboard-fast tagging over the selected libraries "
                + "(folders then files, recursively). Metadata is preloaded so the walk stays snappy; "
                + "changes are saved when you finish.</html>");
        intro.setAlignmentX(0f);
        center.add(intro);

        JPanel mapPanel = new JPanel(new BorderLayout(6, 6));
        mapPanel.setBorder(BorderFactory.createTitledBorder("Tag map (single active map)"));
        mapPanel.setAlignmentX(0f);
        mapPanel.add(mapInfoLabel, BorderLayout.CENTER);
        JPanel mapButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton editButton = new JButton("Edit map...");
        editButton.addActionListener(e -> onEditMap());
        mapButtons.add(editButton);
        mapPanel.add(mapButtons, BorderLayout.SOUTH);
        center.add(mapPanel);

        JPanel typesPanel = new JPanel();
        typesPanel.setLayout(new BoxLayout(typesPanel, BoxLayout.Y_AXIS));
        typesPanel.setBorder(BorderFactory.createTitledBorder("Which files to walk"));
        typesPanel.setAlignmentX(0f);
        typesPanel.add(videosBox);
        typesPanel.add(imagesBox);
        typesPanel.add(otherBox);
        center.add(typesPanel);

        return center;
    }

    private void reloadMapInfo() {
        try {
            activeMap = tagMapStore.loadActive();
        } catch (Exception ex) {
            activeMap = null;
            mapInfoLabel.setText("<html><b>Failed to read tag map:</b> " + ex.getMessage() + "</html>");
            startButton.setEnabled(false);
            return;
        }
        if (activeMap == null || activeMap.isEmpty()) {
            mapInfoLabel.setText("<html>No tag map yet. Use <b>Tools → Tag Map Editor…</b> or "
                    + "<b>Tools → Import Tag Map…</b> "
                    + "(" + tagMapStore.getFile().getAbsolutePath() + ").</html>");
            startButton.setEnabled(false);
        } else {
            mapInfoLabel.setText("<html>Active map: <b>" + activeMap.getTrees().size()
                    + "</b> tree(s). Stored at " + tagMapStore.getFile().getAbsolutePath() + "</html>");
            startButton.setEnabled(!selectedFolderIds.isEmpty());
        }
    }

    private void onEditMap() {
        TagMapEditorWindow editor = new TagMapEditorWindow(owner, tagMapStore);
        editor.setVisible(true);
        reloadMapInfo();
        if (editor.wasSaved()) {
            statusLabel.setText("Tag map updated.");
        }
    }

    private void onStart() {
        if (activeMap == null || activeMap.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Create or import a tag map first (Tools → Tag Map Editor / Import Tag Map).",
                    "Tag-Team Mode", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final boolean includeVideos = videosBox.isSelected();
        final boolean includeImages = imagesBox.isSelected();
        final boolean includeOther = otherBox.isSelected();
        if (!includeVideos && !includeImages && !includeOther) {
            JOptionPane.showMessageDialog(this, "Select at least one file type to walk.",
                    "Tag-Team Mode", JOptionPane.WARNING_MESSAGE);
            return;
        }
        settings.setTagTeamIncludes("videos", includeVideos);
        settings.setTagTeamIncludes("images", includeImages);
        settings.setTagTeamIncludes("other", includeOther);

        startButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        statusLabel.setText("Scanning selected libraries...");

        SwingWorker<SessionBundle, String> worker = new SwingWorker<SessionBundle, String>() {
            @Override
            protected SessionBundle doInBackground() throws Exception {
                TagTeamNavigator navigator = new TagTeamNavigator(api, selectedFolderIds,
                        includeVideos, includeImages, includeOther);
                navigator.build();
                if (navigator.isEmpty()) {
                    return new SessionBundle(navigator, CatalogIndex.from(null, null));
                }

                publish("Loading people & studios catalogs...");
                List<JellyfinCadPeopleItem> people = new ArrayList<>();
                List<JellyfinCadStudioItem> studios = new ArrayList<>();
                try {
                    JellyfinCadPeopleItems p = api.getPeople();
                    if (p != null && p.getItems() != null) {
                        people.addAll(p.getItems());
                    }
                } catch (Exception ignore) {
                    // Catalog is best-effort; suggestions still work without it.
                }
                try {
                    JellyfinCadStudioItems s = api.getStudios();
                    if (s != null && s.getItems() != null) {
                        studios.addAll(s.getItems());
                    }
                } catch (Exception ignore) {
                    // same
                }
                CatalogIndex catalog = CatalogIndex.from(people, studios);

                publish("Loading metadata...");
                navigator.preloadAll((done, total) -> {
                    setProgress(total == 0 ? 100 : (done * 100 / total));
                    publish("Loading " + done + "/" + total + "...");
                });
                return new SessionBundle(navigator, catalog);
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
                if (progressBar.isIndeterminate()) {
                    progressBar.setIndeterminate(false);
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
                try {
                    SessionBundle bundle = get();
                    if (bundle.navigator.isEmpty()) {
                        statusLabel.setText("Nothing to walk with the current filter.");
                        startButton.setEnabled(true);
                        return;
                    }
                    TagTeamModeWindow window = new TagTeamModeWindow(
                            owner, api, instance, bundle.navigator, activeMap, bundle.catalog);
                    dispose();
                    window.setVisible(true);
                } catch (InterruptedException | ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statusLabel.setText("Scan failed: " + cause.getMessage());
                    startButton.setEnabled(true);
                }
            }
        };
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        worker.execute();
    }

    private List<String> resolveFolderIds(JellyfinInstanceDetails instance, int[] indexes) {
        List<String> ids = new ArrayList<>();
        if (instance == null || instance.getFolders() == null
                || instance.getFolders().getItems() == null || indexes == null) {
            return ids;
        }
        JellyfinFolders folders = instance.getFolders();
        for (int index : indexes) {
            if (index >= 0 && index < folders.getItems().size()) {
                JellyfinFolder folder = folders.getItems().get(index);
                if (folder != null && folder.getId() != null) {
                    ids.add(folder.getId());
                }
            }
        }
        return ids;
    }

    private static final class SessionBundle {
        private final TagTeamNavigator navigator;
        private final CatalogIndex catalog;

        private SessionBundle(TagTeamNavigator navigator, CatalogIndex catalog) {
            this.navigator = navigator;
            this.catalog = catalog;
        }
    }
}
