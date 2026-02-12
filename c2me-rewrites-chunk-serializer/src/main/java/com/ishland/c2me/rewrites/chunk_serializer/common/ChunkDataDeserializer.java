package com.ishland.c2me.rewrites.chunk_serializer.common;

import com.ishland.c2me.base.mixin.access.IThreadedAnvilChunkStorage;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.EmptyPaletteStorage;
import net.minecraft.util.collection.PackedIntegerArray;
import net.minecraft.util.collection.PaletteStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.tick.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.stream.LongStream;

@SuppressWarnings("JavadocReference")
public final class ChunkDataDeserializer {
    private static final Logger LOGGER = LogManager.getLogger();


    private static final byte[] STRING_DATA_VERSION = NbtWriter.getAsciiStringBytes("DataVersion");
    private static final byte[] STRING_X_POS = NbtWriter.getAsciiStringBytes("xPos");
    private static final byte[] STRING_Y_POS = NbtWriter.getAsciiStringBytes("yPos");
    private static final byte[] STRING_Z_POS = NbtWriter.getAsciiStringBytes("zPos");
    private static final byte[] STRING_LAST_UPDATE = NbtWriter.getAsciiStringBytes("LastUpdate");
    private static final byte[] STRING_INHABITED_TIME = NbtWriter.getAsciiStringBytes("InhabitedTime");
    private static final byte[] STRING_STATUS = NbtWriter.getAsciiStringBytes("Status");
    private static final byte[] STRING_BLENDING_DATA = NbtWriter.getAsciiStringBytes("blending_data");
    private static final byte[] STRING_BELOW_ZERO_RETROGEN = NbtWriter.getAsciiStringBytes("below_zero_retrogen");
    private static final byte[] STRING_UPGRADE_DATA = NbtWriter.getAsciiStringBytes("UpgradeData");
    private static final byte[] STRING_IS_LIGHT_ON = NbtWriter.getAsciiStringBytes("isLightOn");
    private static final byte[] STRING_BLOCK_ENTITIES = NbtWriter.getAsciiStringBytes("block_entities");
    private static final byte[] STRING_PALETTE = NbtWriter.getAsciiStringBytes("palette");
    private static final byte[] STRING_DATA = NbtWriter.getAsciiStringBytes("data");
    private static final byte[] STRING_SECTIONS = NbtWriter.getAsciiStringBytes("sections");
    private static final byte[] STRING_BLOCK_STATES = NbtWriter.getAsciiStringBytes("block_states");
    private static final byte[] STRING_BIOMES = NbtWriter.getAsciiStringBytes("biomes");
    private static final byte[] STRING_BLOCK_LIGHT = NbtWriter.getAsciiStringBytes("BlockLight");
    private static final byte[] STRING_SKY_LIGHT = NbtWriter.getAsciiStringBytes("SkyLight");
    private static final byte[] STRING_OLD_NOISE = NbtWriter.getAsciiStringBytes("old_noise");
    private static final byte[] STRING_HEIGHTS = NbtWriter.getAsciiStringBytes("heights");
    private static final byte[] STRING_MIN_SECTION = NbtWriter.getAsciiStringBytes("min_section");
    private static final byte[] STRING_MAX_SECTION = NbtWriter.getAsciiStringBytes("max_section");
    private static final byte[] STRING_TARGET_STATUS = NbtWriter.getAsciiStringBytes("target_status");
    private static final byte[] STRING_MISSING_BEDROCK = NbtWriter.getAsciiStringBytes("missing_bedrock");
    private static final byte[] STRING_INDICES = NbtWriter.getAsciiStringBytes("Indices");
    private static final byte[] STRING_SIDES = NbtWriter.getAsciiStringBytes("Sides");
    private static final byte[] STRING_ENTITIES = NbtWriter.getAsciiStringBytes("entities");
    private static final byte[] STRING_LIGHTS = NbtWriter.getAsciiStringBytes("Lights");
    private static final byte[] STRING_CARVING_MASK = NbtWriter.getAsciiStringBytes("carving_mask");
    private static final byte[] STRING_HEIGHTMAPS = NbtWriter.getAsciiStringBytes("Heightmaps");
    private static final byte[] STRING_POST_PROCESSING = NbtWriter.getAsciiStringBytes("PostProcessing");
    private static final byte[] STRING_BLOCK_TICKS = NbtWriter.getAsciiStringBytes("block_ticks");
    private static final byte[] STRING_FLUID_TICKS = NbtWriter.getAsciiStringBytes("fluid_ticks");
    private static final byte[] STRING_STRUCTURES = NbtWriter.getAsciiStringBytes("structures");
    private static final byte[] STRING_STARTS = NbtWriter.getAsciiStringBytes("starts");
    private static final byte[] STRING_BIG_REFERENCES = NbtWriter.getAsciiStringBytes("References");
    private static final byte[] STRING_ID = NbtWriter.getAsciiStringBytes("id");
    private static final byte[] STRING_CHUNK_X = NbtWriter.getAsciiStringBytes("ChunkX");
    private static final byte[] STRING_CHUNK_Z = NbtWriter.getAsciiStringBytes("ChunkZ");
    private static final byte[] STRING_SMALL_REFERENCES = NbtWriter.getAsciiStringBytes("references");
    private static final byte[] STRING_CHILDREN = NbtWriter.getAsciiStringBytes("Children");
    private static final byte[] STRING_INVALID = NbtWriter.getAsciiStringBytes("INVALID");
    private static final byte[] STRING_BB = NbtWriter.getAsciiStringBytes("BB");
    private static final byte[] STRING_O = NbtWriter.getAsciiStringBytes("O");
    private static final byte[] STRING_GD = NbtWriter.getAsciiStringBytes("GD");
    private static final byte[] STRING_NAME = NbtWriter.getAsciiStringBytes("Name");
    private static final byte[] STRING_PROPERTIES = NbtWriter.getAsciiStringBytes("Properties");

