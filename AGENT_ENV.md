# AGENT ENV  (generated 2026-08-31 — regenerate only if the toolchain changes)

Every command below was **executed on this machine and confirmed working** before being
written down. Copy them literally; do not retype from memory.

## Where to look

778 lines. Do not read it whole — find the row and open that section.

| You need | Section |
| --- | --- |
| Shell chaining, quoting, `grep -P` | Shell |
| Build, test, run commands | Commands |
| **A TPS or allocation claim** — required before you make one | Measuring performance |
| Proving a mixin or registration change actually applied | Runtime verification |
| Proving what the player *sees* | Verifying what the player SEES |
| Which files must be corrected in the same commit, and `archdoc.py` | Living documents |
| Driving the game as a player; client + dedicated server | Driving the game as a player |
| Why the test world kills the player, and the gamerules that stop it | The test world kills the player |
| **Building an item, entity or block already in a given state** | Building an item that has components |
| **Deploying the mod pack, and the traps** | Running against the integration modpack |
| What a good modded launch looks like; which warnings are noise | What a HEALTHY modded launch looks like |
| **Listing foreign items; probing whether one is tracked** | Enumerating what the pack registers |
| Missing-texture warnings — dev artifact vs real report | Dev-client texture loading |

**Two things about the current state of this machine, both changed 2026-09-10:**

- The **mod pack is deployed by default**. `Loading 5 mods` in a launch log means vanilla,
  `Loading 79 mods` means the pack. Run `modpack.ps1 -Status` before any runtime verdict.
- `AGENT_LOG.md` and `TASKS.md` were compacted; the older entries live in `.claude/archive/`
  and `asked.py` searches both. A subject answered there is answered.

## Shell

- **Chaining: use the Bash tool.** `&&` works there:
  `cd "/e/Spoilage Enhanced/26.2" && ./gradlew.bat --version`
- **Never write `&&` in the PowerShell tool.** This machine has **PowerShell 5.1 only**
  (no PowerShell 7), and `&&` is a *parse error* there — the command dies before running,
  so a chain you thought was verifying something never executed at all.
  In PowerShell, run **one command per call**.
- **Path quoting:** the project path contains a space. In Bash always wrap it exactly as
  `"/e/Spoilage Enhanced/26.2"`.
- **Avoid `grep -P`.** Under this machine's locale it aborts with
  `grep: -P supports only unibyte and UTF-8 locales`. Use `awk` or `sed` instead —
  e.g. read a field with `awk '/^Pass: /{print $2; exit}' AGENT_STATE.md`.

## Layout

- Project root: `E:\Spoilage Enhanced\26.2`  (Bash: `/e/Spoilage Enhanced/26.2`)
- Source: `src/main/java/com/spoilageenhanced/` · Tests: `src/test/java/com/spoilageenhanced/`
- Resources: `src/main/resources/` (mixin config: `spoilage_enhanced.mixins.json`)
- Vanilla sources to check signatures against: `minecraft-sources-26.2/`
- Run logs: `run/fabric_server/logs/latest.log` (server), `run/fabric/logs/latest.log` (client)
- Version file: `gradle.properties`, field `mod_version`

### The repository holds the mod and nothing else

Only `src/`, the Gradle files, `README.md`, `.gitignore` and `.vscode/settings.json` are tracked.
Everything this loop runs on — `AGENT_ENV.md`, `AGENT_STATE.md`, `AGENT_LOG.md`, `TASKS.md`,
`.claude/`, the helper scripts — lives on disk and is **gitignored**, so the repository can be
public without carrying the development setup.

Two things follow, and Step 7 depends on both:

- **Never stage them.** `git add` on an ignored path exits **1**, which breaks any `&&` chain
  and, in the release pipeline, aborts after a successful build with the new version already
  written into `gradle.properties`.
- **A pass that touched nothing under `src/` has nothing to commit.** Do not force it with `-f`
  and do not treat the empty commit as a failure to retry: `00_LOOP.md` already says a pass whose
  entire diff is a counter bump and a log line is not a commit. Write the log line, keep the
  state file current on disk, and go to the next pass.

## Commands  (verified working 2026-08-31)

```bash
# fast check — this is what Step 5 runs on most passes  (~33s)
cd "/e/Spoilage Enhanced/26.2" && ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" test 2>&1 | tail -25

# FORCE the tests to actually run (see Gotchas — the plain form can skip them)
cd "/e/Spoilage Enhanced/26.2" && ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" test --rerun-tasks 2>&1 | tail -25

# full build
cd "/e/Spoilage Enhanced/26.2" && ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" build

# headless server — ALWAYS time-bound it
cd "/e/Spoilage Enhanced/26.2" && timeout 60 ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" runServer

# deploy the built jar into the Forge/NeoForge test runs
cd "/e/Spoilage Enhanced/26.2" && ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" deployToRuns
```

Baseline: **70 tests pass** as of 2026-08-31. A drop below that is a regression.

### Measuring performance — REQUIRED before any TPS/allocation claim

`HotPathBenchmarkTest` already prints 36 timing lines. **Gradle does not show test
`System.out` on the console** — that is why piping the test task into `grep "[BENCH]"`
returns nothing and looks like the benchmark did not run. The numbers land in the XML
report instead. Read them there:

```bash
# 1. run the benchmark (console output will look empty - that is expected)
cd "/e/Spoilage Enhanced/26.2" && ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" test --tests "*HotPathBenchmarkTest" --rerun-tasks

# 2. read the numbers out of the report  (verified working - 36 lines)
cd "/e/Spoilage Enhanced/26.2" && grep -ohE "\[BENCH\][^<]{0,90}" build/test-results/test/TEST-com.spoilageenhanced.HotPathBenchmarkTest.xml
```

Take the "before" numbers BEFORE touching the code, the "after" the same way, and quote
both in the log entry. Sample line so you know what you are looking for:

```
[BENCH] extractWorstItems(64, take 1) (Pass 79 scratch pool): 1.3516 us/call
```

**But a microbenchmark is not a tick.** `HotPathBenchmarkTest` measures one method on the JVM;
it cannot tell you what the server's tick actually costs, and a method that got 30% faster can
still be irrelevant to TPS. The server will tell you directly, and this costs one RCON call:

```bash
python "E:/_claude_ops/rcon_probe.py" "tick query"
```

