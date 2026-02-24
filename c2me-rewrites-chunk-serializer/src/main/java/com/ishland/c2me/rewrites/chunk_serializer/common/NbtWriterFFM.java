package com.ishland.c2me.rewrites.chunk_serializer.common;


import com.ishland.c2me.base.mixin.access.INbtList;
import com.ishland.c2me.rewrites.chunk_serializer.common.utils.StringBytesConvertible;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterable;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.UTFDataFormatException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static com.ishland.c2me.rewrites.chunk_serializer.common.utils.NbtUtils.getNameBytesFromRegistry;
import static com.ishland.c2me.rewrites.chunk_serializer.common.utils.NbtUtils.getStringBytes;

@SuppressWarnings("WeakerAccess")
public class NbtWriterFFM implements AutoCloseable {

    private static final int INCREMENT = 2;
    private static final long INITIAL_SIZE = 1024L * 64L;

    // Use UNALIGNED layouts because writes happen at arbitrary byte offsets.
    private static final ValueLayout.OfByte B = ValueLayout.JAVA_BYTE;
    private static final ValueLayout.OfShort S_BE = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt I_BE = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong L_BE = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat F_BE = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble D_BE = ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final byte[] EMPTY_NAME = new byte[]{0, 0};

    private final Arena arena;
    private MemorySegment buffer;
    private long size;
    private long remaining;
    private long pointer;

    public NbtWriterFFM() {
        this.arena = Arena.ofConfined();
        this.size = INITIAL_SIZE;
        this.buffer = this.arena.allocate(this.size, 1);
        this.remaining = this.size;
        this.pointer = 0L;
    }

    private void claimCapacity(long extra) {
        this.remaining -= extra;
        while (this.remaining < 0) {
            this.remaining += this.size * (INCREMENT - 1L);
            this.size *= INCREMENT;

            MemorySegment newBuffer = this.arena.allocate(this.size, 1);
            MemorySegment.copy(this.buffer, 0, newBuffer, 0, this.pointer);
            this.buffer = newBuffer;
        }
    }

    public long getOffset() {
        return this.pointer;
    }

    public void start(byte type) {
        this.putByteEntry(type);
        if (type != NbtElement.END_TYPE) {
            this.putShortEntry((short) 0);
        }
    }

    private void insertByte(byte value) {
        this.buffer.set(B, this.pointer, value);
        this.pointer++;
    }

    private void insertShort(short value) {
        this.buffer.set(S_BE, this.pointer, value);
        this.pointer += 2;
    }

    private void insertInt(int value) {
        this.buffer.set(I_BE, this.pointer, value);
        this.pointer += 4;
    }

    private void insertLong(long value) {
        this.buffer.set(L_BE, this.pointer, value);
        this.pointer += 8;
    }

    private void insertFloat(float value) {
        this.buffer.set(F_BE, this.pointer, value);
        this.pointer += 4;
    }

    private void insertDouble(double value) {
        this.buffer.set(D_BE, this.pointer, value);
        this.pointer += 8;
    }

    private void insertByteArray(byte[] value) {
        if (value.length == 0) return;
        MemorySegment.copy(MemorySegment.ofArray(value), 0, this.buffer, this.pointer, value.length);
        this.pointer += value.length;
    }

    private void insertIntArray(int[] value) {
        for (int i : value) {
            this.insertInt(i);
        }
    }

    private void insertLongArray(long[] value) {
        for (long i : value) {
            this.insertLong(i);
        }
    }

    private void insertLongArray(LongIterable value) {
        var iter = value.longIterator();
        while (iter.hasNext()) {
            this.insertLong(iter.nextLong());
        }
    }

    public void putBooleanEntry(boolean value) {
        this.putByteEntry(value ? (byte) 1 : 0);
    }

    public void putByteEntry(byte value) {
        this.claimCapacity(1);
        this.insertByte(value);
    }

    public void putShortEntry(short value) {
        this.claimCapacity(2);
        this.insertShort(value);
    }

    public void putIntEntry(int value) {
        this.claimCapacity(4);
        this.insertInt(value);
    }

    public void putLongEntry(long value) {
        this.claimCapacity(8);
        this.insertLong(value);
    }

    public void putFloatEntry(float value) {
        this.claimCapacity(4);
        this.insertFloat(value);
    }

