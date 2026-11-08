package buildcraft.additionalpipes.pipes;

import net.minecraft.world.item.ItemStack;

/** Modern Minecraft uses Data Components instead of the legacy NBT stack tags. */
public final class APJeweledItemMatcher {
    private APJeweledItemMatcher() { }
    public static boolean sameData(ItemStack expected, ItemStack actual) {
        // The old "match NBT" flag did not implicitly require matching damage/metadata.
        // Damage is a Data Component in modern Minecraft, so normalize it separately;
        // matchMetadata is enforced by SideFilterData's own comparison.
        ItemStack left = expected.copy();
        ItemStack right = actual.copy();
        if (left.isDamageableItem()) left.setDamageValue(0);
        if (right.isDamageableItem()) right.setDamageValue(0);
        return ItemStack.isSameItemSameComponents(left, right);
    }
}
