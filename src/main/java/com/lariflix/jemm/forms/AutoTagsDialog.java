package com.lariflix.jemm.forms;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.core.SaveItemMetadataDirect;
import com.lariflix.jemm.dtos.JellyfinFolder;
import com.lariflix.jemm.dtos.JellyfinFolders;
import com.lariflix.jemm.dtos.JellyfinInstanceDetails;
import com.lariflix.jemm.tools.AutoTagRules;
import com.lariflix.jemm.tools.BatchJobResult;
import com.lariflix.jemm.tools.FfprobeLocator;
import com.lariflix.jemm.tools.FfprobeResult;
import com.lariflix.jemm.tools.FfprobeService;
import com.lariflix.jemm.tools.ManagedAutoTags;
import com.lariflix.jemm.tools.MediaInputResolver;
import com.lariflix.jemm.tools.MediaTechInfo;
import com.lariflix.jemm.tools.SelectedItemsCollector;
import com.lariflix.jemm.utils.JellyfinUtilFunctions;
import com.lariflix.jemm.utils.JemmSettingsStore;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
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
    private final JemmSettingsStore settings = new JemmSettingsStore();
    private final JCheckBox ffprobeFallbackBox = new JCheckBox("Use ffprobe as fallback for missing technical data");
    private final JTextField ffprobePathField = new JTextField();
    private final JCheckBox aspectHintFallbackBox = new JCheckBox(
            "If ffprobe is unavailable, use poster/aspect-ratio hint for orientation only");

    private int skippedNoResolution;
    private int skippedNoRules;
    private int skippedUnchanged;
    private int probeFilled;
    private int probeFailed;
    private boolean ffprobeAvailable;
    private String firstSkipExample;
    private String firstError;
    private String firstProbeError;

    public AutoTagsDialog(Frame owner, ConnectJellyfinAPI api, JellyfinInstanceDetails instance, int[] selectedFolderIndexes) {
        super(owner, "JEMM - Auto Tags", true);
        this.api = api;
        this.selectedFolderIds = resolveFolderIds(instance, selectedFolderIndexes);
        setSize(560, 280);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        progressBar.setStringPainted(true);
        add(buildNorthPanel(), BorderLayout.NORTH);
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

    private JPanel buildNorthPanel() {
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        statusLabel.setAlignmentX(0f);
        north.add(statusLabel);

        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("ffprobe fallback"));
        settingsPanel.setAlignmentX(0f);

        ffprobeFallbackBox.setSelected(settings.isUseFfprobeFallback());
        ffprobeFallbackBox.setAlignmentX(0f);
        settingsPanel.add(ffprobeFallbackBox);

        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.setAlignmentX(0f);
        pathRow.add(new JLabel("ffprobe path (leave empty to use PATH):"), BorderLayout.NORTH);
        ffprobePathField.setText(settings.getFfprobePath());
        pathRow.add(ffprobePathField, BorderLayout.CENTER);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> chooseFfprobePath());
        pathRow.add(browse, BorderLayout.EAST);
        settingsPanel.add(pathRow);

        aspectHintFallbackBox.setSelected(settings.isUsePosterAspectFallback());
        aspectHintFallbackBox.setAlignmentX(0f);
        settingsPanel.add(aspectHintFallbackBox);

        north.add(settingsPanel);
        return north;
    }

    private void chooseFfprobePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String current = ffprobePathField.getText();
        if (current != null && !current.isBlank()) {
            File f = new File(current);
            if (f.getParentFile() != null && f.getParentFile().isDirectory()) {
                chooser.setCurrentDirectory(f.getParentFile());
            }
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            ffprobePathField.setText(chooser.getSelectedFile().getAbsolutePath());
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

        // Reset diagnostics so results from a previous run are not shown again.
        skippedNoResolution = 0;
        skippedNoRules = 0;
        skippedUnchanged = 0;
        probeFilled = 0;
        probeFailed = 0;
        ffprobeAvailable = false;
        firstSkipExample = null;
        firstError = null;
        firstProbeError = null;

        final boolean useFfprobe = ffprobeFallbackBox.isSelected();
        final boolean useAspectHint = aspectHintFallbackBox.isSelected();
        final String ffprobePath = ffprobePathField.getText() == null ? "" : ffprobePathField.getText().trim();
        settings.setUseFfprobeFallback(useFfprobe);
        settings.setUsePosterAspectFallback(useAspectHint);
        settings.setFfprobePath(ffprobePath);

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

                FfprobeService ffprobe = null;
                MediaInputResolver inputResolver = null;
                if (useFfprobe) {
                    FfprobeLocator locator = new FfprobeLocator(ffprobePath);
                    ffprobeAvailable = locator.isAvailable();
                    if (ffprobeAvailable) {
                        ffprobe = new FfprobeService(locator.getExecutable());
                        inputResolver = new MediaInputResolver(api.getcBaseURL(), api.getcTokenApi());
                    } else if (firstProbeError == null) {
                        firstProbeError = locator.getVerifyError();
                    }
                }

                int index = 0;
                for (SelectedItemsCollector.CollectedItem item : items) {
                    index++;
                    publish("Item " + index + "/" + items.size());
                    try {
                        MediaTechInfo tech = MediaTechInfo.fromMetadata(item.getMetadata());
                        if (ffprobe != null && tech.needsProbe()) {
                            try {
                                String path = item.getMetadata() == null ? null : item.getMetadata().getPath();
                                String input = inputResolver.resolve(path, item.getItemId());
                                if (input != null) {
                                    FfprobeResult probeResult = ffprobe.probe(input);
                                    if (probeResult != null && probeResult.hasAnyData()) {
                                        tech.merge(probeResult);
                                        probeFilled++;
                                    }
                                }
                            } catch (Exception probeEx) {
                                probeFailed++;
                                if (firstProbeError == null) {
                                    firstProbeError = probeEx.getMessage();
                                }
                            }
                        }
                        List<String> computed = rules.compute(tech);
                        boolean hasDimensions = tech.getWidth() > 0 && tech.getHeight() > 0;
                        if ((computed == null || computed.isEmpty()) && !hasDimensions && useAspectHint) {
                            // Degraded fallback: derive orientation only from the poster/aspect-ratio hint.
                            String hint = aspectHint(item);
                            String orientation = rules.orientationFromAspect(hint);
                            if (orientation != null) {
                                computed = new ArrayList<>();
                                computed.add(orientation);
                            }
                        }
                        if (computed == null || computed.isEmpty()) {
                            result.skipped++;
                            if (!hasDimensions) {
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

            private String aspectHint(SelectedItemsCollector.CollectedItem item) {
                if (item == null || item.getMetadata() == null) {
                    return null;
                }
                String aspect = item.getMetadata().getAspectRatio();
                if ((aspect == null || aspect.isBlank()) && item.getMetadata().getPrimaryImageAspectRatio() > 0) {
                    aspect = String.valueOf(item.getMetadata().getPrimaryImageAspectRatio());
                }
                return aspect;
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
                    if (ffprobeFallbackBox.isSelected()) {
                        msg.append("\n\nffprobe fallback: ")
                                .append(ffprobeAvailable ? "available" : "NOT available");
                        if (ffprobeAvailable) {
                            msg.append("\n- Items filled via ffprobe: ").append(probeFilled);
                            if (probeFailed > 0) {
                                msg.append("\n- ffprobe failures: ").append(probeFailed);
                            }
                        }
                    }
                    if (result.skipped > 0) {
                        msg.append("\n\nSkipped breakdown:");
                        msg.append("\n- No resolution info: ").append(skippedNoResolution);
                        msg.append("\n- Already up to date: ").append(skippedUnchanged);
                        if (skippedNoRules > 0) {
                            msg.append("\n- Had info but no rule matched: ").append(skippedNoRules);
                        }
                        if (skippedNoResolution > 0 && firstSkipExample != null) {
                            msg.append("\n\nExample without resolution info:\n").append(firstSkipExample);
                            msg.append("\n\nTip: those items expose no width/height via Jellyfin and could not"
                                    + " be probed with ffprobe, so orientation/resolution cannot be derived.");
                        }
                    }
                    if (firstProbeError != null) {
                        msg.append("\n\nFirst ffprobe issue:\n").append(firstProbeError);
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
