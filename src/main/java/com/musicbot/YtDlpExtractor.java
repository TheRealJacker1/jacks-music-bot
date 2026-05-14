package com.musicbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class YtDlpExtractor {

    public record TrackInfo(String title, String streamUrl, long durationMs) {}

    public static TrackInfo extract(String query) throws Exception {
        String ytQuery = query.startsWith("http://") || query.startsWith("https://")
                ? query
                : "ytsearch1:" + query;

        // --print outputs title and duration, -g outputs the stream URL
        ProcessBuilder pb = new ProcessBuilder(
                "./yt-dlp",
                "--no-playlist",
                "--no-warnings",
                "-f", "bestaudio[ext=webm][acodec=opus]/bestaudio[acodec=opus]/bestaudio",
                "--print", "%(title)s",
                "--print", "%(duration)s",
                "-g",
                ytQuery
        );
        pb.directory(new File("."));
        pb.redirectErrorStream(false);

        Process process = pb.start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) lines.add(trimmed);
            }
        }
        process.waitFor();

        // Expect at least 3 lines: title, duration, stream URL
        if (lines.size() < 3) return null;

        String title    = lines.get(0);
        long durationMs = parseDuration(lines.get(1));
        String url      = lines.get(lines.size() - 1); // URL is always last

        if (!url.startsWith("http")) return null;

        return new TrackInfo(title, url, durationMs);
    }

    private static long parseDuration(String raw) {
        try {
            return (long)(Double.parseDouble(raw) * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
