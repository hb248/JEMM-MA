package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.tagteam.model.AssignKind;
import com.lariflix.jemm.tagteam.model.RequireMode;
import com.lariflix.jemm.tagteam.model.TagAssign;
import com.lariflix.jemm.tagteam.model.TagMap;
import com.lariflix.jemm.tagteam.model.TagNode;
import com.lariflix.jemm.tagteam.model.TagRequire;
import com.lariflix.jemm.tagteam.model.TagRequires;
import com.lariflix.jemm.tagteam.model.TagTree;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TagRequireRoundTripTest {

    @Test
    public void loaderPreservesRequiresGroup() throws Exception {
        TagNode duo = new TagNode();
        duo.setLabel("Duo");
        duo.getAssign().add(new TagAssign(AssignKind.TAG, "duo"));

        TagTree type = new TagTree();
        type.setName("Type");
        type.setOrder(1);
        type.setChildren(new ArrayList<>(Arrays.asList(duo)));

        TagNode gated = new TagNode();
        gated.setLabel("Duo Mood");
        gated.setRequires(new TagRequires(RequireMode.ANY, new ArrayList<>(Arrays.asList(
                new TagRequire("Type", "Duo"),
                new TagRequire("Setting", "Indoor")))));
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
        TagRequires round = again.getTrees().get(1).getChildren().get(0).getRequires();
        assertNotNull(round);
        assertEquals(RequireMode.ANY, round.getMode());
        assertEquals(2, round.getItems().size());
        assertEquals("Type", round.getItems().get(0).getTree());
        assertEquals("Duo", round.getItems().get(0).getLabel());
    }

    @Test
    public void loaderAcceptsLegacySingleRequireObject() throws Exception {
        String json = "{\n"
                + "  \"version\": 1,\n"
                + "  \"trees\": [{\n"
                + "    \"name\": \"Mood\", \"order\": 1,\n"
                + "    \"children\": [{\n"
                + "      \"label\": \"Duo vibe\",\n"
                + "      \"requires\": { \"tree\": \"Type\", \"label\": \"Duo\" },\n"
                + "      \"assign\": [{ \"kind\": \"tag\", \"value\": \"duo vibe\" }]\n"
                + "    }]\n"
                + "  }]\n"
                + "}";
        TagRequires req = new TagMapLoader().parse(json).getTrees().get(0).getChildren().get(0).getRequires();
        assertNotNull(req);
        assertEquals(RequireMode.ANY, req.getMode());
        assertEquals(1, req.getItems().size());
        assertEquals("Type", req.getItems().get(0).getTree());
        assertEquals("Duo", req.getItems().get(0).getLabel());
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