    private static final byte[] STRING_CHAR_BIG_Y = NbtWriter.getAsciiStringBytes("Y");
    private static final byte[] STRING_CHAR_SMALL_I = NbtWriter.getAsciiStringBytes("i");
    private static final byte[] STRING_CHAR_SMALL_P = NbtWriter.getAsciiStringBytes("p");
    private static final byte[] STRING_CHAR_SMALL_T = NbtWriter.getAsciiStringBytes("t");
    private static final byte[] STRING_CHAR_SMALL_X = NbtWriter.getAsciiStringBytes("x");
    private static final byte[] STRING_CHAR_SMALL_Y = NbtWriter.getAsciiStringBytes("y");
    private static final byte[] STRING_CHAR_SMALL_Z = NbtWriter.getAsciiStringBytes("z");


    private static final byte[] STRING_HEIGHTMAP_TYPE_WORLD_SURFACE_WG = ((HeightMapTypeAccessor) (Object) Heightmap.Type.WORLD_SURFACE_WG).getNameBytes();
    private static final byte[] STRING_HEIGHTMAP_TYPE_WORLD_SURFACE = ((HeightMapTypeAccessor) (Object) Heightmap.Type.WORLD_SURFACE).getNameBytes();
    private static final byte[] STRING_HEIGHTMAP_TYPE_OCEAN_FLOOR_WG = ((HeightMapTypeAccessor) (Object) Heightmap.Type.OCEAN_FLOOR_WG).getNameBytes();
    private static final byte[] STRING_HEIGHTMAP_TYPE_OCEAN_FLOOR = ((HeightMapTypeAccessor) (Object) Heightmap.Type.OCEAN_FLOOR).getNameBytes();
    private static final byte[] STRING_HEIGHTMAP_TYPE_MOTION_BLOCKING = ((HeightMapTypeAccessor) (Object) Heightmap.Type.MOTION_BLOCKING).getNameBytes();
    private static final byte[] STRING_HEIGHTMAP_TYPE_MOTION_BLOCKING_NO_LEAVES = ((HeightMapTypeAccessor) (Object) Heightmap.Type.MOTION_BLOCKING_NO_LEAVES).getNameBytes();

    static private boolean needsNbtUpgrading(
            NbtReader2 nbtReader
    ) {
        int i = nbtReader.findDataVersion();
        return i != SharedConstants.getGameVersion().dataVersion().id();
    }

    public static @Nullable SerializedChunk convert(
            byte @Nullable[] rawData,
            IThreadedAnvilChunkStorage tacs,
            ChunkPos pos
    ){
        if (rawData == null) {
            return null;
        }
        ServerWorld world = tacs.getWorld();

        try (NbtReader2 nbtReader = new NbtReader2(rawData)) {
            SerializedChunk serializedChunk;
            if (needsNbtUpgrading(nbtReader)) {
                LOGGER.warn("FRICK; FALLBACK, FALLBACK");
                // fallback to vanilla logic
                NbtCompound nbtCompound = nbtReader.readCompound();
                nbtCompound = tacs.invokeUpdateChunkNbt(nbtCompound);
                serializedChunk = SerializedChunk.fromNbt(world, world.getPalettesFactory(), nbtCompound);
            } else {
                // Fastpath, vroom vroom
                serializedChunk = ChunkDataDeserializer.fromNbt(world, world.getPalettesFactory(), nbtReader);
            }

            if (serializedChunk == null) {
                LOGGER.error("Chunk file at {} is missing level data, skipping", pos);
            }

            // So what is mojang doing here, this confuses me?
            return serializedChunk;
        }
    }

