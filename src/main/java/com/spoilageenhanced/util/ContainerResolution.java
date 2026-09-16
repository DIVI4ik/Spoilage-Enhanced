package com.spoilageenhanced.util;

import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pass 1275: resolves a block entity to the {@link Container} whose contents the aging
 * sweep should age. Lives OUTSIDE the mixin (the same rule as {@code VillagerInventoryAging})
 * for two reasons: a mixin class cannot carry non-private static methods (the JVM-level
 * rule that broke the server when this logic was widened for testing — mixins may only
 * add private or @Unique members), and the logic is unit-testable here without a
 * MinecraftServer.
 *
 * <p>Two shapes are recognised, both universal contracts rather than mod lookups:</p>
 * <ul>
 * <li>the block entity itself implements {@code Container} (chest, barrel, shulker,
 * decorated pot, dispenser, dropper, and any modded container that does the same);</li>
 * <li>the block entity exposes its inventory through a public no-arg
 * {@code getContainer()} returning {@code Container} — the Balm convention
 * ({@code BalmContainerProvider}) used by Cooking for Blockheads' cookie jar, fruit
 * basket, spice rack, tool rack, counter and fridge. Those block entities do NOT
 * implement {@code Container} themselves, which is why the sweep missed them (pass
 * 1265, L14: a tracked apple in a cookie jar stayed fresh forever while the same apple
 * in a chest beside it rotted).</li>
 * </ul>
 *
 * <p>The reflection result is cached per block-entity class: the lookup happens once
 * per distinct class, then every later sweep pass is one map read. A class with no such
 * method is cached as a negative so it costs nothing after the first probe.</p>
 *
 * <p>No mod id, class name or item name appears here. Any present or future mod whose
 * block entity follows either convention is aged by the sweep without further work —
 * the generalisation test from the L14 rules.</p>
 */
public final class ContainerResolution {

    private ContainerResolution() {
    }

    /** Cache of {@code getContainer()} accessors per block-entity class. */
    private static final ConcurrentHashMap<Class<?>, Method> CONTAINER_GETTERS = new ConcurrentHashMap<>();

    /** Marker for "probed this class, it has no usable getContainer()" — distinguishes a cached negative from an absent entry. */
    private static final Method NEGATIVE = sentinelMethod();

    private static Method sentinelMethod() {
        try {
            // A Method that can never be a real getContainer() result: declared on this
            // utility class itself, private, and taking no arguments. Used only as a map
            // sentinel value, never invoked.
            return ContainerResolution.class.getDeclaredMethod("sentinelMethod");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Resolves the block entity to the container the sweep should age, or null when the
     * block entity follows neither convention.
     */
    public static Container asAgingContainer(BlockEntity blockEntity) {
        if (blockEntity instanceof Container direct) {
            return direct;
        }
        Class<?> clazz = blockEntity.getClass();
        Method method = CONTAINER_GETTERS.get(clazz);
        if (method == null) {
            try {
                Method found = clazz.getMethod("getContainer");
                if (!Container.class.isAssignableFrom(found.getReturnType())) {
                    found = null;
                }
                method = found != null ? found : NEGATIVE;
            } catch (NoSuchMethodException e) {
                method = NEGATIVE;
            }
            CONTAINER_GETTERS.put(clazz, method);
            if (method != NEGATIVE) {
                SpoilageEnhancedLogger.log("ContainerAgingSweep: aging contents of "
                        + clazz.getName() + " via getContainer() (not a vanilla Container)");
            }
        }
        if (method == NEGATIVE) {
            return null;
        }
        try {
            return (Container) method.invoke(blockEntity);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
