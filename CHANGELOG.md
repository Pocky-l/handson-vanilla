# Changelog

## [1.2.1]
- With [Crafting Tweaks](https://www.curseforge.com/minecraft/mc-mods/crafting-tweaks), its rotate, balance and clear buttons lie
  as charcoal marks on the far edge of the crafting table (Shift: rotate back, spread, force clear), and its keys work there.
- With [Easy Magic](https://www.curseforge.com/minecraft/mc-mods/easy-magic) the enchanting table is worked by hand too (it used to
  open Easy Magic's screen): the item and lapis stay in the table, and the book's turned-down corner buys new offers.
- With [Easy Anvils](https://www.curseforge.com/minecraft/mc-mods/easy-anvils) the anvil is worked by hand too (it used to open Easy
  Anvils' screen): the pieces stay on the anvil, and a block of iron used on a worn anvil mends it as before.
- With [Quark](https://www.curseforge.com/minecraft/mc-mods/quark) or
  [More Banner Features](https://modrinth.com/mod/more-banner-features), the loom weaves as many layers as they allow.
- The anvil shows the cost of repairs that mods such as [Tax Free Levels](https://modrinth.com/mod/tax-free-levels) allow over
  40 levels, instead of "Too Expensive".

## [1.2.0] - 2026-11-20
- The enchanting table is worked by hand: its book opens over the table with the three offers written in it.
- The enchanting book is drawn anew, with living runes in the glint's colours and drawings on its pages, also in the world.
- A book on a lectern is read on the lectern, two pages at once, and taken by its ribbon.
- The loom is worked by hand: the banner lies on it as cloth and a brush paints the chosen pattern onto it.
- The cartography table is worked by hand: the map lies on it and what it will become hovers over it.
- Empty places at every station show what goes into them, see-through, taking turns when several things fit.
- The recipe books show long names smaller in up to three lines instead of cutting them.
- Each new station can be turned off on its own in `config/handson_vanilla.properties`.
- Fixed: a station used from the edge of reach opened the block's vanilla screen.
- Fixed: with some JEI builds (15.74) JEI was gone: no item list and no recipe pages.
- Fixed: JEI's "+" was missing at the stations with JEI 15.70 and newer.
- Needs [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis) 1.0.0-beta.14.

## [1.1.1]
- JEI's "+" at the crafting table also lays out recipes that are not in your recipe book yet, as at the vanilla crafting table.

## [1.1.0]
- The smoker is worked by hand: its glazed door swings open, the food lies on an iron rack and the fuel before the logs.
- The blast furnace is worked by hand: its iron hatch drops open onto a hearth with a grate and coals.
- Smokers and blast furnaces everywhere show their insides, with flames and smoke while lit.
- The stonecutter is worked by hand: pieces are picked in its recipe book and the stone is fed through the turning saw.
- The smithing table is worked by hand: its hammer comes off the hook and strikes the gear into its upgrade or trim.
- The grindstone is worked by hand on the floor, a wall or the ceiling.
- The brewing stand is worked by hand: the bottles stand on its feet and the ingredient on the rod melts away as it brews.
- The anvil is worked by hand: the name and the cost hang over it on a tag, and the name is typed right there.
- Each new station can be turned off on its own in `config/handson_vanilla.properties`.
- Stations standing side by side are one workshop: the movement keys and the screen edge go to the next one without leaving.
- The count of the stack in the hand is turned to you, as the counts on the stations are.
- Needs [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis) 1.0.0-beta.13; Fabric also needs
  [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api).

## [1.0.0-fabric] - 2026-11-16
Hands-On Vanilla now runs on [Fabric](https://fabricmc.net) for Minecraft 1.20.1, with everything the Forge file of 1.0.0 has.

- Needs [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) and the Fabric file of
  [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis) 1.0.0-beta.11 or newer.
- The config file is the same as on Forge: `config/handson_vanilla.properties`.

## [1.0.0]

The first release: the crafting table and the furnace are worked by hand, right on the block, with no menu screen.

- Use a crafting table and the camera glides to its top: lay items into its grid by hand and take the result that
  hovers over it.
- Use a furnace and its grille doors swing open: what smelts lies on the grate, the fuel on the glowing coals, and a thin
  bar shows how far the smelting is.
- Furnaces everywhere show their open mouth, grate and coals, and what smelts in them behind the grille.
- A green recipe book lies beside the table and does all the vanilla one does: grouped recipes, the switch for what you
  can craft now, new-recipe marks and the see-through recipe in the grid.
- The book finds recipes by name or mod id, turns by the wheel, page keys and clicks, can be moved and resized, and
  keeps its tab, page, size and place.
- Every vanilla click works at both stations: shift-clicks, drags, double clicks, the number keys, Q and the off-hand key.
- Client-side only: works on any server, with or without the mod.
- [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) and [EMI](https://www.curseforge.com/minecraft/mc-mods/emi)
  keys work over the stations' items, and JEI's + lays a recipe into the table.
- Works with [Visual Workbench](https://www.curseforge.com/minecraft/mc-mods/visual-workbench),
  [FastWorkbench](https://www.curseforge.com/minecraft/mc-mods/fastworkbench),
  [Polymorph](https://www.curseforge.com/minecraft/mc-mods/polymorph) and
  [Mouse Tweaks](https://www.curseforge.com/minecraft/mc-mods/mouse-tweaks).
- Resource packs keep their furnace textures but cannot take the open furnace away; each station can be turned off in
  `config/handson_vanilla.properties`.