```
The game is running normally. Target tick rate: 20.0 per second.
Average time per tick: 2.7ms (Target: 50.0ms)
Percentiles: P50: 2.5ms P95: 3.7ms P99: 5.0ms. Sample: 100
```

P99 is the number that matters — a stutter a player feels is a tail, not a mean. As of
2026-09-08 `asked.py mspt` returns nothing across all 840 passes: the loop has spent 45 passes
on the `hot path` lens reasoning about allocations in source and has never once measured a
tick. **Any TPS claim from now on carries a before/after `tick query` pair**, taken on the same
world with the same load, alongside the benchmark lines.

**Do not treat the 2.7 ms / 0.5 ms figures above as a baseline to compare against.** They were
taken on whatever world the session happened to be holding, with whatever earlier passes had
left in it. Proof they are not comparable: a modded server with all fourteen pack jars loaded,
started fresh on 2026-09-10, measured **0.1 ms average, P99 0.2** — five times *cheaper* than
the "vanilla" number, which cannot be true of more code doing more work. An idle reading mostly
measures how much junk is in the world, not the mod.

So a TPS pass takes **both halves itself, in one session**: same world, same load, one reading
before the change or condition and one after. A number quoted from a previous day is not a
baseline, it is a coincidence.

If the hot path you are changing has no benchmark, add one to `HotPathBenchmarkTest` as
part of the pass — a path worth optimising is a path worth being able to measure. Without a
pair of numbers the verdict is `IMPROVED (unmeasured)`, never `FIXED`.

### Runtime verification — REQUIRED for any mixin / registration change

The unit suite cannot load a mixin: injections are applied by the transformer at class-load
time, which only happens when the game actually starts. A broken `@At` selector compiles,
passes every test, and then crashes the game at Bootstrap. This has already shipped once —
see `AGENT_LOG.md` pass 123.

```bash
# launch headless, then check the fresh log for injection failures
cd "/e/Spoilage Enhanced/26.2" &&   LOG="run/fabric_server/logs/latest.log" &&   BEFORE=$(wc -l < "$LOG" 2>/dev/null || echo 0) &&   timeout 90 ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" runServer > /dev/null 2>&1;   tail -n +$((BEFORE+1)) "$LOG" | grep -iE "InjectionError|Mixin apply failed|failed injection check|Critical injection|Done \(" | head -20
```

**Pass criteria:** the appended output contains a `Done (` line (the server finished
starting) and **no** `InjectionError` / `failed injection check` / `Mixin apply failed`.
Reading only what was appended matters — the log is append-only, so an unbounded `grep`
returns lines from an earlier run and passes while this run crashed.

### Verifying what the player SEES — read `VISUAL_VERIFICATION.md` first

There are **no screenshots** on this machine. The laptop runs with the lid closed and RDP
disconnected, so a screen capture has no desktop surface and returns a flat colour; and the
models behind the gateway are text-only, so an image that did reach one would be described
rather than seen. `.claude/tools/capture_window.ps1` exists but refuses to save a one-colour
frame on purpose — do not work around that guard.

The supported proof is **RENDERDUMP**: the renderers log the values they resolved, on change
only, and you read the lines back out of the client log. Full specification and line format in
`VISUAL_VERIFICATION.md`. Client log: `run/fabric/logs/latest.log`.

```bash
# language-file parity — missing keys render as raw identifiers, mismatched %s/%d throw
cd "/e/Spoilage Enhanced/26.2" && python .claude/tools/check_lang.py
```

Verified working 2026-09-02: `OK: 2 языковых файла, 55 ключей, расхождений нет`.

## Living documents  (Step 7 keeps these true)

- **`MOD_ARCHITECTURE.md`** — the reference for how this mod is actually built. It opens with a
  **"Куда смотреть" table**: find the row that matches your task and open only that section.
  Do not read the file whole — it is ~680 lines.

  These changes oblige you to correct it **in the same commit**, and the table names the
  section for each:

  | You changed | Section to fix |
  | --- | --- |
  | Added, removed or retargeted a mixin | §7 |
  | Data component, its registration, or the network path | §2, §3, §4 |
  | Added a container-aging path (a new place food ages) | §8.5 |
  | A number a player sees — duration, effect, composter chance | §5.5 |
  | Added or changed a debug/player command | §7.5 |
  | How foreign-mod food is detected or caught | §9 |
  | Added a class that lives outside the mixins | §7.2 |

  **The coverage half is checkable, so check it rather than remembering:**

  ```bash
  python "E:/_claude_ops/archdoc.py"
  ```

  It reports any class under `src/main/java` or any registered mixin the document never
  mentions, and any `§N` reference pointing at a heading that does not exist. Exit 1 when
  something is missing. As of 2026-09-10 it passes: 86 classes, 49 mixins, 15 sections, all
  named, every reference resolving. It cannot tell you whether a description is *good* — only
  whether the thing is mentioned at all; that is the honest half a script can do.

  Why it exists: on 2026-09-10 a sweep found five core classes — `BlockDropSpoilageHandler`,
  `BlockSpoilageNetworking`, `CraftingSpoilageTransfer`, `ClientVirtualSpoilageAnchor` and
  `RenderDump`, 890 lines between them — with no mention anywhere in the file, five mixins
  added that week missing from the map, and four claims naming things that no longer exist
  (`TRANSFER_COMPONENT_CACHE`, `client/SpoilageEnhancedClient.java`, `ConsumableComponent`,
  `Projectile.spawnProjectileFromRotation`). A duty nobody can check cheaply is a duty that
  gets skipped.
- **`CLAUDE.md` sections 1–3** — environment, architecture rules, run-task matrix. Correct
  these when a command, a path or an architectural constraint actually changes.

Not living, do not maintain: `BUGS.md` and `.claude/archive/` describe the past on purpose.

Why this is a Step 7 duty and not a task for later: drift here is silently expensive. Every
pass reads `MOD_ARCHITECTURE.md` as ground truth to decide where to look, so a wrong line
misdirects work until someone notices. It already happened — pass 126 had to spend a whole
pass repairing drift left by passes 83, 118 and 123, and reconstructing which pass
invalidated what is far harder than fixing one line while you still remember the change.

## Periodic duties

