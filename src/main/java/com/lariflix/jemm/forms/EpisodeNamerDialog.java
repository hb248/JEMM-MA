package com.lariflix.jemm.forms;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.core.SaveItemMetadataDirect;
import com.lariflix.jemm.dtos.JellyfinFolder;
import com.lariflix.jemm.dtos.JellyfinFolders;
import com.lariflix.jemm.dtos.JellyfinInstanceDetails;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.tools.BatchJobResult;
import com.lariflix.jemm.tools.EpisodeNamerService;
import com.lariflix.jemm.tools.SelectedItemsCollector;
import com.lariflix.jemm.utils.JellyfinUtilFunctions;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Assigns sequential episode names to all media under the selected libraries (recursive).
 */
public class EpisodeNamerDialog extends JDialog {

    private final ConnectJellyfinAPI api;
    private final List<String> selectedFolderIds;

    private final JRadioButton useFolderNameRadio = new JRadioButton("Use each folder's name as base title", true);
    private final JRadioButton useCustomPrefixRadio = new JRadioButton("Use this custom base title:");
    private final JTextField customPrefixField = new JTextField(18);
    private final JCheckBox setNameBox = new JCheckBox("Set Name", true);
    private final JCheckBox setOriginalBox = new JCheckBox("Set Original Title & Sort Name", false);
    private final JTextField separatorField = new JTextField(" - EP", 6);
    private final JSpinner minDigitsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 6, 1));

    private final JLabel statusLabel = new JLabel("Numbers items per folder in the order Jellyfin returns them.");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton startButton = new JButton("Assign episode names");

    private String firstError;

    public EpisodeNamerDialog(Frame owner, ConnectJellyfinAPI api, JellyfinInstanceDetails instance, int[] selectedFolderIndexes) {
        super(owner, "JEMM - Episode Namer", true);
        this.api = api;
        this.selectedFolderIds = resolveFolderIds(instance, selectedFolderIndexes);
        setSize(560, 340);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        ButtonGroup baseGroup = new ButtonGroup();
        baseGroup.add(useFolderNameRadio);
        baseGroup.add(useCustomPrefixRadio);

        JPanel prefixRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        prefixRow.add(useCustomPrefixRadio);
        prefixRow.add(customPrefixField);

        JPanel formatRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        formatRow.add(new JLabel("Separator:"));
        formatRow.add(separatorField);
        formatRow.add(new JLabel("Min digits:"));
        formatRow.add(minDigitsSpinner);

        JPanel options = new JPanel(new GridLayout(0, 1, 0, 2));
        options.add(new JLabel("Applies to all media under the selected libraries (including subfolders)."));
        options.add(useFolderNameRadio);
        options.add(prefixRow);
        options.add(setNameBox);
        options.add(setOriginalBox);
        options.add(formatRow);
        add(options, BorderLayout.NORTH);

        progressBar.setStringPainted(true);
        JPanel center = new JPanel(new BorderLayout());
        center.add(statusLabel, BorderLayout.NORTH);
        center.add(progressBar, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        startButton.addActionListener(e -> runJob());
        buttons.add(startButton);
        buttons.add(closeButton);
        add(buttons, BorderLayout.SOUTH);

        customPrefixField.setEnabled(false);
        useFolderNameRadio.addActionListener(e -> customPrefixField.setEnabled(false));
        useCustomPrefixRadio.addActionListener(e -> customPrefixField.setEnabled(true));

        if (selectedFolderIds.isEmpty()) {
            statusLabel.setText("Select one or more libraries first.");
            startButton.setEnabled(false);
        }
    }

    private List<String> resolveFolderIds(JellyfinInstanceDetails instance, int[] indexes) {
        List<String> ids = new ArrayList<>();
        if (instance == null || instance.getFolders() == null || instance.getFolders().getItems() == null || indexes == null) {
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

    private void runJob() {
        final EpisodeNamerService.Config config = new EpisodeNamerService.Config();
        config.useFolderName = useFolderNameRadio.isSelected();
        config.customPrefix = customPrefixField.getText();
        config.setName = setNameBox.isSelected();
        config.setOriginalAndSort = setOriginalBox.isSelected();
        config.separator = separatorField.getText();
        config.minDigits = (Integer) minDigitsSpinner.getValue();

        if (!config.setName && !config.setOriginalAndSort) {
            JOptionPane.showMessageDialog(this, "Select at least one field to set (Name and/or Original Title).",
                    "Episode Namer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (config.useFolderName == false && config.customPrefix.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a custom base title or use the folder name.",
                    "Episode Namer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Assign episode names to all media under the selected libraries?\nThis overwrites the selected name fields.",
                "Confirm Episode Namer",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        startButton.setEnabled(false);
        statusLabel.setIcon(new JellyfinUtilFunctions().getOficialJemmIcon());
        statusLabel.setText("Collecting items...");
        progressBar.setIndeterminate(true);

        SwingWorker<BatchJobResult, String> worker = new SwingWorker<>() {
            @Override
            protected BatchJobResult doInBackground() throws Exception {
                BatchJobResult result = new BatchJobResult();
                SelectedItemsCollector collector = new SelectedItemsCollector(api);
                List<SelectedItemsCollector.CollectedItem> items = collector.collectRecursive(selectedFolderIds);
                result.total = items.size();

                EpisodeNamerService namer = new EpisodeNamerService(api);
                List<JellyfinItemMetadata> toSave = namer.apply(items, config);
                result.skipped = items.size() - toSave.size();

                publish("Saving " + toSave.size() + " items...");
                javax.swing.SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setMinimum(0);
                    progressBar.setMaximum(Math.max(1, toSave.size()));
                    progressBar.setValue(0);
                });

                SaveItemMetadataDirect saver = new SaveItemMetadataDirect(api.getcBaseURL(), api.getcTokenApi());
                int index = 0;
                for (JellyfinItemMetadata metadata : toSave) {
                    index++;
                    publish("Item " + index + "/" + toSave.size());
                    try {
                        saver.postUpdate(metadata);
                        result.updated++;
                    } catch (Exception ex) {
                        result.failed++;
                        if (firstError == null) {
                            firstError = ex.getMessage();
                        }
                    }
                    final int progress = index;
                    javax.swing.SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                }
                return result;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                startButton.setEnabled(true);
                progressBar.setIndeterminate(false);
                try {
                    BatchJobResult result = get();
                    statusLabel.setText("Done.");
                    StringBuilder msg = new StringBuilder(result.summary("Episode Namer"));
                    if (result.skipped > 0) {
                        msg.append("\n\nSkipped = names were already correct.");
                    }
                    if (result.failed > 0 && firstError != null) {
                        msg.append("\n\nFirst error:\n").append(firstError);
                    }
                    JOptionPane.showMessageDialog(EpisodeNamerDialog.this, msg.toString(), "Episode Namer",
                            result.failed > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(EpisodeNamerDialog.this, "Episode Namer failed: " + ex.getMessage(),
                            "Episode Namer", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
