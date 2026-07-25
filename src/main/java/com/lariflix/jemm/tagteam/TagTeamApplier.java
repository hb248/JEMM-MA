package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.core.ConnectJellyfinAPI;
import com.lariflix.jemm.core.LoadItemMetadata;
import com.lariflix.jemm.core.LoadItems;
import com.lariflix.jemm.core.SaveItemMetadataDirect;
import com.lariflix.jemm.dtos.JellyfinGenreItem;
import com.lariflix.jemm.dtos.JellyfinItem;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.dtos.JellyfinItems;
import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.utils.JellyfinParameters;
import com.lariflix.jemm.utils.MetadataListMerge;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.simple.parser.ParseException;

/**
 * Applies Tag-Team selections onto items/folders and saves them.
 *
 * <p>The overwrite rule: only tag/genre values owned by the trees the user actually walked
 * are removed and replaced; manual tags, {@code ManagedAutoTags} values and values owned by
 * skipped trees are preserved. People and studios follow the panel (authoritative for the
 * current stop; merged when propagating a folder base to children). Date/year are set on the
 * current stop and used to fill empty values on propagated children.</p>
 */
public class TagTeamApplier {

    private final ConnectJellyfinAPI api;
    private final SaveItemMetadataDirect saver;
    private final TagMapVocabulary vocabulary;
    private String adminUserId;

    public TagTeamApplier(ConnectJellyfinAPI api, TagMapVocabulary vocabulary) {
        this.api = api;
        this.vocabulary = vocabulary;
        this.saver = api == null ? null
                : new SaveItemMetadataDirect(api.getcBaseURL(), api.getcTokenApi());
    }

    /**
     * Mutates the given metadata with the selection using vocabulary-scoped overwrite for
     * tags/genres. Pure logic (no network) so it can be unit tested.
     *
     * @param meta          the metadata to update
     * @param selection     the user's choices
     * @param authoritative true for the current stop (people/studios replace, date always set);
     *                      false when merging a folder base into children (add-only, fill dates)
     */
    public void applyToMetadata(JellyfinItemMetadata meta, TagTeamSelection selection, boolean authoritative) {
        if (meta == null || selection == null) {
            return;
        }

        // --- Tags: remove owned-by-walked-trees, then add the freshly chosen ones. ---
        Set<String> ownedTags = vocabulary == null
                ? new HashSet<>() : vocabulary.ownedTagsForTrees(selection.getWalkedTreeNames());
        ArrayList<String> tags = meta.getTags() == null ? new ArrayList<>() : new ArrayList<>(meta.getTags());
        tags.removeIf(t -> ownedTags.contains(key(t)));
        ArrayList<String> newTags = new ArrayList<>();
        for (TagAssign assign : selection.getAssigns()) {
            if (assign != null && assign.getKind() == AssignKind.TAG && notBlank(assign.getValue())) {
                newTags.add(assign.getValue().trim());
            }
        }
        meta.setTags(MetadataListMerge.mergeTags(MetadataListMerge.dedupeTags(tags), newTags));

        // --- Genres: same overwrite rule, kept on GenreItems (what the saver posts). ---
        Set<String> ownedGenres = vocabulary == null
                ? new HashSet<>() : vocabulary.ownedGenresForTrees(selection.getWalkedTreeNames());
        ArrayList<JellyfinGenreItem> genreItems = seedGenreItems(meta);
        genreItems.removeIf(g -> g == null || ownedGenres.contains(key(g.getName())));
        ArrayList<JellyfinGenreItem> newGenres = new ArrayList<>();
        for (TagAssign assign : selection.getAssigns()) {
            if (assign != null && assign.getKind() == AssignKind.GENRE && notBlank(assign.getValue())) {
                JellyfinGenreItem g = new JellyfinGenreItem();
                g.setName(assign.getValue().trim());
                newGenres.add(g);
            }
        }
        ArrayList<JellyfinGenreItem> mergedGenres = MetadataListMerge.mergeGenres(
                MetadataListMerge.dedupeGenres(genreItems), newGenres);
        meta.setGenreItems(mergedGenres);
        ArrayList<String> genreNames = new ArrayList<>();
        for (JellyfinGenreItem g : mergedGenres) {
            if (g != null && g.getName() != null) {
                genreNames.add(g.getName());
            }
        }
        meta.setGenres(genreNames);

        // --- People / Studios ---
        if (selection.getPeople() != null) {
            if (authoritative) {
                meta.setPeople(MetadataListMerge.dedupePeople(selection.getPeople()));
            } else {
                meta.setPeople(MetadataListMerge.mergePeople(meta.getPeople(), selection.getPeople()));
            }
        }
        if (selection.getStudios() != null) {
            if (authoritative) {
                meta.setStudios(MetadataListMerge.dedupeStudios(selection.getStudios()));
            } else {
                meta.setStudios(MetadataListMerge.mergeStudios(meta.getStudios(), selection.getStudios()));
            }
        }

        // --- Release date / year ---
        if (selection.getPremiereDate() != null && (authoritative || meta.getPremiereDate() == null)) {
            meta.setPremiereDate(selection.getPremiereDate());
        }
        if (selection.getProductionYear() != null && (authoritative || meta.getProductionYear() <= 0)) {
            meta.setProductionYear(selection.getProductionYear());
        }
    }

    /**
     * Applies the selection to a single already-loaded item and saves it.
     *
     * @return the HTTP status code from the save
     */
    public int applyAndSave(JellyfinItemMetadata meta, TagTeamSelection selection) throws IOException {
        applyToMetadata(meta, selection, true);
        return saver.postUpdate(meta);
    }

