package app.morphe.extension.instagram.tts;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/** Minimal RIFF/WAVE writer and reader for mono float PCM (WAVE_FORMAT_IEEE_FLOAT, format 3). */
final class WavIo {
    static final int SAMPLE_RATE = 24_000;
    private static final int HEADER_SIZE = 44;

    private WavIo() { }

    static void writeFloatWav(File file, float[] pcm) throws IOException {
        try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
            out.setLength(0);
            out.write(header(pcm.length));
            ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            buffer.asFloatBuffer().put(pcm);
            out.write(buffer.array());
        }
    }

    static byte[] header(int sampleCount) {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int byteRate = SAMPLE_RATE * 4;
        header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(36 + sampleCount * 4);
        header.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(16);                      // fmt chunk size
        header.putShort((short) 3);              // WAVE_FORMAT_IEEE_FLOAT
        header.putShort((short) 1);              // mono
        header.putInt(SAMPLE_RATE);
        header.putInt(byteRate);
        header.putShort((short) 4);              // block align
        header.putShort((short) 32);             // bits per sample
        header.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(sampleCount * 4);
        return header.array();
    }

    /** Sample count in the data chunk of a float wav; -1 when unparseable. */
    static long sampleCount(File file) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            return dataLength(in) / 4;
        } catch (IOException ex) {
            return -1;
        }
    }

    /** Reads up to {@code max} float samples from the data chunk (null on unparseable file). */
    static float[] readFloats(File file, int max) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long length = dataLength(in);
            if (length <= 0) return null;
            int count = (int) Math.min(max, length / 4);
            byte[] raw = new byte[count * 4];
            in.readFully(raw);
            float[] pcm = new float[count];
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(pcm);
            return pcm;
        } catch (IOException | OutOfMemoryError ex) {
            return null;
        }
    }

    /** Positions the stream at the data chunk and returns its byte length. */
    static long seekData(RandomAccessFile in) throws IOException {
        byte[] tag = new byte[4];
        in.seek(0);
        in.readFully(tag);
        if (!new String(tag, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")) throw new IOException("not RIFF");
        in.skipBytes(4 + 4);
        in.readFully(tag);
        if (!new String(tag, java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE")) throw new IOException("not WAVE");
        while (in.getFilePointer() + 8 <= in.length()) {
            in.readFully(tag);
            int size = readIntLE(in);
            if (new String(tag, java.nio.charset.StandardCharsets.US_ASCII).equals("data")) {
                if (in.getFilePointer() + size > in.length()) size = (int) (in.length() - in.getFilePointer());
                return size;
            }
            in.skipBytes(size + (size & 1));
        }
        throw new IOException("no data chunk");
    }

    private static long dataLength(RandomAccessFile in) throws IOException {
        return seekData(in);
    }

    private static int readIntLE(RandomAccessFile in) throws IOException {
        byte[] b = new byte[4];
        in.readFully(b);
        return (b[0] & 0xff) | (b[1] & 0xff) << 8 | (b[2] & 0xff) << 16 | (b[3] & 0xff) << 24;
    }
}