    public void putDoubleEntry(double value) {
        this.claimCapacity(8);
        this.insertDouble(value);
    }

    public void putByteArrayEntry(byte[] value) {
        this.claimCapacity(4L + value.length);
        this.insertInt(value.length);
        this.insertByteArray(value);
    }

    public void putIntArrayEntry(int[] value) {
        this.claimCapacity(4L + value.length * 4L);
        this.putIntEntry(value.length);
        this.insertIntArray(value);
    }

    public void putLongArrayEntry(long[] value) {
        this.claimCapacity(4L + value.length * 8L);
        this.putIntEntry(value.length);
        this.insertLongArray(value);
    }

    public void putStringEntry(byte[] s) {
        this.claimCapacity(s.length);
        this.insertByteArray(s);
    }

    public void putStringEntry(String s) {
        this.putStringEntry(getStringBytes(s));
    }

    public <T> void putRegistryEntry(Registry<T> registry, T value) {
        this.putStringEntry(getNameBytesFromRegistry(registry, value));
    }

    public <T> void putRegistryEntry(RegistryEntry<T> registryEntry) {
        this.putStringEntry(getNameBytesFromRegistry(registryEntry));
    }

    public void compoundEntryStart() {
        // nop
    }

    @Deprecated
    public void putElementEntry(NbtElement data) {
        this.writeElementEntry(data);
    }

    @Deprecated
    public void putElementList(String name, List<? extends NbtElement> element) {
        byte heldType = element.isEmpty() ? NbtElement.END_TYPE : element.get(0).getType();
        this.startFixedList(getStringBytes(name), element.size(), heldType);
        for (NbtElement nbtElement : element) {
            this.writeElementEntry(nbtElement);
        }
    }

    public void startFixedListEntry(int size, byte heldType) {
        this.claimCapacity(1L + 4L);
        this.insertByte(heldType);
        this.insertInt(size);
    }

    public void putBoolean(byte[] name, boolean value) {
        this.putByte(name, value ? (byte) 1 : 0);
    }

    public void putByte(byte[] name, byte value) {
        this.claimCapacity(1L + name.length + 1L);
        this.insertByte(NbtElement.BYTE_TYPE);
        this.insertByteArray(name);
        this.insertByte(value);
    }

    public void putShort(byte[] name, short value) {
        this.claimCapacity(1L + name.length + 2L);
        this.insertByte(NbtElement.SHORT_TYPE);
        this.insertByteArray(name);
        this.insertShort(value);
    }

    public void putInt(byte[] name, int value) {
        this.claimCapacity(1L + name.length + 4L);
        this.insertByte(NbtElement.INT_TYPE);
        this.insertByteArray(name);
        this.insertInt(value);
    }

    public void putLong(byte[] name, long value) {
        this.claimCapacity(1L + name.length + 8L);
        this.insertByte(NbtElement.LONG_TYPE);
        this.insertByteArray(name);
        this.insertLong(value);
    }

    public void putFloat(byte[] name, float value) {
        this.claimCapacity(1L + name.length + 4L);
        this.insertByte(NbtElement.FLOAT_TYPE);
        this.insertByteArray(name);
        this.insertFloat(value);
    }

    public void putDouble(byte[] name, double value) {
        this.claimCapacity(1L + name.length + 8L);
        this.insertByte(NbtElement.DOUBLE_TYPE);
        this.insertByteArray(name);
        this.insertDouble(value);
    }

    @Deprecated
    public void putString(byte[] name, String value) {
        this.putString(name, getStringBytes(value));
    }

    public void putString(byte[] name, byte[] value) {
        this.claimCapacity(1L + name.length + value.length);
        this.insertByte(NbtElement.STRING_TYPE);
        this.insertByteArray(name);
        this.insertByteArray(value);
    }

    public NbtWriterFFM startCompound(byte[] name) {
        this.claimCapacity(1L + name.length);
        this.insertByte(NbtElement.COMPOUND_TYPE);
        this.insertByteArray(name);
        return this;
    }

    public void finishCompound() {
        this.claimCapacity(1);
        this.insertByte(NbtElement.END_TYPE);
    }

    public void putDoubles(byte[] name, double[] value) {
        this.startFixedList(name, value.length, NbtElement.DOUBLE_TYPE);
        for (double d : value) {
            this.putDoubleEntry(d);
        }
    }