    /**
     * Mirror of {@link SerializedChunk#fromNbt(HeightLimitView, PalettesFactory, NbtCompound)}
     */
    public static SerializedChunk fromNbt(World world, PalettesFactory palettesFactory, NbtReader2 nbtReader) {
        int xPos = 0, zPos = 0;
        long lastUpdate = 0, inhabitedTime = 0;
        boolean isLightOn = false;

        ChunkStatus chunkStatus = ChunkStatus.EMPTY;
        UpgradeData upgradeData = UpgradeData.NO_UPGRADE_DATA;
        BlendingData.Serialized blendingData = null;
        BelowZeroRetrogen belowZeroRetrogen = null;
        long[] carvingMask = null;
        Map<Heightmap.Type, long[]> heightmaps = null; // null => empty
        List<Tick<Block>> blockTicks = List.of();
        List<Tick<Fluid>> fluidTicks = List.of();
        ShortList[] postProcessing = null;  // null => empty array
        List<NbtCompound> entities = List.of();
        List<NbtCompound> blockEntities = List.of();
        NbtCompound structures = null; // null => empty compound
        List<SerializedChunk.SectionData> sections = List.of();

        while (true) {
            switch (nbtReader.readType()) {
                case NbtElement.END_TYPE -> {
                    // compound end
                    // filter ticks
                    if (chunkStatus == ChunkStatus.EMPTY) {
                        return null;
                    }
                    if (heightmaps == null) {
                        heightmaps = new EnumMap<>(Heightmap.Type.class);
                    } else {
                        heightmaps.keySet().retainAll(chunkStatus.getHeightmapTypes());
                    }
                    ChunkPos chunkPos = new ChunkPos(xPos, zPos);

                    blockTicks = Tick.filter(blockTicks, chunkPos);
                    fluidTicks = Tick.filter(fluidTicks, chunkPos);

//                    LOGGER.info("WE LOADED A CHUNK!");
                    return new SerializedChunk(
                            palettesFactory,
                            chunkPos,
                            world.getBottomSectionCoord(),
                            lastUpdate,
                            inhabitedTime,
                            chunkStatus,
                            blendingData,
                            belowZeroRetrogen,
                            upgradeData,
                            carvingMask,
                            heightmaps,
                            new Chunk.TickSchedulers(blockTicks, fluidTicks),
                            postProcessing,
                            isLightOn,
                            sections,
                            entities,
                            blockEntities,
                            structures
                    );
                }
                case NbtElement.BYTE_TYPE -> {
                    if (nbtReader.matchesString(STRING_IS_LIGHT_ON)) {
                        isLightOn = nbtReader.getByte(NbtElement.BYTE_TYPE, (byte) 0) != 0;
                    } else {
                        // raise? skip?
                        nbtReader.skipCompoundEntry();
                    }
                }
                case NbtElement.INT_TYPE -> {
                    if (nbtReader.matchesString(STRING_X_POS)) {
                        xPos = nbtReader.getInt(NbtElement.INT_TYPE, 0);
                    } else if (nbtReader.matchesString(STRING_Z_POS)) {
                        zPos = nbtReader.getInt(NbtElement.INT_TYPE, 0);
                    } else {
                        // skip ypos
                        // raise? skip?
                        nbtReader.skipCompoundEntry();
                    }
                }
                case NbtElement.LONG_TYPE -> {
                    if (nbtReader.matchesString(STRING_INHABITED_TIME)) {
                        inhabitedTime = nbtReader.getLong(NbtElement.LONG_TYPE, 0L);
                    } else if (nbtReader.matchesString(STRING_LAST_UPDATE)) {
                        lastUpdate = nbtReader.getLong(NbtElement.LONG_TYPE, 0L);
                    } else {
                        // raise? skip?
                        nbtReader.skipCompoundEntry();
                    }
                }
                case NbtElement.STRING_TYPE -> {
                    if (nbtReader.matchesString(STRING_STATUS)) {
                        chunkStatus = nbtReader.readRegistry(Registries.CHUNK_STATUS);
                    } else {
                        nbtReader.skipCompoundEntry();
                    }
                }
                case NbtElement.LIST_TYPE -> {
                    if (nbtReader.matchesString(STRING_BLOCK_TICKS)) {
                        byte subType = nbtReader.readListType();
                        if (subType == NbtElement.COMPOUND_TYPE) {
                            blockTicks = readTicks(nbtReader, Registries.BLOCK);
                        } else {
                            // raise
                            nbtReader.skipList(subType);
                        }
                    } else if (nbtReader.matchesString(STRING_FLUID_TICKS)) {
                        byte subType = nbtReader.readListType();
                        if (subType == NbtElement.COMPOUND_TYPE) {
                            fluidTicks = readTicks(nbtReader, Registries.FLUID);
                        } else {
                            // raise
                            nbtReader.skipList(subType);
                        }
                    } else if (nbtReader.matchesString(STRING_POST_PROCESSING)) {
                        byte subType = nbtReader.readListType();
                        if (subType == NbtElement.LIST_TYPE) {
                            int length = nbtReader.readListSize();
                            postProcessing = new ShortList[length];
                            for (int i = 0; i < length; i++) {
                                byte subSubType = nbtReader.readListType();
                                int subLength = nbtReader.readListSize();
                                if (subLength == 0) {
                                    continue;
                                }
                                ShortList shorts = new ShortArrayList(subLength);
                                for (int j = 0; j < subLength; j++) {
                                    shorts.add(nbtReader.getShort(subSubType, (short) 0));
                                }
                                postProcessing[i] = shorts;
                            }
                        } else {
                            nbtReader.skipList(subType);
                        }
                    } else if (nbtReader.matchesString(STRING_ENTITIES)) {
                        byte subType = nbtReader.readListType();
                        if (subType == NbtElement.COMPOUND_TYPE) {
                            entities = nbtReader.readCompoundList();
                        } else {
                            nbtReader.skipList(subType);
                        }
                    } else if (nbtReader.matchesString(STRING_BLOCK_ENTITIES)) {
                        byte subType = nbtReader.readListType();
                        if (subType == NbtElement.COMPOUND_TYPE) {
                            blockEntities = nbtReader.readCompoundList();
                        } else {
                            nbtReader.skipList(subType);
                        }
                    } else if (nbtReader.matchesString(STRING_SECTIONS)) {
                        byte subType = nbtReader.readListType();
                        if (subType == NbtElement.COMPOUND_TYPE) {
                            sections = readSections(
                                    nbtReader,
                                    world,
                                    palettesFactory,
                                    world.getRegistryManager().getOrThrow(RegistryKeys.BIOME)
                            );
                        } else {
                            nbtReader.skipList(subType);
                        }
                    } else {
                        nbtReader.skipCompoundEntry();
                    }
                }
                case NbtElement.COMPOUND_TYPE -> {
                    if (nbtReader.matchesString(STRING_BLENDING_DATA)) {
                        blendingData = readBlendingData(nbtReader);
                    } else if (nbtReader.matchesString(STRING_UPGRADE_DATA)) {
                        // TODO: inline
                        upgradeData = new UpgradeData(nbtReader.readCompound(), world);
                    } else if (nbtReader.matchesString(STRING_BELOW_ZERO_RETROGEN)) {
                        belowZeroRetrogen = readBelowZeroRetrogen(nbtReader);
                    } else if (nbtReader.matchesString(STRING_HEIGHTMAPS)) {
                        heightmaps = readHeightmaps(nbtReader);

                    } else if (nbtReader.matchesString(STRING_STRUCTURES)) {
                        structures = nbtReader.readCompound();
                    } else {
                        // raise? skip?
                        nbtReader.skipCompoundEntry();
                    }
                }
                case NbtElement.LONG_ARRAY_TYPE -> {
                    // long array
                    if (nbtReader.matchesString(STRING_CARVING_MASK)) {
                        carvingMask = nbtReader.readLongArray();
                    } else {
                        // raise? skip?
                        nbtReader.skipCompoundEntry();
                    }
                }
                case NbtElement.SHORT_TYPE, NbtElement.FLOAT_TYPE, NbtElement.DOUBLE_TYPE, NbtElement.BYTE_ARRAY_TYPE,
                     NbtElement.INT_ARRAY_TYPE -> {
                    // raise? skip?
                    nbtReader.skipCompoundEntry();
                }
                default -> {
                    throw new IllegalStateException("Unknown tag type");
                }
            }
        }
    }

