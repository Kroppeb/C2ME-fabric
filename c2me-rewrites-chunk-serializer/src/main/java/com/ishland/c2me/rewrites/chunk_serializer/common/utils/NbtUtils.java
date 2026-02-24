package com.ishland.c2me.rewrites.chunk_serializer.common.utils;

import com.ishland.c2me.rewrites.chunk_serializer.common.NbtWriter;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;

import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NbtUtils {

    public static final byte[] STRING_DATA_VERSION = getAsciiStringBytes("DataVersion");
    public static final byte[] STRING_X_POS = getAsciiStringBytes("xPos");
    public static final byte[] STRING_Y_POS = getAsciiStringBytes("yPos");
    public static final byte[] STRING_Z_POS = getAsciiStringBytes("zPos");
    public static final byte[] STRING_LAST_UPDATE = getAsciiStringBytes("LastUpdate");
    public static final byte[] STRING_INHABITED_TIME = getAsciiStringBytes("InhabitedTime");
    public static final byte[] STRING_STATUS = getAsciiStringBytes("Status");
    public static final byte[] STRING_BLENDING_DATA = getAsciiStringBytes("blending_data");
    public static final byte[] STRING_BELOW_ZERO_RETROGEN = getAsciiStringBytes("below_zero_retrogen");
    public static final byte[] STRING_UPGRADE_DATA = getAsciiStringBytes("UpgradeData");
    public static final byte[] STRING_IS_LIGHT_ON = getAsciiStringBytes("isLightOn");
    public static final byte[] STRING_BLOCK_ENTITIES = getAsciiStringBytes("block_entities");
    public static final byte[] STRING_PALETTE = getAsciiStringBytes("palette");
    public static final byte[] STRING_DATA = getAsciiStringBytes("data");
    public static final byte[] STRING_SECTIONS = getAsciiStringBytes("sections");
    public static final byte[] STRING_BLOCK_STATES = getAsciiStringBytes("block_states");
    public static final byte[] STRING_BIOMES = getAsciiStringBytes("biomes");
    public static final byte[] STRING_BLOCK_LIGHT = getAsciiStringBytes("BlockLight");
    public static final byte[] STRING_SKY_LIGHT = getAsciiStringBytes("SkyLight");
    public static final byte[] STRING_OLD_NOISE = getAsciiStringBytes("old_noise");
    public static final byte[] STRING_HEIGHTS = getAsciiStringBytes("heights");
    public static final byte[] STRING_MIN_SECTION = getAsciiStringBytes("min_section");
    public static final byte[] STRING_MAX_SECTION = getAsciiStringBytes("max_section");
    public static final byte[] STRING_TARGET_STATUS = getAsciiStringBytes("target_status");
    public static final byte[] STRING_MISSING_BEDROCK = getAsciiStringBytes("missing_bedrock");
    public static final byte[] STRING_INDICES = getAsciiStringBytes("Indices");
    public static final byte[] STRING_SIDES = getAsciiStringBytes("Sides");
    public static final byte[] STRING_ENTITIES = getAsciiStringBytes("entities");
    public static final byte[] STRING_LIGHTS = getAsciiStringBytes("Lights");
    public static final byte[] STRING_CARVING_MASK = getAsciiStringBytes("carving_mask");
    public static final byte[] STRING_HEIGHTMAPS = getAsciiStringBytes("Heightmaps");
    public static final byte[] STRING_POST_PROCESSING = getAsciiStringBytes("PostProcessing");
    public static final byte[] STRING_BLOCK_TICKS = getAsciiStringBytes("block_ticks");
    public static final byte[] STRING_FLUID_TICKS = getAsciiStringBytes("fluid_ticks");
    public static final byte[] STRING_STRUCTURES = getAsciiStringBytes("structures");
    public static final byte[] STRING_STARTS = getAsciiStringBytes("starts");
    public static final byte[] STRING_BIG_REFERENCES = getAsciiStringBytes("References");
    public static final byte[] STRING_ID = getAsciiStringBytes("id");
    public static final byte[] STRING_CHUNK_X = getAsciiStringBytes("ChunkX");
    public static final byte[] STRING_CHUNK_Z = getAsciiStringBytes("ChunkZ");
    public static final byte[] STRING_SMALL_REFERENCES = getAsciiStringBytes("references");
    public static final byte[] STRING_CHILDREN = getAsciiStringBytes("Children");
    public static final byte[] STRING_INVALID = getAsciiStringBytes("INVALID");
    public static final byte[] STRING_BB = getAsciiStringBytes("BB");
    public static final byte[] STRING_O = getAsciiStringBytes("O");
    public static final byte[] STRING_GD = getAsciiStringBytes("GD");
    public static final byte[] STRING_NAME = getAsciiStringBytes("Name");
    public static final byte[] STRING_PROPERTIES = getAsciiStringBytes("Properties");

    public static final byte[] STRING_CHAR_BIG_Y = getAsciiStringBytes("Y");
    public static final byte[] STRING_CHAR_SMALL_I = getAsciiStringBytes("i");
    public static final byte[] STRING_CHAR_SMALL_P = getAsciiStringBytes("p");
    public static final byte[] STRING_CHAR_SMALL_T = getAsciiStringBytes("t");
    public static final byte[] STRING_CHAR_SMALL_X = getAsciiStringBytes("x");
    public static final byte[] STRING_CHAR_SMALL_Y = getAsciiStringBytes("y");
    public static final byte[] STRING_CHAR_SMALL_Z = getAsciiStringBytes("z");


    public static byte @NotNull [] getAsciiStringBytes(String string) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        for (byte aByte : bytes) {
            if (aByte <= 0) {
                throw new IllegalArgumentException("String contains invalid characters");
            }
        }
        return wrapAsciiBytes(bytes);
    }

    @NotNull
    private static byte[] wrapAsciiBytes(byte[] bytes) {
        byte[] wrappedBytes = new byte[bytes.length + 2];
        // store length in first 2 bytes
        wrappedBytes[0] = (byte) (bytes.length >> 8);
        wrappedBytes[1] = (byte) (bytes.length);
        System.arraycopy(bytes, 0, wrappedBytes, 2, bytes.length);
        return wrappedBytes;
    }
    public static byte @NotNull [] getStringBytes(String string) {
        ;
        byte[] res = new byte[string.length() * 3 + 2];
        int index = 2;
        for (char c : string.toCharArray()) {
            if (c >= '\u0001' && c <= '\u007f') {
                res[index++] = (byte) c;
            } else if (c <= '\u07ff') {
                res[index++] = (byte) (0xc0 | (0x1f & (c >> 6)));
                res[index++] = (byte) (0x80 | (0x3f & c));
            } else {
                res[index++] = (byte) (0xe0 | (0x0f & (c >> 12)));
                res[index++] = (byte) (0x80 | (0x3f & (c >> 6)));
                res[index++] = (byte) (0x80 | (0x3f & c));
            }
        }

        int length = index - 2;

        if (length > 65535) {
            throw new RuntimeException(new UTFDataFormatException("String too large"));
        }

        res[0] = (byte) (length >> 8);
        res[1] = (byte) (length);

        return Arrays.copyOf(res, index);
    }

    public static <T> byte @NotNull [] getNameBytesFromRegistry(Registry<T> registry, T value) {
        return getNameBytesFromId(registry.getId(value));
    }

    public static <T> byte @NotNull [] getNameBytesFromRegistry(RegistryEntry<T> value) {
        return getNameBytesFromId(value.getKey().get().getValue());
    }

    public static <T> byte @NotNull [] getNameBytesFromId(Identifier id) {
        if (id instanceof StringBytesConvertible stringBytesConvertible) {
            return stringBytesConvertible.getStringBytes();
        }
        return getAsciiStringBytes(id.toString());
    }


    public static long @NotNull [] toLongs(byte @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    public static long @NotNull [] toLongs(short @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    public static long @NotNull [] toLongs(int @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    public static long @NotNull [] toLongs(float @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (long) input[i];  // No flooring...
        }
        return output;
    }
    public static long @NotNull [] toLongs(double @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (long) Math.floor(input[i]);  // Flooring...
        }
        return output;
    }


    public static double @NotNull [] toDoubles(byte @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }


    public static double @NotNull [] toDoubles(short @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    public static double @NotNull [] toDoubles(int @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    public static double @NotNull [] toDoubles(long @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    public static double @NotNull [] toDoubles(float @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
}
