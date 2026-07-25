package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Derives the set of tag/genre values "owned" by a {@link TagMap}, globally and per tree.
 *
 * <p>Ownership drives the overwrite rule of Tag-Team mode: when a tree is walked, the
 * applier removes only the values that tree owns and adds the freshly chosen ones. Values
 * that are not owned by any walked tree (manual tags, {@code ManagedAutoTags}, or values
 * from trees the user skipped) are never touched. Comparison is case-insensitive.</p>
 */
public class TagMapVocabulary {

    private final Set<String> ownedTags = new HashSet<>();
    private final Set<String> ownedGenres = new HashSet<>();
    private final Map<String, Set<String>> tagsByTree = new HashMap<>();
    private final Map<String, Set<String>> genresByTree = new HashMap<>();

    private TagMapVocabulary() {
    }

    /**
     * Builds a vocabulary from the given map.
     */
    public static TagMapVocabulary from(TagMap map) {
        TagMapVocabulary vocab = new TagMapVocabulary();
        if (map == null || map.getTrees() == null) {
            return vocab;
        }
        for (TagTree tree : map.getTrees()) {
            Set<String> treeTags = new LinkedHashSet<>();
            Set<String> treeGenres = new LinkedHashSet<>();
            collect(tree.getChildren(), treeTags, treeGenres);
            vocab.tagsByTree.put(key(tree.getName()), treeTags);
            vocab.genresByTree.put(key(tree.getName()), treeGenres);
            vocab.ownedTags.addAll(treeTags);
            vocab.ownedGenres.addAll(treeGenres);
        }
        return vocab;
    }

    private static void collect(Collection<TagNode> nodes, Set<String> tags, Set<String> genres) {
        if (nodes == null) {
            return;
        }
        for (TagNode node : nodes) {
            if (node.getAssign() != null) {
                for (TagAssign assign : node.getAssign()) {
                    if (assign == null || assign.getValue() == null) {
                        continue;
                    }
                    String value = assign.getValue().trim();
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (assign.getKind() == AssignKind.GENRE) {
                        genres.add(key(value));
                    } else {
                        tags.add(key(value));
                    }
                }
            }
            collect(node.getChildren(), tags, genres);
        }
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /**
     * @return true when any tree in the map assigns the given tag value
     */
    public boolean isOwnedTag(String value) {
        return ownedTags.contains(key(value));
    }

    /**
     * @return true when any tree in the map assigns the given genre value
     */
    public boolean isOwnedGenre(String value) {
        return ownedGenres.contains(key(value));
    }

    /**
     * @return the lower-cased tag values owned by the named tree (empty if unknown)
     */
    public Set<String> ownedTagsForTree(String treeName) {
        Set<String> set = tagsByTree.get(key(treeName));
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    /**
     * @return the lower-cased genre values owned by the named tree (empty if unknown)
     */
    public Set<String> ownedGenresForTree(String treeName) {
        Set<String> set = genresByTree.get(key(treeName));
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    /**
     * @return union of the tag values owned by the given trees (lower-cased)
     */
    public Set<String> ownedTagsForTrees(Collection<String> treeNames) {
        Set<String> result = new HashSet<>();
        if (treeNames != null) {
            for (String name : treeNames) {
                result.addAll(ownedTagsForTree(name));
            }
        }
        return result;
    }

    /**
     * @return union of the genre values owned by the given trees (lower-cased)
     */
    public Set<String> ownedGenresForTrees(Collection<String> treeNames) {
        Set<String> result = new HashSet<>();
        if (treeNames != null) {
            for (String name : treeNames) {
                result.addAll(ownedGenresForTree(name));
            }
        }
        return result;
    }
}
