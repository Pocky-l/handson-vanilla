package com.pockyl.handson_vanilla.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BannerPatternItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.pockyl.diegesis.api.client.StationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The loom's book: a page for each pattern the loom offers now, in the loom's own order (its buttons are numbered by it). With a
 * banner and a dye on the loom, a page shows the banner that pattern gives, named as the banner's tooltip names the layer ("Red
 * Bend Sinister"); without them, vanilla's sample (a grey banner with the pattern in white). What goes in: any banner, any dye,
 * and the pattern item when the patterns come from one. A pick weaves at once ({@link LoomBehavior#weave}), Shift the whole stack.
 */
final class LoomBook implements RecipeBook.Source {
    private final LoomBehavior station;
    /** What the pages were last made from: the three stacks on the loom and its list of patterns. */
    private ItemStack madeBanner = ItemStack.EMPTY;
    private ItemStack madeDye = ItemStack.EMPTY;
    private ItemStack madePattern = ItemStack.EMPTY;
    private List<Holder<BannerPattern>> madeFrom = List.of();
    private List<RecipeBook.Page> made = List.of();

    LoomBook(LoomBehavior station) {
        this.station = station;
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        ItemStack banner = station.stack(view, LoomBehavior.BANNER);
        ItemStack dye = station.stack(view, LoomBehavior.DYE);
        ItemStack pattern = station.stack(view, LoomBehavior.PATTERN);
        List<Holder<BannerPattern>> patterns = patterns(view, pattern);
        if (ItemStack.matches(banner, madeBanner) && ItemStack.matches(dye, madeDye) && ItemStack.matches(pattern, madePattern)
                && patterns.equals(madeFrom)) {
            return made;
        }
        boolean real = banner.getItem() instanceof BannerItem && dye.getItem() instanceof DyeItem;
        DyeColor colour = real ? ((DyeItem) dye.getItem()).getDyeColor() : DyeColor.WHITE;
        List<Ingredient> needs = new ArrayList<>();
        needs.add(Ingredient.of(ItemTags.BANNERS));
        needs.add(Ingredient.of(Arrays.stream(DyeColor.values()).map(DyeItem::byColor).toArray(DyeItem[]::new)));
        if (!pattern.isEmpty()) {
            needs.add(Ingredient.of(pattern.getItem()));
        }
        List<Ingredient> ingredients = List.copyOf(needs);
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (Holder<BannerPattern> holder : patterns) {
            ResourceKey<BannerPattern> key = holder.unwrapKey().orElse(null);
            if (key == null) {
                continue;
            }
            ItemStack dish = real ? woven(banner, holder, colour) : sample(holder);
            dish.setHoverName(Component.translatable("block.minecraft.banner." + key.location().toShortLanguageKey() + "."
                    + colour.getName()).withStyle(style -> style.withItalic(false)));
            pages.add(new RecipeBook.Page(key.location(), dish, ingredients, List.of(), null, false));
        }
        madeBanner = banner.copy();
        madeDye = dye.copy();
        madePattern = pattern.copy();
        madeFrom = patterns;
        made = List.copyOf(pages);
        return made;
    }

    /** The patterns the loom offers: its menu's own list once banner and dye lie on it, else the same rule (vanilla's) from the tags. */
    static List<Holder<BannerPattern>> patterns(StationView view, ItemStack pattern) {
        LoomMenu loom = LoomBehavior.loom(view);
        if (loom != null && !loom.getSelectablePatterns().isEmpty()) {
            return loom.getSelectablePatterns();
        }
        var tag = pattern.getItem() instanceof BannerPatternItem item ? item.getBannerPattern() : BannerPatternTags.NO_ITEM_REQUIRED;
        return BuiltInRegistries.BANNER_PATTERN.getTag(tag).map(set -> set.stream().toList()).orElse(List.of());
    }

    /** The banner {@code holder} in {@code colour} on top of what {@code banner} already bears, as the loom's result slot makes it. */
    static ItemStack woven(ItemStack banner, Holder<BannerPattern> holder, DyeColor colour) {
        ItemStack dish = banner.copyWithCount(1);
        CompoundTag held = BlockItem.getBlockEntityData(dish);
        CompoundTag data = held == null ? new CompoundTag() : held.copy();
        ListTag layers = data.contains("Patterns", 9) ? data.getList("Patterns", 10) : new ListTag();
        CompoundTag layer = new CompoundTag();
        layer.putString("Pattern", holder.value().getHashname());
        layer.putInt("Color", colour.getId());
        layers.add(layer);
        data.put("Patterns", layers);
        BlockItem.setBlockEntityData(dish, BlockEntityType.BANNER, data);
        return dish;
    }

    /** Vanilla's sample of a pattern: a grey banner with the pattern in white. */
    private static ItemStack sample(Holder<BannerPattern> holder) {
        ItemStack banner = new ItemStack(Items.GRAY_BANNER);
        return woven(banner, holder, DyeColor.WHITE);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(station.stack(view, LoomBehavior.BANNER), station.stack(view, LoomBehavior.DYE),
                station.stack(view, LoomBehavior.PATTERN));
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        if (page.key() instanceof ResourceLocation pattern) {
            station.weave(view, pattern, Screen.hasShiftDown());
        }
    }

    /** The book opens at the pattern the loom has selected. */
    @Override
    public Object current(StationView view) {
        LoomMenu loom = LoomBehavior.loom(view);
        if (loom == null) {
            return null;
        }
        int index = loom.getSelectedBannerPatternIndex();
        List<Holder<BannerPattern>> list = loom.getSelectablePatterns();
        return index >= 0 && index < list.size() ? list.get(index).unwrapKey().map(ResourceKey::location).orElse(null) : null;
    }

    @Override
    public void hovered(StationView view, RecipeBook.Page page) {
        station.preview(page == null ? null : (ResourceLocation) page.key());
    }

    /** A pattern item on the loom offers one pattern, selected by itself: the right page shows it. */
    @Override
    public boolean followsCurrent() {
        return true;
    }

    /** The loom's patterns are its buttons, numbered by their place: the book keeps that order. */
    @Override
    public boolean sorted() {
        return false;
    }

    /** All, and the patterns woven lately. */
    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        return List.of(tab(null, Items.COMPASS, "all"), tab(RecipeBook.RECENT, Items.CLOCK, "recent"));
    }

    private static RecipeBook.Tab tab(String kind, net.minecraft.world.item.Item icon, String name) {
        return new RecipeBook.Tab(kind, new ItemStack(icon), Component.translatable("handson_vanilla.book.tab." + name));
    }

    @Override
    public String memory() {
        return "loom";
    }

    /** Patterns are picked only here: a player new to the loom must see the book open. */
    @Override
    public boolean openByDefault() {
        return true;
    }

    @Override
    public boolean required() {
        return true;
    }
}
