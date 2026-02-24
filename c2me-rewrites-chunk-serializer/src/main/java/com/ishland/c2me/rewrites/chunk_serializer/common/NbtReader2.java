package com.ishland.c2me.rewrites.chunk_serializer.common;

import com.ishland.c2me.rewrites.chunk_serializer.common.utils.NbtUtils;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.nbt.*;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static com.ishland.c2me.rewrites.chunk_serializer.common.utils.NbtUtils.*;


public class NbtReader2 implements AutoCloseable {

    // Use UNALIGNED layouts because reads happen at arbitrary byte offsets.
    private static final ValueLayout.OfByte B = ValueLayout.JAVA_BYTE;
    private static final ValueLayout.OfShort S_BE = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt I_BE = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong L_BE = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat F_BE = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble D_BE = ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private static final boolean LOG_SKIPS = false;
    private static final Logger LOGGER = LogManager.getLogger("C2ME-serializer");

    // Root keys used by vanilla chunk NBT (the ones your fromNbt(...) reads)
    private static final byte[] STRING_DATA_VERSION = NbtUtils.getAsciiStringBytes("DataVersion");

    private final Arena arena;
    private final MemorySegment segment; // wraps the provided byte[] (zero-copy)
    private long pointer;
    private boolean inLooking = false;


    public NbtReader2(byte @NotNull [] data) {
        //segment = MemorySegment.ofArray(data);
        arena = Arena.ofConfined();
        segment = arena.allocate(data.length, 1);
        //is an auto arena as good as a deterministic management?
        MemorySegment.copy(MemorySegment.ofArray(data), 0, this.segment, 0, data.length);
        this.pointer = 0L;
    }

    public void skipBytes(long length) {
        this.pointer += length;
    }

    private byte readByte() {
        byte value = this.segment.get(B, this.pointer);
        this.pointer++;
        return value;
    }

    private void skipByte() {
        skipBytes(1);
    }

    private short readShort() {
        short value = this.segment.get(S_BE, this.pointer);
        this.pointer += 2;
        return value;
    }

    private void skipShort() {
        skipBytes(2);
    }

    private int readInt() {
        int value = this.segment.get(I_BE, this.pointer);
        this.pointer += 4;
        return value;
    }

    private void skipInt() {
        skipBytes(4);
    }

    private long readLong() {
        long value = this.segment.get(L_BE, this.pointer);
        this.pointer += 8;
        return value;
    }

    // Kept drop-in equivalent to your existing implementation (even though it looks like it should be 8).
    private void skipLong() {
        skipBytes(8);
    }

    private float readFloat() {
        float value = this.segment.get(F_BE, this.pointer);
        this.pointer += 4;
        return value;
    }

    private void skipFloat() {
        skipInt();
    }

    private double readDouble() {
        double value = this.segment.get(D_BE, this.pointer);
        this.pointer += 8;
        return value;
    }

    private void skipDouble() {
        skipLong();
    }

    public byte[] readBytes(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative byte array length: " + length);
        if (length == 0) return new byte[0];

        byte[] out = new byte[length];
        MemorySegment.copy(this.segment, this.pointer, MemorySegment.ofArray(out), 0, length);
        this.pointer += length;
        return out;
    }

    public byte[] readByteArray() {
        return readBytes(readInt());
    }

    public void skipByteArray() {
        int length = this.readInt();
        if (length < 0) throw new IllegalArgumentException("Negative byte array length: " + length);
        skipBytes(length);
    }

    public short[] readShorts(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative short array length: " + length);
        if (length == 0) return new short[0];
        short[] res = this.segment.asSlice(this.pointer, length * 2L).toArray(S_BE);
        this.pointer += length * 2L;
        return res;
    }

    public short[] readShortArray() {
        return readShorts(readInt());
    }

    public void skipShortArray() {
        int length = this.readInt();
        if (length < 0) throw new IllegalArgumentException("Negative short array length: " + length);

        skipBytes(length * 2L);
    }