    private static BlendingData.Serialized readBlendingData(NbtReader2 nbtReader) {
        // min_section: Codec.INT, required
        // max_section: Codec.INT, required
        // heights: Codec.DOUBLE.listOf(), lenient optional (extra validation)

        byte seenMask = 0;  // bit 0 = minSection, bit 1 = maxSection
        int minSection = 0, maxSection = 0;
        double @Nullable [] heights = null;

        while (true) {
            byte tag = nbtReader.readType();
            if (tag == NbtElement.END_TYPE) {
                // compound end
                if (seenMask != 0b11) {
                    if ((seenMask & 0b01) == 0) {
                        throw new IllegalStateException("No key min_section");
                    } else {
                        throw new IllegalStateException("No key max_section");
                    }

                }
                if (heights != null && heights.length != BlendingData.HORIZONTAL_BIOME_COUNT) {
                    throw new IllegalStateException("heights has to be of length " + BlendingData.HORIZONTAL_BIOME_COUNT);
                }
                return new BlendingData.Serialized(minSection, maxSection, Optional.ofNullable(heights));
            }
            if (nbtReader.matchesString(STRING_MIN_SECTION)) {
                minSection = nbtReader.getIntOrThrow(tag);
                seenMask |= 0b01;
            } else if (nbtReader.matchesString(STRING_MAX_SECTION)) {
                maxSection = nbtReader.getIntOrThrow(tag);
                seenMask |= 0b10;
            } else if (nbtReader.matchesString(STRING_HEIGHTS)) {
                byte subType = nbtReader.listType(tag);
                heights = nbtReader.getDoubleArray(subType);
            } else {
                // skip
                nbtReader.skipCompoundEntry();
            }
        }
    }

