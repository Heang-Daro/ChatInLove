package org.dr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class VideoTranscodeService {

    @Value("${app.upload.hls-dir:./uploads/hls/}")
    private String hlsOutputDir;

    @Async
    public void transcodeToHlsAsync(String uploadId, Path sourceVideo) {
        log.info("Starting FFmpeg HLS segmentation for video uploadId: {}", uploadId);
        try {
            Path outputFolder = Paths.get(hlsOutputDir, uploadId);
            Files.createDirectories(outputFolder);

            Path masterPlaylist = outputFolder.resolve("master.m3u8");
            Path segmentPattern = outputFolder.resolve("seg_%03d.ts");

            // Check if ffmpeg exists in PATH
            if (!isFfmpegAvailable()) {
                log.info("FFmpeg not detected in system PATH. Video will be streamed via Byte-Range Partial Content.");
                return;
            }

            // Execute FFmpeg: copy codec first for ultra-fast segmentation without re-encoding
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", sourceVideo.toAbsolutePath().toString(),
                    "-codec:", "copy",
                    "-start_number", "0",
                    "-hls_time", "2",
                    "-hls_list_size", "0",
                    "-hls_segment_filename", segmentPattern.toAbsolutePath().toString(),
                    "-f", "hls",
                    masterPlaylist.toAbsolutePath().toString()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("HLS transcode completed successfully for uploadId: {}", uploadId);
            } else {
                log.warn("Fast FFmpeg copy failed with exit code {}. Attempting transcode with libx264...", exitCode);
                runFallbackTranscode(sourceVideo, masterPlaylist, segmentPattern);
            }
        } catch (Exception e) {
            log.warn("FFmpeg HLS transcode skipped/failed for {}: {}. Byte-Range streaming remains active.", uploadId, e.getMessage());
        }
    }

    private boolean isFfmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void runFallbackTranscode(Path source, Path master, Path segments) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", source.toAbsolutePath().toString(),
                "-c:v", "libx264",
                "-crf", "23",
                "-preset", "veryfast",
                "-c:a", "aac",
                "-b:a", "128k",
                "-hls_time", "2",
                "-hls_list_size", "0",
                "-hls_segment_filename", segments.toAbsolutePath().toString(),
                "-f", "hls",
                master.toAbsolutePath().toString()
        );
        Process p = pb.start();
        p.waitFor();
    }
}