    public int[] readInts(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative int array length: " + length);
        if (length == 0) return new int[0];
        int[] res = this.segment.asSlice(this.pointer, length * 4L).toArray(I_BE);
        this.pointer += length * 4L;
        return res;
    }

    public int[] readIntArray() {
        return readInts(readInt());
    }

    public void skipIntArray() {
        int length = this.readInt();
        if (length < 0) throw new IllegalArgumentException("Negative int array length: " + length);
        skipBytes(length * 4L);
    }

    public long[] readLongs(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative long array length: " + length);
        if (length == 0) return new long[0];
        long[] res = this.segment.asSlice(this.pointer, length * 8L).toArray(L_BE);
        this.pointer += length * 8L;
        return res;
    }

    public long[] readLongArray() {
        return readLongs(this.readInt());
    }

    public void skipLongArray() {
        int length = this.readInt();
        if (length < 0) throw new IllegalArgumentException("Negative long array length: " + length);
        skipBytes(length * 8L);
    }

    public float[] readFloats(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative float array length: " + length);
        if (length == 0) return new float[0];
        float[] res = this.segment.asSlice(this.pointer, length * 4L).toArray(F_BE);
        this.pointer += length * 4L;
        return res;
    }

    public float[] readFloatArray() {
        return readFloats(readInt());
    }

    public void skipFloatArray() {
        int length = this.readInt();
        if (length < 0) throw new IllegalArgumentException("Negative float array length: " + length);
        skipBytes(length * 4L);
    }

    public double[] readDoubles(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative double array length: " + length);
        if (length == 0) return new double[0];
        double[] res = this.segment.asSlice(this.pointer, length * 8L).toArray(D_BE);
        this.pointer += length * 8L;
        return res;
    }

    public double[] readDoubleArray() {
        return readDoubles(readInt());
    }

    public void skipDoubleArray() {
        int length = this.readInt();
        if (length < 0) throw new IllegalArgumentException("Negative double array length: " + length);
        skipBytes(length * 8L);
    }

    public byte @NotNull [] readStringEntryBytes() {
        int length = this.readUnsignedShort();
        if (length == 0) return new byte[]{0, 0};
        byte[] out = new byte[length + 2];
        out[0] = (byte) (length >> 8);
        out[1] = (byte) length;

        MemorySegment.copy(this.segment, this.pointer, MemorySegment.ofArray(out), 2, length);
        this.pointer += length;
        return out;
    }

    public String readString() {
        int length = this.readUnsignedShort();
        if (length == 0) return "";
        String value = decodeModifiedUtf8(this.segment, this.pointer, length);
        this.pointer += length;
        return value;
    }

    public void skipString() {
        int length = this.readUnsignedShort();
        this.skipBytes(length);
    }

    public byte readType() {
        return this.readByte();
    }

    public byte readListType() {
        return this.readByte();
    }

    public int readListSize() {
        return this.readInt();
    }

    private int readUnsignedShort() {
        return this.readShort() & 0xFFFF;
    }

    private static String decodeModifiedUtf8(MemorySegment seg, long offset, int length) {
        char[] chars = new char[length];
        int charCount = 0;
        int count = 0;

        while (count < length) {
            int c = seg.get(B, offset + count) & 0xFF;
            if (c <= 0x7F) {
                chars[charCount++] = (char) c;
                count++;
                continue;
            }
            if ((c & 0xE0) == 0xC0) {
                if (count + 1 >= length)
                    throw new IllegalArgumentException("Malformed input: partial character at end");
                int c2 = seg.get(B, offset + count + 1) & 0xFF;
                if ((c2 & 0xC0) != 0x80) throw new IllegalArgumentException("Malformed input around byte " + count);
                chars[charCount++] = (char) (((c & 0x1F) << 6) | (c2 & 0x3F));
                count += 2;
                continue;
            }
            if ((c & 0xF0) == 0xE0) {
                if (count + 2 >= length)
                    throw new IllegalArgumentException("Malformed input: partial character at end");
                int c2 = seg.get(B, offset + count + 1) & 0xFF;
                int c3 = seg.get(B, offset + count + 2) & 0xFF;
                if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) {
                    throw new IllegalArgumentException("Malformed input around byte " + count);
                }
                chars[charCount++] = (char) (((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F));
                count += 3;
                continue;
            }
            throw new IllegalArgumentException("Malformed input around byte " + count);
        }

        return new String(chars, 0, charCount);
    }

