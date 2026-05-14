package com.musicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.HashMap;
import java.util.Map;

public class MusicManager {

    private static MusicManager instance;

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers = new HashMap<>();

    private MusicManager() {
        playerManager = new DefaultAudioPlayerManager();

        YoutubeAudioSourceManager youtubeSource = new YoutubeAudioSourceManager();
        String oauthToken = System.getenv("YOUTUBE_OAUTH_TOKEN");
        if (oauthToken != null && !oauthToken.isBlank()) {
            // Use saved token — no interactive prompt needed
            youtubeSource.useOauth2(oauthToken, true);
        } else {
            // No token set: triggers device auth flow on startup.
            // Check the console for a URL + code, authorize once, then save the
            // printed refresh token as the YOUTUBE_OAUTH_TOKEN server variable.
            youtubeSource.useOauth2(null, false);
        }

        playerManager.registerSourceManager(youtubeSource);
        AudioSourceManagers.registerRemoteSources(playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
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

        // Treat non-URLs as YouTube searches
        String query = input.startsWith("http://") || input.startsWith("https://")
                ? input
                : "ytsearch:" + input;

        playerManager.loadItemOrdered(musicManager, query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.scheduler.queue(track);
                channel.sendMessage("Added to queue: **" + track.getInfo().title + "**").queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    AudioTrack track = playlist.getTracks().get(0);
                    musicManager.scheduler.queue(track);
                    channel.sendMessage("Added to queue: **" + track.getInfo().title + "**").queue();
                } else {
                    playlist.getTracks().forEach(musicManager.scheduler::queue);
                    channel.sendMessage("Added **" + playlist.getTracks().size()
                            + "** tracks from playlist: **" + playlist.getName() + "**").queue();
                }
            }

            @Override
            public void noMatches() {
                channel.sendMessage("No results found for: `" + input + "`").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Could not load track: " + exception.getMessage()).queue();
            }
        });
    }
}
