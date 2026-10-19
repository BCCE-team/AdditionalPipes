package buildcraft.additionalpipes.pipes;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;

public final class APAdvancedWoodStackIO {
    private APAdvancedWoodStackIO() { }

    public static void write(DataOutputStream output, ItemStack stack) throws IOException {
        output.writeBoolean(!stack.isEmpty());
        if (!stack.isEmpty()) NbtIo.write(stack.save(new CompoundTag()), output);
    }

    public static ItemStack read(DataInputStream input) throws IOException {
        return input.readBoolean() ? ItemStack.of(NbtIo.read(input)) : ItemStack.EMPTY;
    }
}