    public boolean matchesString(byte[] key) {
        long start = this.pointer;
        for (int i = 0; i < key.length; i++) {
            if (this.segment.get(B, start + i) != key[i]) return false;
        }
        this.skipBytes(key.length);
        return true;
    }

    public <T> @Nullable T readRegistry(Registry<T> registry) {
        Identifier id = readIdentifier();

        if (registry instanceof DefaultedRegistry<T>) {
            // Bypass the default :[
            var val = registry.getEntry(id);
//            noinspection OptionalIsPresent
            if (val.isEmpty()) {
                return null;
            }
            return val.get().value();
        }
        return registry.get(id);
    }

    public <T> @NotNull T readRegistryOrThrow(Registry<T> registry) {
        Identifier id = readIdentifier();

        if (registry instanceof DefaultedRegistry<T>) {
            // Bypass the default :[
            var val = registry.getEntry(id);
            if (val.isEmpty()) {
                throw new IllegalStateException("Unknown id in registry: " + id);
            }
            return val.get().value();
        }

        T t = registry.get(id);
        if (t == null) {
            throw new IllegalStateException("Unknown id in registry: " + id);
        }
        return t;

    }

    public Identifier readIdentifier() {
        return Identifier.of(readString());
    }

    public NbtElement getElement(byte type) {
        return switch (type) {
            case NbtElement.BYTE_TYPE -> NbtByte.of(readByte());
            case NbtElement.SHORT_TYPE -> NbtShort.of(readShort());
            case NbtElement.INT_TYPE -> NbtInt.of(readInt());
            case NbtElement.LONG_TYPE -> NbtLong.of(readLong());
            case NbtElement.FLOAT_TYPE -> NbtFloat.of(readFloat());
            case NbtElement.DOUBLE_TYPE -> NbtDouble.of(readDouble());
            case NbtElement.BYTE_ARRAY_TYPE -> new NbtByteArray(readByteArray());
            case NbtElement.STRING_TYPE -> NbtString.of(readString());
            case NbtElement.LIST_TYPE -> readList();
            case NbtElement.COMPOUND_TYPE -> readCompound();
            case NbtElement.INT_ARRAY_TYPE -> new NbtIntArray(readIntArray());
            case NbtElement.LONG_ARRAY_TYPE -> new NbtLongArray(readLongArray());
            default -> throw new IllegalStateException("Unknown tag type: " + type);
        };
    }

    public void skipElement(byte type) {
        switch (type) {
            case NbtElement.BYTE_TYPE -> skipByte();
            case NbtElement.SHORT_TYPE -> skipShort();
            case NbtElement.INT_TYPE -> skipInt();
            case NbtElement.LONG_TYPE -> skipLong();
            case NbtElement.FLOAT_TYPE -> skipFloat();
            case NbtElement.DOUBLE_TYPE -> skipDouble();
            case NbtElement.BYTE_ARRAY_TYPE -> skipByteArray();
            case NbtElement.STRING_TYPE -> skipString();
            case NbtElement.LIST_TYPE -> skipList();
            case NbtElement.COMPOUND_TYPE -> skipCompound();
            case NbtElement.INT_ARRAY_TYPE -> skipIntArray();
            case NbtElement.LONG_ARRAY_TYPE -> skipLongArray();
            default -> throw new IllegalStateException("Unknown tag type");
        }
        ;
    }

    public void skipElement() {
        skipElement(readByte());
    }

