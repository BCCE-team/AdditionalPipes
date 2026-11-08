package buildcraft.additionalpipes.pipes;

import net.minecraft.world.item.ItemStack;

/** Pre-1.21 Minecraft stores the item-specific comparison data in NBT. */
public final class APJeweledItemMatcher {
    private APJeweledItemMatcher() { }
    public static boolean sameData(ItemStack expected, ItemStack actual) {
        return ItemStack.isSameItemSameTags(expected, actual);
    }
}
