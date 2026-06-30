package com.diabetes.home.util;

import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.iso14496.part12.MovieBox;
import org.mp4parser.boxes.iso14496.part12.MovieHeaderBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoDurationParserTest {

    @Test
    void parseMp4DurationHandlesSuccessInvalidHeaderIoAndGenericFailure() throws Exception {
        Path file = Files.createTempFile("duration", ".mp4");
        try {
            MovieBox movieBox = mock(MovieBox.class);
            MovieHeaderBox header = mock(MovieHeaderBox.class);
            when(movieBox.getMovieHeaderBox()).thenReturn(header);
            when(header.getTimescale()).thenReturn(1_000L);
            when(header.getDuration()).thenReturn(65_000L);
            try (MockedConstruction<IsoFile> ignored = mockConstruction(IsoFile.class,
                    (mock, context) -> when(mock.getMovieBox()).thenReturn(movieBox))) {
                assertEquals(LocalTime.of(0, 1, 5), VideoDurationParser.parseMp4Duration(file));
            }

            when(header.getTimescale()).thenReturn(0L);
            try (MockedConstruction<IsoFile> ignored = mockConstruction(IsoFile.class,
                    (mock, context) -> when(mock.getMovieBox()).thenReturn(movieBox))) {
                assertEquals(400, assertThrows(BusinessException.class,
                        () -> VideoDurationParser.parseMp4Duration(file)).getCode());
            }

            when(header.getTimescale()).thenReturn(1L);
            when(header.getDuration()).thenReturn(-1L);
            try (MockedConstruction<IsoFile> ignored = mockConstruction(IsoFile.class,
                    (mock, context) -> when(mock.getMovieBox()).thenReturn(movieBox))) {
                assertEquals(400, assertThrows(BusinessException.class,
                        () -> VideoDurationParser.parseMp4Duration(file)).getCode());
            }

            try (MockedConstruction<IsoFile> ignored = mockConstruction(IsoFile.class,
                    (mock, context) -> when(mock.getMovieBox()).thenThrow(new IllegalStateException("bad")))) {
                assertEquals(400, assertThrows(BusinessException.class,
                        () -> VideoDurationParser.parseMp4Duration(file)).getCode());
            }
        } finally {
            Files.deleteIfExists(file);
        }

        assertEquals(400, assertThrows(BusinessException.class,
                () -> VideoDurationParser.parseMp4Duration(Path.of("missing.mp4"))).getCode());
    }
}
