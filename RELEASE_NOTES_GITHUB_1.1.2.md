# Spoilage Enhanced v1.1.2 [26.2] — Release Notes

Cumulative major update over **v1.1.1**.

This release brings dramatic server performance optimizations, an intelligent multi-layer heuristic preventing any non-food block or material from ever being falsely detected as food, complete multi-loader compatibility (Fabric, Forge, NeoForge), and robust arithmetic safety guards across all game systems.

---

### 1. Performance & Server Tick Optimizations

* **Container Sweep Tick Budgeting (`haveTime`):**
  * The background container sweep (`ContainerAgingSweepMixin`) now strictly monitors the server's tick budget via `haveTime`.
  * If the server is saturated or lagging behind (e.g. during heavy world generation or entity spikes), the sweep yields immediately. Because food aging is a pure, idempotent function of absolute `gameTime`, food seamlessly catches up in the next available tick without producing lag spikes.
* **Starvation Protection:**
  * If a low-spec server remains saturated for over 40 consecutive ticks (2 seconds), an adaptive time-capped slice (maximum 1.5 ms) executes to ensure continuous progress without ever freezing or hitching.
* **C2ME & Chunk System Optimization (20x Query Reduction):**
  * The sweep now inspects `holder.getPos()` directly from `GenerationChunkHolder` before querying ticking state.
  * Over 95% of chunks are filtered out via integer coordinate phase arithmetic without invoking C2ME's synchronized `getTickingChunk()` lookup, reducing CPU time during fast flight by over 80%.
* **Unopened Dungeon Loot Table Protection:**
  * Containers with pending loot tables (`getLootTable() != null`), such as unopened chests in Roguelike Dungeons, desert pyramids, and villages, are bypassed completely during world generation sweeps.
  * This prevents premature loot generation, loot table rolls, slot iterations, and synchronous logging during chunk generation.
* **Single-Pass Container Traversal:**
  * Replaced the dual-pass probe/update loops with a streamlined single-pass traversal.
  * Completely empty containers are skipped instantly (`isEmpty()`). Non-food items (cobblestone, tools, dirt, ingots) are filtered out via a zero-allocation check and never touch data component updates.
* **Client & HUD Cache Optimizations:**
  * Fixed cache eviction orders in `TooltipTextCache` and `SpoilageEnhancedTranslations.formatTime`.
  * Added per-tick memoization in `ClientBlockSpoilageCache` to eliminate redundant raycast/HUD computations.

---

### 2. Universal Non-Food Material Filtering

* **Ice & Frozen Blocks Protection:**
  * Regular ice, packed ice, blue ice, and frosted ice are permanently exempted from spoilage. They will never spoil, receive freshness bars, or be registered as food containers.
* **Universal Non-Food Material Filter (`isKnownNonFoodMaterial`):**
  * Implemented an extensive, categorized semantic filter that automatically rejects modded and vanilla non-food items:
    * **Minerals, Ores & Metals:** `_ore`, `_raw_ore`, `raw_iron`, `raw_copper`, `raw_gold`, `_ingot`, `diamond`, `emerald`, `amethyst`, `quartz`, `ruby`, `sapphire`, `topaz`, `coal`, `redstone`, `lapis_lazuli`, `_dust`, `_shard`, `_crystal`, `_gem`. *(Raw meats like `raw_beef` and culinary nuggets like `chicken_nugget` are safely preserved!)*
    * **Building & Construction:** `_slab`, `_stairs`, `_wall`, `_fence`, `_fence_gate`, `_door`, `_trapdoor`, `_pane`, `_glass`, `_brick`, `_bricks`, `_tiles`, `_planks`, `_pillar`, `_bars`, `_chain`, `_rod`, `_lantern`, `_torch`, `_scaffolding`, `_button`, `_pressure_plate`, `_sign`, `_anvil`.
    * **Geology & Terrain:** `_stone`, `_cobblestone`, `_deepslate`, `_granite`, `_diorite`, `_andesite`, `_tuff`, `_basalt`, `_obsidian`, `_sandstone`, `_gravel`, `_sand`, `_dirt`, `_mud`, `_clay`, `_concrete`, `_terracotta`.
    * **Redstone & Mechanisms:** `_piston`, `_dispenser`, `_dropper`, `_hopper`, `_observer`, `_repeater`, `_comparator`, `_rail`.
    * **Textiles & Furniture:** `_wool`, `_carpet`, `_bed`, `_banner`, `_shulker_box`, `_candle`.
* **Safe Food Storage Heuristic:**
  * 4x and 9x compression/decompression recipes in `RecipeScanner` only register as food storage if the ingredient is genuine edible food and the output is a known container (`crate`, `bag`, `sack`, `basket`, `bale`, `bundle`, `barrel`, `box`, `pack`) or food block. Compression of cobblestone, ingots, or ice will never turn into food storage blocks.
* **Dynamic Block Cache Guard:**
  * Non-food materials and drops cannot be registered into `DynamicFoodBlockCache`.

---

### 3. Multi-Loader Universal Compatibility

* **Universal Single JAR:**
  * A single binary distribution runs natively on **Fabric**, **Forge**, and **NeoForge** for Minecraft 26.2.
* **Java 21 Bytecode Target (`release = 21`):**
  * Pinned bytecode compiler target to Java 21 to ensure seamless compatibility with Forge 65.1.3's bundled Mixin 0.8.7 (which rejects Java 25 bytecode).
  * Mixin configuration compatibility level pinned to `JAVA_21`.
* **Verified Runtime Environments:**
  * **Fabric Loader 0.19.3 / 0.19.5 (Minecraft 26.2):** Verified on dedicated server and client.
  * **NeoForge 26.2.0.68:** Verified on dedicated server with 0 injection errors and 0 mixin warnings.
  * **Minecraft Forge 26.2-65.1.2:** Verified on dedicated server with 0 injection errors and clean startup.

---

### 4. Correctness, Boundaries & Safety Guards

* **Arithmetic Overflow Guards:**
  * Added overflow protection in `freshDuration`, `virtualMinTime`, stem growth, and gourd block state transitions against extreme duration values or long-running worlds.
* **Safe Container Integrations:**
  * Supports direct `Container`, reflected `getContainer()` (Balm / Cooking for Blockheads), and `getItems()` list shape (Ecologics pot).
* **High-Res Mod Icon:**
  * Bundled updated high-resolution mod icon for Mod Menu, Forge, and NeoForge mod lists.

---

### Verification Summary

* Full test suite: **1,099+ automated unit and integration tests passing**.
* Live dedicated server launches verified on all three loaders.
* Spark profiles confirm reduction of container sweep CPU time from **7.5% down to <0.3%**.
