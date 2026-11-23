package com.pockyl.handson_vanilla.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Polymorph's choice among recipes at the stations (the client's {@code RecipeChoice}): every station whose block Polymorph lets the
 * player choose at has the four places for the other results in its scene, hidden until there is a choice, each with a box that
 * takes the click.
 */
public final class ChoiceTests {
    /** As many as {@code RecipeChoice.PLACES}. */
    private static final int PLACES = 4;

    private ChoiceTests() {
    }

    public static void everyChoosingStationHasItsPlaces(GameTestHelper helper) {
        for (String scene : List.of("crafting_table", "furnace", "smoker", "blast_furnace", "smithing_table")) {
            Map<String, JsonObject> nodes = new HashMap<>();
            collect(read(scene).getAsJsonArray("nodes"), nodes);
            for (int i = 0; i < PLACES; i++) {
                JsonObject place = nodes.get("choice_" + i);
                helper.assertTrue(place != null, scene + " has the place choice_" + i);
                helper.assertTrue(place.has("hidden") && place.get("hidden").getAsBoolean(), scene + "'s choice_" + i + " starts hidden");
                helper.assertTrue(place.has("collider"), scene + "'s choice_" + i + " takes clicks");
            }
        }
        helper.succeed();
    }

    private static JsonObject read(String scene) {
        String path = "/assets/handson_vanilla/diegesis/scenes/" + scene + ".json";
        try (InputStream in = ChoiceTests.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no scene " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failed) {
            throw new IllegalStateException("cannot read " + path, failed);
        }
    }

    private static void collect(Iterable<JsonElement> list, Map<String, JsonObject> nodes) {
        for (JsonElement element : list) {
            JsonObject node = element.getAsJsonObject();
            nodes.put(node.get("id").getAsString(), node);
            if (node.has("children")) {
                collect(node.getAsJsonArray("children"), nodes);
            }
        }
    }
}
