package absentialis.director;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Video export: an AVI file holding Motion-JPEG video and, optionally, uncompressed PCM sound, interleaved a frame at
 * a time. It is the plainest video container there is - every player and every editor opens it - and it needs
 * nothing that is not in the JDK: each frame is a JPEG that has already been made, and this class only lays them
 * end to end with the headers and the index a player needs to find them.
 */
public final class AviWriter implements AutoCloseable {
    private final RandomAccessFile f;
    private final int width, height, fps, audioRate, audioChannels;
    private final boolean audio;
    private final List<int[]> index = new ArrayList<>();          // {fourcc, flags, offset from 'movi', size}
    private long riffSize, hdrlFrames, strhFrames, moviList, moviStart, audioStrhLength;
    private int frames, maxFrame;
    private long audioBytes;

    private static final int F00DC = cc("00dc"), F01WB = cc("01wb");

    private static int cc(String s) { return (s.charAt(0)) | (s.charAt(1) << 8) | (s.charAt(2) << 16) | (s.charAt(3) << 24); }

    /** Opens the file and writes its headers. Pass {@code audioRate} 0 for a silent film. */
    public AviWriter(Path file, int width, int height, int fps, int audioRate, int audioChannels) throws IOException {
        this.width = width; this.height = height; this.fps = fps; this.audioRate = audioRate; this.audioChannels = Math.max(1, audioChannels); this.audio = audioRate > 0;
        f = new RandomAccessFile(file.toFile(), "rw");
        f.setLength(0);
        ByteBuffer b = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cc("RIFF")); b.putInt(0); b.putInt(cc("AVI "));
        int hdrlAt = b.position();
        b.putInt(cc("LIST")); b.putInt(0); b.putInt(cc("hdrl"));
        b.putInt(cc("avih")); b.putInt(56);
        b.putInt(1_000_000 / fps);                 // microseconds per frame
        b.putInt(0); b.putInt(0);
        b.putInt(0x10 | 0x100);                    // has an index, interleaved
        hdrlFrames = b.position(); b.putInt(0);    // total frames (patched)
        b.putInt(0); b.putInt(audio ? 2 : 1);
        b.putInt(0); b.putInt(width); b.putInt(height);
        b.putInt(0); b.putInt(0); b.putInt(0); b.putInt(0);
        // ---- the video stream
        int strlAt = b.position();
        b.putInt(cc("LIST")); b.putInt(0); b.putInt(cc("strl"));
        b.putInt(cc("strh")); b.putInt(56);
        b.putInt(cc("vids")); b.putInt(cc("MJPG")); b.putInt(0); b.putInt(0); b.putInt(0);
        b.putInt(1); b.putInt(fps);                // scale, rate
        b.putInt(0);
        strhFrames = b.position(); b.putInt(0);    // length in frames (patched)
        b.putInt(0); b.putInt(-1); b.putInt(0);
        b.putShort((short) 0); b.putShort((short) 0); b.putShort((short) width); b.putShort((short) height);
        b.putInt(cc("strf")); b.putInt(40);
        b.putInt(40); b.putInt(width); b.putInt(height); b.putShort((short) 1); b.putShort((short) 24); b.putInt(cc("MJPG")); b.putInt(width * height * 3); b.putInt(0); b.putInt(0); b.putInt(0); b.putInt(0);
        b.putInt(strlAt + 4, b.position() - strlAt - 8);
        // ---- the sound stream
        if (audio) {
            int block = this.audioChannels * 2;
            int aAt = b.position();
            b.putInt(cc("LIST")); b.putInt(0); b.putInt(cc("strl"));
            b.putInt(cc("strh")); b.putInt(56);
            b.putInt(cc("auds")); b.putInt(0); b.putInt(0); b.putInt(0); b.putInt(0);
            b.putInt(block); b.putInt(audioRate * block);       // scale, rate: bytes
            b.putInt(0);
            audioStrhLength = b.position(); b.putInt(0);        // length in blocks' bytes / scale (patched)
            b.putInt(audioRate * block); b.putInt(-1); b.putInt(block);
            b.putShort((short) 0); b.putShort((short) 0); b.putShort((short) 0); b.putShort((short) 0);
            b.putInt(cc("strf")); b.putInt(16);
            b.putShort((short) 1); b.putShort((short) this.audioChannels); b.putInt(audioRate); b.putInt(audioRate * block); b.putShort((short) block); b.putShort((short) 16);
            b.putInt(aAt + 4, b.position() - aAt - 8);
        }
        b.putInt(hdrlAt + 4, b.position() - hdrlAt - 8);
        moviList = b.position();
        b.putInt(cc("LIST")); b.putInt(0); b.putInt(cc("movi"));
        moviStart = b.position() - 4;                            // index offsets are measured from the 'movi' word
        f.write(b.array(), 0, b.position());
    }

    private void chunk(int fourcc, byte[] data, int len, int flags) throws IOException {
        long at = f.getFilePointer();
        ByteBuffer h = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(fourcc); h.putInt(len);
        f.write(h.array());
        f.write(data, 0, len);
        if ((len & 1) != 0) f.write(0);
        index.add(new int[]{fourcc, flags, (int) (at - moviStart), len});
    }

    /** Adds one frame: a complete JPEG image of the film's size. */
    public void frame(byte[] jpeg) throws IOException { chunk(F00DC, jpeg, jpeg.length, 0x10); frames++; maxFrame = Math.max(maxFrame, jpeg.length); }

    /** Adds the sound that goes with the frames written so far: 16-bit little-endian PCM, interleaved. */
    public void sound(byte[] pcm, int len) throws IOException { if (!audio || len <= 0) return; chunk(F01WB, pcm, len, 0x10); audioBytes += len; }

    public int frames() { return frames; }

    /** Writes the index and goes back to fill in the lengths the headers could not know at the start. */
    @Override public void close() throws IOException {
        long moviEnd = f.getFilePointer();
        ByteBuffer ix = ByteBuffer.allocate(8 + index.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
        ix.putInt(cc("idx1")); ix.putInt(index.size() * 16);
        for (int[] e : index) { ix.putInt(e[0]); ix.putInt(e[1]); ix.putInt(e[2]); ix.putInt(e[3]); }
        f.write(ix.array());
        long end = f.getFilePointer();
        patch(4, (int) (end - 8));
        patch(moviList + 4, (int) (moviEnd - moviList - 8));
        patch(hdrlFrames, frames);
        patch(strhFrames, frames);
        if (audio) patch(audioStrhLength, (int) (audioBytes / (audioChannels * 2)));
        f.close();
    }

    private void patch(long at, int value) throws IOException {
        f.seek(at);
        f.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }
}
