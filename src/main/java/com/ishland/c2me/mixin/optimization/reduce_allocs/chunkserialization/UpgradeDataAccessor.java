package com.ishland.c2me.mixin.optimization.reduce_allocs.chunkserialization;

import net.minecraft.util.math.EightWayDirection;
import net.minecraft.world.chunk.UpgradeData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.EnumSet;

@Mixin(UpgradeData.class)
public interface UpgradeDataAccessor {
    @Accessor
    int[][] getCenterIndicesToUpgrade();

    @Accessor
    EnumSet<EightWayDirection> getSidesToUpgrade();
}