- **Release: every 3–5 commits THAT CHANGED `src/main`** → run the `release` skill

  Count with this, not by eye:

  ```bash
  python "E:/_claude_ops/relcount.py"
  ```

  It prints the count, lists the commits behind it, says whether a release is due, and — the
  part that matters — **compares the number against `Commits since: release` in
  `AGENT_STATE.md` and says so when they disagree**.

  That comparison is the whole point. A correct counting command lived in this file already,
  and it was not enough: on 2026-09-11 at 20:47 the state file said `release = 0` while the
  repository held two commits changing `src/main` since `v1.1.7-26.2` — `78bdc14` and
  `9408b1b`. Nothing was wrong with the command; nobody ran it. At 0 the threshold of 3 is
  unreachable, so the release would never have come due no matter how many fixes landed —
  the third time this exact failure has been recorded here. `checkpass.py` now reports the
  mismatch on every edit to `AGENT_LOG.md`, so it surfaces itself instead of waiting to be
  looked for.

  Zero means there is nothing to publish — leave the counter alone and keep working. Commits
  touching only `src/test`, `AGENT_*.md`, `TASKS.md` or the rules do not count: a user
  installing the jar receives nothing from them. Counting raw commits produced **18 releases
  in one night** (v1.0.50–v1.0.67) for 3 actual code changes.

  (`.claude/skills/release/SKILL.md`): bump `mod_version` in `gradle.properties`, build,
  tag, publish a GitHub Release to branch `26.2` of **`DIVI4ik/Spoilage-Enhanced-dev`**, the
  PRIVATE repository, via remote `devbackup`.

  **Never commit, push or release into `origin`.** That is the public repository
  `DIVI4ik/Spoilage-Enhanced`: one curated commit per published version, updated by a human
  when a version is worth showing. It was rebuilt from scratch to get this loop's output out
  of it — hundreds of `NO_BUG` passes, working notes, tooling — and pushing there would undo
  that within a day. The local branch's upstream is `devbackup/26.2`, so a bare `git push`
  already goes to the right place; do not re-point it.

  Success = `git log -1 --grep="Release v"` shows the new version, `gh release list` contains
  it, **and `gh release view <tag> --json assets` names the jar**. The last part is not
  optional: a release with no jar still appears in the list and still prints a URL when
  created, so the old success criterion was satisfied by two consecutive empty releases
  (v1.0.77, v1.0.78) that gave users nothing to download.

  This is Step 8 of the loop, not an optional extra. It has slipped before: passes 120–128
  accumulated **nine** commits before a release went out, because the cadence lived only in
  `CLAUDE.md` and nothing in the loop checked it. The counter in `AGENT_STATE.md` is what
  makes it come due — keep it accurate.

## Gotchas

- **setblock-powered droppers/dispensers multi-fire.** Placing a redstone block beside a
  dropper with `setblock` cascades block updates and re-triggers it within the same RCON
  batch — a 2-stack appears to move WHOLE into a hopper while vanilla moves 1 per fire
  (pass 1281). Read per-fire state or use a single-tick pulse.
- **EnderChestMenu slot mapping is non-obvious.** Menu slots 0-26 = ender chest, 27-53 =
  main inventory, 54-62 = hotbar 0-8 (pass 1303: hotbar slot 8 = menu slot 62). ChestMenu
  differs: 27-35 = hotbar. Probe with menuclick PICKUP before assuming an index.
- **setblock-placed comparators never recalculate.** `setblock` placement does not fire the
  neighbor update that schedules the comparator's tick, so OutputSignal stays 0 forever —
  a harness artifact, not mod interference (pass 1299: the signal code reads counts only).
- **data modify on player inventory components is refused by vanilla** — use
  `give <item>[spoilage_enhanced:spoilage={...}]` instead (pass 1295).
- **Scenario readbacks land in client chat, not logs** — selftest verdicts are observed via
  events.log (rotteneat) or direct RCON drives, not the scenario's own data get (pass 1309).
- **A cached check is not a check.** `gradlew test` prints `BUILD SUCCESSFUL` while the
  per-task line says `Task :test UP-TO-DATE` — the tests did **not** run and nothing was
  verified. Observed on this project. Read the per-task lines, or use `--rerun-tasks`.
- **JDK 25 is required** and the global default may be JDK 21, hence the explicit
  `-Dorg.gradle.java.home` on every gradle call.
- **Compilation never proves mixin behaviour.** Mixins and data components exist only at
  runtime; a change to either needs a fresh run log, not a green build.
- **Log freshness matters.** `latest.log` is append-only across a run and only replaced on
  startup. Record its line count before an action and read only what was appended, or a
  search returns matching lines from an *earlier* run and the check passes while the
  server is down.
- **Do not run `genSources`** — yarn mappings fail on 26.2. Read `minecraft-sources-26.2/`
  instead of reconstructing signatures **or behaviour** from memory. Behaviour is the one
  that bites: vanilla `ComposterBlock.bootStrap()` calls
  `COMPOSTABLES.defaultReturnValue(-1.0F)` (line 68), so the natural assumption that a
  missing key yields `0.0f` is wrong — and a pass shipped exactly that assumption as a
  source comment. Vanilla sources are on disk; open the file and cite the line.
- **Stop leftover java processes and free the RCON port** before a test launch; a run that
  silently attaches to a stale process produces fake evidence. There is one command for this
  and it takes two seconds — run it before **every** launch, not only when something looks wrong:

  ```bash
  powershell -NoProfile -ExecutionPolicy Bypass -File "E:/_claude_ops/kill_runs.ps1"
  ```

  `timeout 90 ./gradlew runServer` kills gradle, **not** the Minecraft JVM it forked; that JVM
  keeps port 25575 after the pass ends. RCON then answers from a server running the PREVIOUS
  jar, so a fix appears to fail, or worse, a broken build appears to pass. The script leaves
  gradle daemons and test workers alone, so it is safe to run at any time. A janitor task
  (`runs_janitor.ps1`, every 15 min) sweeps runs older than 90 minutes that no live pass owns,
  but that is a net for crashes — it is not a substitute for step 0.

### Driving the game as a player (what L13 can and cannot reach)

RCON has no player, so it can place, summon and give — but it cannot click. Every behaviour
task about containers, right-clicking or menus came back `BLOCKED` until these existed:

```
spoilage debug use <x> <y> <z>                  # right-click that block AS the player
spoilage debug menuclick <slot> <button> <type> # real click in the player's open menu
```

