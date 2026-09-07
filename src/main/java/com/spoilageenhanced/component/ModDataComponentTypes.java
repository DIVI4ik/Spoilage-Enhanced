package com.spoilageenhanced.component;

import com.spoilageenhanced.platform.ForgeRegistryBridge;
import net.minecraft.core.component.DataComponentType;

public class ModDataComponentTypes {
    public static final DataComponentType<SpoilageData> SPOILAGE = register(
            "spoilage",
            DataComponentType.<SpoilageData>builder()
                    .persistent(SpoilageData.CODEC)
                    .networkSynchronized(SpoilageData.STREAM_CODEC)
                    .build()
    );

    private static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
        return ForgeRegistryBridge.registerDataComponent(name, type);
    }

    public static void initialize() {
        // Trigger static init
    }
}