    public NbtCompound readCompound() {
        NbtCompound compound = new NbtCompound();
        while (true) {
            byte type = readByte();
            if (type == NbtElement.END_TYPE) {
                return compound;
            }
            compound.put(readString(), getElement(type));
        }
    }

    public void skipCompound() {
        while (true) {
            byte type = readByte();
            if (type == NbtElement.END_TYPE) {
                return;
            }
            skipString();
            skipElement(type);
        }
    }

    public List<NbtCompound> readCompoundList() {
        int length = readInt();
        if (length < 0) throw new IllegalArgumentException("Negative double array length: " + length);
        if (length == 0) return List.of();

        ArrayList<NbtCompound> out = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            out.add(readCompound());
        }
        return out;
    }

    public NbtList readList() {
        NbtList list = new NbtList();
        byte tag = readByte();
        int size = readInt();
        for (int i = 0; i < size; i++) {
            list.add(getElement(tag));
        }
        return list;
    }

    public void skipList(byte tag) {
        switch (tag) {
            case NbtElement.BYTE_TYPE -> skipByteArray();
            case NbtElement.SHORT_TYPE -> skipShortArray();
            case NbtElement.INT_TYPE -> skipIntArray();
            case NbtElement.LONG_TYPE -> skipLongArray();
            case NbtElement.FLOAT_TYPE -> skipFloatArray();
            case NbtElement.DOUBLE_TYPE -> skipDoubleArray();
            default -> {
                int length = readInt();
                for (int i = 0; i < length; i++) {
                    skipElement(tag);
                }
            }
        }
    }

    public void skipList() {
        skipList(readByte());
    }

    /**
     * Assumes the tag byte has been read already!!!!
     */
    public void skipCompoundEntry() {
        byte tagType = this.segment.get(B, this.pointer - 1);
        if (LOG_SKIPS && !inLooking) {
            var key = readString();
            var element = getElement(tagType);
            var elementString = element.toString();
            if (elementString.length() > 150) {
                elementString = elementString.substring(0, 150) + "...";
            }
            LOGGER.info("Skipped entry: \"" + key + "\" => " + elementString);
        } else {
            skipString();
            skipElement(tagType);
        }
    }

    public byte getByte(byte tag, byte fallback) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> (byte) readShort();
            case NbtElement.INT_TYPE -> (byte) readInt();
            case NbtElement.LONG_TYPE -> (byte) readLong();
            case NbtElement.FLOAT_TYPE -> (byte) MathHelper.floor(readFloat()); // Mojang does not follow java spec
            case NbtElement.DOUBLE_TYPE -> (byte) MathHelper.floor(readDouble()); // Mojang does not follow java spec
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> fallback;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public short getShort(byte tag, short fallback) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> (short) readInt();
            case NbtElement.LONG_TYPE -> (short) readLong();
            case NbtElement.FLOAT_TYPE -> (short) MathHelper.floor(readFloat());// Mojang does not follow java spec
            case NbtElement.DOUBLE_TYPE -> (short) MathHelper.floor(readDouble());// Mojang does not follow java spec
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> fallback;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public int getInt(byte tag, int fallback) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> readInt();
            case NbtElement.LONG_TYPE -> (int) readLong();
            case NbtElement.FLOAT_TYPE -> MathHelper.floor(readFloat());// Mojang does not follow java spec
            case NbtElement.DOUBLE_TYPE -> MathHelper.floor(readDouble());// Mojang does not follow java spec
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> fallback;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public long getLong(byte tag, long fallback) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> readInt();
            case NbtElement.LONG_TYPE -> readLong();
            case NbtElement.FLOAT_TYPE -> (long) readFloat();// YES NO FLOORING, MOJANG IS INSANE
            case NbtElement.DOUBLE_TYPE -> (long) Math.floor(readDouble());// Mojang does not follow java spec
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> fallback;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }


    public float getFloat(byte tag, float fallback) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> readInt();
            case NbtElement.LONG_TYPE -> (float) readLong();
            case NbtElement.FLOAT_TYPE -> readFloat();
            case NbtElement.DOUBLE_TYPE -> (float) readDouble();
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> fallback;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public double getDouble(byte tag, double fallback) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> readInt();
            case NbtElement.LONG_TYPE -> readLong();
            case NbtElement.FLOAT_TYPE -> readFloat();
            case NbtElement.DOUBLE_TYPE -> readDouble();
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> fallback;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }


    public byte getByteOrThrow(byte tag) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> (byte) readShort();
            case NbtElement.INT_TYPE -> (byte) readInt();
            case NbtElement.LONG_TYPE -> (byte) readLong();
            case NbtElement.FLOAT_TYPE -> (byte) MathHelper.floor(readFloat());
            case NbtElement.DOUBLE_TYPE -> (byte) MathHelper.floor(readDouble());
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> throw new IllegalStateException("Not a number");
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public short getShortOrThrow(byte tag) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> (short) readInt();
            case NbtElement.LONG_TYPE -> (short) readLong();
            case NbtElement.FLOAT_TYPE -> (short) MathHelper.floor(readFloat());
            case NbtElement.DOUBLE_TYPE -> (short) MathHelper.floor(readDouble());
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> throw new IllegalStateException("Not a number");
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public int getIntOrThrow(byte tag) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> readInt();
            case NbtElement.LONG_TYPE -> (int) readLong();
            case NbtElement.FLOAT_TYPE -> MathHelper.floor(readFloat());
            case NbtElement.DOUBLE_TYPE -> MathHelper.floor(readDouble());
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> throw new IllegalStateException("Not a number");
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public long getLongOrThrow(byte tag) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> readInt();
            case NbtElement.LONG_TYPE -> readLong();
            case NbtElement.FLOAT_TYPE -> (long) readFloat();// YES NO FLOORING, MOJANG IS INSANE
            case NbtElement.DOUBLE_TYPE -> (long) Math.floor(readDouble());
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> throw new IllegalStateException("Not a number");
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public float getFloatOrThrow(byte tag) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> readInt();
            case NbtElement.LONG_TYPE -> (float) readLong();
            case NbtElement.FLOAT_TYPE -> readFloat();
            case NbtElement.DOUBLE_TYPE -> (float) readDouble();
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> throw new IllegalStateException("Not a number");
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public double getDoubleOrThrow(byte tag) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE -> readByte();
            case NbtElement.SHORT_TYPE -> readShort();
            case NbtElement.INT_TYPE -> readInt();
            case NbtElement.LONG_TYPE -> readLong();
            case NbtElement.FLOAT_TYPE -> readFloat();
            case NbtElement.DOUBLE_TYPE -> readDouble();
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> throw new IllegalStateException("Not a number");
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public long @Nullable [] getLongArray(byte tag) {
        return switch (tag) {
            case NbtElement.END_TYPE -> {
                if (readListSize() == 0) {
                    yield new long[0];
                }
                throw new IllegalStateException("Encountered an illegal list");
            }
            case NbtElement.BYTE_TYPE -> toLongs(readByteArray());
            case NbtElement.SHORT_TYPE -> toLongs(readShortArray());
            case NbtElement.INT_TYPE -> toLongs(readIntArray());
            case NbtElement.LONG_TYPE -> readLongArray();
            case NbtElement.FLOAT_TYPE -> toLongs(readFloatArray());
            case NbtElement.DOUBLE_TYPE -> toLongs(readDoubleArray());
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE, -1 -> null;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public double @Nullable [] getDoubleArray(byte tag) {
        return switch (tag) {
            case NbtElement.END_TYPE -> {
                if (readListSize() == 0) {
                    yield new double[0];
                }
                throw new IllegalStateException("Encountered an illegal list");
            }
            case NbtElement.BYTE_TYPE -> toDoubles(readByteArray());
            case NbtElement.SHORT_TYPE -> toDoubles(readShortArray());
            case NbtElement.INT_TYPE -> toDoubles(readIntArray());
            case NbtElement.LONG_TYPE -> toDoubles(readLongArray());
            case NbtElement.FLOAT_TYPE -> toDoubles(readFloatArray());
            case NbtElement.DOUBLE_TYPE -> readDoubleArray();
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE,
                 -1 -> null;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }


    public byte listType(byte tag) {
        // -1 if not a list
        return switch (tag) {
            case NbtElement.BYTE_TYPE,
                 NbtElement.SHORT_TYPE,
                 NbtElement.INT_TYPE,
                 NbtElement.LONG_TYPE,
                 NbtElement.FLOAT_TYPE,
                 NbtElement.DOUBLE_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.COMPOUND_TYPE -> -1;

            case NbtElement.BYTE_ARRAY_TYPE -> NbtElement.BYTE_TYPE;
            case NbtElement.INT_ARRAY_TYPE -> NbtElement.INT_TYPE;
            case NbtElement.LONG_ARRAY_TYPE -> NbtElement.LONG_TYPE;

            case NbtElement.LIST_TYPE -> {
                byte subType = readByte();
                if (subType < 0 || subType > 12) {
                    throw new IllegalStateException("Unknown tag type: " + subType);
                }
                yield subType;
            }

            default -> throw new IllegalStateException("Unknown tag type: " + tag);
        };
    }

    public long @Nullable [] readLongStream(byte tag) {
        byte listType = listType(tag);
        if (listType == -1) return null;

        int length = readInt();
        if (length < 0) throw new IllegalArgumentException("Negative byte array length: " + length);
        if (length == 0) return new long[0];

        switch (listType) {
            case NbtElement.BYTE_TYPE -> {
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    out[i] = (long) this.readByte();
                }
                return out;
            }
            case NbtElement.SHORT_TYPE -> {
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    out[i] = (long) this.readShort();
                }
                return out;
            }
            case NbtElement.INT_TYPE -> {
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    out[i] = (long) this.readInt();
                }
                return out;
            }
            case NbtElement.LONG_TYPE -> {
                return this.readLongs(length);
            }
            case NbtElement.FLOAT_TYPE -> {
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    // This path uses java casting rules
                    out[i] = (long) this.readFloat();
                }
                return out;
            }
            case NbtElement.DOUBLE_TYPE -> {
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    // This path uses java casting rules
                    out[i] = (long) this.readDouble();
                }
                return out;
            }
            default -> {
                return null;
            }
        }
    }

    public int findDataVersion() {
        assert this.pointer == 0L; // Only allowed at the start
        this.inLooking = true;

        byte startTag = this.readByte();
        if (startTag != NbtElement.COMPOUND_TYPE) {
            throw new IllegalStateException("Your data is corrupted, I think :whoops:");
        }

        this.readString();
        long startPointer = this.pointer;

        while (true) {
            byte tag = this.readByte();
            if (tag == NbtElement.END_TYPE) {
                this.pointer = startPointer;
                this.inLooking = false;
                return -1;
            }

            if (this.matchesString(STRING_DATA_VERSION)) {
                int version = this.getInt(tag, -1);
                this.pointer = startPointer;
                this.inLooking = false;
                return version;
            } else {
                this.skipCompoundEntry();
            }
        }
    }

    @Override
    public void close() {
        this.arena.close();
    }

    static public boolean isNumeric(byte tag) {
        return switch (tag) {
            case NbtElement.BYTE_TYPE,
                 NbtElement.SHORT_TYPE,
                 NbtElement.INT_TYPE,
                 NbtElement.LONG_TYPE,
                 NbtElement.FLOAT_TYPE,
                 NbtElement.DOUBLE_TYPE -> true;

            case NbtElement.STRING_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE,
                 NbtElement.LIST_TYPE -> false;

            default -> throw new IllegalStateException("Unknown tag type: " + tag);
        };
    }
}
