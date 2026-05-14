package com.musicbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class YtDlpExtractor {

    public record TrackInfo(String title, String streamUrl, long durationMs) {}
    public record SearchResult(String title, long durationMs, String videoId) {}

    private static final String COOKIES_FILE = "./yt-dlp-cookies.txt";

    /** Resolve a direct URL or ytsearch1: query into a playable stream URL. */
    public static TrackInfo extract(String query) throws Exception {
        String ytQuery = query.startsWith("http://") || query.startsWith("https://")
                ? query
                : "ytsearch1:" + query;

        List<String> baseArgs = baseArgs();
        baseArgs.add("-f");
        baseArgs.add("bestaudio/best");
        baseArgs.add("--print");
        baseArgs.add("%(title)s");
        baseArgs.add("--print");
        baseArgs.add("%(duration)s");
        baseArgs.add("-g");
        baseArgs.add(ytQuery);

        RunResult result = run(baseArgs);
        // With --ignore-errors, yt-dlp may exit non-zero even when individual videos
        // succeed (e.g. 1 of 5 search results works). Trust the line count, not the exit code.
        if (result.lines().size() < 3) {
            throw new Exception(friendlyError(result.stderr()));
        }

        String title    = result.lines().get(0);
        long durationMs = parseDuration(result.lines().get(1));
        String url      = result.lines().get(result.lines().size() - 1);

        if (!url.startsWith("http")) throw new Exception("No stream URL in yt-dlp output");
        return new TrackInfo(title, url, durationMs);
    }

    /** Search YouTube for up to {@code count} results and return metadata (no stream URL). */
    public static List<SearchResult> searchMultiple(String query, int count) throws Exception {
        List<String> args = baseArgs();
        args.add("--print");
        args.add("%(title)s");
        args.add("--print");
        args.add("%(duration)s");
        args.add("--print");
        args.add("%(id)s");
        args.add("ytsearch" + count + ":" + query);

        RunResult result = run(args);
        List<SearchResult> results = new ArrayList<>();
        // Trust line count, not exit code — --ignore-errors makes yt-dlp exit non-zero
        // even when some videos succeeded.
        if (result.lines().isEmpty()) return results;

        List<String> lines = result.lines();
        for (int i = 0; i + 2 < lines.size(); i += 3) {
            String title    = lines.get(i);
            long durationMs = parseDuration(lines.get(i + 1));
            String videoId  = lines.get(i + 2);
            if (!videoId.isBlank()) {
                results.add(new SearchResult(title, durationMs, videoId));
            }
        }
        return results;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private static boolean cookiesLogged = false;

    private static List<String> baseArgs() {
        List<String> args = new ArrayList<>();
        args.add("./yt-dlp");
        args.add("--no-playlist");
        args.add("--quiet");
        args.add("--no-warnings");
        File cookiesFile = new File(COOKIES_FILE);
        boolean hasCookies = cookiesFile.exists();
        if (!cookiesLogged) {
            cookiesLogged = true;
            System.out.println("[yt-dlp] cookies file " + cookiesFile.getAbsolutePath()
                    + " exists=" + hasCookies
                    + (hasCookies ? " size=" + cookiesFile.length() : ""));
        }
        if (hasCookies) {
            args.add("--cookies");
            args.add(COOKIES_FILE);
        }
        // tv_embedded is the only client that:
        //  - Bypasses YouTube's PO-token requirement (no "Sign in to confirm" on datacenter IPs)
        //  - Returns the full format list including audio-only DASH streams
        args.add("--extractor-args");
        args.add("youtube:player_client=tv_embedded");
        // Continue past per-video errors so search results still yield SOMETHING
        args.add("--ignore-errors");
        return args;
    }

    private record RunResult(boolean ok, List<String> lines, String stderr) {}

    private static RunResult run(List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File("."));

        Process process = pb.start();

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

        if (exitCode != 0) {
            System.err.println("[yt-dlp] exit=" + exitCode + " lines=" + lines.size());
            if (!stderr.isEmpty()) System.err.println("[yt-dlp] stderr: " + stderr.toString().trim());
        }

        return new RunResult(exitCode == 0, lines, stderr.toString());
    }

    private static String friendlyError(String stderr) {
        if (stderr.contains("Sign in to confirm")) {
            return "YouTube requires authentication on this server IP.\n" +
                   "Fix: export YouTube cookies from your browser, save them as `yt-dlp-cookies.txt` " +
                   "and upload to the server root via SFTP.\n" +
                   "Guide: https://github.com/yt-dlp/yt-dlp/wiki/FAQ#how-do-i-pass-cookies-to-yt-dlp";
        }
        if (stderr.contains("Video unavailable") || stderr.contains("This video is not available")) {
            return "That video is unavailable or region-locked.";
        }
        if (stderr.contains("Private video") || stderr.contains("is private")) {
            return "That video is private.";
        }
        if (stderr.contains("No such file") || stderr.contains("error=2")) {
            return "yt-dlp binary not found — please reinstall the server.";
        }
        // Trim to first meaningful ERROR line
        for (String line : stderr.split("\n")) {
            if (line.startsWith("ERROR:")) {
                String msg = line.replaceFirst("ERROR:\\s*\\[youtube\\]\\s*[A-Za-z0-9_-]*:\\s*", "");
                return msg.trim();
            }
        }
        return stderr.isEmpty() ? "Unknown yt-dlp error" : stderr.lines().findFirst().orElse("yt-dlp failed");
    }

    private static long parseDuration(String raw) {
        try {
            return (long) (Double.parseDouble(raw) * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
