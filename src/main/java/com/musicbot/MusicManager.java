package com.musicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MusicManager {

    private static MusicManager instance;

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers = new HashMap<>();

    private MusicManager() {
        playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    public static synchronized MusicManager getInstance() {
        if (instance == null) instance = new MusicManager();
        return instance;
    }

    public synchronized GuildMusicManager getGuildMusicManager(Guild guild) {
        GuildMusicManager manager = musicManagers.computeIfAbsent(
                guild.getIdLong(), id -> new GuildMusicManager(playerManager)
        );
        guild.getAudioManager().setSendingHandler(manager.sendHandler);
        return manager;
    }

    public void loadAndPlay(TextChannel channel, Guild guild, String input) {
        GuildMusicManager musicManager = getGuildMusicManager(guild);

        channel.sendMessage("Searching...").queue();

        CompletableFuture.runAsync(() -> {
            try {
                YtDlpExtractor.TrackInfo info = YtDlpExtractor.extract(input);
                if (info == null) {
                    channel.sendMessage("No results found for: `" + input + "`").queue();
                    return;
                }

                final String title = info.title();

                playerManager.loadItemOrdered(musicManager, info.streamUrl(), new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        track.setUserData(title);
                        musicManager.scheduler.queue(track);
                        channel.sendMessage("Added to queue: **" + title + "**").queue();
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        if (!playlist.getTracks().isEmpty()) {
                            AudioTrack track = playlist.getTracks().get(0);
                            track.setUserData(title);
                            musicManager.scheduler.queue(track);
                            channel.sendMessage("Added to queue: **" + title + "**").queue();
                        }
                    }

                    @Override
                    public void noMatches() {
                        channel.sendMessage("Could not load audio. Try a different search.").queue();
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        channel.sendMessage("Failed to play track: " + exception.getMessage()).queue();
                    }
                });

            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("No such file") || msg.contains("error=2"))) {
                    channel.sendMessage("yt-dlp not found. Please reinstall the server.").queue();
                } else {
                    channel.sendMessage("Error loading track: " + msg).queue();
                }
            }
        });
    }
}