    public <T> void putRegistry(byte[] name, Registry<T> registry, T value) {
        this.putString(name, getNameBytesFromRegistry(registry, value));
    }

    public long startList(byte[] name, byte type) {
        this.claimCapacity(1L + name.length + 1L + 4L);
        this.insertByte(NbtElement.LIST_TYPE);
        this.insertByteArray(name);
        this.insertByte(type);

        long offset = this.getOffset();
        this.pointer += 4;
        return offset;
    }

    public void finishList(long indicesStart, int indicesCount) {
        this.buffer.set(I_BE, indicesStart, indicesCount);
    }

    public void startFixedList(byte[] name, int size, byte type) {
        this.claimCapacity(1L + name.length + 1L + 4L);
        this.insertByte(NbtElement.LIST_TYPE);
        this.insertByteArray(name);
        this.insertByte(type);
        this.insertInt(size);
    }

    public void putByteArray(byte[] name, byte[] value) {
        this.claimCapacity(1L + name.length + 4L + value.length);
        this.insertByte(NbtElement.BYTE_ARRAY_TYPE);
        this.insertByteArray(name);
        this.insertInt(value.length);
        this.insertByteArray(value);
    }

    public void putIntArray(byte[] name, int[] value) {
        this.claimCapacity(1L + name.length + 4L + value.length * 4L);
        this.insertByte(NbtElement.INT_ARRAY_TYPE);
        this.insertByteArray(name);
        this.insertInt(value.length);
        this.insertIntArray(value);
    }

    public void putLongArray(byte[] name, long[] value) {
        this.claimCapacity(1L + name.length + 4L + value.length * 8L);
        this.insertByte(NbtElement.LONG_ARRAY_TYPE);
        this.insertByteArray(name);
        this.insertInt(value.length);
        this.insertLongArray(value);
    }

    public void putLongArray(byte[] name, LongCollection value) {
        this.claimCapacity(1L + name.length + 4L + value.size() * 8L);
        this.insertByte(NbtElement.LONG_ARRAY_TYPE);
        this.insertByteArray(name);
        this.insertInt(value.size());
        this.insertLongArray(value);
    }

    public void putLongArray(byte[] name, LongStream value) {
        this.claimCapacity(1L + name.length + 4L);
        this.insertByte(NbtElement.LONG_ARRAY_TYPE);
        this.insertByteArray(name);
        long offset = this.getOffset();
        this.insertInt(-1);

        int count = 0;
        var iter = value.iterator();
        while (iter.hasNext()) {
            this.claimCapacity(8);
            this.insertLong(iter.nextLong());
            count++;
        }
        this.buffer.set(I_BE, offset, count);
    }

    @Deprecated
    public void putElement(String name, NbtElement data) {
        this.putElement(getStringBytes(name), data);
    }

    @Deprecated
    public void putElement(byte[] name, NbtElement data) {
        this.writeNamedElement(name, data);
    }

    public byte[] toByteArray() {
        if (this.pointer > Integer.MAX_VALUE) {
            throw new IllegalStateException("Serialized payload too large: " + this.pointer);
        }

        byte[] bytes = new byte[(int) this.pointer];
        if (bytes.length == 0) return bytes;
        MemorySegment.copy(this.buffer, 0, MemorySegment.ofArray(bytes), 0, bytes.length);
        return bytes;
    }

    public void release() {
        this.close();
    }

    @Override
    public void close() {
        this.arena.close();
    }

    private void writeNamedElement(byte[] name, NbtElement data) {
        switch (data.getType()) {
            case NbtElement.STRING_TYPE -> this.putString(name, ((NbtString) data).value());
            case NbtElement.BYTE_TYPE -> this.putByte(name, ((NbtByte) data).byteValue());
            case NbtElement.SHORT_TYPE -> this.putShort(name, ((NbtShort) data).shortValue());
            case NbtElement.INT_TYPE -> this.putInt(name, ((NbtInt) data).intValue());
            case NbtElement.LONG_TYPE -> this.putLong(name, ((NbtLong) data).longValue());
            case NbtElement.FLOAT_TYPE -> this.putFloat(name, ((NbtFloat) data).floatValue());
            case NbtElement.DOUBLE_TYPE -> this.putDouble(name, ((NbtDouble) data).doubleValue());
            case NbtElement.BYTE_ARRAY_TYPE -> this.putByteArray(name, ((NbtByteArray) data).getByteArray());
            case NbtElement.INT_ARRAY_TYPE -> this.putIntArray(name, ((NbtIntArray) data).getIntArray());
            case NbtElement.LONG_ARRAY_TYPE -> this.putLongArray(name, ((NbtLongArray) data).getLongArray());
            case NbtElement.LIST_TYPE -> this.writeNamedList(name, (NbtList) data);
            case NbtElement.COMPOUND_TYPE -> this.writeNamedCompound(name, (NbtCompound) data);
            default -> throw new IllegalArgumentException("Unknown NbtElement type: " + data.getType());
        }
    }

