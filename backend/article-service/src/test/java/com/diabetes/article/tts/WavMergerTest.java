package com.diabetes.article.tts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WavMergerTest {

    @Test
    void merge_singleFileReturnsOriginal() throws IOException {
        byte[] wav = buildWav(1, 8000, 16, new byte[]{1, 2, 3, 4});
        assertArrayEquals(wav, WavMerger.merge(List.of(wav)));
    }

    @Test
    void merge_concatenatesPcmData() throws IOException {
        byte[] first = buildWav(1, 8000, 16, new byte[]{1, 2});
        byte[] second = buildWav(1, 8000, 16, new byte[]{3, 4});
        byte[] merged = WavMerger.merge(List.of(first, second));
        WavHeader header = parseHeader(merged);
        assertEquals(4, header.dataSize);
        byte[] pcm = new byte[4];
        System.arraycopy(merged, header.dataOffset, pcm, 0, 4);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, pcm);
    }

    @Test
    void merge_handlesOversizedDeclaredDataChunk() throws IOException {
        byte[] pcm = new byte[]{1, 2, 3, 4};
        byte[] wav = buildWav(1, 8000, 16, pcm);
        // 将 data 块声明长度改为远大于实际（模拟 DashScope 返回）
        wav[40] = (byte) 0xFF;
        wav[41] = (byte) 0xFF;
        wav[42] = (byte) 0xFF;
        wav[43] = (byte) 0x7F;
        byte[] merged = WavMerger.merge(List.of(wav, wav));
        assertTrue(merged.length > 44);
    }

    @Test
    void merge_rejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> WavMerger.merge(List.of()));
    }

    private static byte[] buildWav(int channels, int sampleRate, int bitsPerSample, byte[] pcm) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        ByteBuffer buffer = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(36 + pcm.length);
        buffer.put(new byte[]{'W', 'A', 'V', 'E'});
        buffer.put(new byte[]{'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);
        buffer.put(new byte[]{'d', 'a', 't', 'a'});
        buffer.putInt(pcm.length);
        buffer.put(pcm);
        return buffer.array();
    }

    private static WavHeader parseHeader(byte[] wav) {
        int dataSize = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return new WavHeader(44, dataSize);
    }

    private record WavHeader(int dataOffset, int dataSize) {
    }
}
