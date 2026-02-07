package com.ishland.c2me.rewrites.chunk_serializer.common;

import com.ishland.c2me.rewrites.chunk_serializer.common.utils.UnsafeUtils;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

public class NbtReader {
    private static final Unsafe UNSAFE = UnsafeUtils.UNSAFE;
    private static final int BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private static final boolean LOG_SKIPS = true;
    private static final Logger LOGGER = LogManager.getLogger("C2ME-serializer");

    private byte[] buffer;
    private long pointer;
    private long limit;
    // Root keys used by vanilla chunk NBT (the ones your fromNbt(...) reads)
// Root keys used by vanilla chunk NBT (the ones your fromNbt(...) reads)

    static private final byte[] STRING_DATA_VERSION = NbtWriter.getAsciiStringBytes("DataVersion");

    public NbtReader(byte @NotNull[] data) {
        this.reset(data);
    }

    public void reset(byte[] data) {
        this.buffer = data;
        this.pointer = BYTE_ARRAY_OFFSET;
        this.limit = BYTE_ARRAY_OFFSET + data.length;
    }

    public long getOffset() {
        return this.pointer - BYTE_ARRAY_OFFSET;
    }

    public byte start() {
        byte type = this.readByte();
        if (type != NbtElement.END_TYPE) {
            this.skipString();
        }
        return type;
    }

    private void ensureAvailable(long length) {
        if (this.pointer + length > this.limit) {
            throw new IndexOutOfBoundsException("Not enough bytes remaining");
        }
    }

    public void skipBytes(long length) {
        this.ensureAvailable(length);
        this.pointer += length;
    }

    public byte readByte() {
        this.ensureAvailable(1);
        byte value = UNSAFE.getByte(this.buffer, this.pointer);
        this.pointer++;
        return value;
    }

    public void skipByte() {
        skipBytes(1);
    }

    public short readShort() {
        this.ensureAvailable(2);
        short value = Short.reverseBytes(UNSAFE.getShort(this.buffer, this.pointer));
        this.pointer += 2;
        return value;
    }

    public void skipShort() {
        skipBytes(2);
    }

    public int readInt() {
        this.ensureAvailable(4);
        int value = Integer.reverseBytes(UNSAFE.getInt(this.buffer, this.pointer));
        this.pointer += 4;
        return value;
    }

    public void skipInt() {
        skipBytes(4);
    }

    public long readLong() {
        this.ensureAvailable(8);
        long value = Long.reverseBytes(UNSAFE.getLong(this.buffer, this.pointer));
        this.pointer += 8;
        return value;
    }

    public void skipLong() {
        skipBytes(4);
    }

    public boolean readBoolean() {
        return this.readByte() != 0;
    }

    public float readFloat() {
        return Float.intBitsToFloat(this.readInt());
    }

    public void skipFloat() {
        skipInt();
    }

    public double readDouble() {
        return Double.longBitsToDouble(this.readLong());
    }

    public void skipDouble() {
        skipLong();
    }