    private static BelowZeroRetrogen readBelowZeroRetrogen(NbtReader2 nbtReader) {
        // target_status: Registries.CHUNK_STATUS.codec(), required (extra validation)
        // missing_bedrock: Codec.LONG_STREAM, lenient optional
        @Nullable ChunkStatus targetStatus = null;
        Optional<BitSet> missingBedrock = Optional.empty();

        while (true) {
            byte tag = nbtReader.readType();
            if (tag == NbtElement.END_TYPE) {
                if (targetStatus == null) {
                    throw new IllegalStateException("No key chunk_status");
                }
                return new BelowZeroRetrogen(targetStatus, missingBedrock);
            }

            if (nbtReader.matchesString(STRING_TARGET_STATUS)) {
                if (tag != NbtElement.STRING_TYPE) {
                    throw new IllegalStateException("Not a string");
                }
                targetStatus = nbtReader.readRegistry(Registries.CHUNK_STATUS);
                if (targetStatus == ChunkStatus.EMPTY) {
                    throw new IllegalStateException("target_status cannot be EMPTY");
                }
            } else if (nbtReader.matchesString(STRING_MISSING_BEDROCK)) {
                byte listType = nbtReader.listType(tag);
                long[] longs = nbtReader.getLongArray(listType);
                if (longs != null){
                    missingBedrock = Optional.of(BitSet.valueOf(longs));
                }
            } else if (nbtReader.matchesString(STRING_MISSING_BEDROCK)) {
                byte subType = nbtReader.readListType();
                if (subType == NbtEnd.LONG_TYPE) {
                    missingBedrock = Optional.of(BitSet.valueOf(nbtReader.readLongArray()));
                } else {
                    // skip, error
                    // this one is lenient so error => null
                    nbtReader.skipList(subType);
                }
            } else {
                nbtReader.skipCompoundEntry();
            }
        }
    }

