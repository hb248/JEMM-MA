package com.lariflix.jemm.forms;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.core.SaveItemMetadataDirect;
import com.lariflix.jemm.dtos.JellyfinFolder;
import com.lariflix.jemm.dtos.JellyfinFolders;
import com.lariflix.jemm.dtos.JellyfinInstanceDetails;
import com.lariflix.jemm.tools.BatchJobResult;
import com.lariflix.jemm.tools.MetadataCleanerService;
import com.lariflix.jemm.tools.SelectedItemsCollector;
import com.lariflix.jemm.utils.JellyfinUtilFunctions;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
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
 * Clears selected metadata lists for recursively collected items.
 */
public class MetadataCleanerDialog extends JDialog {

    private final ConnectJellyfinAPI api;
    private final List<String> selectedFolderIds;
    private final JCheckBox tagsBox = new JCheckBox("Tags", true);
    private final JCheckBox peopleBox = new JCheckBox("People", false);
    private final JCheckBox genresBox = new JCheckBox("Genres", false);
    private final JCheckBox studiosBox = new JCheckBox("Studios", false);
    private final JLabel statusLabel = new JLabel("Choose metadata types to clear.");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton startButton = new JButton("Clear selected metadata");

    private String firstError;

    public MetadataCleanerDialog(Frame owner, ConnectJellyfinAPI api, JellyfinInstanceDetails instance, int[] selectedFolderIndexes) {
        super(owner, "JEMM - Metadata Cleaner", true);
        this.api = api;
        this.selectedFolderIds = resolveFolderIds(instance, selectedFolderIndexes);
        setSize(520, 240);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel options = new JPanel(new GridLayout(0, 1));
        options.add(new JLabel("This cannot be undone. Clears lists on all media under selected libraries (recursive)."));
        options.add(tagsBox);
        options.add(peopleBox);
        options.add(genresBox);
        options.add(studiosBox);
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
        boolean tags = tagsBox.isSelected();
        boolean people = peopleBox.isSelected();
        boolean genres = genresBox.isSelected();
        boolean studios = studiosBox.isSelected();
        if (!tags && !people && !genres && !studios) {
            JOptionPane.showMessageDialog(this, "Select at least one metadata type.", "Metadata Cleaner", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Clear the selected metadata types from all media under the selected libraries?\nThis cannot be undone.",
                "Confirm Metadata Cleaner",
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

                MetadataCleanerService cleaner = new MetadataCleanerService();
                SaveItemMetadataDirect saver = new SaveItemMetadataDirect(api.getcBaseURL(), api.getcTokenApi());
                int index = 0;
                for (SelectedItemsCollector.CollectedItem item : items) {
                    index++;
                    publish("Item " + index + "/" + items.size());
                    try {
                        boolean changed = cleaner.clear(item.getMetadata(), tags, people, genres, studios);
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
                    StringBuilder msg = new StringBuilder(result.summary("Metadata Cleaner"));
                    if (result.skipped > 0) {
                        msg.append("\n\nSkipped = nothing to remove (the selected lists were already empty).");
                    }
                    if (result.failed > 0 && firstError != null) {
                        msg.append("\n\nFirst error:\n").append(firstError);
                    }
                    JOptionPane.showMessageDialog(MetadataCleanerDialog.this, msg.toString(), "Metadata Cleaner",
                            result.failed > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MetadataCleanerDialog.this, "Metadata Cleaner failed: " + ex.getMessage(), "Metadata Cleaner", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
