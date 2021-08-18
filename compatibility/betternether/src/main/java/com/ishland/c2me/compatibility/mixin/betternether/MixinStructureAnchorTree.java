package com.ishland.c2me.compatibility.mixin.betternether;

import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import paulevs.betternether.structures.plants.StructureAnchorTree;

import java.util.HashSet;
import java.util.Set;

@Mixin(StructureAnchorTree.class)
public class MixinStructureAnchorTree {

    private static final ThreadLocal<Set<BlockPos>> BLOCKSThreadLocal = ThreadLocal.withInitial(() -> new HashSet<>(2048));

    @Dynamic
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lpaulevs/betternether/structures/plants/StructureAnchorTree;BLOCKS:Ljava/util/Set;", opcode = Opcodes.GETSTATIC), remap = false)
    private Set<BlockPos> redirectGetBLOCKS() {
        return BLOCKSThreadLocal.get();
    }

}