Both act **as the executing player**, so they only work from a client — the self-test harness
(`./gradlew runClient -Pselftest=<scenario>`) is the way to run them unattended. From the
server console or RCON they refuse with a message saying so; that is not a failure, it is the
command telling you it needs a player.

Available scenarios (set via `-Dspoilage_enhanced.selftest=<name>`):
- `place` — drives BUG-12 placement scenario (rotten pumpkin place -> break -> drop carries state)
- `guard` — drives ScreenHandlerMixin §2 right-click guard (worst-slice rejection in crafting grid)
- `cake` — drives CakeEatMixin poison on rotten cake
- `container` — drives container-interaction paths (QUICK_MOVE, SWAP, PICKUP_ALL, QUICK_CRAFT)
- `campfire` — drives CampfireBlockEntityMixin.placeFood rotten-refusal guard
- `animal` — drives AnimalEntityMixin rotten-food guard (needs debug useentity)
- `composter` — drives ComposterBlockMixin.addItem rotten-chance guard
- `loot` — drives RandomizableContainerBlockEntityMixin.onGenerateLoot (loot randomization)
- `eat` — drives ClearAllStatusEffectsConsumeEffectMixin via real player eating (Pass 670)
- `furnace` — drives AbstractFurnaceBlockEntityMixin.canBurn rotten-refusal guard (Pass 672)
- `staleeat` — eats a STALE item as a real player and checks the nutrition penalty
- `rotteneat` — drives the ROTTEN branch of ItemStackMixin.onFinishUsingItem (pass 1211): finisheat a rotten apple, assert POISON present (deterministic — no chance roll)
  arithmetic (nutrition 4, 50% penalty must drop foodLevel by 2 from the post-eat level).
  Nausea is a 50% roll, so the run reports whichever outcome happened rather than asserting.
- `enderchest` — drives the ender-chest aging mixin through the real right-click + menu path.
  RCON cannot reach it: `data modify` on players is refused by vanilla, and the debug
  `use`/`menuclick` commands act AS a player, so they must come from a client.
- `chest` — places a FILLED shulker into a chest via give + menuclick, which is the only path
  vanilla's container sanitization allows, and verifies the CONTAINER branch of the shared
  food-carrying probe end to end.

**`rotteneat` exists now (pass 1211).** It drives the ROTTEN branch of the eat path —
finisheat a rotten apple, assert POISON in active_effects (deterministic, no chance roll).
Verified live: `[{duration: 120, show_icon: 1b, id: "minecraft:poison"}]`.

Available debug commands (added in Passes 641/644/657, full list in MOD_ARCHITECTURE.md §7.5):
- `spoilage debug logging <bool>` — toggle mod logging
- `spoilage debug renderdump <bool>` — toggle RENDERDUMP (what was drawn, change-only)
- `spoilage debug inspect [x y z]` — spoilage data of targeted block (or by coords)
- `spoilage debug place <item> <state> [x y z]` — place block as player (for scripts)
- `spoilage debug use [x y z]` — right-click block as player
- `spoilage debug useentity <type>` — calls Animal.mobInteract directly (cow/chicken/pig/sheep/wolf)
- `spoilage debug menuclick <slot> <button> <type>` — real click in the player's open menu
- `spoilage debug eat <item> <fresh|stale|rotten> <count>` — simulates eating via Item.finishUsingItem (drives ConsumeEffect mixins like ClearAllStatusEffectsConsumeEffectMixin)
- `spoilage debug stress <count>` — spawns N item entities for L11 load testing

They call vanilla's own paths — `BlockState.useItemOn` / `useWithoutItem`, and
`AbstractContainerMenu.clicked` — deliberately. The guard that let a rotten carrot into a
crafting grid lives inside `clicked`, so a helper that moved the item itself would have
reported success while the real click stayed broken.

`use` on a container also opens its menu, which is what makes `menuclick` usable: open first,
then click.

Verified working: registered in the command tree (`help spoilage` lists them) and refusing
cleanly without a player, on a live server.

#### Behaviour tests need a CLIENT JOINED TO THE DEDICATED SERVER, not a single-player world

`runClient` on its own opens a single-player world, and in that world the player has no
command permission. Every command bounces with **"Unknown or incomplete command"** — including
plain vanilla ones like `clear @p`, `setblock` and `data get`. That message reads like a typo
or a missing mod command, which is what makes it expensive: a whole session was spent sending
commands into a world that rejected all ten of them, zero succeeded, and nothing looked broken
because the client was running and rendering normally.

The dedicated server already ops the dev player (`run/fabric_server/ops.json`, `Player499`,
level 4). So run both and join:

```bash
# 0. ALWAYS FIRST - close the runs the previous pass left open, and wait for 25575 to free
powershell -NoProfile -ExecutionPolicy Bypass -File "E:/_claude_ops/kill_runs.ps1"

# 1. server in the background (RCON on 25575, see rcon_probe.py in _claude_ops)
cd "/e/Spoilage Enhanced/26.2" && ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" runServer

# 2. client joins it straight away — the player is op there
cd "/e/Spoilage Enhanced/26.2" && ./gradlew.bat -Dorg.gradle.java.home="E:/jdk-25" runClient -Pquickplayserver=localhost
```

That split is also the right division of labour: **RCON sets the scene** (spawn, setblock,
give, speed) because it needs no player, and the **client player performs the interactions**
(`spoilage debug use`, `spoilage debug menuclick`) because those act as a player.

#### The test world kills the player, and a dead player fails every command silently

`server.properties` is `gamemode=survival`, `difficulty=easy`, and monsters spawn. The logs
carry four real deaths — `was slain by Zombie`, `was impaled by Drowned`, `drowned` — and on
2026-09-08 a single `kill @e[type=minecraft:zombie]` removed **18** live zombies from the test
world. This is not a theoretical hazard.

What it does to a pass: death scatters the whole inventory as `ItemEntity`s, so a stack the
scenario prepared is gone and the assertion reads an empty slot; the corpse respawns at world
spawn, so the next `spoilage debug use <x> <y> <z>` fires from the wrong place; and while the
death screen is up the player cannot execute anything at all. None of that reports an error —
the run just quietly stops proving what it set out to prove. Vanilla zombies also pick items up
off the ground, so a dropped test stack can simply walk away.

Four gamerules close it. **In 26.2 gamerule names are `snake_case`, not the old camelCase** —
`gamerule keepInventory` answers `Incorrect argument` and looks like a parse error:

