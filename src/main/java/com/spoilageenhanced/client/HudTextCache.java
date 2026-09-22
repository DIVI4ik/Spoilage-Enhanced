package com.spoilageenhanced.client;

import net.minecraft.network.chat.Component;

/**
 * Pass 186: HUD text cache shared between BlockSpoilageHudMixin (client render path)
 * and ClientLanguageMixin (language change). Lives in a non-mixin class so both can
 * access it without mixin visibility rules blocking the call.
 *
 * <p>The cache holds a per-(spoilState, displayedTicks) Component + its measured
 * width + color. The Component re-resolves its language on render
 * (TranslatableContents.decompose checks the current language), so the TEXT is fine
 * after a language change — but the cached WIDTH was measured in the old language and
 * goes stale, mis-centering the HUD line. {@link #clear()} must be called on every
 * language (re)load alongside {@code SpoilageEnhancedTranslations.clearFormatTimeCache()}.</p>
 *
 * <p>Pass 1405 (L5 — render path): added a stable cache key for the "checking" placeholder
 * text that appears while waiting for the server answer. This text never changes, so it
 * can be cached with a single fixed key, eliminating per-frame allocation and font measurement.</p>
 */
public final class HudTextCache {

    public record CachedHudText(Component text, int width, int color) {}

    private static final int MAX_ENTRIES = 64;

    /** Stable cache key for the "checking freshness" placeholder text. */
    private static final long CHECKING_KEY = 0x7FFFFFFFFFFFFFFFL;

    private HudTextCache() {}

    // ConcurrentHashMap so the render thread and the language-load thread do not race.
    private static final java.util.Map<Long, CachedHudText> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static CachedHudText get(long key) {
        return CACHE.get(key);
    }

    public static void put(long key, CachedHudText value) {
        if (CACHE.size() >= MAX_ENTRIES && !CACHE.containsKey(key)) {
            // Pass 604 (L3/L4 — cache correctness): the cap was declared but never enforced.
            // Once full, new entries were silently dropped. Evict one arbitrary entry to
            // make room — the next put for the evicted key will re-add it.
            var first = CACHE.keySet().iterator();
            if (first.hasNext()) {
                CACHE.remove(first.next());
            }
        }
        CACHE.put(key, value);
    }

    public static void clear() {
        CACHE.clear();
    }

    /** Returns the stable cache key for the "checking" placeholder text. */
    public static long checkingKey() {
        return CHECKING_KEY;
    }
}