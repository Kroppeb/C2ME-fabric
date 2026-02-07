package com.ishland.c2me.rewrites.chunk_serializer.mixin;

import com.ishland.c2me.base.common.scheduler.IVanillaChunkManager;
import com.ishland.c2me.base.common.theinterface.IDirectStorage;
import com.ishland.c2me.base.mixin.access.IVersionedChunkStorage;
import com.ishland.c2me.rewrites.chunk_serializer.common.ChunkDataDeserializer;
import com.ishland.c2me.rewrites.chunk_serializer.common.ChunkDataSerializer;
import com.ishland.c2me.rewrites.chunk_serializer.common.NbtReader;
import com.ishland.c2me.rewrites.chunk_serializer.common.NbtWriter;
import com.ishland.c2me.rewrites.chunk_serializer.common.utils.ValidationUtils;
import com.mojang.datafixers.DataFixer;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ChunkType;
import net.minecraft.world.chunk.SerializedChunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.storage.StorageIoWorker;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = ServerChunkLoadingManager.class, priority = 1099)
public abstract class MixinThreadedAnvilChunkStorage extends VersionedChunkStorage {
    @Final
    @Shadow
    private static Logger LOGGER;

    @Final
    @Shadow
    private PointOfInterestStorage pointOfInterestStorage;

    @Final
    @Shadow
    private ThreadExecutor<Runnable> mainThreadExecutor;

    @Final
    @Shadow
    ServerWorld world;

    public MixinThreadedAnvilChunkStorage(StorageKey storageKey, Path path, DataFixer dataFixer, boolean bl, DataFixTypes dataFixTypes) {
        super(storageKey, path, dataFixer, bl, dataFixTypes);
    }

    @Shadow
    private native boolean isLevelChunk(ChunkPos chunkPos);

    @Shadow
    private native byte mark(ChunkPos chunkPos, ChunkType chunkType);


    @Shadow
    protected abstract @Nullable ChunkHolder getCurrentChunkHolder(long pos);

    @Shadow
    @Final
    private AtomicInteger chunksBeingSavedCount;

    @Shadow
    native private CompletableFuture<Optional<NbtCompound>> getUpdatedChunkNbt(ChunkPos chunkPos);

    @Shadow
    native private Chunk getProtoChunk(ChunkPos chunkPos);

    @Shadow
    native private Chunk recoverFromException(Throwable throwable, ChunkPos chunkPos);

    @Shadow
    native private NbtCompound updateChunkNbt(NbtCompound nbt);

    @Shadow
    native protected ChunkGenerator getChunkGenerator();


    /**
     * @author Kroppeb
     * @reason Reduces allocations
     */
    @Overwrite()
    private boolean save(Chunk chunk) {
        // [VanillaCopy]
        this.pointOfInterestStorage.saveChunk(chunk.getPos());
        if (!chunk.tryMarkSaved()) {
            return false;
        }

        ChunkPos chunkPos = chunk.getPos();

        try {
            ChunkStatus chunkStatus = chunk.getStatus();
            if (chunkStatus.getChunkType() != ChunkType.LEVELCHUNK) {
                if (this.isLevelChunk(chunkPos)) {
                    return false;
                }

                if (chunkStatus == ChunkStatus.EMPTY && chunk.getStructureStarts().values().stream().noneMatch(StructureStart::hasChildren)) {
                    return false;
                }
            }

            Profilers.get().visit("chunkSave");

            this.chunksBeingSavedCount.incrementAndGet();
            SerializedChunk chunkSerializer = SerializedChunk.fromChunk(this.world, chunk);
            //region start replaced code
            // NbtCompound nbtCompound = ChunkSerializer.serialize(this.world, chunk);
            CompletableFuture<byte[]> serializationFuture = CompletableFuture.supplyAsync(() -> {
                NbtWriter nbtWriter = new NbtWriter();
                try {
                    nbtWriter.start(NbtElement.COMPOUND_TYPE);
                    ChunkDataSerializer.write(chunkSerializer, nbtWriter);
                    nbtWriter.finishCompound();
                    byte[] byteArray = nbtWriter.toByteArray();
                    ValidationUtils.validateNbt(byteArray);
                    return byteArray;
                } finally {
                    nbtWriter.release();
                }
            }, ((IVanillaChunkManager) this).c2me$getSchedulingManager().positionedExecutor(chunk.getPos().toLong()));

            CompletableFuture<Void> saveFuture = ((IDirectStorage) ((IVersionedChunkStorage) this).getWorker()).setRawChunkData(chunkPos, serializationFuture);

            saveFuture.handle((void_, exceptionx) -> {
                if (exceptionx != null) {
                    this.world.getServer().onChunkSaveFailure(exceptionx, this.getStorageKey(), chunkPos);
                }

                this.chunksBeingSavedCount.decrementAndGet();
                return null;
            });
            //endregion end replaced code

            this.mark(chunkPos, chunkStatus.getChunkType());
            return true;
        } catch (Exception var5) {
            LOGGER.error("Failed to save chunk {},{}", chunkPos.x, chunkPos.z, var5);
            return false;
        }
    }

    public boolean needsNbtUpgrading(
            NbtReader nbtReader
    ) {
        int i = nbtReader.findDataVersion();
        return i == SharedConstants.getGameVersion().dataVersion().id();
    }

    /**
     * @author Kroppeb
     * @reason Reduces allocations
     */
    @Overwrite()
    private CompletableFuture<Chunk> loadChunk(
            ChunkPos pos
    ) {
        CompletableFuture< byte @Nullable[]> data = ((IDirectStorage) ((IVersionedChunkStorage) this).getWorker()).readRawChunkData(pos);
        CompletableFuture<Optional< @Nullable SerializedChunk>> completableFuture = data.thenApplyAsync(rawData -> {
            if (rawData == null) return Optional.empty();
            NbtReader nbtReader = new NbtReader(rawData);

            SerializedChunk serializedChunk;
            if (this.needsNbtUpgrading(nbtReader)){
                // fallback to vanilla logic
                NbtCompound nbtCompound = nbtReader.readCompound();
                nbtCompound = this.updateChunkNbt(nbtCompound);
                serializedChunk = SerializedChunk.fromNbt(this.world, this.world.getPalettesFactory(), nbtCompound);
            }else {
                // Fastpath, vroom vroom
                serializedChunk = ChunkDataDeserializer.fromNbt(world, this.world.getPalettesFactory(), nbtReader);
            }

            if (serializedChunk == null) {
                LOGGER.error("Chunk file at {} is missing level data, skipping", pos);
            }

            // So what is mojang doing here, this confuses me?
            return Optional.of(serializedChunk);
        }, Util.getMainWorkerExecutor().named("parseChunk"));


        CompletableFuture<?> completableFuture2 = this.pointOfInterestStorage.load(pos);
        return completableFuture.thenCombine(completableFuture2, (optional, object) -> optional).thenApplyAsync(serializedChunk -> {
            Profilers.get().visit("chunkLoad");
            if (serializedChunk.isPresent()) {
                Chunk chunk = serializedChunk.get().convert(this.world, this.pointOfInterestStorage, this.getStorageKey(), pos);
                this.mark(pos, chunk.getStatus().getChunkType());
                return chunk;
            } else {
                return this.getProtoChunk(pos);
            }
        }, this.mainThreadExecutor).exceptionallyAsync(throwable -> this.recoverFromException(throwable, pos), this.mainThreadExecutor);
    }


}
