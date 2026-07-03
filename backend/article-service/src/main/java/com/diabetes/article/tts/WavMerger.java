package com.diabetes.article.tts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 合并多段同格式 WAV 音频为单个文件（用于长文分段合成后拼接）。
 */
public final class WavMerger {

    private WavMerger() {
    }

    public static byte[] merge(List<byte[]> wavFiles) throws IOException {
        if (wavFiles == null || wavFiles.isEmpty()) {
            throw new IllegalArgumentException("WAV 列表不能为空");
        }
        if (wavFiles.size() == 1) {
            return wavFiles.get(0);
        }

        WavHeader header = parseHeader(wavFiles.get(0));
        List<byte[]> pcmChunks = new ArrayList<>();
        pcmChunks.add(extractPcm(wavFiles.get(0), header));

        for (int i = 1; i < wavFiles.size(); i++) {
            WavHeader next = parseHeader(wavFiles.get(i));
            if (next.sampleRate != header.sampleRate
                    || next.channels != header.channels
                    || next.bitsPerSample != header.bitsPerSample) {
                throw new IOException("WAV 格式不一致，无法合并");
            }
            pcmChunks.add(extractPcm(wavFiles.get(i), next));
        }

        int totalPcm = pcmChunks.stream().mapToInt(chunk -> chunk.length).sum();
        return buildWav(header, pcmChunks, totalPcm);
    }

    private static WavHeader parseHeader(byte[] wav) throws IOException {
        if (wav.length < 44) {
            throw new IOException("无效的 WAV 文件");
        }
        if (wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            throw new IOException("无效的 WAV 文件头");
        }
        int channels = readShort(wav, 22);
        int sampleRate = readInt(wav, 24);
        int bitsPerSample = readShort(wav, 34);
        int dataOffset = findDataOffset(wav);
        int declaredSize = readInt(wav, dataOffset - 4);
        int available = wav.length - dataOffset;
        if (available <= 0) {
            throw new IOException("WAV data 块无效");
        }
        // 部分 TTS 返回的 data 块声明长度大于实际文件，以剩余字节为准
        int dataSize = (declaredSize > 0 && declaredSize <= available) ? declaredSize : available;
        return new WavHeader(channels, sampleRate, bitsPerSample, dataOffset, dataSize);
    }

    private static int findDataOffset(byte[] wav) throws IOException {
        int offset = 12;
        while (offset + 8 <= wav.length) {
            String chunkId = new String(wav, offset, 4, StandardCharsets.US_ASCII);
            int chunkSize = readInt(wav, offset + 4);
            if (chunkSize < 0) {
                throw new IOException("WAV 块长度无效");
            }
            if ("data".equals(chunkId)) {
                return offset + 8;
            }
            int next = offset + 8 + chunkSize + (chunkSize & 1);
            if (next <= offset || next > wav.length) {
                throw new IOException("WAV 块结构无效");
            }
            offset = next;
        }
        throw new IOException("WAV 缺少 data 块");
    }

    private static byte[] extractPcm(byte[] wav, WavHeader header) throws IOException {
        int available = wav.length - header.dataOffset;
        int size = Math.min(header.dataSize, available);
        if (size <= 0) {
            throw new IOException("WAV PCM 数据为空");
        }
        byte[] pcm = new byte[size];
        System.arraycopy(wav, header.dataOffset, pcm, 0, size);
        return pcm;
    }

    private static byte[] buildWav(WavHeader header, List<byte[]> pcmChunks, int totalPcm) throws IOException {
        int byteRate = header.sampleRate * header.channels * header.bitsPerSample / 8;
        int blockAlign = header.channels * header.bitsPerSample / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + totalPcm);
        out.write(new byte[]{'R', 'I', 'F', 'F'});
        writeInt(out, 36 + totalPcm);
        out.write(new byte[]{'W', 'A', 'V', 'E'});
        out.write(new byte[]{'f', 'm', 't', ' '});
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, header.channels);
        writeInt(out, header.sampleRate);
        writeInt(out, byteRate);
        writeShort(out, blockAlign);
        writeShort(out, header.bitsPerSample);
        out.write(new byte[]{'d', 'a', 't', 'a'});
        writeInt(out, totalPcm);
        for (byte[] chunk : pcmChunks) {
            out.write(chunk);
        }
        return out.toByteArray();
    }

    private static int readShort(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private static int readInt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static void writeShort(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private record WavHeader(int channels, int sampleRate, int bitsPerSample, int dataOffset, int dataSize) {
    }
}
