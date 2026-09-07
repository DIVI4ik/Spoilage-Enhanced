package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockDropSpoilageHandler;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The popResource stamp path must accept a drop that IS the broken block, not only the
 * mapped food drop.
 *
 * <p>hay_block is tracked as wheat (the food it represents) but breaks into a hay_block
 * item. dried_kelp_block is tracked as dried_kelp but breaks into a dried_kelp_block item.
 * carved_pumpkin is tracked as pumpkin but breaks into a carved_pumpkin item. The after()
 * fallback already accepted both; the popResource path did not, so a tracked hay_block
 * broken on Fabric dropped a hay_block with no spoilage data — the tracked state was
 * silently lost.</p>
 *
 * <p>This test pins the fix by simulating the ThreadLocal state that before() sets and
 * calling stampPending with a stack whose item is the block itself. The stack must end
 * up with a SPOILAGE component.</p>
 */
public class StampPendingBlockIdTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getThreadLocal(String fieldName) throws Exception {
        Field f = BlockDropSpoilageHandler.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (T) f.get(null);
    }

    private static void setThreadLocal(String fieldName, Object value) throws Exception {
        ThreadLocal<?> tl = getThreadLocal(fieldName);
        // The ThreadLocal fields are private static; we set via reflection on the ThreadLocal itself.
        java.lang.reflect.Method set = ThreadLocal.class.getDeclaredMethod("set", Object.class);
        set.setAccessible(true);
        // We need the actual ThreadLocal instance, not the value. Re-fetch as ThreadLocal.
        Field f = BlockDropSpoilageHandler.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        ThreadLocal<Object> tlObj = (ThreadLocal<Object>) f.get(null);
        tlObj.set(value);
    }

    @Test
    void stampPendingAcceptsBlockItemDrop() throws Exception {
        // Simulate what before() sets for a tracked hay_block break:
        //   PENDING_SPOILAGE = [FRESH, expirationTime]
        //   PENDING_DROP_ID  = "minecraft:wheat"  (the mapped food)
        //   PENDING_BLOCK_ID = "minecraft:hay_block"  (the block itself)
        long expiration = 100000L;
        setThreadLocal("PENDING_SPOILAGE", new long[] { 0, expiration }); // 0 = FRESH
        setThreadLocal("PENDING_DROP_ID", "minecraft:wheat");
        setThreadLocal("PENDING_BLOCK_ID", "minecraft:hay_block");

        // The actual drop is a hay_block item (what vanilla drops from a hay_block break).
        ItemStack stack = new ItemStack(Items.HAY_BLOCK);

        BlockDropSpoilageHandler.stampPending(stack);

        // The stack must now carry the tracked state. Before the fix, stampPending saw
        // expectedId="minecraft:wheat" vs itemId="minecraft:hay_block" and returned without
        // stamping — the tracked state was silently lost.
        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(data, "stampPending must accept a drop that is the block itself (hay_block), not only the mapped food (wheat)");
        assertEquals(1, data.freshExpirations().size(), "one item dropped, one fresh tracker");
        assertEquals(expiration, data.freshExpirations().get(0), "the tracked expiration must flow to the drop");
        assertEquals(0, data.rottenCount(), "fresh drop must not be marked rotten");
    }

    @Test
    void stampPendingStillRejectsUnrelatedDrop() throws Exception {
        // A hay_block break must NOT stamp a stone item that happens to be nearby.
        setThreadLocal("PENDING_SPOILAGE", new long[] { 0, 100000L });
        setThreadLocal("PENDING_DROP_ID", "minecraft:wheat");
        setThreadLocal("PENDING_BLOCK_ID", "minecraft:hay_block");

        ItemStack stone = new ItemStack(Items.STONE);
        BlockDropSpoilageHandler.stampPending(stone);

        assertNull(stone.get(ModDataComponentTypes.SPOILAGE),
                "stone is neither the mapped food nor the block — must not be stamped");
    }
}
