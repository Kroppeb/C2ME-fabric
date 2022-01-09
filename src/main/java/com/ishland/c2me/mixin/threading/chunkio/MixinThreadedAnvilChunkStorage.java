package com.ishland.c2me.mixin.threading.chunkio;

import com.ibm.asyncutil.locks.AsyncNamedLock;
import com.ishland.c2me.common.GlobalExecutors;
import com.ishland.c2me.common.optimization.chunkserialization.ChunkDataSerializer;
import com.ishland.c2me.common.optimization.chunkserialization.NbtWriter;
import com.ishland.c2me.common.threading.chunkio.*;
import com.ishland.c2me.common.util.SneakyThrow;
import com.ishland.c2me.mixin.optimization.reduce_allocs.chunkserialization.StorageIoWorkerAccessor;
import com.ishland.c2me.mixin.optimization.reduce_allocs.chunkserialization.VersionedChunkStorageAccessor;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinThreadedAnvilChunkStorage extends VersionedChunkStorage implements ChunkHolder.PlayersWatchingChunkProvider, VersionedChunkStorageAccessor {

    public MixinThreadedAnvilChunkStorage(Path path, DataFixer dataFixer, boolean bl) {
        super(path, dataFixer, bl);
    }

    @Shadow
    @Final
    private ServerWorld world;

    @Shadow
    @Final
    private StructureManager structureManager;

    @Shadow
    @Final
    private PointOfInterestStorage pointOfInterestStorage;

    @Shadow
    protected abstract byte mark(ChunkPos chunkPos, ChunkStatus.ChunkType chunkType);

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    protected abstract void markAsProtoChunk(ChunkPos chunkPos);

    @Shadow
    @Final
    private Supplier<PersistentStateManager> persistentStateManagerFactory;

    @Shadow
    @Final
    private ThreadExecutor<Runnable> mainThreadExecutor;

    @Shadow
    protected abstract boolean isLevelChunk(ChunkPos chunkPos);

    @Shadow
    private ChunkGenerator chunkGenerator;

    @Shadow
    protected abstract boolean save(Chunk chunk);

    @Shadow
    protected abstract void save(boolean flush);

    private AsyncNamedLock<ChunkPos> chunkLock = AsyncNamedLock.createFair();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        chunkLock = AsyncNamedLock.createFair();
    }

    private Set<ChunkPos> scheduledChunks = new HashSet<>();

    /**
     * @author ishland
     * @reason async io and deserialization
     */
    @Overwrite
    private CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> loadChunk(ChunkPos pos) {
        if (scheduledChunks == null) scheduledChunks = new HashSet<>();
        synchronized (scheduledChunks) {
            if (scheduledChunks.contains(pos)) throw new IllegalArgumentException("Already scheduled");
            scheduledChunks.add(pos);
        }

        final CompletableFuture<NbtCompound> poiData = ((IAsyncChunkStorage) this.pointOfInterestStorage.worker).getNbtAtAsync(pos);

        final CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> future = getUpdatedChunkNbtAtAsync(pos).thenApplyAsync(compoundTag -> {
            if (compoundTag != null) {
                try {
                    if (compoundTag.contains("Status", 8)) {
                        ChunkIoMainThreadTaskUtils.push();
                        try {
                            return ChunkSerializer.deserialize(this.world, this.pointOfInterestStorage, pos, compoundTag);
                        } finally {
                            ChunkIoMainThreadTaskUtils.pop();
                        }
                    }

                    LOGGER.warn("Chunk file at {} is missing level data, skipping", pos);
                } catch (Throwable t) {
                    LOGGER.error("Couldn't load chunk {}, chunk data will be lost!", pos, t);
                }
            }
            return null;
        }, GlobalExecutors.executor).thenCombine(poiData, (protoChunk, tag) -> protoChunk).thenApplyAsync(protoChunk -> {
            ((ISerializingRegionBasedStorage) this.pointOfInterestStorage).update(pos, poiData.join());
            ChunkIoMainThreadTaskUtils.drainQueue();
            if (protoChunk != null) {
                this.mark(pos, protoChunk.getStatus().getChunkType());
                return Either.left(protoChunk);
            } else {
                this.markAsProtoChunk(pos);
                return Either.left(new ProtoChunk(pos, UpgradeData.NO_UPGRADE_DATA, this.world, this.world.getRegistryManager().get(Registry.BIOME_KEY), null));
            }
        }, this.mainThreadExecutor);
        future.exceptionally(throwable -> null).thenRun(() -> {
            synchronized (scheduledChunks) {
                scheduledChunks.remove(pos);
            }
        });
        return future;

        // [VanillaCopy] - for reference
        /*
        return CompletableFuture.supplyAsync(() -> {
         try {
            this.world.getProfiler().visit("chunkLoad");
            CompoundTag compoundTag = this.getUpdatedChunkNbt(pos);
            if (compoundTag != null) {
               boolean bl = compoundTag.contains("Level", 10) && compoundTag.getCompound("Level").contains("Status", 8);
               if (bl) {
                  Chunk chunk = ChunkSerializer.deserialize(this.world, this.structureManager, this.pointOfInterestStorage, pos, compoundTag);
                  this.method_27053(pos, chunk.getStatus().getChunkType());
                  return Either.left(chunk);
               }

               LOGGER.error((String)"Chunk file at {} is missing level data, skipping", (Object)pos);
            }
         } catch (CrashException var5) {
            Throwable throwable = var5.getCause();
            if (!(throwable instanceof IOException)) {
               this.method_27054(pos);
               throw var5;
            }

            LOGGER.error((String)"Couldn't load chunk {}", (Object)pos, (Object)throwable);
         } catch (Exception var6) {
            LOGGER.error((String)"Couldn't load chunk {}", (Object)pos, (Object)var6);
         }

         this.method_27054(pos);
         return Either.left(new ProtoChunk(pos, UpgradeData.NO_UPGRADE_DATA, this.world));
      }, this.mainThreadExecutor);
         */
    }

    private CompletableFuture<NbtCompound> getUpdatedChunkNbtAtAsync(ChunkPos pos) {
        return chunkLock.acquireLock(pos).toCompletableFuture().thenCompose(lockToken -> ((IAsyncChunkStorage) this.worker).getNbtAtAsync(pos).thenApply(compoundTag -> {
            if (compoundTag != null)
                return this.updateChunkNbt(this.world.getRegistryKey(), this.persistentStateManagerFactory, compoundTag, this.chunkGenerator.getCodecKey());
            else return null;
        }).handle((tag, throwable) -> {
            lockToken.releaseLock();
            if (throwable != null)
                SneakyThrow.sneaky(throwable);
            return tag;
        }));
    }

    private ConcurrentLinkedQueue<CompletableFuture<Void>> saveFutures = new ConcurrentLinkedQueue<>();

    @Dynamic
    @Redirect(method = "method_18843", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage;save(Lnet/minecraft/world/chunk/Chunk;)Z"))
    // method: consumer in tryUnloadChunk
    private boolean asyncSave(ThreadedAnvilChunkStorage tacs, Chunk chunk, ChunkHolder holder) {
        // TODO [VanillaCopy] - check when updating minecraft version
        this.pointOfInterestStorage.saveChunk(chunk.getPos());
        if (!chunk.needsSaving()) {
            return false;
        } else {
            chunk.setShouldSave(false);
            ChunkPos chunkPos = chunk.getPos();

            try {
                ChunkStatus chunkStatus = chunk.getStatus();
                if (chunkStatus.getChunkType() != ChunkStatus.ChunkType.LEVELCHUNK) {
                    if (this.isLevelChunk(chunkPos)) {
                        return false;
                    }

                    if (chunkStatus == ChunkStatus.EMPTY && chunk.getStructureStarts().values().stream().noneMatch(StructureStart::hasChildren)) {
                        return false;
                    }
                }

                final CompletableFuture<Chunk> originalSavingFuture = holder.getSavingFuture();
                if (!originalSavingFuture.isDone()) {
                    originalSavingFuture.handleAsync((_unused, __unused) -> asyncSave(tacs, chunk, holder), this.mainThreadExecutor);
                    return false;
                }

                this.world.getProfiler().visit("chunkSave");
                // C2ME start - async serialization
                if (saveFutures == null) saveFutures = new ConcurrentLinkedQueue<>();
                AsyncSerializationManager.Scope scope = new AsyncSerializationManager.Scope(chunk, world);

                saveFutures.add(chunkLock.acquireLock(chunk.getPos()).toCompletableFuture().thenCompose(lockToken ->
                        CompletableFuture.supplyAsync(() -> {
                                    scope.open();
                                    if (holder.getSavingFuture() != originalSavingFuture) {
                                        this.mainThreadExecutor.execute(() -> asyncSave(tacs, chunk, holder));
                                        throw new TaskCancellationException();
                                    }
                                    AsyncSerializationManager.push(scope);
                                    try {
                                        NbtWriter nbtWriter = new NbtWriter();
                                        nbtWriter.start(NbtElement.COMPOUND_TYPE);
                                        ChunkDataSerializer.write(this.world, chunk, nbtWriter);
                                        nbtWriter.finishCompound();
                                        return nbtWriter;
                                    } catch (Throwable e) {
                                        e.printStackTrace();
                                        throw e;
                                    } finally {
                                        AsyncSerializationManager.pop(scope);
                                    }
                                }, GlobalExecutors.executor)
                                .thenAccept(compoundTag -> {
                                    // this.setNbt(chunkPos, compoundTag);
                                    // temp fix, idk,
                                    var storageWorker = ((StorageIoWorkerAccessor) this.getIoWorker());
                                    var storage = storageWorker.getStorage();
                                    storageWorker.invokeRun(() -> {
                                        try {
                                            DataOutputStream chunkOutputStream = storage.getRegionFile(chunkPos).getChunkOutputStream(chunkPos);
                                            chunkOutputStream.write(compoundTag.toByteArray());
                                            chunkOutputStream.close();
                                            return Either.left((Void) null);
                                        } catch (Exception t) {
                                            return Either.right(t);
                                        }
                                    });
                                })
                                .handle((unused, throwable) -> {
                                    lockToken.releaseLock();
                                    if (throwable != null) {
                                        if (!(throwable instanceof TaskCancellationException)) {
                                            LOGGER.error("Failed to save chunk {},{} asynchronously, falling back to sync saving", chunkPos.x, chunkPos.z, throwable);
                                            final CompletableFuture<Chunk> savingFuture = holder.getSavingFuture();
                                            if (savingFuture != originalSavingFuture) {
                                                savingFuture.handleAsync((_unused, __unused) -> save(chunk), this.mainThreadExecutor);
                                            } else {
                                                this.mainThreadExecutor.execute(() -> this.save(chunk));
                                            }
                                        }
                                    }
                                    return unused;
                                })
                ));
                this.mark(chunkPos, chunkStatus.getChunkType());
                // C2ME end
                return true;
            } catch (Exception var5) {
                LOGGER.error((String) "Failed to save chunk {},{}", (Object) chunkPos.x, chunkPos.z, var5);
                return false;
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo info) {
        GlobalExecutors.executor.execute(() -> saveFutures.removeIf(CompletableFuture::isDone));
    }

    @Override
    public void completeAll() {
        final CompletableFuture<Void> future = CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]));
        this.mainThreadExecutor.runTasks(future::isDone); // wait for serialization to complete
        super.completeAll();
    }
}
