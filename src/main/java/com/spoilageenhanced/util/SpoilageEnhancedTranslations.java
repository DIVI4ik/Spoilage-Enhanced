package com.spoilageenhanced.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class SpoilageEnhancedTranslations {

    private SpoilageEnhancedTranslations() {}

    // ==================== Item Tooltips ====================
    public static final String TOOLTIP_STACK_DETAILS = "spoilage_enhanced.tooltip.stack_details";
    public static final String TOOLTIP_FRESH_COUNT = "spoilage_enhanced.tooltip.fresh_count";
    public static final String TOOLTIP_STALE_COUNT = "spoilage_enhanced.tooltip.stale_count";
    public static final String TOOLTIP_ROTTEN_COUNT = "spoilage_enhanced.tooltip.rotten_count";
    public static final String TOOLTIP_FRESH = "spoilage_enhanced.tooltip.fresh";
    /**
     * Worst state of a MIXED stack, deliberately without a count.
     *
     * <p>A mixed stack shows one summary line until the player holds Shift. Naming the count
     * here would give away the composition, which is exactly what the Shift hint promises to
     * reveal — the tooltip would be answering a question it had just offered to answer.</p>
     */
    public static final String TOOLTIP_STALE = "spoilage_enhanced.tooltip.stale";
    public static final String TOOLTIP_ROTTEN = "spoilage_enhanced.tooltip.rotten";
    public static final String TOOLTIP_ROTTEN_WITH_COUNT = "spoilage_enhanced.tooltip.rotten_with_count";
    public static final String TOOLTIP_STALE_WITH_COUNT = "spoilage_enhanced.tooltip.stale_with_count";
    public static final String TOOLTIP_SHIFT_DETAILS = "spoilage_enhanced.tooltip.shift_details";
    public static final String TOOLTIP_SPOILS_IN = "spoilage_enhanced.tooltip.spoils_in";
    public static final String TOOLTIP_NEXT_SPOIL = "spoilage_enhanced.tooltip.next_spoil";

    // ==================== HUD Block Spoilage ====================
    public static final String HUD_FRESH = "spoilage_enhanced.hud.fresh";
    public static final String HUD_FRESH_NO_TIMER = "spoilage_enhanced.hud.fresh_no_timer";
    public static final String HUD_STALE = "spoilage_enhanced.hud.stale";
    public static final String HUD_ROTTEN = "spoilage_enhanced.hud.rotten";
    public static final String HUD_CHECKING = "spoilage_enhanced.hud.checking";

    // ==================== Time Units ====================
    public static final String TIME_DAYS = "spoilage_enhanced.time.days";
    public static final String TIME_HOURS = "spoilage_enhanced.time.hours";
    public static final String TIME_MINUTES = "spoilage_enhanced.time.minutes";
    public static final String TIME_SECONDS = "spoilage_enhanced.time.seconds";
    public static final String TIME_LESS_THAN_MINUTE = "spoilage_enhanced.time.less_than_minute";

    // ==================== Commands — General ====================
    public static final String CMD_PLAYER_ONLY = "spoilage_enhanced.command.player_only";
    public static final String CMD_AIM_AT_BLOCK = "spoilage_enhanced.command.aim_at_block";
    public static final String CMD_ROTTEN_CANNOT_COOK = "spoilage_enhanced.command.rotten_cannot_cook";

    // ==================== Commands — /spoilage_enhanced speed ====================
    public static final String CMD_SPEED_CURRENT = "spoilage_enhanced.command.speed.current";
    public static final String CMD_SPEED_CHANGED = "spoilage_enhanced.command.speed.changed";
    public static final String CMD_SPEED_NORMAL = "spoilage_enhanced.command.speed.normal";
    public static final String CMD_SPEED_FASTER = "spoilage_enhanced.command.speed.faster";
    public static final String CMD_SPEED_SLOWER = "spoilage_enhanced.command.speed.slower";

    // ==================== Commands — /spoilage_enhanced debug ====================
    public static final String CMD_DEBUG_HELP = "spoilage_enhanced.command.debug.help";
    public static final String CMD_DEBUG_INSPECT_HEADER = "spoilage_enhanced.command.debug.inspect_header";
    public static final String CMD_DEBUG_BLOCK = "spoilage_enhanced.command.debug.block";
    public static final String CMD_DEBUG_TRACKING_STATUS = "spoilage_enhanced.command.debug.tracking_status";
    public static final String CMD_DEBUG_TRACKING_TRACKED = "spoilage_enhanced.command.debug.tracking_tracked";
    public static final String CMD_DEBUG_TRACKING_UNTRACKED = "spoilage_enhanced.command.debug.tracking_untracked";
    public static final String CMD_DEBUG_STATE_REMAINING = "spoilage_enhanced.command.debug.state_remaining";
    public static final String CMD_DEBUG_EXPIRATION_TICK = "spoilage_enhanced.command.debug.expiration_tick";
    public static final String CMD_DEBUG_ESTIMATED_FRESHNESS = "spoilage_enhanced.command.debug.estimated_freshness";
    public static final String CMD_DEBUG_CHUNK_BIRTH = "spoilage_enhanced.command.debug.chunk_birth";

    // ==================== Commands — /spoilage_enhanced debug chunk ====================
    public static final String CMD_DEBUG_CHUNK_HEADER = "spoilage_enhanced.command.debug.chunk_header";
    public static final String CMD_DEBUG_CHUNK_BIRTH_ABS = "spoilage_enhanced.command.debug.chunk_birth_abs";
    public static final String CMD_DEBUG_CHUNK_AGE = "spoilage_enhanced.command.debug.chunk_age";

    // ==================== Commands — /spoilage_enhanced debug dump ====================
    public static final String CMD_DEBUG_DUMP_SUCCESS = "spoilage_enhanced.command.debug.dump_success";
    public static final String CMD_DEBUG_DUMP_ERROR = "spoilage_enhanced.command.debug.dump_error";
    public static final String CMD_RENDERDUMP_STATUS = "spoilage_enhanced.command.renderdump.status";
    public static final String CMD_RENDERDUMP_SET = "spoilage_enhanced.command.renderdump.set";

    // ==================== Commands — /givespoiled ====================
    public static final String CMD_GIVESPOILED_NOT_SPOILABLE = "spoilage_enhanced.command.givespoiled.not_spoilable";
    public static final String CMD_GIVESPOILED_INVALID_STAGE = "spoilage_enhanced.command.givespoiled.invalid_stage";
    public static final String CMD_GIVESPOILED_SUCCESS = "spoilage_enhanced.command.givespoiled.success";

    // ==================== Commands — /spoilage logging ====================
    public static final String CMD_LOGGING_STATUS = "spoilage_enhanced.command.logging.status";
    public static final String CMD_LOGGING_SET = "spoilage_enhanced.command.logging.set";
    public static final String CMD_LOGGING_ENABLED = "spoilage_enhanced.command.logging.enabled";
    public static final String CMD_LOGGING_DISABLED = "spoilage_enhanced.command.logging.disabled";

    // ==================== Commands — /spoilage config reload ====================
    public static final String CMD_CONFIG_RELOADED = "spoilage_enhanced.command.config.reloaded";
    public static final String CMD_CONFIG_RELOAD_FAILED = "spoilage_enhanced.command.config.reload_failed";

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * Formats tick duration into in-game days, hours, and minutes (identical to 1.20.1).
     * 1 game day = 24000 ticks, 1 in-game hour = 1000 ticks.
     */
    /**
     * Pass 110 (Lens 13): memoized formatTime results. The tooltip calls formatTime every
     * frame for the "Spoils in" line, and each call ran the full translation pipeline up to
     * three times (Component.translatable + getString per unit). The result depends only on
     * the quantized (days, hours, minutes) triple, and the tooltip re-renders the same bucket
     * for seconds at a time — near-100% hit rate. Key is the packed triple; the map is capped
     * and cleared on language change via clearFormatTimeCache().
     */
    private static final java.util.Map<Long, String> FORMAT_TIME_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int FORMAT_TIME_CACHE_MAX = 512;

    public static String formatTime(long ticks) {
        if (ticks <= 0) return Component.translatable(TIME_LESS_THAN_MINUTE).getString();

        long days = ticks / 24000;
        long remainingAfterDays = ticks % 24000;
        long hours = remainingAfterDays / 1000;
        long remainingAfterHours = remainingAfterDays % 1000;
        long minutes = (remainingAfterHours * 60) / 1000;

        // Pass 110: pack the quantized triple and answer from the cache when present.
        long key = (days << 42) | (hours << 21) | minutes;
        String cached = FORMAT_TIME_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(Component.translatable(TIME_DAYS, days).getString()).append(" ");
        if (hours > 0)
            sb.append(Component.translatable(TIME_HOURS, hours).getString()).append(" ");
        if (minutes > 0 && days == 0)
            sb.append(Component.translatable(TIME_MINUTES, minutes).getString());
        if (sb.length() == 0)
            sb.append(Component.translatable(TIME_LESS_THAN_MINUTE).getString());

        String result = sb.toString().trim();
        if (FORMAT_TIME_CACHE.size() >= FORMAT_TIME_CACHE_MAX && !FORMAT_TIME_CACHE.containsKey(key)) {
            // Pass 604 (L3/L4 — cache correctness): the cap was declared but never enforced.
            // Once full, new entries were silently dropped. Evict one arbitrary entry to
            // make room — the next put for the evicted key will re-add it.
            var first = FORMAT_TIME_CACHE.keySet().iterator();
            if (first.hasNext()) {
                FORMAT_TIME_CACHE.remove(first.next());
            }
        }
        FORMAT_TIME_CACHE.put(key, result);
        return result;
    }

    /** Clears the memoized formatTime results — call on language change. */
    public static void clearFormatTimeCache() {
        FORMAT_TIME_CACHE.clear();
    }
}
