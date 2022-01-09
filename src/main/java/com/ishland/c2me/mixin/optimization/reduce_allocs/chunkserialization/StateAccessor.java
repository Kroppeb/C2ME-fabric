package com.ishland.c2me.mixin.optimization.reduce_allocs.chunkserialization;

import com.mojang.serialization.MapCodec;
import net.minecraft.state.State;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(State.class)
public interface StateAccessor<S> {
    @Accessor
    MapCodec<S> getCodec();
}
