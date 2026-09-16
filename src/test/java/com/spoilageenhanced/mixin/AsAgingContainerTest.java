package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.ContainerResolution;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1275 regression test: the pass-1265 getContainer() reflection in
 * {@code ContainerResolution.asAgingContainer}.
 *
 * The sweep originally recognised only block entities that implement {@link Container}
 * directly. Cooking for Blockheads' block entities do not — they expose their inventory
 * through a public no-arg {@code getContainer()} (the Balm convention) — so food inside
 * them never aged. The fix resolves that method by reflection, cached per class with a
 * negative sentinel.
 *
 * These tests pin the CONTRACT, not the mod: a synthetic block entity following either
 * shape behaves correctly, and a class with no usable accessor is answered null. No
 * foreign jar is needed (the L14 pinning pattern from FruitingBlockRipenessTest).
 */
public class AsAgingContainerTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BlockState stoneState() {
        return Blocks.CHEST.defaultBlockState();
    }

    /** A block entity that implements Container directly — the vanilla chest shape. */
    static class DirectContainerBe extends BlockEntity implements Container {
        final SimpleContainer backing = new SimpleContainer(1);

        DirectContainerBe() {
            super(net.minecraft.world.level.block.entity.BlockEntityTypes.CHEST, BlockPos.ZERO, stoneState());
        }

        @Override
        public int getContainerSize() {
            return backing.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public net.minecraft.world.item.ItemStack getItem(int slot) {
            return backing.getItem(slot);
        }

        @Override
        public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) {
            return backing.removeItem(slot, amount);
        }

        @Override
        public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) {
            return backing.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
            backing.setItem(slot, stack);
        }

        @Override
        public void setChanged() {
            backing.setChanged();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return backing.stillValid(player);
        }

        @Override
        public void clearContent() {
            backing.clearContent();
        }
    }

    /** A block entity exposing its inventory via getContainer() — the Balm/C4B shape. */
    public static class GetterContainerBe extends BlockEntity {
        final SimpleContainer backing = new SimpleContainer(1);

        public GetterContainerBe() {
            super(net.minecraft.world.level.block.entity.BlockEntityTypes.CHEST, BlockPos.ZERO, stoneState());
        }

        public Container getContainer() {
            return backing;
        }
    }

    /** A block entity with a getContainer() whose return type is NOT a Container. */
    static class WrongReturnTypeBe extends BlockEntity {
        WrongReturnTypeBe() {
            super(net.minecraft.world.level.block.entity.BlockEntityTypes.CHEST, BlockPos.ZERO, stoneState());
        }

        public String getContainer() {
            return "not a container";
        }
    }

    /** A block entity with no accessor at all — must answer null, cached as negative. */
    static class NoAccessorBe extends BlockEntity {
        NoAccessorBe() {
            super(net.minecraft.world.level.block.entity.BlockEntityTypes.CHEST, BlockPos.ZERO, stoneState());
        }
    }

    @Test
    void directContainerBeReturnsItself() {
        DirectContainerBe be = new DirectContainerBe();
        assertSame(be, ContainerResolution.asAgingContainer(be),
                "a BlockEntity implementing Container must resolve to itself");
    }

    @Test
    void getterContainerBeReturnsItsBacking() {
        GetterContainerBe be = new GetterContainerBe();
        Container resolved = ContainerResolution.asAgingContainer(be);
        assertSame(be.backing, resolved,
                "a BlockEntity with a public getContainer() must resolve to that container");
    }

    @Test
    void wrongReturnTypeIsAnsweredNull() {
        assertNull(ContainerResolution.asAgingContainer(new WrongReturnTypeBe()),
                "a getContainer() returning a non-Container type must not resolve");
    }

    @Test
    void noAccessorIsAnsweredNull() {
        assertNull(ContainerResolution.asAgingContainer(new NoAccessorBe()),
                "a BlockEntity with no accessor must answer null");
    }

    @Test
    void repeatedCallsUseTheCache() {
        // Two instances of the same class: the second call must go through the cached
        // Method and still resolve correctly (the cache is keyed by class, not instance).
        GetterContainerBe first = new GetterContainerBe();
        GetterContainerBe second = new GetterContainerBe();
        assertSame(first.backing, ContainerResolution.asAgingContainer(first));
        assertSame(second.backing, ContainerResolution.asAgingContainer(second),
                "the cached accessor must be invoked per instance, not return one instance's container");
    }
}
