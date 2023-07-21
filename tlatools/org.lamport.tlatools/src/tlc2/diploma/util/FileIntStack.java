package tlc2.diploma.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileIntStack {
    private final Path dir;
    private final int batchSize;

    private int batchPos;
    private ByteBuffer firstBatch;
    private ByteBuffer secondBatch;


    private static final int DEFAULT_BATCH_SIZE = (1 << 16) / Integer.BYTES;

    public FileIntStack(Path dir, int batchSize) {
        this.dir = dir;
        this.batchSize = batchSize;
        this.batchPos = 0;
        this.firstBatch = ByteBuffer.allocate(batchSize * Integer.BYTES);
        this.secondBatch = ByteBuffer.allocate(batchSize * Integer.BYTES);
    }

    public FileIntStack(Path dir) {
        this(dir, DEFAULT_BATCH_SIZE);
    }

    private void swapBatches() {
        ByteBuffer tmp = firstBatch;
        firstBatch = secondBatch;
        secondBatch = tmp;
    }

    private Path getFilePath(int index) {
        return dir.resolve(String.format("%04d.dat", index));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void shiftBatchWindowForward() throws IOException {
        Path filePath = getFilePath(batchPos++);
        try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            firstBatch.flip();
            while (firstBatch.hasRemaining()) {
                fc.write(firstBatch);
            }
            swapBatches();
            secondBatch.clear();
        }
    }

    private void shiftBatchWindowBack() throws IOException {
        if (batchPos == 0) {
            throw new IllegalStateException("Stack is empty");
        }
        Path filePath = getFilePath(--batchPos);
        try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.READ)) {
            secondBatch.clear();
            swapBatches();
            while (firstBatch.hasRemaining()) {
                fc.read(firstBatch);
            }
        }
    }

    public void push(int value) throws IOException {
        if (!secondBatch.hasRemaining()) {
            shiftBatchWindowForward();
        }
        ByteBuffer batch = firstBatch.hasRemaining() ? firstBatch : secondBatch;
        batch.putInt(value);
    }

    public int pop() throws IOException {
        if (firstBatch.position() == 0) {
            shiftBatchWindowBack();
        }
        ByteBuffer batch = secondBatch.position() == 0 ? firstBatch : secondBatch;
        int pos = batch.position() - Integer.BYTES;
        int value = batch.getInt(pos);
        batch.position(pos);
        return value;
    }

    public int peek() throws IOException {
        if (firstBatch.position() == 0) {
            shiftBatchWindowBack();
        }
        ByteBuffer batch = secondBatch.position() == 0 ? firstBatch : secondBatch;
        return batch.getInt(batch.position() - Integer.BYTES);
    }

    public long size() {
        return firstBatch.hasRemaining()
                ? (long) batchSize * batchPos + firstBatch.position() / Integer.BYTES
                : (long) batchSize * (batchPos + 1) + secondBatch.position() / Integer.BYTES;
    }

    public boolean isEmpty() {
        return batchPos == 0 && firstBatch.position() == 0;
    }
}
