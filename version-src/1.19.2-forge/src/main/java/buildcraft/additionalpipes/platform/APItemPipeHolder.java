package buildcraft.additionalpipes.platform;

import buildcraft.transport.internal.pipe.PipeDefinition;
import buildcraft.transport.item.ItemPipeHolder;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Overrides the BCCE 1.19.2 pipe item's hard-coded BuildCraft creative tab. */
public final class APItemPipeHolder extends ItemPipeHolder {
    public APItemPipeHolder(PipeDefinition definition) {
        super(definition);
    }

    @Override
    public Collection<CreativeModeTab> getCreativeTabs() {
        return Collections.singletonList(APContentRegistry.creativeTab());
    }

    @Override
    public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
        // The search inventory must still be able to find every AP pipe.
        if (tab == APContentRegistry.creativeTab() || tab == CreativeModeTab.TAB_SEARCH) {
            items.add(getDefaultInstance());
        }
    }
}