```bash
python "E:/_claude_ops/rcon_probe.py" \
  "gamerule keep_inventory true" "gamerule immediate_respawn true" \
  "gamerule spawn_monsters false" "gamerule drowning_damage false"
```

They were set on 2026-09-08 and live in `level.dat`, so this world keeps them. **Re-run the
line on any world you create fresh** — a new world starts at the vanilla defaults again.
Nothing in `src/` reads death, death-drops or hostile mobs (grep confirms), so none of these
weakens a test; they only stop the world from destroying the evidence mid-run.

**The world persists between server restarts, and so does everything lying on the ground.**
Pass 841 spent a whole pass on a defect that did not exist: a
`data get entity @e[type=minecraft:item,limit=1,nbt=...]` query matched a `glow_berries` drop
from a test 4.7 minutes earlier and reported it ROTTEN, while the drop the harvest had just
produced was FRESH. Same failure family as a leftover server on port 25575 — old state
answering a new question. Clear the floor before a drop scenario, and check `Age` on anything
you match:

```
kill @e[type=minecraft:item]
```

Hunger is **not** part of this. On `difficulty=easy` starvation stops at 10 HP and cannot kill,
and the test player sits at `foodLevel 20` anyway. But a full food bar has its own trap: vanilla
refuses to eat ordinary food at 20, so a scenario that drives a **real** right-click eat gets a
refusal that looks like a broken mixin. `spoilage debug eat` sidesteps it by calling
`Item.finishUsingItem` directly; if you want the genuine path, drop the bar first —
`data merge entity <player> {foodLevel:6}` — and say in the log which of the two you drove.

#### Building an item that has components — `data modify` works in 26.2

**CORRECTION (pass 1239):** The original claim that `data modify` silently no-ops on item
components was based on passes 474/476 but is **incorrect for current 26.2**. Verified via RCON
on live server: `data modify entity @e[tag=test,limit=1] Item.components.spoilage_enhanced:spoilage
set value {fresh_expirations:[999999999L],...}` succeeded and read-back confirmed the component
was updated.

`data modify` CAN edit item components in 26.2. `give` and `summon` also work to build items
with components already in place:

```bash
# fill a bundle without a single right-click - measured on this server 2026-09-08
summon item 8 200 8 {Tags:["probe"],Item:{id:"minecraft:bundle",count:1,components:{"minecraft:bundle_contents":[{count:4,id:"minecraft:carrot"}]}}}
data get entity @e[tag=probe,limit=1] Item
kill @e[tag=probe]

# or straight into a player's inventory
give <player> minecraft:bundle[bundle_contents=[{id:"minecraft:carrot",count:4}]] 1
```

The read-back came out exactly as written:
`{components: {"minecraft:bundle_contents": [{count: 4, id: "minecraft:carrot"}]}, count: 1, id: "minecraft:bundle"}`.
Both the bare `bundle_contents` and the namespaced `minecraft:bundle_contents` parse.

**Verify the syntax against a nobody-selector before blaming the harness.** `give
@a[tag=probe_nobody] <item>[<components>] 1` parses the item fully and then reports `No player
was found`, which touches nothing. A genuinely wrong component says so instead — `Unknown item
component 'minecraft:nonsense_component'`, or `Malformed 'minecraft:bundle_contents' component:
…`. That pair is how you tell "the harness cannot do this" from "I wrote it wrong", and it is
the check that was missing when this was called BLOCKED.

Generalise it: any scenario that needs an item in a particular state — a bundle with contents,
a container item, a written book, a dyed thing — is reachable this way. Before writing BLOCKED
on an item-state scenario, try building the item instead of editing it.

**The same rule covers entities and block entities, and the loop has now lost three passes to
not knowing that.** Pass 854 (allay) and pass 859 (brewing stand) were both closed on "`data
modify` on entity NBT doesn't affect a live container", exactly as pass 845 was. All three were
wrong. `summon` and `setblock` take the container's contents inline, and both were measured
working on 2026-09-09:

```bash
summon allay 8 101 12 {Tags:["p"],Inventory:[{id:"minecraft:carrot",count:1}]}
data get entity @e[tag=p,limit=1] Inventory
#   -> [{count: 1, id: "minecraft:carrot"}]

setblock 8 101 14 minecraft:brewing_stand{Items:[{Slot:3b,id:"minecraft:carrot",count:1}]}
data get block 8 101 14 Items
#   -> [{count: 1, Slot: 3b, id: "minecraft:carrot"}]
```

And a stack can be born already spoiled, which is what lets you test aging without waiting:
give the item a `spoilage_enhanced:spoilage` component whose `fresh_expirations` is in the
**past**, then read it back a second later. Measured on a chest minecart:

```bash
time query gametime                       # -> 7477120
summon chest_minecart 8 101 8 {Tags:["c"],Items:[{Slot:0b,count:1,id:"minecraft:carrot",
  components:{"spoilage_enhanced:spoilage":{fresh_expirations:[7477000L],speed_multiplier:100.0d}}}]}
data get entity @e[tag=c,limit=1] Items   # -> rotten_count: 1, within seconds
```

**So the rule is: `data modify` edits and WORKS; `summon`, `setblock` and `give` also build
and work.** All four methods work in 26.2. Reach for any of them before writing BLOCKED, and
never carry a harness limitation forward from an older pass without re-testing it — that is
how one false blocker became three.

Symptom to recognise: if commands come back "Unknown or incomplete command" with the arrow at
the very end, check permission before checking the command. A permission-gated node is hidden
from the parser, so "no permission" and "no such command" look identical.

#### Two traps that make a client run LOOK successful while testing nothing

Both cost a full attempt each on 2026-09-05, and neither reports an error you would notice.

**The dev client's username is random per run.** Observed across three consecutive launches:
`Player18`, `Player686`, `Player340` — not `Player499`, whatever `ops.json` says. An
`execute as Player499 run ...` matches nobody, and the server answers "No player was found"
once, then every following command in the batch runs against an empty selector and reports
nothing at all. **Always use `@a`**, never a name. Permission is not a problem: `execute as`
changes the executor, not the permission level, so the console's level 4 still applies and the
player does not need to be an op.

**BUT the selftest harness (`-Pselftest=<scenario>`) DOES need the player opped.** The above
note about `execute as` applies to RCON/console commands. The selftest sends commands through
`ServerboundChatCommandPacket` on the client's connection, which the server parses against the
**PLAYER's** command tree — a subset that only includes commands the player has permission for.
The quickplay client uses a random `Player###` name that is NOT in `ops.json`, so every
selftest command comes back `Unknown or incomplete command` (a permission-tree miss, not a
parse error — the same symptom a broken `sendRawCommand` produces, so check this first).
**Fix: after the client joins, op the player via RCON before the selftest's 300-tick settle
window ends.** Verified on 2026-09-08 (pass 837): `op Player593` then the container selftest
ran the full chain on the real `CraftingMenu` and the §2 guard fired on a mixed right-click.