    private static EnumMap<Heightmap.Type, long[]> readHeightmaps(NbtReader2 nbtReader) {
        EnumMap<Heightmap.Type, long[]> heightmaps = new EnumMap<>(Heightmap.Type.class);

        while (true) {
            byte tag = nbtReader.readType();
            if (tag == NbtElement.END_TYPE) {
                return heightmaps;
            }

            if (tag != NbtElement.LONG_ARRAY_TYPE) {
                // skip, error
                nbtReader.skipCompoundEntry();
            }

            if (nbtReader.matchesString(STRING_HEIGHTMAP_TYPE_WORLD_SURFACE_WG)) {
                heightmaps.put(Heightmap.Type.WORLD_SURFACE_WG, nbtReader.readLongArray());
            } else if (nbtReader.matchesString(STRING_HEIGHTMAP_TYPE_WORLD_SURFACE)) {
                heightmaps.put(Heightmap.Type.WORLD_SURFACE, nbtReader.readLongArray());
            } else if (nbtReader.matchesString(STRING_HEIGHTMAP_TYPE_OCEAN_FLOOR_WG)) {
                heightmaps.put(Heightmap.Type.OCEAN_FLOOR_WG, nbtReader.readLongArray());
            } else if (nbtReader.matchesString(STRING_HEIGHTMAP_TYPE_OCEAN_FLOOR)) {
                heightmaps.put(Heightmap.Type.OCEAN_FLOOR, nbtReader.readLongArray());
            } else if (nbtReader.matchesString(STRING_HEIGHTMAP_TYPE_MOTION_BLOCKING)) {
                heightmaps.put(Heightmap.Type.MOTION_BLOCKING, nbtReader.readLongArray());
            } else if (nbtReader.matchesString(STRING_HEIGHTMAP_TYPE_MOTION_BLOCKING_NO_LEAVES)) {
                heightmaps.put(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, nbtReader.readLongArray());
            } else {
                // skip, error
                nbtReader.skipCompoundEntry();
            }
        }
    }

