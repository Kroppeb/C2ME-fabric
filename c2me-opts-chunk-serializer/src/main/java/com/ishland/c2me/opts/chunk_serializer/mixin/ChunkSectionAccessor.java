package com.ishland.c2me.opts.chunk_serializer.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkSection.class)
public interface ChunkSectionAccessor {
    @Accessor
    PalettedContainer<BlockState> getBlockStateContainer();

    @Accessor
    PalettedContainer<RegistryEntry<Biome>> getBiomeContainer();
}