**`spoilage debug place <item> <state> <x> <y> <z>` names WHERE THE BLOCK GOES**, not the
block to aim at — the command derives the support itself from `pos.below()`. Passing the
support instead aims at solid, non-replaceable ground and the whole thing comes back as a bare
`Fail[]` with no explanation.

And one thing that makes eating testable at all: put the player in creative first
(`gamemode creative @a`). `Player.canEat` returns true for an invulnerable player, so a bite is
never refused for a full stomach and the scene does not have to drain hunger.

Worked example, verified end to end (rotten cake placed, then eaten, poison applied):

```
setblock 8 100 9 minecraft:stone replace
gamemode creative @a
tp @a 8 101 8
execute as @a run spoilage debug place minecraft:cake rotten 8 101 9
execute as @a run spoilage debug use 8 101 9
```

Proof lands in `run/fabric_server/spoilage_enhanced_logs/events.log`, not in `latest.log`.

Note: a monitor being off or the screen locked does NOT stop the client — it runs and renders
regardless. That was ruled out by observation, not assumed.

### Running against the integration modpack (2026-09-10)

Fourteen real mods are on this machine at `E:\_claude_ops\modpack\mods`, copied from the
player's own 26.2 Fabric instance. They are the reason the integration lens can finally run:
before this, every modded scenario came back `BLOCKED` because no modded pack was reachable.

What is in it, and why each matters to this mod:

| Jar | Why it is interesting |
| --- | --- |
| `FarmersDelight` | Cooking pot, skillet, cutting board — food produced by containers that are not a vanilla furnace, plus its own crops |
| `croptopia` | ~100 foods and crops in one namespace: the widest test auto-detection will ever get |
| `cookingforblockheads` | **Has a fridge.** A cooling container is the one piece of foreign content that argues directly with a spoilage clock |
| `rightclickharvest` | Harvest by right-click — hits `learnFoodDropFromInteraction` and the hand-picked-block path head on |
| `better-end`, `Ecologics` | Foods from other dimensions and biomes |
| `better_mcdonalds_mod` | Crafted composite foods |
| `balm`, `bclib`, `wunderlib`, `worldweaver`, `jamlib`, `epherolib`, `fabric-api` | Libraries the above need — deploy them or the pack will not load |

Deploy and remove with one command; do not copy the folder by hand:

```bash
powershell -File "E:/_claude_ops/modpack.ps1" -Deploy    # into both run/fabric*/mods
powershell -File "E:/_claude_ops/modpack.ps1" -Remove
powershell -File "E:/_claude_ops/modpack.ps1" -Status    # when unsure which world you are in
```

**This pack is FABRIC ONLY, and the project has Forge and NeoForge test servers.** Checked by
reading the loader metadata out of every jar on 2026-09-10, not by trusting file names: 14 of
14 declare `fabric.mod.json`, and only `Ecologics-NeoFab` also carries NeoForge metadata. So
thirteen of the fourteen would simply not load under `loader-tests\forge` or
`loader-tests\neoforge` — and a pack that half-loads produces a startup failure that reads
like a mod incompatibility. `modpack.ps1 -Status` prints this line every time, so you never
have to remember it:

```
pack loaders: 14/14 fabric, 1 neoforge, 0 forge
  -> FABRIC ONLY. Do not deploy this pack into loader-tests\forge or loader-tests\neoforge.
```

`-Deploy` targets only `run\fabric_server\mods` and `run\fabric\mods` for this reason. L14 is a
Fabric-side lens; loader-specific drop paths stay a vanilla question (passes 825, 826).

### Verifying the mod loads on Forge and NeoForge (2026-09-22)

**Everything else in this file drives Fabric, and for a long time that was the only loader
anyone started.** On 2026-09-22 the mod was published announcing Fabric, Forge and NeoForge.
A real Forge 65.1.3 client died before the title screen:

```
MixinInitialisationError: Mixin config spoilage_enhanced.mixins.json specifies
compatibility level JAVA_25 which is not recognised
```

Forge ships Mixin 0.8.7, whose `CompatibilityLevel` enum ends at **JAVA_21**; Fabric Loader
carries its own newer fork, which is why the dev client never noticed. The build also emitted
bytecode version 69 (`options.release = 25`), which Mixin refuses above its level regardless
of what the JSON declares. Both are now pinned at 21.

Two checks exist. Run the cheap one always, the real one whenever you touch the mixin config,
`options.release`, `mods.toml`, `neoforge.mods.toml` or `fabric.mod.json`.

```bash
# Cheap: reads the jar, compares the declared level and the bytecode version against the
# level Forge's own Mixin understands. One second, no game. Exit 1 = would not load.
"E:/Python312/python.exe" "E:/_claude_ops/loadercheck.py"

# Real: starts the dedicated server that lives in loader-tests\<loader>, with the newest jar
# from build/libs copied into its mods folder, and reads the log for a verdict.
powershell -File "E:/_claude_ops/loader_launch.ps1" -Loader forge
powershell -File "E:/_claude_ops/loader_launch.ps1" -Loader neoforge
```

A **server**, not a client, on purpose: it loads the same mixin configs and the same
entrypoints, wants no GPU, no window and no desktop session, and fits in a gigabyte. It
refuses to start under 1700 MB free and stops itself afterwards, because a forgotten server
holds that gigabyte all night.

Verdicts it prints: `STARTED` (the log reached `Done (…)! For help`), `MIXIN_ERROR`, `CRASH`,
`TIMEOUT`. The full server output is left in `E:\_claude_ops\loader_<loader>_server.log`.

**A green Fabric run says nothing about the other two.** That sentence is the whole lesson of
the 09-22 breakage, and it is why `loadercheck.py` now gates publishing.

