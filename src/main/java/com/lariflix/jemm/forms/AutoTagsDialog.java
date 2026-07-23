package com.lariflix.jemm.forms;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.core.SaveItemMetadataDirect;
import com.lariflix.jemm.dtos.JellyfinFolder;
import com.lariflix.jemm.dtos.JellyfinFolders;
import com.lariflix.jemm.dtos.JellyfinInstanceDetails;
import com.lariflix.jemm.tools.AutoTagRules;
import com.lariflix.jemm.tools.BatchJobResult;
import com.lariflix.jemm.tools.ManagedAutoTags;
import com.lariflix.jemm.tools.MediaTechInfo;
import com.lariflix.jemm.tools.SelectedItemsCollector;
import com.lariflix.jemm.utils.JellyfinUtilFunctions;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Runs auto-tagging for recursively collected items under selected libraries.
 */
public class AutoTagsDialog extends JDialog {

    private final ConnectJellyfinAPI api;
    private final List<String> selectedFolderIds;
    private final JLabel statusLabel = new JLabel("Ready to compute and apply auto tags.");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton startButton = new JButton("Start");

    private int skippedNoResolution;
    private int skippedNoRules;
    private int skippedUnchanged;
    private String firstSkipExample;
    private String firstError;

    public AutoTagsDialog(Frame owner, ConnectJellyfinAPI api, JellyfinInstanceDetails instance, int[] selectedFolderIndexes) {
        super(owner, "JEMM - Auto Tags", true);
        this.api = api;
        this.selectedFolderIds = resolveFolderIds(instance, selectedFolderIndexes);
        setSize(520, 160);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        progressBar.setStringPainted(true);
        add(statusLabel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);

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
                setProgressMode(items.size());

                AutoTagRules rules = new AutoTagRules();
                SaveItemMetadataDirect saver = new SaveItemMetadataDirect(api.getcBaseURL(), api.getcTokenApi());

                int index = 0;
                for (SelectedItemsCollector.CollectedItem item : items) {
                    index++;
                    publish("Item " + index + "/" + items.size());
                    try {
                        MediaTechInfo tech = MediaTechInfo.fromMetadata(item.getMetadata());
                        String aspect = item.getMetadata() == null ? null : item.getMetadata().getAspectRatio();
                        if ((aspect == null || aspect.isBlank()) && item.getMetadata() != null
                                && item.getMetadata().getPrimaryImageAspectRatio() > 0) {
                            aspect = String.valueOf(item.getMetadata().getPrimaryImageAspectRatio());
                        }
                        List<String> computed = rules.compute(tech, aspect);
                        if (computed == null || computed.isEmpty()) {
                            result.skipped++;
                            boolean hasDimensions = tech.getWidth() > 0 && tech.getHeight() > 0;
                            boolean hasAspect = aspect != null && !aspect.isBlank();
                            if (!hasDimensions && !hasAspect) {
                                skippedNoResolution++;
                                if (firstSkipExample == null) {
                                    firstSkipExample = describe(item);
                                }
                            } else {
                                skippedNoRules++;
                            }
                            continue;
                        }
                        ArrayList<String> synced = ManagedAutoTags.sync(
                                item.getMetadata() == null ? null : item.getMetadata().getTags(),
                                computed);
                        if (synced == null) {
                            result.skipped++;
                            skippedUnchanged++;
                        } else {
                            item.getMetadata().setTags(synced);
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
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        if (!progressBar.isIndeterminate()) {
                            progressBar.setValue(progress);
                        }
                    });
                }
                return result;
            }

            private String describe(SelectedItemsCollector.CollectedItem item) {
                if (item == null || item.getMetadata() == null) {
                    return "(no metadata)";
                }
                String name = item.getMetadata().getName();
                String type = item.getMetadata().getType();
                String mediaType = item.getMetadata().getMediaType();
                boolean hasSources = item.getMetadata().getMediaSources() != null
                        && !item.getMetadata().getMediaSources().isEmpty();
                return (name == null ? item.getItemId() : name)
                        + " [Type=" + type + ", MediaType=" + mediaType
                        + ", MediaSources=" + (hasSources ? "yes" : "none") + "]";
            }

            private void setProgressMode(int total) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setMinimum(0);
                    progressBar.setMaximum(Math.max(1, total));
                    progressBar.setValue(0);
                });
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
                    StringBuilder msg = new StringBuilder(result.summary("Auto Tags"));
                    if (result.skipped > 0) {
                        msg.append("\n\nSkipped breakdown:");
                        msg.append("\n- No resolution/aspect info: ").append(skippedNoResolution);
                        msg.append("\n- Already up to date: ").append(skippedUnchanged);
                        if (skippedNoRules > 0) {
                            msg.append("\n- Had info but no rule matched: ").append(skippedNoRules);
                        }
                        if (skippedNoResolution > 0 && firstSkipExample != null) {
                            msg.append("\n\nExample without resolution info:\n").append(firstSkipExample);
                            msg.append("\n\nTip: those items expose no MediaStreams width/height via Jellyfin,"
                                    + " so orientation/resolution cannot be derived.");
                        }
                    }
                    if (result.failed > 0 && firstError != null) {
                        msg.append("\n\nFirst error:\n").append(firstError);
                    }
                    JOptionPane.showMessageDialog(AutoTagsDialog.this, msg.toString(), "Auto Tags",
                            result.failed > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AutoTagsDialog.this, "Auto Tags failed: " + ex.getMessage(), "Auto Tags", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
