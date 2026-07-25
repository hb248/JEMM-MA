package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagRequire;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TagRequireRoundTripTest {

    @Test
    public void loaderPreservesRequires() throws Exception {
        TagNode duo = new TagNode();
        duo.setLabel("Duo");
        duo.getAssign().add(new TagAssign(AssignKind.TAG, "duo"));

        TagTree type = new TagTree();
        type.setName("Type");
        type.setOrder(1);
        type.setChildren(new ArrayList<>(Arrays.asList(duo)));

        TagNode gated = new TagNode();
        gated.setLabel("Duo Mood");
        gated.setRequires(new TagRequire("Type", "Duo"));
        gated.getAssign().add(new TagAssign(AssignKind.TAG, "duoMood"));

        TagTree mood = new TagTree();
        mood.setName("Mood");
        mood.setOrder(2);
        mood.setChildren(new ArrayList<>(Arrays.asList(gated)));

        TagMap map = new TagMap();
        map.setVersion(1);
        map.setTrees(new ArrayList<>(Arrays.asList(type, mood)));

        TagMapLoader loader = new TagMapLoader();
        TagMap again = loader.parse(loader.toJson(map));
        TagNode round = again.getTrees().get(1).getChildren().get(0);
        assertNotNull(round.getRequires());
        assertEquals("Type", round.getRequires().getTree());
        assertEquals("Duo", round.getRequires().getLabel());
    }

    @Test
    public void loaderPreservesExclusive() throws Exception {
        TagNode none = new TagNode();
        none.setLabel("N/A");
        none.setExclusive(true);
        none.getAssign().add(new TagAssign(AssignKind.TAG, "na"));

        TagTree setting = new TagTree();
        setting.setName("Setting");
        setting.setOrder(1);
        setting.setMultiSelect(true);
        setting.setChildren(new ArrayList<>(Arrays.asList(none)));

        TagMap map = new TagMap();
        map.setVersion(1);
        map.setTrees(new ArrayList<>(Arrays.asList(setting)));

        TagMapLoader loader = new TagMapLoader();
        TagMap again = loader.parse(loader.toJson(map));
        assertTrue(again.getTrees().get(0).getChildren().get(0).isExclusive());
    }
}
