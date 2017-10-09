/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.lang.io;

import net.openhft.lang.io.serialization.ObjectSerializer;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.io.EOFException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * works in conjunction with a MappedFile to map additional memory blocks when required This
 * implementation is very similar to {@code NativeBytes}, accept that the  memory address resolution
 * is left to ChronicleUnsafe, rather than being part of this class.
 */
public class MappedNativeBytes extends AbstractBytes {

    static final int BYTES_OFFSET;
    static final int CHARS_OFFSET;

    static {
        try {
            @SuppressWarnings("ALL")
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe unsafe = (Unsafe) theUnsafe.get(null);
            BYTES_OFFSET = unsafe.arrayBaseOffset(byte[].class);
            CHARS_OFFSET = unsafe.arrayBaseOffset(char[].class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @NotNull
    public final ThreadLocal<ChronicleUnsafe> threadLocal = new ThreadLocal<ChronicleUnsafe>();
    private final boolean isSingleThreaded;
    private final MappedFile mappedFile;
    private final ChronicleUnsafe chronicleUnsafe;
    protected long start;
    protected long position;
    protected long limit;
    protected long capacity;

    public MappedNativeBytes(@NotNull final MappedFile mappedFile, boolean isSingleThreaded) {
        this.isSingleThreaded = isSingleThreaded;
        this.mappedFile = mappedFile;
        this.start = 0;
        this.position = start;
        this.limit = this.capacity = Long.MAX_VALUE;
        this.chronicleUnsafe = (isSingleThreaded) ? new ChronicleUnsafe(mappedFile) : null;
    }

    public MappedNativeBytes(ObjectSerializer objectSerializer,
                             long sliceStart,
                             long capacity,
                             @NotNull AtomicInteger refCount,
                             @NotNull MappedFile mappedFile,
                             boolean singleThreaded) {
        this.isSingleThreaded = singleThreaded;
        setObjectSerializer(objectSerializer);
        this.start = sliceStart;
        this.position = 0;
        this.capacity = capacity;
        this.refCount.set(refCount.get());
        this.mappedFile = mappedFile;
        this.chronicleUnsafe = (isSingleThreaded) ? new ChronicleUnsafe(mappedFile) : null;
    }

    @Override
    public MappedNativeBytes slice() {
        return new MappedNativeBytes(objectSerializer(), position, limit, refCount, mappedFile, isSingleThreaded);
    }

    @Override
    public MappedNativeBytes slice(long offset, long length) {
        long sliceStart = position + offset;
        assert sliceStart >= start && sliceStart < capacity;
        long sliceEnd = sliceStart + length;
        assert sliceEnd > sliceStart && sliceEnd <= capacity;
        return new MappedNativeBytes(objectSerializer(), sliceStart, sliceEnd, refCount, mappedFile, isSingleThreaded);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        long subStart = position + start;
        if (subStart < position || subStart > limit)
            throw new IndexOutOfBoundsException();
        long subEnd = position + end;
        if (subEnd < subStart || subEnd > limit)
            throw new IndexOutOfBoundsException();
        if (start == end)
            return "";
        return new MappedNativeBytes(objectSerializer(), subStart, subEnd, refCount, mappedFile, isSingleThreaded);
    }

    @Override
    public MappedNativeBytes bytes() {
        return new MappedNativeBytes(objectSerializer(), start, capacity, refCount, mappedFile, isSingleThreaded);
    }

    @Override
    public MappedNativeBytes bytes(long offset, long length) {
        long sliceStart = start + offset;
        assert sliceStart >= start && sliceStart < capacity;
        long sliceEnd = sliceStart + length;
        assert sliceEnd > sliceStart && sliceEnd <= capacity;
        return new MappedNativeBytes(objectSerializer(), sliceStart, sliceEnd, refCount, mappedFile, isSingleThreaded);
    }

    @Override
    public long address() {
        return start;
    }

    @Override
    public Bytes zeroOut() {
        clear();
        getChronicleUnsafe().setMemory(start, capacity(), (byte) 0);
        return this;
    }

    @Override
    public Bytes zeroOut(long start, long end) {
        if (start < 0 || end > limit())
            throw new IllegalArgumentException("start: " + start + ", end: " + end);
        if (start >= end)
            return this;
        getChronicleUnsafe().setMemory(this.start + start, end - start, (byte) 0);
        return this;
    }

    @Override
    public Bytes zeroOut(long start, long end, boolean ifNotZero) {
        return ifNotZero ? zeroOutDirty(start, end) : zeroOut(start, end);
    }

    private Bytes zeroOutDirty(long start, long end) {
        if (start < 0 || end > limit())
            throw new IllegalArgumentException("start: " + start + ", end: " + end);
        if (start >= end)
            return this;
        // get unaligned leading bytes
        ChronicleUnsafe unsafe = getChronicleUnsafe();
        while (start < end && (start & 7) != 0) {
            byte b = unsafe.getByte(this.start + start);
            if (b != 0)
                unsafe.putByte(this.start + start, (byte) 0);
            start++;
        }
        // check 64-bit aligned access
        while (start < end - 7) {
            long l = unsafe.getLong(this.start + start);
            if (l != 0)
                unsafe.putLong(this.start + start, 0L);
            start += 8;
        }
        // check unaligned tail
        while (start < end) {
            byte b = unsafe.getByte(this.start + start);
            if (b != 0)
                unsafe.putByte(this.start + start, (byte) 0);
            start++;
        }
        return this;
    }

    @Override
    public int read(@NotNull byte[] bytes, int off, int len) {
        if (len < 0 || off < 0 || off + len > bytes.length)
            throw new IllegalArgumentException();
        long left = remaining();
        if (left <= 0) return -1;
        int len2 = (int) Math.min(len, left);
        getChronicleUnsafe().copyMemory(null, position, bytes, BYTES_OFFSET + off, len2);
        addPosition(len2);
        return len2;
    }

    @Override
    public byte readByte() {
        byte aByte = getChronicleUnsafe().getByte(position);
        addPosition(1);
        return aByte;
    }

    @Override
    public byte readByte(long offset) {
        return getChronicleUnsafe().getByte(start + offset);
    }

    @Override
    public void readFully(@NotNull byte[] b, int off, int len) {
        checkArrayOffs(b.length, off, len);
        long left = remaining();
        if (left < len)
            throw new IllegalStateException(new EOFException());
        getChronicleUnsafe().copyMemory(null, position, b, BYTES_OFFSET + off, len);
        addPosition(len);
    }

    @Override
    public void readFully(long offset, byte[] bytes, int off, int len) {
        checkArrayOffs(bytes.length, off, len);
        getChronicleUnsafe().copyMemory(null, start + offset, bytes, BYTES_OFFSET + off, len);
    }

    @Override
    public void readFully(@NotNull char[] data, int off, int len) {
        checkArrayOffs(data.length, off, len);
        long bytesOff = off * 2L;
        long bytesLen = len * 2L;
        long left = remaining();
        if (left < bytesLen)
            throw new IllegalStateException(new EOFException());
        getChronicleUnsafe().copyMemory(null, position, data, BYTES_OFFSET + bytesOff, bytesLen);
        addPosition(bytesLen);
    }

    @Override
    public short readShort() {
        short s = getChronicleUnsafe().getShort(position);
        addPosition(2);
        return s;
    }

    @Override
    public short readShort(long offset) {
        return getChronicleUnsafe().getShort(start + offset);
    }

    @Override
    public char readChar() {
        char ch = getChronicleUnsafe().getChar(position);
        addPosition(2);
        return ch;
    }

    @Override
    public char readChar(long offset) {
        return getChronicleUnsafe().getChar(start + offset);
    }

    @Override
    public int readInt() {
        int i = getChronicleUnsafe().getInt(position);
        addPosition(4);
        return i;
    }

    @Override
    public int readInt(long offset) {
        return getChronicleUnsafe().getInt(start + offset);
    }

    @Override
    public int readVolatileInt() {
        int i = getChronicleUnsafe().getIntVolatile(null, position);
        addPosition(4);
        return i;
    }

    @Override
    public int readVolatileInt(long offset) {
        return getChronicleUnsafe().getIntVolatile(null, start + offset);
    }

    @Override
    public long readLong() {
        long l = getChronicleUnsafe().getLong(position);
        addPosition(8);
        return l;
    }

    @Override
    public long readLong(long offset) {
        return getChronicleUnsafe().getLong(start + offset);
    }

    @Override
    public long readVolatileLong() {
        long l = getChronicleUnsafe().getLongVolatile(null, position);
        addPosition(8);
        return l;
    }

    @Override
    public long readVolatileLong(long offset) {
        return getChronicleUnsafe().getLongVolatile(null, start + offset);
    }

    @Override
    public float readFloat() {
        float f = getChronicleUnsafe().getFloat(position);
        addPosition(4);
        return f;
    }

    @Override
    public float readFloat(long offset) {
        return getChronicleUnsafe().getFloat(start + offset);
    }

    @Override
    public double readDouble() {
        double d = getChronicleUnsafe().getDouble(position);
        addPosition(8);
        return d;
    }

    @Override
    public double readDouble(long offset) {
        return getChronicleUnsafe().getDouble(start + offset);
    }

    @Override
    public void write(int b) {
        getChronicleUnsafe().putByte(position, (byte) b);
        incrementPositionAddr(1);
    }

    @Override
    public void writeByte(long offset, int b) {
        offsetChecks(offset, 1L);
        getChronicleUnsafe().putByte(start + offset, (byte) b);
    }

    @Override
    public void write(long offset, @NotNull byte[] bytes) {
        if (offset < 0 || offset + bytes.length > capacity())
            throw new IllegalArgumentException();
        getChronicleUnsafe().copyMemory(bytes, BYTES_OFFSET, null, start + offset, bytes.length);
        addPosition(bytes.length);
    }

    @Override
    public void write(byte[] bytes, int off, int len) {
        if (off < 0 || off + len > bytes.length || len > remaining())
            throw new IllegalArgumentException();
        getChronicleUnsafe().copyMemory(bytes, BYTES_OFFSET + off, null, position, len);
        addPosition(len);
    }

    @Override
    public void write(long offset, byte[] bytes, int off, int len) {
        if (offset < 0 || off + len > bytes.length || offset + len > capacity())
            throw new IllegalArgumentException();
        getChronicleUnsafe().copyMemory(bytes, BYTES_OFFSET + off, null, start + offset, len);
    }

    @Override
    public void writeShort(int v) {
        positionChecks(position + 2L);
        getChronicleUnsafe().putShort(position, (short) v);
        position += 2L;
    }

    private long incrementPositionAddr(long value) {
        positionAddr(positionAddr() + value);
        return positionAddr();
    }

    @Override
    public void writeShort(long offset, int v) {
        offsetChecks(offset, 2L);
        getChronicleUnsafe().putShort(start + offset, (short) v);
    }

    @Override
    public void writeChar(int v) {
        positionChecks(position + 2L);
        getChronicleUnsafe().putChar(position, (char) v);
        position += 2L;
    }

    void addPosition(long delta) {
        positionAddr(positionAddr() + delta);
    }

    @Override
    public void writeChar(long offset, int v) {
        offsetChecks(offset, 2L);
        getChronicleUnsafe().putChar(start + offset, (char) v);
    }

    @Override
    public void writeInt(int v) {
        positionChecks(position + 4L);
        getChronicleUnsafe().putInt(position, v);
        position += 4L;
    }

    @Override
    public void writeInt(long offset, int v) {
        offsetChecks(offset, 4L);
        getChronicleUnsafe().putInt(start + offset, v);
    }

    @Override
    public void writeOrderedInt(int v) {
        positionChecks(position + 4L);
        getChronicleUnsafe().putOrderedInt(null, position, v);
        position += 4L;
    }

    @Override
    public void writeOrderedInt(long offset, int v) {
        offsetChecks(offset, 4L);
        getChronicleUnsafe().putOrderedInt(null, start + offset, v);
    }

    @Override
    public boolean compareAndSwapInt(long offset, int expected, int x) {
        offsetChecks(offset, 4L);
        return getChronicleUnsafe().compareAndSwapInt(null, start + offset, expected, x);
    }

    @Override
    public void writeLong(long v) {
        positionChecks(position + 8L);
        getChronicleUnsafe().putLong(position, v);
        position += 8L;
    }

    @Override
    public void writeLong(long offset, long v) {
        offsetChecks(offset, 8L);
        getChronicleUnsafe().putLong(start + offset, v);
    }

    @Override
    public void writeOrderedLong(long v) {
        positionChecks(position + 8L);
        getChronicleUnsafe().putOrderedLong(null, position, v);
        position += 8L;
    }

    @Override
    public void writeOrderedLong(long offset, long v) {
        offsetChecks(offset, 8L);
        getChronicleUnsafe().putOrderedLong(null, start + offset, v);
    }

    @Override
    public boolean compareAndSwapLong(long offset, long expected, long x) {
        offsetChecks(offset, 8L);
        return getChronicleUnsafe().compareAndSwapLong(null, start + offset, expected, x);
    }

    @Override
    public void writeFloat(float v) {
        positionChecks(position + 4L);
        getChronicleUnsafe().putFloat(position, v);
        position += 4L;
    }

    @Override
    public void writeFloat(long offset, float v) {
        offsetChecks(offset, 4L);
        getChronicleUnsafe().putFloat(start + offset, v);
    }

    @Override
    public void writeDouble(double v) {
        positionChecks(position + 8L);
        getChronicleUnsafe().putDouble(position, v);
        position += 8L;
    }

    @Override
    public void writeDouble(long offset, double v) {
        offsetChecks(offset, 8L);
        getChronicleUnsafe().putDouble(start + offset, v);
    }

    @Override
    public void readObject(Object object, int start, int end) {
        int len = end - start;
        if (position + len >= limit)
            throw new IndexOutOfBoundsException("Length out of bounds len: " + len);

        ChronicleUnsafe unsafe = getChronicleUnsafe();
        for (; len >= 8; len -= 8) {
            unsafe.putLong(object, (long) start, unsafe.getLong(position));
            incrementPositionAddr(8L);
            start += 8;
        }
        for (; len > 0; len--) {
            unsafe.putByte(object, (long) start, unsafe.getByte(position));
            incrementPositionAddr(1L);
            start++;
        }
    }

    @Override
    public void writeObject(Object object, int start, int end) {
        int len = end - start;

        ChronicleUnsafe unsafe = getChronicleUnsafe();
        for (; len >= 8; len -= 8) {
            positionChecks(position + 8L);
            unsafe.putLong(position, unsafe.getLong(object, (long) start));
            position += 8;
            start += 8;
        }
        for (; len > 0; len--) {
            positionChecks(position + 1L);
            unsafe.putByte(position, unsafe.getByte(object, (long) start));
            position++;
            start++;
        }
    }

    @Override
    public boolean compare(long offset, RandomDataInput input, long inputOffset, long len) {
        if (offset < 0 || inputOffset < 0 || len < 0)
            throw new IndexOutOfBoundsException();
        if (offset + len < 0 || offset + len > capacity() || inputOffset + len < 0 ||
                inputOffset + len > input.capacity()) {
            return false;
        }
        long i = 0L;
        ChronicleUnsafe unsafe = getChronicleUnsafe();
        for (; i < len - 7L; i += 8L) {
            if (unsafe.getLong(start + offset + i) != input.readLong(inputOffset + i))
                return false;
        }
        if (i < len - 3L) {
            if (unsafe.getInt(start + offset + i) != input.readInt(inputOffset + i))
                return false;
            i += 4L;
        }
        if (i < len - 1L) {
            if (unsafe.getChar(start + offset + i) != input.readChar(inputOffset + i))
                return false;
            i += 2L;
        }
        if (i < len) {
            if (unsafe.getByte(start + offset + i) != input.readByte(inputOffset + i))
                return false;
        }
        return true;
    }

    @Override
    public long position() {
        return (position - start);
    }

    @Override
    public MappedNativeBytes position(long position) {
        if (position < 0 || position > limit())
            throw new IndexOutOfBoundsException("position: " + position + " limit: " + limit());

        positionAddr(start + position);
        return this;
    }

    /**
     * Change the position acknowleging there is no thread safety assumptions. Best effort setting
     * is fine. *
     *
     * @param position to set if we can.
     * @return this
     */
    public MappedNativeBytes lazyPosition(long position) {
        if (position < 0 || position > limit())
            throw new IndexOutOfBoundsException("position: " + position + " limit: " + limit());

        // assume we don't need to no check thread safety.

        positionAddr(start + position);
        return this;
    }

    @Override
    public void write(RandomDataInput bytes, long position, long length) {
        if (length > remaining())
            throw new IllegalArgumentException("Attempt to write " + length + " bytes with " + remaining() + " remaining");
        if (bytes instanceof MappedNativeBytes) {
            getChronicleUnsafe().copyMemory(((MappedNativeBytes) bytes).start + position, this.position, length);
            skip(length);

        } else {
            super.write(bytes, position, length);
        }
    }

    @Override
    public long capacity() {
        return (capacity - start);
    }

    @Override
    public long remaining() {
        return (limit - position);
    }

    @Override
    public long limit() {
        return (limit - start);
    }

    @Override
    public MappedNativeBytes limit(long limit) {
        if (limit < 0 || limit > capacity()) {
            throw new IllegalArgumentException("limit: " + limit + " capacity: " + capacity());
        }

        this.limit = start + limit;
        return this;
    }

    @NotNull
    @Override
    public ByteOrder byteOrder() {
        return ByteOrder.nativeOrder();
    }

    @Override
    public void checkEndOfBuffer() throws IndexOutOfBoundsException {
        if (position() > limit()) {
            throw new IndexOutOfBoundsException(
                    "position is beyond the end of the buffer " + position() + " > " + limit());
        }
    }

    public long startAddr() {
        return start;
    }

    long capacityAddr() {
        return capacity;
    }

    @Override
    protected void cleanup() {
        // TODO nothing to do.
    }

    @Override
    public Bytes load() {
        ChronicleUnsafe unsafe = getChronicleUnsafe();
        int pageSize = unsafe.pageSize();
        for (long addr = start; addr < capacity; addr += pageSize)
            unsafe.getByte(addr);
        return this;
    }

    public void alignPositionAddr(int powerOf2) {
        long value = (position + powerOf2 - 1) & ~(powerOf2 - 1);
        positionAddr(value);
    }

    public void positionAddr(long positionAddr) {
        positionChecks(positionAddr);
        this.position = positionAddr;
    }

    void positionChecks(long positionAddr) {
        assert actualPositionChecks(positionAddr);
    }

    boolean actualPositionChecks(long positionAddr) {
        if (positionAddr < start)
            throw new IndexOutOfBoundsException("position before the start by " + (start - positionAddr) + " bytes");
        if (positionAddr > limit)
            throw new IndexOutOfBoundsException("position after the limit by " + (positionAddr - limit) + " bytes");

        return true;
    }

    void offsetChecks(long offset, long len) {
        assert actualOffsetChecks(offset, len);
    }

    boolean actualOffsetChecks(long offset, long len) {
        if (offset < 0L || offset + len > capacity())
            throw new IndexOutOfBoundsException("offset out of bounds: " + offset + ", len: " +
                    len + ", capacity: " + capacity());
        return true;
    }

    public long positionAddr() {
        return position;
    }

    @Override
    public ByteBuffer sliceAsByteBuffer(ByteBuffer toReuse) {
        return sliceAsByteBuffer(toReuse, null);
    }

    protected ByteBuffer sliceAsByteBuffer(ByteBuffer toReuse, Object att) {
        return ByteBufferReuse.INSTANCE.reuse(position, (int) remaining(), att, toReuse);
    }

    // todo :  we should move this lookup further up the stack, so that it can be done, just ONCE, for example once by a single threaded appender
    // todo : hence the constructor should be give then instance of chronicleUnsafe to use
    @NotNull
    public ChronicleUnsafe getChronicleUnsafe() {
        if (isSingleThreaded)
            return chronicleUnsafe;

        ChronicleUnsafe chronicleUnsafe = threadLocal.get();
        if (chronicleUnsafe == null) {
            chronicleUnsafe = new ChronicleUnsafe(mappedFile);
            threadLocal.set(chronicleUnsafe);
        }

        return chronicleUnsafe;
    }
}