**Three things that will cost you a pass if you skip them.**

**The packaged `spoilage_enhanced` jar must never sit beside the dev build.** The dev run
supplies this mod from the classpath; a packaged copy in `run/*/mods` is a second declaration
of the same mod id. That is why the pack's own `spoilage_enhanced_26.2-1.1.6.jar` is parked in
the parent folder as `*.DO_NOT_DEPLOY` and why `-Deploy` refuses to run if it finds one in the
source. `-Status` on 2026-09-10 also found a stray `spoilage_enhanced_26.2-1.1.4.jar` already
sitting in `run/fabric_server/mods` — check what it is before adding fourteen more jars on top,
because a duplicate-mod refusal at startup looks exactly like a mod incompatibility.

**As of 2026-09-10 the pack is DEPLOYED — that is now the default state of this machine.**
Fourteen jars sit in `run\fabric_server\mods` and `run\fabric\mods`, left there deliberately
because the queue's next tasks are L14. Which inverts the older habit:

**`-Status` before ANY runtime verdict, not only an L14 one.** Previously "vanilla" was what
you got by doing nothing; now it is something you have to ask for with `-Remove`. A pass that
launches, drives a scenario and writes "vanilla behaviour confirmed" without checking is
writing a verdict about a world with seventy-eight foreign mods in it. `Loading 5 mods` in the
log means vanilla; `Loading 79 mods` means the pack. That line is in every launch log and
costs one grep.

This is the same failure family as the leftover server holding port 25575, the eighteen
leftover zombies and the four-minute-old glow_berries drop that cost pass 840 — old state
answering a new question. When a modded and a vanilla scenario disagree, `-Status` is the first
thing to check, not the last.

**The first modded launch is its own task, and it is allowed to fail.** Fourteen jars, a
version mismatch or a missing dependency will stop the server, and that is a result, not a
defect in this mod. Read `run/fabric_server/logs/latest.log` from the line count you recorded
before launching, name the offending jar, and say so. Do not report it as a Spoilage Enhanced
defect and do not delete jars until you have read the reason.

#### What a HEALTHY modded launch looks like — measured 2026-09-10, 21:00

Driven once by hand so the first L14 pass does not have to discover it. The pack deploys and
the server starts clean:

```
[main/INFO] (FabricLoader) Loading 79 mods:
	- spoilage_enhanced 1.1.6
[Server thread/INFO] (Minecraft) Done (1.264s)! For help, type "help"
```

**79, not 14** — several jars carry nested ones (`balm` bundles `kuma_api`, and Fabric API is
about forty modules). `Loading 5 mods` means the pack is NOT deployed; check
`modpack.ps1 -Status` before reading anything else into a result.

**This also settled a question the log could not answer on its own.** A packaged
`spoilage_enhanced_26.2-1.1.4.jar` had been sitting in `run/fabric_server/mods` while the dev
build supplied 1.1.6 from the classpath, and it never appeared in the loaded list. That alone
did not prove whether Loom's `runServer` scans `<runDir>\mods` at all — Fabric skips a
duplicate mod id in silence. Fourteen jars with different ids settle it: **the folder is
scanned, and the duplicate was being dropped quietly.** So the "never leave a packaged copy of
this mod beside the dev build" rule above is real, and its symptom is silence, not an error.

**Three kinds of warning are expected here. None is a defect. Do not spend a pass on them:**

| Warning | Why it is noise |
| --- | --- |
| `Reference map '<mod>.refmap.json' ... could not be read` (6 of them: kuma_api, epherolib, croptopia) | Published jars carry refmaps built for intermediary names; a mojmap dev environment cannot read them. The mixins still apply. |
| `@Mixin target net.minecraft.client... was not found` (2, from `farmersdelight.mixins.json`) | Client-side mixins evaluated on a **dedicated server**. Expected on every server run. |
| `Mod 'Croptopia' recommends any version of patchouli, which is missing` | A *recommendation*, not a dependency. Croptopia loads and works without it. |

What would be real: `Incompatible mod set`, `requires ... which is missing` (requires, not
recommends), a `Mixin apply failed` or `InjectionError` naming **`spoilage_enhanced`**, or no
`Done (` line at all. Grep for those four, not for the word "WARN".

**And these three lines are the positive evidence — all present on the 2026-09-10 run:**

```
[main/INFO] (spoilage_enhanced) SpoilageEnhanced common initialization complete!
[Server thread/INFO] (spoilage_enhanced) Data component check OK: spoilage_enhanced:spoilage (raw id N)
[Server thread/INFO] (Minecraft) Done (1.264s)!
```

The middle one matters most: it is the mod's own registration self-check, and it passed with
78 other mods present. Searching for conflicts (`conflict`, `overwrite`, `already been
applied`, `incompatible mixin`) and for `spoilage_enhanced` mixin failures returned **nothing**
on that run — so mixin coexistence with this pack is a settled question, not an open risk. If
a later pass sees any of those, that is new and worth a task.

#### Enumerating what the pack registers — no server needed

There is no vanilla command that lists a namespace: tab completion does it in a client, and
RCON has no completion. The jars answer it for free — every mod ships
`assets/<namespace>/lang/en_us.json`, and its `item.<namespace>.<name>` keys are exactly the
registered items.

```bash
python "E:/_claude_ops/modded_items.py"                # counts per namespace
python "E:/_claude_ops/modded_items.py" croptopia      # ids, ready to paste into a command
```

Measured 2026-09-10 — **804 items across six namespaces**:

| namespace | items |
| --- | --- |
| croptopia | 444 |
| betterend | 197 |
| farmersdelight | 98 |
| better_mcdonalds_mod | 33 |
| ecologics | 23 |
| cookingforblockheads | 9 |

**Size the work before starting it.** 804 items at one command each is not a pass, it is a
night. Take a namespace-sized slice — `better_mcdonalds_mod` at 33 is the natural first one —
prove the method end to end there, and say in the log which slice you drove. A sweep that
reports "modded food" without naming its slice cannot be trusted or repeated.

**Probing one item. Two things measured on the running modded server 2026-09-10 — the second
one contradicts the obvious guess, so read both.**

**The command is `/givespoiled`, a ROOT command.** Not `spoilage givespoiled`. Nested under
`spoilage` every call fails with `Incorrect argument for command` and the arrow at the very
end, which looks like a bad argument and is not. `help givespoiled` prints the real shape:

