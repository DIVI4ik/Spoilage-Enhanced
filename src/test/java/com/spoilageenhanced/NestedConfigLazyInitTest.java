package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 328 regression test: SpoilageConfig nested config lazy init.
 *
 * <p>getEffectsConfig, getMilkEffectsConfig, getAnimalFeedingConfig, getComposterConfig,
 * getLootRandomizationConfig all use the same lazy-init pattern: if the field is null,
 * create a new one. This test pins the contract by nulling the fields via reflection
 * and verifying the getters return non-null values.</p>
 */
public class NestedConfigLazyInitTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void effectsConfigLazyInit() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        try {
            setField(config, "effects", null);
            assertNotNull(config.getEffectsConfig(),
                    "getEffectsConfig must return non-null even when effects field is null");
        } catch (Exception e) {
            fail("Failed to null effects field: " + e.getMessage());
        }
    }

    @Test
    void composterConfigLazyInit() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        try {
            setField(config, "composter", null);
            assertNotNull(config.getComposterConfig(),
                    "getComposterConfig must return non-null even when composter field is null");
        } catch (Exception e) {
            fail("Failed to null composter field: " + e.getMessage());
        }
    }

    @Test
    void lootRandomizationConfigLazyInit() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        try {
            setField(config, "loot_randomization", null);
            assertNotNull(config.getLootRandomizationConfig(),
                    "getLootRandomizationConfig must return non-null even when field is null");
        } catch (Exception e) {
            fail("Failed to null loot_randomization field: " + e.getMessage());
        }
    }

    @Test
    void milkEffectsConfigLazyInit() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        try {
            setField(config, "milk_effects", null);
            assertNotNull(config.getMilkEffectsConfig(),
                    "getMilkEffectsConfig must return non-null even when milk_effects field is null");
        } catch (Exception e) {
            fail("Failed to null milk_effects field: " + e.getMessage());
        }
    }

    @Test
    void animalFeedingConfigLazyInit() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        try {
            setField(config, "animal_feeding", null);
            assertNotNull(config.getAnimalFeedingConfig(),
                    "getAnimalFeedingConfig must return non-null even when animal_feeding field is null");
        } catch (Exception e) {
            fail("Failed to null animal_feeding field: " + e.getMessage());
        }
    }

    private static void setField(SpoilageConfig config, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = SpoilageConfig.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(config, value);
    }
}