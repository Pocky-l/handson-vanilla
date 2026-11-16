<p align="center">
  <img src="docs/showcase/title.gif" alt="Hands-On Vanilla" width="834">
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/hands-on-vanilla"><img alt="CurseForge" src="https://img.shields.io/badge/CurseForge-Hands--On%20Vanilla-F16436"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/hands-on-vanilla/files"><img alt="Minecraft 1.20.1" src="https://img.shields.io/badge/Minecraft-1.20.1-62B47A"></a>
  <a href="https://files.minecraftforge.net"><img alt="Forge" src="https://img.shields.io/badge/Loader-Forge-DFA86A"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/hands-on-vanilla"><img alt="Client-side" src="https://img.shields.io/badge/Client--side-any%20server-8a6a4a"></a>
  <a href="https://discord.gg/mGkPKccJEf"><img alt="Discord: Pocky's Kitchen" src="https://img.shields.io/badge/Discord-Pocky's%20Kitchen-5865F2?logo=discord&logoColor=white"></a>
  <a href="LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/License-MIT-blue"></a>
</p>

<p align="center"><i>Craft and smelt with your own hands.</i></p>

<p align="center"><b>The crafting table and the furnace open right where they stand, with no menu screen. You lay the items into the grid yourself, take the result off the table and watch your ore on the furnace's grate. Client-side: it works on any server.</b></p>

<p align="center"><b>No menus</b>, the blocks open in the world &middot; <b>Every recipe</b> of the game and your mods &middot; <b>No new blocks or items</b></p>

Built on [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis), from the maker of
[Hands-On Delight](https://www.curseforge.com/minecraft/mc-mods/hands-on-delight).

## Features

- **Crafting table.** Use it and the camera glides to its top. Pick a stack from your inventory, lay it into the grid
  one by one or all at once, and the result hovers over the table, its count turned to you. Take it with a click, or
  Shift + click to craft as many as the grid allows.
- **Furnace.** Its grille doors swing open: what smelts lies on the grate in its mouth, the result beside it, and the
  fuel on the coals below, glowing while it burns. A thin bar over the grate fills as it cooks. Use a furnace with ore
  or coal in hand and it goes in by itself.
- **Furnaces in the world.** Every furnace shows its open mouth, grate and coals, with flames and smoke inside when it is
  lit, and what smelts in it behind the grille. On a server you see what you last saw in it.
- **Recipe book.** A green book lies on the table's edge. Click it and it opens beside the table with everything the
  vanilla book has: recipes of one kind share a place (right click lays them all out), a switch for what you can craft
  now, red marks on new recipes, and a see-through recipe in the grid when items are missing.
- **Finding a recipe.** Type into the book's search to find recipes by name or by mod id. The mouse wheel, Page Up and
  Page Down or a click on either half of the page turns the pages; hold the click to leaf through. A "Made lately" tab
  keeps the last 24 recipes you crafted from it.
- **The book stays where you like it.** Drag it by its cover to move it, by its top right corner to make it bigger or
  smaller; a right click puts it back. Its tab, page, size and place are kept.
- **Every vanilla click.** Left and right clicks, Shift + click, dragging, double clicks, the number keys, Q and the
  off-hand key work as in the vanilla screen, and the mouse wheel over a slot puts one item in or takes one back.
- **Client-side.** The stations use the game's own menus, so the mod works on any server, with or without it.

## Controls

| Action | How |
|---|---|
| Open a station | Right-click the crafting table or the furnace |
| Place a block on it | Sneak + right-click with the block in hand |
| Put items in | Click a stack in your inventory, then the cell: left click lays all, right click one |
| Spread a stack | Drag it over the cells |
| Craft | Click the result; Shift + click crafts as many as you can |
| Open the recipe book | Click the book on the table's edge |
| Leave | Escape or the inventory key |

## Compatibility

[![JEI](https://raw.githubusercontent.com/Pocky-l/handson-vanilla/main/docs/compat/jei.png)](https://www.curseforge.com/minecraft/mc-mods/jei) ✅ **[JEI](https://www.curseforge.com/minecraft/mc-mods/jei)** (optional) - its keys work over the stations' items, its + lays a recipe into the table

[![EMI](https://raw.githubusercontent.com/Pocky-l/handson-vanilla/main/docs/compat/emi.png)](https://www.curseforge.com/minecraft/mc-mods/emi) ✅ **[EMI](https://www.curseforge.com/minecraft/mc-mods/emi)** (optional) - its keys work over the stations' items

[![Visual Workbench](https://raw.githubusercontent.com/Pocky-l/handson-vanilla/main/docs/compat/visual-workbench.png)](https://www.curseforge.com/minecraft/mc-mods/visual-workbench) ✅ **[Visual Workbench](https://www.curseforge.com/minecraft/mc-mods/visual-workbench)** (optional) - its tables are worked by hand too

[![FastWorkbench](https://raw.githubusercontent.com/Pocky-l/handson-vanilla/main/docs/compat/fastworkbench.png)](https://www.curseforge.com/minecraft/mc-mods/fastworkbench) ✅ **[FastWorkbench](https://www.curseforge.com/minecraft/mc-mods/fastworkbench)** (optional) - the result shows on the table

[![Polymorph](https://raw.githubusercontent.com/Pocky-l/handson-vanilla/main/docs/compat/polymorph.png)](https://www.curseforge.com/minecraft/mc-mods/polymorph) ✅ **[Polymorph](https://www.curseforge.com/minecraft/mc-mods/polymorph)** (optional) - the other results of a grid lie beside it, click one to make it

[![Mouse Tweaks](https://raw.githubusercontent.com/Pocky-l/handson-vanilla/main/docs/compat/mouse-tweaks.png)](https://www.curseforge.com/minecraft/mc-mods/mouse-tweaks) ✅ **[Mouse Tweaks](https://www.curseforge.com/minecraft/mc-mods/mouse-tweaks)** (optional) - its drags work at both stations

Resource packs keep their furnace textures. The open furnace stays this mod's, so a pack cannot misplace what lies in it.

## Configuration

`config/handson_vanilla.properties`, read at startup.

| Key | Default | What it does |
|---|---|---|
| `crafting_table` | `true` | The crafting table as a station. Off, it opens its vanilla screen |
| `furnace` | `true` | The furnace as a station, and its open look in the world. Off, it is vanilla's again |
| `recipe_book` | `true` | The recipe book beside the crafting table |
| `sound_volume` | `1.0` | The stations' own sounds, from `0` (silent) to `1` |

## Installation

1. Minecraft 1.20.1 with [Forge](https://files.minecraftforge.net).
2. [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis) and this mod in the `mods` folder of the client. The
   server needs neither.

Bug reports, questions and ideas for new stations are welcome in [Pocky's Kitchen](https://discord.gg/mGkPKccJEf), our
Discord server.

## Building

```sh
./gradlew build
```

The jar is written to `forge/build/libs/`.

## Credits

- Author: **Pocky**.
- Built on [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis). The furnace's lining, grate and doors are
  this mod's own textures; everything else comes from the game.

<!-- more-mods:start -->
<!-- more-mods:end -->

## License

[MIT](LICENSE)
