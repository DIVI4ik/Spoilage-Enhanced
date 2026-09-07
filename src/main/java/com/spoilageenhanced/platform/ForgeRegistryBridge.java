package com.spoilageenhanced.platform;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Registers the mod's data components on Fabric, Forge and NeoForge.
 *
 * <p>There is no single path that works everywhere — this is what each loader actually does in
 * 26.2, verified on real servers:
 *
 * <ul>
 *   <li><b>Fabric</b> — nothing but the vanilla registry exists. {@code DATA_COMPONENT_TYPE} is
 *       already frozen during mod init, so the frozen flag is lifted by reflection for the
 *       duration of the call and restored afterwards.</li>
 *   <li><b>NeoForge</b> — the same vanilla path works. Adding a {@code DeferredRegister} on top
 *       registers the id a second time on RegisterEvent and kills startup with
 *       {@code IllegalStateException: Adding duplicate key}.</li>
 *   <li><b>Forge</b> — refuses the vanilla path outright ("Can not register to a locked registry.
 *       Modder should use Forge Register methods"), and the failure is quiet: the component then
 *       looks fine in memory but every item carrying it fails to serialize with
 *       "Unregistered component". Forge therefore has to go through its own DeferredRegister.</li>
 * </ul>
 */
public class ForgeRegistryBridge {

    private static final String MOD_ID = "spoilage_enhanced";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Forge's DeferredRegister for DATA_COMPONENT_TYPE, created on demand (Forge only). */
    private static Object forgeDeferredRegister;

    public static <T> DataComponentType<T> registerDataComponent(String name, DataComponentType<T> type) {
        if (isForge()) {
            if (registerViaForge(name, type)) {
                return type;
            }
            log("Falling back to the vanilla registry after the Forge DeferredRegister path failed.");
        }
        return registerIntoVanillaRegistry(name, type);
    }

    // ======================== Fabric / NeoForge ========================

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> registerIntoVanillaRegistry(String name, DataComponentType<T> type) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);

        if (BuiltInRegistries.DATA_COMPONENT_TYPE.get(id).isPresent()) {
            // Already registered (e.g. class re-initialized): reuse the existing entry.
            Object existing = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            if (existing != null) {
                return (DataComponentType<T>) existing;
            }
        }

        boolean wasFrozen = false;
        Field frozenField = null;
        try {
            frozenField = MappedRegistry.class.getDeclaredField("frozen");
            frozenField.setAccessible(true);
            wasFrozen = (boolean) frozenField.get(BuiltInRegistries.DATA_COMPONENT_TYPE);
            if (wasFrozen) {
                frozenField.set(BuiltInRegistries.DATA_COMPONENT_TYPE, false);
            }
        } catch (Throwable t) {
            log("Could not unfreeze DATA_COMPONENT_TYPE (" + t + "); registration will likely fail.");
        }

        try {
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, type);
            bindHolder(id, type);
            log("Registered data component " + id + " into BuiltInRegistries.DATA_COMPONENT_TYPE.");
        } catch (Throwable t) {
            log("FAILED to register " + id + ": " + t);
        } finally {
            // Never call freeze(): with tags already loaded the game throws
            // "Tags already present before freezing". Restore the flag instead.
            if (wasFrozen && frozenField != null) {
                try {
                    frozenField.set(BuiltInRegistries.DATA_COMPONENT_TYPE, true);
                } catch (Throwable t) {
                    log("Could not restore the frozen flag on DATA_COMPONENT_TYPE: " + t);
                }
            }
        }

        return type;
    }

    /**
     * Binds the value on the freshly created {@link Holder.Reference} so serialization and
     * network codecs can resolve it.
     */
    private static <T> void bindHolder(Identifier id, DataComponentType<T> type) {
        var holder = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
        if (holder.isPresent() && holder.get() instanceof Holder.Reference<?> ref) {
            try {
                Method bindValue = Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
                bindValue.setAccessible(true);
                bindValue.invoke(ref, type);
            } catch (Throwable t) {
                log("Could not bind holder value for " + id + ": " + t);
            }
        }
    }

    // ======================== Forge ========================

    private static boolean isForge() {
        return classPresent("net.minecraftforge.registries.DeferredRegister");
    }

    /**
     * Queues the component in Forge's DeferredRegister and attaches that register to the mod event
     * bus. Must run from the {@code @Mod} constructor, which is where FMLJavaModLoadingContext is
     * valid.
     */
    private static <T> boolean registerViaForge(String name, DataComponentType<T> type) {
        try {
            Class<?> deferredRegisterClass = Class.forName("net.minecraftforge.registries.DeferredRegister");

            if (forgeDeferredRegister == null) {
                Method create = deferredRegisterClass.getMethod("create", ResourceKey.class, String.class);
                forgeDeferredRegister = create.invoke(null, Registries.DATA_COMPONENT_TYPE, MOD_ID);
            }

            Method register = deferredRegisterClass.getMethod("register", String.class, Supplier.class);
            register.invoke(forgeDeferredRegister, name, (Supplier<?>) () -> type);

            attachToForgeModBus(deferredRegisterClass);
            log("Queued data component " + MOD_ID + ":" + name + " in the Forge DeferredRegister.");
            return true;
        } catch (Throwable t) {
            log("Forge DeferredRegister registration failed: " + t);
            return false;
        }
    }

    private static void attachToForgeModBus(Class<?> deferredRegisterClass) throws Exception {
        Class<?> contextClass = Class.forName("net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext");
        Object context = contextClass.getMethod("get").invoke(null);
        // Forge 65 dispatches mod events through a BusGroup rather than an IEventBus.
        Object busGroup = contextClass.getMethod("getModBusGroup").invoke(context);

        for (Method method : deferredRegisterClass.getMethods()) {
            if (method.getName().equals("register")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].getSimpleName().equals("BusGroup")) {
                method.invoke(forgeDeferredRegister, busGroup);
                return;
            }
        }
        throw new IllegalStateException("DeferredRegister.register(BusGroup) not found on this Forge version");
    }

    // ======================== Diagnostics ========================

    /**
     * Re-checks that the component is resolvable through the registry. Both Forge and NeoForge
     * rebuild registries after mod construction, so a registration that looked fine can be missing
     * later — which otherwise only shows up as "Unregistered component" when items are saved.
     */
    public static void verifyRegistration(DataComponentType<?> type) {
        try {
            Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            int rawId = BuiltInRegistries.DATA_COMPONENT_TYPE.getId(type);
            if (key == null || rawId < 0) {
                log("WARNING: data component is NOT resolvable in the registry (key=" + key + ", rawId=" + rawId
                        + "). Items carrying spoilage data will fail to serialize on this loader.");
            } else {
                log("Data component check OK: " + key + " (raw id " + rawId + ")");
            }
        } catch (Throwable t) {
            log("Data component check failed: " + t);
        }
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, ForgeRegistryBridge.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void log(String message) {
        LOGGER.info("[SpoilageEnhanced] {}", message);
        try {
            SpoilageEnhancedLogger.log(message);
        } catch (Throwable ignored) {
            // The file logger may not exist this early; the SLF4J line above is enough.
        }
    }
}
