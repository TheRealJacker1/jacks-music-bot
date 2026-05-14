package com.musicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {

    public final AudioPlayer player;
    public final Queue<AudioTrack> queue = new LinkedBlockingQueue<>();
    private final GuildMusicManager manager;

    public TrackScheduler(AudioPlayer player, GuildMusicManager manager) {
        this.player  = player;
        this.manager = manager;
    }

    public void queue(AudioTrack track) {
        boolean started = player.startTrack(track, true);
        System.err.println("[Music] startTrack returned " + started
                + " | playing=" + (player.getPlayingTrack() != null)
                + " | paused=" + player.isPaused()
                + " | track=" + getTitle(track));
        if (!started) {
            queue.offer(track);
        }
    }

    public void skip() {
        player.startTrack(queue.poll(), false);
    }

    public void stop() {
        queue.clear();
        player.stopTrack();
    }

    public List<AudioTrack> getQueueAsList() {
        return new LinkedList<>(queue);
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        System.err.println("[Music] Track started: " + getTitle(track));
        if (manager.boundChannel != null) {
            manager.boundChannel.sendMessage("Now playing: **" + getTitle(track) + "**").queue();
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            skip();
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        System.err.println("[Music] Playback error for \"" + getTitle(track) + "\": " + exception.getMessage());
        if (manager.boundChannel != null) {
            manager.boundChannel.sendMessage(
                "Failed to play **" + getTitle(track) + "**: " + exception.getMessage()
            ).queue();
        }
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        System.err.println("[Music] Track stuck: " + getTitle(track));
        if (manager.boundChannel != null) {
            manager.boundChannel.sendMessage(
                "Track got stuck, skipping: **" + getTitle(track) + "**"
            ).queue();
        }
        skip();
    }

    private static String getTitle(AudioTrack track) {
        Object userData = track.getUserData();
        if (userData instanceof String s) return s;
        return track.getInfo().title;
    }
}
