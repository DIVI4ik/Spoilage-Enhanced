package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BlockCountTest {
    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void blockRegistrySize() {
        System.out.println("[BENCH] vanilla block registry size: " + BuiltInRegistries.BLOCK.size());
    }
}
