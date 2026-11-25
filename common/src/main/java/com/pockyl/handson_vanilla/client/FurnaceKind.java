package com.pockyl.handson_vanilla.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.util.function.BooleanSupplier;

/**
 * What sets the furnaces worked by hand apart: the block, its menu and recipes, the station's switch, the door leaves of its scene
 * and how they sound, the grate that heats as the cooking goes. Everything else (slots, the fire's numbers, what flies in) is the
 * same station,
 * {@link FurnaceBehavior}. The models and scenes are named after {@link #id()}: {@code diegesis/<id>_body(_on)} and
 * {@code diegesis/scenes/<id>.json}; the world model replaces {@code minecraft:block/<id>(_on)}.
 */
public enum FurnaceKind {
    /** Two pairs of grille doors (mouth and firebox) that swing open along the front. */
    FURNACE("furnace", Blocks.FURNACE, MenuType.FURNACE, RecipeType.SMELTING, VanillaConfig::furnace,
            new Leaf[] {Leaf.side("mouth_door_right", 170.0F), Leaf.side("mouth_door_left", -170.0F),
                    Leaf.side("firebox_door_right", 170.0F), Leaf.side("firebox_door_left", -170.0F)},
            SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, 1.2F, new Grate("furnace_grate_hot", 9.0F, 3.0F, 13.0F, 0.0F, 6.0F)),
    /** One iron-framed door over the smoking chamber, hinged on the viewer's left, swung out towards them like an oven's. */
    SMOKER("smoker", Blocks.SMOKER, MenuType.SMOKER, RecipeType.SMOKING, VanillaConfig::smoker,
            new Leaf[] {Leaf.side("door", -115.0F)},
            SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_DOOR_CLOSE, 1.35F, new Grate("smoker_rack_hot", 8.0F, 3.0F, 13.0F, 0.0F, 6.0F)),
    /**
     * The iron hatch of vanilla's front, hinged at its foot, let down like an oven's door: lifted, it would turn its underside to
     * the camera, which the game shades dark.
     */
    BLAST_FURNACE("blast_furnace", Blocks.BLAST_FURNACE, MenuType.BLAST_FURNACE, RecipeType.BLASTING, VanillaConfig::blastFurnace,
            new Leaf[] {Leaf.foot("hatch", -95.0F)},
            SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.85F,
            new Grate("furnace_grate_hot", 7.0F, 4.0F, 13.0F, 1.0F, 6.0F));

    private final String id;
    private final Block block;
    private final MenuType<?> menu;
    private final RecipeType<? extends AbstractCookingRecipe> recipes;
    private final BooleanSupplier on;
    private final Leaf[] leaves;
    private final SoundEvent open;
    private final SoundEvent close;
    private final float pitch;
    private final Grate grate;

    FurnaceKind(String id, Block block, MenuType<?> menu, RecipeType<? extends AbstractCookingRecipe> recipes, BooleanSupplier on,
            Leaf[] leaves, SoundEvent open, SoundEvent close, float pitch, Grate grate) {
        this.id = id;
        this.block = block;
        this.menu = menu;
        this.recipes = recipes;
        this.on = on;
        this.leaves = leaves;
        this.open = open;
        this.close = close;
        this.pitch = pitch;
        this.grate = grate;
    }

    /**
     * The grate (the smoker's rack) things cook on, in the block model's pixels (front at z 0): its top, the part of it seen in the
     * hollow from x {@code left} to {@code right} and z {@code front} to {@code back}, and the sprite of its bars red-hot, laid out
     * as the grate's own texture is on its top (u along x, v along z), with the gaps clear.
     */
    record Grate(ResourceLocation texture, float top, float left, float right, float front, float back) {
        Grate(String texture, float top, float left, float right, float front, float back) {
            this(HandsOnVanilla.id("block/" + texture), top, left, right, front, back);
        }
    }

    /**
     * A door leaf of the scene: its node and how far it swings open, degrees about its hinge (the sign is the way it turns), about
     * the node's x axis (a hatch hinged at its foot) or y axis (a door hinged at its side).
     */
    record Leaf(String node, float openX, float openY) {
        static Leaf side(String node, float open) {
            return new Leaf(node, 0.0F, open);
        }

        static Leaf foot(String node, float open) {
            return new Leaf(node, open, 0.0F);
        }
    }

    /** The station's id, and the name of its block, models and scene. */
    public String id() {
        return id;
    }

    public Block block() {
        return block;
    }

    MenuType<?> menu() {
        return menu;
    }

    RecipeType<? extends AbstractCookingRecipe> recipes() {
        return recipes;
    }

    /** Whether the station is on: off, the block is vanilla's (or a resource pack's) again and opens its vanilla screen. */
    public boolean on() {
        return on.getAsBoolean();
    }

    Grate grate() {
        return grate;
    }

    Leaf[] leaves() {
        return leaves;
    }

    SoundEvent openSound() {
        return open;
    }

    SoundEvent closeSound() {
        return close;
    }

    /**
     * The doors' pitch: the furnace's small grille leaves sound higher than a trapdoor, the smoker's door a little higher still, the
     * blast furnace's heavy hatch lower.
     */
    float pitch() {
        return pitch;
    }

    /**
     * The furnace whose model this mod gives the block in {@code state}, or null: only the vanilla block itself (another mod's furnace
     * that extends it, like Quark's, keeps its own model and fire) and only while its station is on.
     */
    public static FurnaceKind remodelled(BlockState state) {
        for (FurnaceKind kind : values()) {
            if (state.is(kind.block) && kind.on()) {
                return kind;
            }
        }
        return null;
    }

    /** Whether any furnace station is on, so the built-in pack with their block states and models is needed. */
    public static boolean anyOn() {
        for (FurnaceKind kind : values()) {
            if (kind.on()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a file of the built-in pack ({@code blockstates/smoker.json}, {@code models/block/smoker_on.json}) is served: only those
     * of furnaces whose station is on.
     */
    public static boolean served(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - ".json".length());
        }
        for (FurnaceKind kind : values()) {
            if (name.equals(kind.id) || name.equals(kind.id + "_on")) {
                return kind.on();
            }
        }
        return false;
    }
}
