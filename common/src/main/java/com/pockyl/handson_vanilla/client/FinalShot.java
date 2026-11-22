package com.pockyl.handson_vanilla.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import com.pockyl.diegesis.api.client.StationView;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where a point of a station will be on the screen once the camera has arrived, while it is still flying in: the scene file's shot
 * (position, target, field of view, widened for a window narrower than 16:9 as Diegesis widens it) projected by hand. So what is
 * laid out beside the station (a recipe viewer's list) can take its place at the click, not only once the camera is there.
 */
final class FinalShot {
    /** The narrowest window shape that sees the whole width of a shot, as Diegesis keeps it by default. */
    private static final double KEEP_WIDTH = 16.0 / 9.0;
    private static final Map<Identifier, Optional<Shot>> SHOTS = new HashMap<>();

    private record Shot(Vec3 position, Vec3 target, double fov) {
    }

    private FinalShot() {
    }

    /** The point's place on the screen at the end of the flight, GUI pixels; empty when the shot is unknown or it is behind. */
    static Optional<Vector2f> toScreen(StationView view, float x, float y, float z) {
        Shot shot = SHOTS.computeIfAbsent(view.type().scene(), FinalShot::read).orElse(null);
        if (shot == null) {
            return Optional.empty();
        }
        Vec3 eye = view.toWorld((float) shot.position.x, (float) shot.position.y, (float) shot.position.z);
        Vec3 target = view.toWorld((float) shot.target.x, (float) shot.target.y, (float) shot.target.z);
        Vec3 forward = target.subtract(eye).normalize();
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 up = right.cross(forward);
        Vec3 along = view.toWorld(x, y, z).subtract(eye);
        double depth = along.dot(forward);
        if (depth <= 0.01) {
            return Optional.empty();
        }
        var window = Minecraft.getInstance().getWindow();
        double aspect = window.getHeight() > 0 ? (double) window.getWidth() / window.getHeight() : KEEP_WIDTH;
        double fov = shot.fov;
        if (aspect < KEEP_WIDTH) {
            fov = Math.min(2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(fov) / 2.0) * KEEP_WIDTH / aspect)), 170.0);
        }
        double half = Math.tan(Math.toRadians(fov) / 2.0);
        double ndcX = along.dot(right) / depth / (half * aspect);
        double ndcY = along.dot(up) / depth / half;
        return Optional.of(new Vector2f((float) ((ndcX + 1.0) / 2.0 * window.getGuiScaledWidth()),
                (float) ((1.0 - ndcY) / 2.0 * window.getGuiScaledHeight())));
    }

    /** The shot of the scene file, {@code assets/<namespace>/diegesis/scenes/<path>.json}; empty without a field of view of its own. */
    @SuppressWarnings("removal")
    private static Optional<Shot> read(Identifier scene) {
        Identifier file = Identifier.fromNamespaceAndPath(scene.getNamespace(), "diegesis/scenes/" + scene.getPath() + ".json");
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(file)) {
            JsonObject camera = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("camera");
            if (camera == null || !camera.has("fov") || camera.get("fov").getAsDouble() <= 0.0) {
                return Optional.empty();
            }
            return Optional.of(new Shot(vector(camera.getAsJsonArray("position")), vector(camera.getAsJsonArray("target")),
                    camera.get("fov").getAsDouble()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Vec3 vector(JsonArray values) {
        return new Vec3(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble());
    }
}