    /**
     * Applies a folder stop in memory only: folder authoritatively, direct children as merge,
     * and (when {@code cascade}) all descendants as merge. Marks affected IDs dirty on the navigator.
     *
     * @return number of metadata objects mutated
     */
    public int applyFolderStopInMemory(TagTeamNavigator navigator, String folderId,
            TagTeamSelection selection, boolean cascade) throws IOException, ParseException {
        if (navigator == null || folderId == null) {
            return 0;
        }
        int touched = 0;
        JellyfinItemMetadata folderMeta = navigator.getCachedMetadata(folderId);
        applyToMetadata(folderMeta, selection, true);
        navigator.markDirty(folderId);
        touched++;

        List<String> childIds;
        if (cascade) {
            childIds = navigator.descendantIds(folderId);
        } else {
            // Direct content items only (not nested folder stops).
            childIds = new ArrayList<>();
            for (String id : navigator.directChildIds(folderId)) {
                TagTeamNavigator.Stop child = findStop(navigator, id);
                if (child != null && !child.isFolder()) {
                    childIds.add(id);
                }
            }
        }

        for (String id : childIds) {
            JellyfinItemMetadata meta = navigator.getCachedMetadata(id);
            applyToMetadata(meta, selection, false);
            navigator.markDirty(id);
            touched++;
        }
        return touched;
    }

    private static TagTeamNavigator.Stop findStop(TagTeamNavigator navigator, String id) {
        for (TagTeamNavigator.Stop s : navigator.getStops()) {
            if (id.equals(s.getId())) {
                return s;
            }
        }
        return null;
    }

    /**
     * Applies a folder stop: the folder itself is set authoritatively, its direct content items
     * receive the base as a merge, and (when {@code cascade} is true) nested subfolders and their
     * contents receive the base too.
     *
     * @return number of items successfully saved
     */
    public int applyFolderStop(JellyfinItemMetadata folderMeta, String folderId,
            TagTeamSelection selection, boolean cascade) throws IOException, ParseException {
        int saved = 0;
        applyToMetadata(folderMeta, selection, true);
        saver.postUpdate(folderMeta);
        saved++;
        saved += applyBaseToChildren(folderId, selection, cascade);
        return saved;
    }

    /**
     * POSTs every dirty cached metadata entry.
     *
     * @return number successfully saved
     */
    public int flushDirty(TagTeamNavigator navigator) throws IOException {
        if (navigator == null || saver == null) {
            return 0;
        }
        int saved = 0;
        // Snapshot to avoid concurrent modification if markDirty is called during flush.
        List<String> ids = new ArrayList<>(navigator.getDirtyIds());
        for (String id : ids) {
            JellyfinItemMetadata meta;
            try {
                meta = navigator.getCachedMetadata(id);
            } catch (Exception ex) {
                throw new IOException("Failed to read cached metadata for " + id, ex);
            }
            if (meta == null) {
                continue;
            }
            saver.postUpdate(meta);
            saved++;
        }
        navigator.clearDirty();
        return saved;
    }

    private int applyBaseToChildren(String folderId, TagTeamSelection selection, boolean cascade)
            throws IOException, ParseException {
        int saved = 0;
        // Direct content items -> merge base.
        JellyfinItems items = listChildren(folderId, JellyfinParameters.JUST_ITEMS);
        if (items != null && items.getItems() != null) {
            for (JellyfinItem item : items.getItems()) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                JellyfinItemMetadata meta = loadMetadata(item.getId());
                applyToMetadata(meta, selection, false);
                saver.postUpdate(meta);
                saved++;
            }
        }
        // Nested subfolders (only when cascading) -> merge base into the subfolder and recurse.
        if (cascade) {
            JellyfinItems subs = listChildren(folderId, JellyfinParameters.JUST_SUBFOLDERS);
            if (subs != null && subs.getItems() != null) {
                for (JellyfinItem sub : subs.getItems()) {
                    if (sub == null || sub.getId() == null) {
                        continue;
                    }
                    JellyfinItemMetadata meta = loadMetadata(sub.getId());
                    applyToMetadata(meta, selection, false);
                    saver.postUpdate(meta);
                    saved++;
                    saved += applyBaseToChildren(sub.getId(), selection, true);
                }
            }
        }
        return saved;
    }

    private JellyfinItems listChildren(String folderId, JellyfinParameters type)
            throws IOException, ParseException {
        LoadItems loader = new LoadItems(
                api.getcBaseURL(), api.getcTokenApi(), adminUserId(), folderId, type);
        return loader.requestItems();
    }

    private JellyfinItemMetadata loadMetadata(String id) throws IOException, ParseException {
        LoadItemMetadata loader = new LoadItemMetadata(
                api.getcBaseURL(), api.getcTokenApi(), adminUserId(), id);
        return loader.requestItemMetadata();
    }

    private String adminUserId() throws IOException, ParseException {
        if (adminUserId == null) {
            adminUserId = api.getAdminUser().getId();
        }
        return adminUserId;
    }

    private static ArrayList<JellyfinGenreItem> seedGenreItems(JellyfinItemMetadata meta) {
        if (meta.getGenreItems() != null) {
            return new ArrayList<>(meta.getGenreItems());
        }
        ArrayList<JellyfinGenreItem> seeded = new ArrayList<>();
        if (meta.getGenres() != null) {
            for (String name : meta.getGenres()) {
                if (name != null && !name.trim().isEmpty()) {
                    JellyfinGenreItem g = new JellyfinGenreItem();
                    g.setName(name.trim());
                    seeded.add(g);
                }
            }
        }
        return seeded;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
