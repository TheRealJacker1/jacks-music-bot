package com.musicbot;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class YtDlpExtractor {

    public record TrackInfo(String title, String streamUrl, long durationMs) {}

    public static TrackInfo extract(String query) throws Exception {
        String ytQuery = query.startsWith("http://") || query.startsWith("https://")
                ? query
                : "ytsearch1:" + query;

        ProcessBuilder pb = new ProcessBuilder(
                "./yt-dlp",
                "--no-playlist",
                "--no-warnings",
                "-f", "bestaudio[ext=webm][acodec=opus]/bestaudio[acodec=opus]/bestaudio",
                "-j",
                ytQuery
        );
        pb.directory(new File("."));
        pb.redirectErrorStream(false);

        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n")).trim();
        }
        process.waitFor();

        if (output.isEmpty()) return null;

        // For ytsearch results yt-dlp may return the playlist wrapper; take first line
        String firstLine = output.lines().filter(l -> l.startsWith("{")).findFirst().orElse(null);
        if (firstLine == null) return null;

        JSONObject json = new JSONObject(firstLine);
        String title    = json.optString("title", "Unknown title");
        double duration = json.optDouble("duration", 0);
        String url      = json.optString("url", "");

        if (url.isEmpty()) return null;

        return new TrackInfo(title, url, (long)(duration * 1000));
    }
}
