package vectorwing.farmersdelight.common.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler;

/**
 * COMPILE STUB ONLY — never shipped. The real class comes from Farmer's Delight at runtime.
 *
 * <p>Needed because a mixin's injected-method parameters must match the target's descriptor
 * exactly, and {@code CookingPotBlockEntity.cookingTick} takes this type as its fourth
 * parameter. {@code Object} and {@code BlockEntity} both fail with InvalidInjectionException
 * ("Expected ...CookingPotBlockEntity; but found ..."), measured on the live server.</p>
 *
 * <p>The stub carries only the shape the mixin touches: the constructor signature (so the
 * class compiles), {@code getInventory()} (shadowed by the mixin) and the static
 * {@code cookingTick} signature (so the mixin's parameter types resolve). At runtime the
 * mixin's references resolve to the real class from the mod jar; without Farmer's Delight
 * the mixin does not apply at all.</p>
 */
public abstract class CookingPotBlockEntity extends BlockEntity {

    protected CookingPotBlockEntity(BlockPos pos, BlockState state) {
        super(null, pos, state);
    }

    public ItemStackHandler getInventory() {
        throw new AssertionError("stub");
    }

    public static void cookingTick(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            BlockState state,
            CookingPotBlockEntity blockEntity) {
        throw new AssertionError("stub");
    }
}