    private void writeElementEntry(NbtElement data) {
        switch (data.getType()) {
            case NbtElement.STRING_TYPE -> this.putStringEntry(getStringBytes(((NbtString) data).value()));
            case NbtElement.BYTE_TYPE -> this.putByteEntry(((NbtByte) data).byteValue());
            case NbtElement.SHORT_TYPE -> this.putShortEntry(((NbtShort) data).shortValue());
            case NbtElement.INT_TYPE -> this.putIntEntry(((NbtInt) data).intValue());
            case NbtElement.LONG_TYPE -> this.putLongEntry(((NbtLong) data).longValue());
            case NbtElement.FLOAT_TYPE -> this.putFloatEntry(((NbtFloat) data).floatValue());
            case NbtElement.DOUBLE_TYPE -> this.putDoubleEntry(((NbtDouble) data).doubleValue());
            case NbtElement.BYTE_ARRAY_TYPE -> this.putByteArrayEntry(((NbtByteArray) data).getByteArray());
            case NbtElement.INT_ARRAY_TYPE -> this.putIntArrayEntry(((NbtIntArray) data).getIntArray());
            case NbtElement.LONG_ARRAY_TYPE -> this.putLongArrayEntry(((NbtLongArray) data).getLongArray());
            case NbtElement.LIST_TYPE -> this.writeListEntry((NbtList) data);
            case NbtElement.COMPOUND_TYPE -> this.writeCompoundEntry((NbtCompound) data);
            default -> throw new IllegalArgumentException("Unknown NbtElement type: " + data.getType());
        }
    }

    private void writeNamedList(byte[] name, NbtList list) {
        byte serializedType = ((INbtList) (Object) list).invokeGetSerializedType();
        this.startFixedList(name, list.size(), serializedType);
        if (serializedType == NbtElement.COMPOUND_TYPE) {
            for (NbtElement element : list) {
                if (element instanceof NbtCompound nbtCompound && !needWeaklyTypedWrapper(nbtCompound)) {
                    this.writeCompoundEntry(nbtCompound);
                } else {
                    this.writeNamedElement(EMPTY_NAME, element);
                    this.finishCompound();
                }
            }
        } else {
            for (NbtElement element : list) {
                this.writeElementEntry(element);
            }
        }
    }

    private void writeListEntry(NbtList list) {
        byte serializedType = ((INbtList) (Object) list).invokeGetSerializedType();
        this.startFixedListEntry(list.size(), serializedType);
        if (serializedType == NbtElement.COMPOUND_TYPE) {
            for (NbtElement element : list) {
                if (element instanceof NbtCompound nbtCompound && !needWeaklyTypedWrapper(nbtCompound)) {
                    this.writeCompoundEntry(nbtCompound);
                } else {
                    this.writeNamedElement(EMPTY_NAME, element);
                    this.finishCompound();
                }
            }
        } else {
            for (NbtElement element : list) {
                this.writeElementEntry(element);
            }
        }
    }

    private void writeNamedCompound(byte[] name, NbtCompound compound) {
        this.startCompound(name);
        for (String key : compound.getKeys()) {
            this.writeNamedElement(getStringBytes(key), compound.get(key));
        }
        this.finishCompound();
    }

    private void writeCompoundEntry(NbtCompound compound) {
        for (String key : compound.getKeys()) {
            this.writeNamedElement(getStringBytes(key), compound.get(key));
        }
        this.finishCompound();
    }

    private static boolean needWeaklyTypedWrapper(NbtCompound nbtCompound) {
        return nbtCompound.getSize() == 1 && nbtCompound.contains("");
    }
}
