package net.blay09.mods.cookingforblockheads.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * COMPILE STUB ONLY — never shipped. The real class comes from Cooking for Blockheads at runtime.
 *
 * <p>Needed because a mixin's injected-method parameters must match the target's descriptor
 * exactly, and {@code CounterBlockEntity.serverTick} takes this type as its fourth parameter
 * (measured: {@code Object} and {@code BlockEntity} both fail with InvalidInjectionException).
 * The stub carries only the shape the mixin touches: the constructor, {@code getContainer()}
 * (which returns the vanilla {@code net.minecraft.world.Container} interface — no foreign stub
 * needed for the inventory itself), and the static {@code serverTick} signature. At runtime the
 * mixin's references resolve to the real class from the mod jar; without Cooking for Blockheads
 * the mixin does not apply at all.</p>
 */
public abstract class CounterBlockEntity extends BlockEntity {

    protected CounterBlockEntity(BlockPos pos, BlockState state) {
        super(null, pos, state);
    }

    public Container getContainer() {
        throw new AssertionError("stub");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            CounterBlockEntity blockEntity) {
        throw new AssertionError("stub");
    }
}