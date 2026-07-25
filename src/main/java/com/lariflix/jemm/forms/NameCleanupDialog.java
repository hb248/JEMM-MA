package com.lariflix.jemm.forms;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.core.SaveItemMetadataDirect;
import com.lariflix.jemm.dtos.JellyfinFolder;
import com.lariflix.jemm.dtos.JellyfinFolders;
import com.lariflix.jemm.dtos.JellyfinInstanceDetails;
import com.lariflix.jemm.tools.BatchJobResult;
import com.lariflix.jemm.tools.NameCleanupService;
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
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Cleans up episode-style names again for all media under the selected libraries (recursive).
 */
public class NameCleanupDialog extends JDialog {

    private final ConnectJellyfinAPI api;
    private final List<String> selectedFolderIds;

    private final JRadioButton removeSuffixRadio = new JRadioButton("Remove trailing \" - EP##\" from Name", true);
    private final JRadioButton resetFromPathRadio = new JRadioButton("Reset Name from the file name (from path)");
    private final JRadioButton keepNameRadio = new JRadioButton("Leave Name unchanged");
    private final JCheckBox clearTitlesBox = new JCheckBox("Clear Original Title & Sort Name", true);

    private final JLabel statusLabel = new JLabel("Reverts the episode naming for the selected libraries.");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton startButton = new JButton("Clean up names");

    private String firstError;

    public NameCleanupDialog(Frame owner, ConnectJellyfinAPI api, JellyfinInstanceDetails instance, int[] selectedFolderIndexes) {
        super(owner, "JEMM - Name Cleanup", true);
        this.api = api;
        this.selectedFolderIds = resolveFolderIds(instance, selectedFolderIndexes);
        setSize(520, 300);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        ButtonGroup nameGroup = new ButtonGroup();
        nameGroup.add(removeSuffixRadio);
        nameGroup.add(resetFromPathRadio);
        nameGroup.add(keepNameRadio);

        JPanel options = new JPanel(new GridLayout(0, 1, 0, 2));
        options.add(new JLabel("This cannot be undone. Affects all media under selected libraries (recursive)."));
        options.add(new JLabel("Name:"));
        options.add(removeSuffixRadio);
        options.add(resetFromPathRadio);
        options.add(keepNameRadio);
        options.add(clearTitlesBox);
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
        final NameCleanupService.Config config = new NameCleanupService.Config();
        if (resetFromPathRadio.isSelected()) {
            config.nameMode = NameCleanupService.NameMode.RESET_FROM_PATH;
        } else if (keepNameRadio.isSelected()) {
            config.nameMode = NameCleanupService.NameMode.KEEP;
        } else {
            config.nameMode = NameCleanupService.NameMode.REMOVE_EP_SUFFIX;
        }
        config.clearOriginalAndSort = clearTitlesBox.isSelected();

        if (config.nameMode == NameCleanupService.NameMode.KEEP && !config.clearOriginalAndSort) {
            JOptionPane.showMessageDialog(this, "Nothing selected to clean up.", "Name Cleanup", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Clean up names for all media under the selected libraries?\nThis cannot be undone.",
                "Confirm Name Cleanup",
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
                publish("Processing " + items.size() + " items...");

                javax.swing.SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setMinimum(0);
                    progressBar.setMaximum(Math.max(1, items.size()));
                    progressBar.setValue(0);
                });

                NameCleanupService cleaner = new NameCleanupService();
                SaveItemMetadataDirect saver = new SaveItemMetadataDirect(api.getcBaseURL(), api.getcTokenApi());
                int index = 0;
                for (SelectedItemsCollector.CollectedItem item : items) {
                    index++;
                    publish("Item " + index + "/" + items.size());
                    try {
                        boolean changed = cleaner.apply(item.getMetadata(), config);
                        if (!changed) {
                            result.skipped++;
                        } else {
                            saver.postUpdate(item.getMetadata());
                            result.updated++;
                        }
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
                    StringBuilder msg = new StringBuilder(result.summary("Name Cleanup"));
                    if (result.skipped > 0) {
                        msg.append("\n\nSkipped = nothing to clean (already clean).");
                    }
                    if (result.failed > 0 && firstError != null) {
                        msg.append("\n\nFirst error:\n").append(firstError);
                    }
                    JOptionPane.showMessageDialog(NameCleanupDialog.this, msg.toString(), "Name Cleanup",
                            result.failed > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(NameCleanupDialog.this, "Name Cleanup failed: " + ex.getMessage(),
                            "Name Cleanup", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
