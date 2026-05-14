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

        ProcessBuilder pb = new ProcessBuilder(
                "./yt-dlp",
                "--no-playlist",
                "-f", "bestaudio",
                "--print", "%(title)s",
                "--print", "%(duration)s",
                "-g",
                ytQuery
        );
        pb.directory(new File("."));

        Process process = pb.start();

        // Read stdout and stderr in parallel to avoid blocking
        List<String> lines = new ArrayList<>();
        StringBuilder stderr = new StringBuilder();

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String l;
                while ((l = r.readLine()) != null) stderr.append(l).append("\n");
            } catch (Exception ignored) {}
        });
        stderrThread.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) lines.add(trimmed);
            }
        }

        int exitCode = process.waitFor();
        stderrThread.join(2000);

        // Log everything so it shows in the Pterodactyl console
        System.err.println("[yt-dlp] exit=" + exitCode + " lines=" + lines.size());
        System.err.println("[yt-dlp] stdout: " + lines);
        if (!stderr.isEmpty()) System.err.println("[yt-dlp] stderr: " + stderr.toString().trim());

        if (exitCode != 0 || lines.size() < 3) return null;

        String title    = lines.get(0);
        long durationMs = parseDuration(lines.get(1));
        String url      = lines.get(lines.size() - 1);

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