    private static <T> List<Tick<T>> readTicks(NbtReader2 nbtReader, Registry<T> registry) {
        int length = nbtReader.readListSize();
        List<Tick<T>> ticks = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            ticks.add(readTick(nbtReader, registry));
        }
        return ticks;
    }

    private static <T> Tick<T> readTick(NbtReader2 nbtReader, Registry<T> registry) {
        byte seenMask = 0;  // pt_xyz
        int t = 0, x = 0, y = 0, z = 0, p = 0;
        T i = null;

        while (true) {
            switch (nbtReader.readType()) {
                case NbtElement.END_TYPE -> {
                    if (seenMask != 0b11_111) {
                        if ((seenMask & 0b10_000) == 0) {
                            throw new IllegalStateException("No key p");
                        } else if ((seenMask & 0b01_000) == 0) {
                            throw new IllegalStateException("No key t");
                        } else if ((seenMask & 0b00_100) == 0) {
                            throw new IllegalStateException("No key x");
                        } else if ((seenMask & 0b00_010) == 0) {
                            throw new IllegalStateException("No key y");
                        } else {
                            throw new IllegalStateException("No key z");
                        }
                    }
                    if (i == null) {
                        throw new IllegalStateException("No key i");
                    }
                    return new Tick<>(i, new BlockPos(x, y, z), t, TickPriority.byIndex(p));
                }
                case NbtElement.INT_TYPE -> {
                    if (nbtReader.matchesString(STRING_CHAR_SMALL_P)) {
                        p = nbtReader.getInt(NbtElement.INT_TYPE, 0);
                        seenMask |= 0b10_000;
                    } else if (nbtReader.matchesString(STRING_CHAR_SMALL_T)) {
                        t = nbtReader.getInt(NbtElement.INT_TYPE, 0);
                        seenMask |= 0b01_000;
                    } else if (nbtReader.matchesString(STRING_CHAR_SMALL_X)) {
                        x = nbtReader.getInt(NbtElement.INT_TYPE, 0);
                        seenMask |= 0b00_100;
                    } else if (nbtReader.matchesString(STRING_CHAR_SMALL_Y)) {
                        y = nbtReader.getInt(NbtElement.INT_TYPE, 0);
                        seenMask |= 0b00_010;
                    } else if (nbtReader.matchesString(STRING_CHAR_SMALL_Z)) {
                        z = nbtReader.getInt(NbtElement.INT_TYPE, 0);
                        seenMask |= 0b00_001;
                    } else {
                        nbtReader.skipCompoundEntry();
                    }
                }
                case NbtElement.STRING_TYPE -> {
                    if (nbtReader.matchesString(STRING_CHAR_SMALL_I)) {
                        i = nbtReader.readRegistry(registry);
                    } else {
                        nbtReader.skipCompoundEntry();
                    }
                }
                default -> {
                    nbtReader.skipCompoundEntry();
                }
            }
        }

    }

    private static List<SerializedChunk.SectionData> readSections(
            NbtReader2 nbtReader,
            HeightLimitView world,
            PalettesFactory palettesFactory,
            Registry<Biome> biomeRegistry
    ) {
        int length = nbtReader.readListSize();
        if (length == 0) {
            return List.of();
        }
        ArrayList<SerializedChunk.SectionData> data = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            data.add(readSection(
                    nbtReader,
                    world,
                    palettesFactory,
                    biomeRegistry
            ));
        }
        return data;
    }

    private static SerializedChunk.SectionData readSection(
            NbtReader2 nbtReader,
            HeightLimitView world,
            PalettesFactory palettesFactory,
            Registry<Biome> biomeRegistry
    ) {
        int y = 0;
        PalettedContainer<BlockState> blockStates = null;
        ReadableContainer<RegistryEntry<Biome>> biomes = null;
        ChunkNibbleArray blockLight = null;
        ChunkNibbleArray skyLight = null;

        while (true) {
            byte tag = nbtReader.readType();
            if (tag == NbtElement.END_TYPE) {
                ChunkSection chunkSection = null;
                if (y >= world.getBottomSectionCoord() && y <= world.getTopSectionCoord()) {
                    if (blockStates == null) {
                        blockStates = palettesFactory.getBlockStateContainer();
                    }
                    if (biomes == null) {
                        biomes = palettesFactory.getBiomeContainer();
                    }
                    chunkSection = new ChunkSection(blockStates, biomes);
                }

                return new SerializedChunk.SectionData(y, chunkSection, blockLight, skyLight);
            }

            if (nbtReader.matchesString(STRING_CHAR_BIG_Y)) {
                y = nbtReader.getByte(tag, (byte) 0);
            } else if (nbtReader.matchesString(STRING_BLOCK_STATES)) {
                if (tag == NbtElement.COMPOUND_TYPE) {
                    blockStates = readBlockStates(
                            nbtReader,
                            palettesFactory.blockStatesStrategy(),
                            BlockState.CODEC,
                            Blocks.AIR.getDefaultState()
                    );
                } else {
                    nbtReader.skipCompoundEntry();
                }
            } else if (nbtReader.matchesString(STRING_BIOMES)) {
                if (tag == NbtElement.COMPOUND_TYPE) {
                    RegistryEntry.Reference<Biome> reference = biomeRegistry.getOrThrow(BiomeKeys.PLAINS);
                    biomes = readBlockStatesBiomes(
                            nbtReader,
                            palettesFactory.biomeStrategy(),
                            (id) -> {
                                return biomeRegistry.getOptional(RegistryKey.of(biomeRegistry.getKey(), id)).orElse(null);
                            },
                            reference
                    );
                } else {
                    nbtReader.skipCompoundEntry();
                }
            } else if (nbtReader.matchesString(STRING_BLOCK_LIGHT)) {
                if (tag == NbtElement.BYTE_ARRAY_TYPE) {
                    blockLight = new ChunkNibbleArray(nbtReader.readByteArray());
                } else {
                    nbtReader.skipCompoundEntry();
                }
            } else if (nbtReader.matchesString(STRING_SKY_LIGHT)) {
                if (tag == NbtElement.BYTE_ARRAY_TYPE) {
                    skyLight = new ChunkNibbleArray(nbtReader.readByteArray());
                } else {
                    nbtReader.skipCompoundEntry();
                }
            } else {
                nbtReader.skipCompoundEntry();
            }
        }
    }

    /**
     * Mirror of {@link PalettesFactory#blockStatesContainerCodec}
     * created by {@link PalettedContainer#createPalettedContainerCodec(Codec, PaletteProvider, Object)}
     * {@link BlockState#CODEC} as entryCodec,
     * {@link PaletteProvider#forBlockStates}({@link Block.STATE_IDS}) as paletteProvider,
     * {@link Blocks#AIR}{@code .getDefaultState()} as defaultValue
     */
    private static <T> PalettedContainer<T> readBlockStatesBiomes(
            NbtReader2 nbtReader,
            PaletteProvider<T> provider,
            Function<Identifier, @Nullable T> lookup,
            T defaultValue
    ) {
        @Nullable List<T> paletteEntries = null;
        long @Nullable [] storage = null;

        while (true) {
            byte tag = nbtReader.readType();
            if (tag == NbtElement.END_TYPE) {
                if (paletteEntries == null) {
                    throw new IllegalStateException("missing key palette");
                }
                return read(provider, paletteEntries, storage);
            }

            if (nbtReader.matchesString(STRING_PALETTE)) {
                if (tag != NbtElement.LIST_TYPE) {
                    throw new IllegalStateException("Pallet should be a list");
                }
                byte subTag = nbtReader.readListType();
                if (subTag != NbtElement.STRING_TYPE) {
                    throw new IllegalStateException("Pallet should be a string list");
                }
                int length = nbtReader.readListSize();
                paletteEntries = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    var entry = lookup.apply(nbtReader.readIdentifier());
                    if (entry == null) {
                        // So minecraft will log an error here once per pallet
                        entry = defaultValue;
                    }
                    paletteEntries.add(entry);
                }
            } else if (nbtReader.matchesString(STRING_DATA)) {
                storage = nbtReader.readLongStream(tag);
            } else {
                nbtReader.skipCompoundEntry();
            }
        }

    }


    /**
     * Mirror of {@link PalettesFactory#blockStatesContainerCodec}
     * created by {@link PalettedContainer#createPalettedContainerCodec(Codec, PaletteProvider, Object)}
     * {@link BlockState#CODEC} as entryCodec,
     * {@link PaletteProvider#forBlockStates}({@link Block.STATE_IDS}) as paletteProvider,
     * {@link Blocks#AIR}{@code .getDefaultState()} as defaultValue
     */
    private static PalettedContainer<BlockState> readBlockStates(
            NbtReader2 nbtReader,
            PaletteProvider<BlockState> provider,
            Codec<BlockState> codec,
            BlockState defaultValue
    ) {
        @Nullable List<BlockState> paletteEntries = null;
        long @Nullable [] storage = null;

        while (true) {
            byte tag = nbtReader.readType();
            if (tag == NbtElement.END_TYPE) {
                if (paletteEntries == null) {
                    throw new IllegalStateException("missing key palette");
                }
                return read(provider, paletteEntries, storage);
            }

            if (nbtReader.matchesString(STRING_PALETTE)) {
                if (tag != NbtElement.LIST_TYPE) {
                    throw new IllegalStateException("Pallet should be a list");
                }
                byte subTag = nbtReader.readListType();
                if (subTag != NbtElement.COMPOUND_TYPE) {
                    throw new IllegalStateException("Blockstate pallet should be a compound list");
                }
                int length = nbtReader.readListSize();
                paletteEntries = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    var comp = nbtReader.readCompound();
                    var entryResult = codec.decode(NbtOps.INSTANCE, comp);
                    BlockState entry = entryResult.result().orElse(Pair.of(null, null)).getFirst();
                    if (entry == null) {
                        // So minecraft will log an error here once per pallet
                        entry = defaultValue;
                    }
                    paletteEntries.add(entry);
                }
            } else if (nbtReader.matchesString(STRING_DATA)) {
                storage = nbtReader.readLongStream(tag);
            } else {
                nbtReader.skipCompoundEntry();
            }
        }

    }

    /**
     * Mirror of {@link PalettedContainer#read(PaletteProvider, ReadableContainer.Serialized)}
     * But with the {@code serialized} parameter flattened and a nullable long array instead of a {@link LongStream}
     * and not wrapping the result
     */
    private static <T> PalettedContainer<T> read(
            @NotNull PaletteProvider<T> provider,
            @NotNull List<T> paletteEntries,
            long @Nullable [] storage
    ) {
        int i = provider.getSize();
        PaletteType paletteType = provider.createTypeFromSize(paletteEntries.size());
        int j = paletteType.bitsInStorage();

        PaletteStorage paletteStorage;
        Palette<T> palette;

        if (paletteType.bitsInMemory() == 0) {
            palette = paletteType.createPalette(provider, paletteEntries);
            paletteStorage = new EmptyPaletteStorage(i);
        } else {
            if (storage == null) {
                throw new IllegalStateException("Missing values for non-zero storage");
            }

            try {
                if (!paletteType.shouldRepack() && paletteType.bitsInMemory() == j) {
                    palette = paletteType.createPalette(provider, paletteEntries);
                    paletteStorage = new PackedIntegerArray(paletteType.bitsInMemory(), i, storage);
                } else {
                    Palette<T> palette2 = new BiMapPalette<>(j, paletteEntries);
                    PackedIntegerArray packedIntegerArray = new PackedIntegerArray(j, i, storage);
                    Palette<T> palette3 = paletteType.createPalette(provider, paletteEntries);
                    int[] is = PalettedContainer.repack(packedIntegerArray, palette2, palette3);
                    palette = palette3;
                    paletteStorage = new PackedIntegerArray(paletteType.bitsInMemory(), i, is);
                }
            } catch (PackedIntegerArray.InvalidLengthException var14) {
                throw new IllegalStateException("Failed to read PalettedContainer: " + var14.getMessage());
            }
        }

        return new PalettedContainer<>(provider, paletteType, paletteStorage, palette);
    }

    @SuppressWarnings("unchecked")
    @Contract("null -> null; !null -> !null")
    private static <T> T cast(Object entry) {
        return (T) entry;
    }
}