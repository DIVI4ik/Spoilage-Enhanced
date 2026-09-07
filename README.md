# Spoilage Enhanced

Food in Minecraft never goes off. This mod makes it.

Every edible item carries a freshness timer and moves through three stages — **fresh → stale →
rotten**. What that means in play: cooking, farming, storage and trading all start to matter,
because a full chest of steak is no longer food forever.

A single jar runs on **Fabric, Forge and NeoForge**. Minecraft **26.2**.

---

## What it does

**Three stages, per item.** A stack is not one timer — a stack of ten apples can genuinely be
five fresh and five rotten, and the game keeps track of both. Whenever items move, split, merge
or are handed over, the **worst** one goes first. That is deliberate: without it, splitting a
stack across chests would be a way to launder spoilage away.

**Eating it matters.** Stale food feeds you less and may bring nausea. Rotten food gives you
nothing and poisons you. Milk and cake have their own paths — a spoiled cake still poisons you
by the slice.

**Rotten food is not just loss.** It is the best compost in the game: a guaranteed composter
fill, where fresh food is worse than vanilla. Spoiled produce has a use.

**Farming has rules.** You cannot plant rotten seeds. A growing crop has no freshness clock at
all — it starts when the plant is actually ripe, so nothing can rot in the ground before you
harvest it. Dig one up early and you get rotten produce for your trouble.

**It works with other mods.** Food is detected automatically — from the item's own components,
from conventional `c:foods` tags, and from recipes. A fruiting tree whose fruit is picked by
hand, and therefore appears in no loot table, is learned from the harvest itself.

**Five languages**, loaded from your game's language setting with no resource pack needed:
English, Русский, Українська, Español (España), Español (Latinoamérica).

---

## Configuring it

`config/spoilage_enhanced.json` opens with a step-by-step guide. It covers how to make an item
spoil, how to give it a custom duration, how to stop something spoiling, and what every section
does — with worked examples. Anything you write there is never overwritten by auto-detection.

Apply changes in game with `/spoilage config reload` — no restart needed.

The global pace is one number: `spoilage_speed_multiplier`. `2.0` spoils twice as fast, `0.5`
twice as slow.

---

## Building from source

Requires **JDK 25**.

```bash
./gradlew build
```

The jar lands in `build/libs/`. Run `./gradlew test` for the unit suite, or
`./gradlew runClient` / `runServer` for a development instance.

## Installing

Drop the jar into `mods/`. On Fabric it needs the Fabric API.

## Licence

[MIT](LICENSE) — © 2026 DIVI4ik.

Use it, change it, ship it in a modpack, build something else on top of it. The one condition is
that the copyright notice and the licence text travel with any copy or substantial portion of
the code.
