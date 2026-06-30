package com.diabetes.home.util;

import com.diabetes.common.exception.BusinessException;
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.iso14496.part12.MovieHeaderBox;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;

public final class VideoDurationParser {

    private VideoDurationParser() {
    }

    /**
     * 从 MP4 文件解析时长，返回 {@link LocalTime}（hh:mm:ss，最长约 23:59:59）。
     */
    public static LocalTime parseMp4Duration(Path filePath) {
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            IsoFile isoFile = new IsoFile(channel);
            MovieHeaderBox header = isoFile.getMovieBox().getMovieHeaderBox();
            long timescale = header.getTimescale();
            long duration = header.getDuration();
            if (timescale <= 0 || duration < 0) {
                throw new BusinessException(400, "无法解析视频时长");
            }
            long totalSeconds = duration / timescale;
            return secondsToLocalTime(totalSeconds);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(400, "无法读取视频文件: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(400, "无法解析视频时长: " + e.getMessage());
        }
    }

    static LocalTime secondsToLocalTime(long totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        if (totalSeconds >= 86400) {
            totalSeconds = 86399;
        }
        return LocalTime.ofSecondOfDay(totalSeconds);
    }
}