    public byte[] readBytes(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative byte array length: " + length);
        }
        if (length == 0) {
            return new byte[0];
        }
        this.ensureAvailable(length);
        byte[] out = new byte[length];
        UNSAFE.copyMemory(this.buffer, this.pointer, out, BYTE_ARRAY_OFFSET, length);
        this.pointer += length;
        return out;
    }

    public byte[] readByteArray() {
        return readBytes(readInt());
    }

    public void skipByteArray() {
        int length = this.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative byte array length: " + length);
        }
        skipBytes(length);
    }

    public short[] readShorts(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative short array length: " + length);
        }
        if (length == 0) {
            return new short[0];
        }
        this.ensureAvailable(length);
        short[] out = new short[length];
        for (int i = 0; i < length; i++) {
            out[i] = Short.reverseBytes(UNSAFE.getShort(this.buffer, this.pointer));
            this.pointer += 2;
        }
        return out;
    }

    public short[] readShortArray() {
        return readShorts(readInt());
    }

    public ShortArrayList readShortList() {
        // avoid copying the short array;
        int length = readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative short array length: " + length);
        }
        if (length == 0) {
            return new ShortArrayList();
        }
        this.ensureAvailable(length);
        ShortArrayList out = new ShortArrayList(length);
        for (int i = 0; i < length; i++) {
            out.add(Short.reverseBytes(UNSAFE.getShort(this.buffer, this.pointer)));
            this.pointer += 2;
        }
        return out;
    }

    public void skipShortArray() {
        int length = this.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative short array length: " + length);
        }
        skipBytes(length);
    }

    public int[] readInts(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative int array length: " + length);
        }
        if (length == 0) {
            return new int[0];
        }
        this.ensureAvailable(length * 4L);
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = Integer.reverseBytes(UNSAFE.getInt(this.buffer, this.pointer));
            this.pointer += 4;
        }
        return out;
    }

    public int[] readIntArray() {
        return readInts(readInt());
    }

    public void skipIntArray() {
        int length = this.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative int array length: " + length);
        }
        skipBytes(length * 4L);
    }

    public long[] readLongs(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative long array length: " + length);
        }
        if (length == 0) {
            return new long[0];
        }

        this.ensureAvailable(length * 8L);
        long[] out = new long[length];
        for (int i = 0; i < length; i++) {
            out[i] = Long.reverseBytes(UNSAFE.getLong(this.buffer, this.pointer));
            this.pointer += 8;
        }
        return out;
    }

    public long[] readLongArray() {
        return readLongs(this.readInt());
    }

    public void skipLongArray() {
        int length = this.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative long array length: " + length);
        }
        skipBytes(length * 8L);
    }


    public float[] readFloats(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative float array length: " + length);
        }
        if (length == 0) {
            return new float[0];
        }

        this.ensureAvailable(length * 4L);
        float[] out = new float[length];
        for (int i = 0; i < length; i++) {
            out[i] = Float.intBitsToFloat(Integer.reverseBytes(UNSAFE.getInt(this.buffer, this.pointer)));
            this.pointer += 4;
        }
        return out;
    }

    public float[] readFloatArray() {
        return readFloats(readInt());
    }

    public void skipFloatArray() {
        int length = this.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative float array length: " + length);
        }
        skipBytes(length * 4L);
    }

    public double[] readDoubles(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative double array length: " + length);
        }
        if (length == 0) {
            return new double[0];
        }

        this.ensureAvailable(length * 8L);
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            out[i] = Double.longBitsToDouble(Long.reverseBytes(UNSAFE.getLong(this.buffer, this.pointer)));
            this.pointer += 8;
        }
        return out;
    }

    public double[] readDoubleArray() {
        return readDoubles(readInt());
    }

    public void skipDoubleArray() {
        int length = this.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative double array length: " + length);
        }
        skipBytes(length * 8L);
    }

    public byte @NotNull [] readStringEntryBytes() {
        int length = this.readUnsignedShort();
        if (length == 0) {
            return new byte[]{0, 0};
        }
        this.ensureAvailable(length);
        byte[] out = new byte[length + 2];
        out[0] = (byte) (length >> 8);
        out[1] = (byte) length;
        UNSAFE.copyMemory(this.buffer, this.pointer, out, BYTE_ARRAY_OFFSET + 2L, length);
        this.pointer += length;
        return out;
    }

    public String readString() {
        int length = this.readUnsignedShort();
        if (length == 0) {
            return "";
        }
        this.ensureAvailable(length);
        String value = decodeModifiedUtf8(this.buffer, this.pointer, length);
        this.pointer += length;
        return value;
    }

    public void skipString() {
        int length = this.readUnsignedShort();
        this.skipBytes(length);
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

    private static String decodeModifiedUtf8(byte[] buffer, long offset, int length) {
        char[] chars = new char[length];
        int charCount = 0;
        int count = 0;

        while (count < length) {
            int c = UNSAFE.getByte(buffer, offset + count) & 0xFF;
            if (c <= 0x7F) {
                chars[charCount++] = (char) c;
                count++;
                continue;
            }
            if ((c & 0xE0) == 0xC0) {
                if (count + 1 >= length) {
                    throw new IllegalArgumentException("Malformed input: partial character at end");
                }
                int c2 = UNSAFE.getByte(buffer, offset + count + 1) & 0xFF;
                if ((c2 & 0xC0) != 0x80) {
                    throw new IllegalArgumentException("Malformed input around byte " + count);
                }
                chars[charCount++] = (char) (((c & 0x1F) << 6) | (c2 & 0x3F));
                count += 2;
                continue;
            }
            if ((c & 0xF0) == 0xE0) {
                if (count + 2 >= length) {
                    throw new IllegalArgumentException("Malformed input: partial character at end");
                }
                int c2 = UNSAFE.getByte(buffer, offset + count + 1) & 0xFF;
                int c3 = UNSAFE.getByte(buffer, offset + count + 2) & 0xFF;
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
        // key.length is known by switch(len), so no length checks needed here.
        long start = this.pointer;
        for (int i = 0; i < key.length; i++) {
            if (UNSAFE.getByte(this.buffer, start + i) != key[i]) return false;
        }
        this.skipBytes(key.length);
        return true;
    }

    public <T> T readRegistry(Registry<T> registry) {
        return registry.get(readIdentifier());
    }

    public Identifier readIdentifier() {
        return Identifier.of(readString());
    }


    public NbtElement readElement(byte type) {
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
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public NbtElement readElement() {
        return readElement(readByte());
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
            compound.put(readString(), readElement(type));
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
        if (length < 0) {
            throw new IllegalArgumentException("Negative double array length: " + length);
        }
        if (length == 0) {
            return List.of();
        }

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
            list.add(readElement(tag));
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
        byte tagType = UNSAFE.getByte(this.buffer, this.pointer - 1);
        if (LOG_SKIPS){
            LOGGER.info("Skipped entry: " + readString() + " => " + readElement(tagType));
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
            case NbtElement.FLOAT_TYPE -> (byte) MathHelper.floor(readFloat());
            case NbtElement.DOUBLE_TYPE -> (byte) MathHelper.floor(readDouble());
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
            case NbtElement.FLOAT_TYPE -> (short) MathHelper.floor(readFloat());
            case NbtElement.DOUBLE_TYPE -> (short) MathHelper.floor(readDouble());
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
            case NbtElement.FLOAT_TYPE -> MathHelper.floor(readFloat());
            case NbtElement.DOUBLE_TYPE -> MathHelper.floor(readDouble());
            case NbtElement.BYTE_ARRAY_TYPE,
                 NbtElement.STRING_TYPE,
                 NbtElement.LIST_TYPE,
                 NbtElement.COMPOUND_TYPE,
                 NbtElement.INT_ARRAY_TYPE,
                 NbtElement.LONG_ARRAY_TYPE -> fallback;
            default -> throw new IllegalStateException("Unknown tag type");
        };
    }



    public byte listType() {
        // -1 if not a list
        return switch (readByte()) {
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
                if(subType < 0 || subType > 12){
                    throw new IllegalStateException("Unknown tag type");
                }
                yield subType;
            }

            default -> throw new IllegalStateException("Unknown tag type");
        };
    }

    public long @Nullable [] readLongStream() {
        byte listType = listType();

        if(listType == -1){
            return null;
        }

        int length = readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative byte array length: " + length);
        }
        if (length == 0) {
            return new long[0];
        }

        switch (listType){
            case NbtElement.BYTE_TYPE -> {
                this.ensureAvailable(length);
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    byte value = UNSAFE.getByte(this.buffer, this.pointer);
                    this.pointer += 1;
                    out[i] = (long) value;
                }
                return out;
            }
            case NbtElement.SHORT_TYPE -> {
                this.ensureAvailable(length * 2L);
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    short value = Short.reverseBytes(UNSAFE.getShort(this.buffer, this.pointer));
                    this.pointer += 2;
                    out[i] = (long) value;
                }
                return out;
            }
            case NbtElement.INT_TYPE -> {
                this.ensureAvailable(length * 4L);
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    int value = Integer.reverseBytes(UNSAFE.getInt(this.buffer, this.pointer));
                    this.pointer += 4;
                    out[i] = (long) value;
                }
                return out;
            }
            case NbtElement.LONG_TYPE -> {
                this.ensureAvailable(length * 8L);
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    long value = Long.reverseBytes(UNSAFE.getLong(this.buffer, this.pointer));
                    this.pointer += 8;
                    out[i] = (long) value;
                }
                return out;
            }
            case NbtElement.FLOAT_TYPE -> {
                this.ensureAvailable(length * 4L);
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    float value = Float.intBitsToFloat(Integer.reverseBytes(UNSAFE.getInt(this.buffer, this.pointer)));
                    this.pointer += 4;
                    out[i] = (long) value;
                }
                return out;
            }
            case NbtElement.DOUBLE_TYPE -> {
                this.ensureAvailable(length * 8L);
                long[] out = new long[length];
                for (int i = 0; i < length; i++) {
                    double value = Double.longBitsToDouble(Long.reverseBytes(UNSAFE.getLong(this.buffer, this.pointer)));
                    this.pointer += 8;
                    out[i] = (long) value;
                }
                return out;
            }
            default -> {
                return null;
            }
        }
    }

    public int findDataVersion() {
        assert this.pointer == BYTE_ARRAY_OFFSET; // Only allowed at the start
        while(true){
            byte tag = this.readByte();
            if (tag == NbtElement.END_TYPE) {
                return -1;
            }
            if(this.matchesString(STRING_DATA_VERSION)){
                return this.getInt(tag, -1);
            }else{
                this.skipCompoundEntry();
            }
        }
    }
}