```
/givespoiled <targets> <item> <stage> [<count>]
```

**A nobody-selector answers EXISTENCE, not spoilability.** The tempting probe —
`givespoiled @a[tag=nobody] <item> fresh 1`, harmless because it touches no inventory — does
not distinguish what it looks like it distinguishes:

```
givespoiled @a[tag=nb] croptopia:tomato fresh 1   ->  No player was found
givespoiled @a[tag=nb] minecraft:stone  fresh 1   ->  No player was found      <-- stone!
givespoiled @a[tag=nb] croptopia:not_real fresh 1 ->  Unknown item 'croptopia:not_real'
```

Stone is not spoilable and answers exactly like the tomato, because the selector is resolved
**before** the spoilability check, so the nobody path never reaches it. What this probe is
genuinely good for is confirming an id exists — worth having, since `modded_items.py` reads
names out of lang files and a lang key is not proof of a registered item.

**To ask about spoilability you need a real player**: `givespoiled <player> <item> fresh 1`
either succeeds or answers `The specified item is not a spoilable item`, and then `clear
<player>` puts the inventory back. Stand a client up (the recipe is above) before planning a
sweep — a sweep designed around the nobody-probe will report every item as spoilable and be
worthless.

**But the question "does this item get a timer" needs no player at all.** Spawn it as an item
entity and read the component back — `ItemEntityMixin` stamps it within a second. Two details
make the difference between this working and looking broken, both measured 2026-09-10:

```bash
forceload add 0 0                              # 1. no players online = no ticking chunks
summon item 8 100 8 {Tags:["p"],NoGravity:1b,  # 2. without NoGravity it falls to the void
    Item:{id:"croptopia:tomato",count:1}}      #    and is gone before your next RCON call
data get entity @e[tag=p,limit=1] Item.components
kill @e[type=item] ; forceload remove 0 0
```

Skip either and the entity reads back `No entity was found` immediately after a successful
`Summoned new ...` — which looks like the mod destroyed it and is really just physics.

Driven this way on the modded server, all three answered:

```
croptopia:tomato       {"spoilage_enhanced:spoilage": {fresh_expirations: [63652L]}}
farmersdelight:tomato  {"spoilage_enhanced:spoilage": {fresh_expirations: [63650L]}}
minecraft:apple        {"spoilage_enhanced:spoilage": {fresh_expirations: [61251L]}}   (control)
```

**So croptopia food does get a timer even though croptopia appears nowhere in the config
lists.** Absence from `additional_tracked_items` is not absence of a timer: an item that
already carries `DataComponents.FOOD` is handled by component detection and needs no entry,
while farmersdelight evidently needs explicit ones. Do not read those lists as the answer.

The other direction was sampled the same way, and also came back clean — three foreign
non-foods, no component on any of them:

```
farmersdelight:cabbage_seeds  {count: 1, id: "farmersdelight:cabbage_seeds"}
farmersdelight:iron_knife     {count: 1, id: "farmersdelight:iron_knife"}
farmersdelight:canvas         {count: 1, id: "farmersdelight:canvas"}
```

Three items is a sample, not a sweep — but it means the false-positive direction is not
obviously broken, and a pass claiming otherwise needs to name the item it saw.

**One trap this turned up:** `summon item ... {Item:{id:"farmersdelight:earthworm"}}` answered
`Summoned new Air`. A lang key is not proof of a registered item — `modded_items.py` reads
names out of `en_us.json`, and mods ship keys for things they did not register (or registered
conditionally). `Summoned new Air` is how that shows: not an error, just the wrong entity. Read
the summon reply, do not assume it worked.

### Dev-client texture loading (Pass 630, 2026-09-06)

The dev client's ResourceManager reload logs `Reloading ResourceManager: vanilla` ONLY —
in loom dev mode (`fabric.development=true`) the mod's resources are served from the
classpath (Fabric loader's mod container), and vanilla's ResourceManager does not include
them. Consequences:

- `SimpleTexture` lookups for `spoilage_enhanced:textures/...` throw FileNotFoundException;
  TextureManager logs `Missing resource ... referenced from ...` and caches the
  missing-texture image. The mold overlay renders as the magenta/black checker ONLY in dev.
- Lang files still load because `ClientLanguageMixin` reads them via
  `getResourceAsStream` (classpath), not the ResourceManager.
- Production is *usually* unaffected: Fabric registers each mod jar as a ModNioResourcePack,
  so the ResourceManager includes the mod namespace there.

Do not file the `Missing resource spoilage_enhanced:textures/overlay/mold_spots.png` WARN
as a defect **when it comes from a dev client** — it is a dev-mode artifact. The textures
really do ship: `mold_spots.png`, `mold_crust.png` and `mold_web.png` are all present in
`spoilage_enhanced_26.2-1.1.6.jar`, checked again 2026-09-10.

**Corrected 2026-09-10 — the "production is unaffected" half of this note was too strong,
and believing it would make you dismiss a real report.** Mod resource packs are registered by
`fabric-resource-loader`, which is part of **Fabric API** — and `fabric.mod.json` lists only
`fabricloader`, `minecraft` and `java` under `depends`. So on a plain Fabric Loader install
with no Fabric API, this mod's textures genuinely are missing at runtime, and a player
reporting a magenta-and-black checker there is reporting a real thing, not the dev artifact.
That is the case `client.SimpleTextureMixin` covers: fall back to `getResourceAsStream` from
the jar when the ResourceManager has no entry, the same route `ClientLanguageMixin` already
uses for lang files.

Before dismissing any texture report, ask which of the two it is: **dev client → artifact;
packaged jar with no Fabric API → real.** The two look identical in the log.

### Dev-client quickplay join time (Pass 1087, 2026-09-11)

`./gradlew.bat runClient -Pquickplayserver=localhost -Pplayername=Player499` connects cleanly to the dedicated server. However, asset loading (atlases, unifonts, sound engine, 79 pack mods) takes ~4-5 minutes on this machine. Do NOT treat silence after `Created: ... shulker_boxes.png-atlas` as a frozen/stuck client — wait up to 5 minutes for `Player499 logged in with entity id` in `run/fabric_server/logs/latest.log`. Once joined, all player commands (`give @p`, `data get entity @p`, inventory inspection) work fully via RCON.
