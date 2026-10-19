package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.service.APServices;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class APAdvancedWoodStackIO {
    private APAdvancedWoodStackIO() { }

    public static void write(DataOutputStream output, ItemStack stack) throws IOException {
        output.writeBoolean(!stack.isEmpty());
        if (stack.isEmpty()) return;
        Tag tag = ItemStack.CODEC.encodeStart(ops(), stack).result().orElseThrow(
            () -> new IOException("Cannot encode advanced wood item"));
        if (!(tag instanceof CompoundTag compound)) throw new IOException("Invalid item tag");
        NbtIo.write(compound, output);
    }

    public static ItemStack read(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return ItemStack.EMPTY;
        CompoundTag tag = NbtIo.read(input);
        return ItemStack.CODEC.parse(ops(), tag).result().orElseThrow(
            () -> new IOException("Cannot decode advanced wood item"));
    }

    private static RegistryOps<Tag> ops() throws IOException {
        MinecraftServer server = APServices.server();
        RegistryAccess access = server == null
            ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
            : server.registryAccess();
        return RegistryOps.create(NbtOps.INSTANCE, access);
    }
}
