package tlc2.diploma.util;

import java.util.AbstractList;

public class ArrayIntList extends AbstractList<Integer> {
    public static final int DEFAULT_CAPACITY = 16;
    private int[] data;
    private int size;

    public ArrayIntList() {
        this(DEFAULT_CAPACITY);
    }

    public ArrayIntList(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("initial capacity is too low");
        }
        this.data = new int[capacity];
        this.size = 0;
    }

    @Override
    public boolean add(Integer value) {
        if (value == null) {
            return false;
        }
        while (size >= data.length) {
            int[] newData = new int[(int) Math.ceil(1.5 * data.length)];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
        }
        data[size++] = value;
        return true;
    }

    private void ensureIndex(int index) {
        if (index < 0 || size <= index) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    @Override
    public Integer set(int index, Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        ensureIndex(index);
        int prev = data[index];
        data[index] = value;
        return prev;
    }

    @Override
    public Integer remove(int index) {
        ensureIndex(index);
        int value = data[index];
        System.arraycopy(data, index + 1, data, index, size - index - 1);
        size--;
        return value;
    }

    @Override
    public Integer get(int index) {
        ensureIndex(index);
        return data[index];
    }

    @Override
    public int size() {
        return size;
    }
}
