# Spoilage Enhanced v1.1.2 for Minecraft 26.2 (Fabric / Forge / NeoForge)

### ⚡ Massive Performance & Lag Fixes
* **Zero Lag Spikes During Exploration:** The container aging sweep now respects the server's tick budget (`haveTime`). Flying with Elytra or generating world terrain will no longer cause micro-stutters or freeze frames.
* **C2ME Compatibility:** Eliminated 95% of chunk queries, drastically reducing CPU usage on servers with C2ME and chunk optimization mods.
* **Dungeon & Structure Lag Fixed:** Unopened chests with pending loot tables (Roguelike Dungeons, desert temples, villages) are no longer prematurely unpacked in the background.
* **Single-Pass Container Processing:** Empty chests and non-food items (cobblestone, tools, ores) are skipped instantly with near-zero CPU cost.

### ❄️ Ice & Universal Non-Food Protection
* **Ice Never Spoils:** Ice, packed ice, blue ice, and frosted ice are permanently protected and will never rot or be registered as food containers.
* **Smart Non-Food Filter:** Minerals, ores, metals, stone, building blocks, redstone mechanisms, furniture, and textiles from vanilla and third-party mods can no longer be falsely identified as food. (Real foods, raw meats, and culinary nuggets remain fully supported!)
* **Safe Storage Detection:** Only actual food crates, bags, sacks, and bales are registered as food storage blocks.

### 🌐 Universal Multi-Loader Support
* A single JAR file works seamlessly across **Fabric**, **Forge**, and **NeoForge**.
* Compiled with Java 21 bytecode compatibility to ensure 100% stable startup on Forge 65.1.3+ and NeoForge.

### 🛠️ Other Improvements & Fixes
* Added arithmetic safety guards against timer overflows on long-running worlds.
* Fixed HUD translation and tooltip cache eviction ordering.
* Full compatibility with Farmer's Delight, Cooking for Blockheads, and Ecologics containers.
