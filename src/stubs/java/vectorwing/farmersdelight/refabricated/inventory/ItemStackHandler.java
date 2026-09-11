package vectorwing.farmersdelight.refabricated.inventory;

import net.minecraft.world.item.ItemStack;

/**
 * COMPILE STUB ONLY — never shipped. The real class comes from Farmer's Delight at runtime.
 *
 * <p>Farmer's Delight is a runtime-only dependency (L14 rule: zero foreign compile
 * dependencies), but {@code CookingPotBlockEntityMixin} needs to read the cooking pot's
 * inventory, and the pot exposes it only through this type. The stub carries exactly the two
 * methods the mixin calls ({@code getSlotCount}, {@code getStackInSlot}), verified against the
 * shipped jar with javap. At runtime the mixin's class reference resolves to the real class
 * from the mod jar; on an installation without Farmer's Delight the mixin itself does not
 * apply ({@code require = 0}), so the stub is never loaded.</p>
 *
 * <p>Same pattern as {@code net.minecraftforge.fml.common.Mod} in this source set: a
 * compile-time shape of a class that only exists at runtime.</p>
 */
public class ItemStackHandler {

    public int getSlotCount() {
        throw new AssertionError("stub");
    }

    public ItemStack getStackInSlot(int slot) {
        throw new AssertionError("stub");
    }
}
