package com.musicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.TvHtml5Simply;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Android;
import dev.lavalink.youtube.clients.AndroidMusic;
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

        // Try every client — Tv is OAuth-compatible, rest are fallbacks
        YoutubeAudioSourceManager youtubeSource = new YoutubeAudioSourceManager(
                true, new Tv(), new TvHtml5Simply(), new Web(), new MWeb(), new Android(), new AndroidMusic()
        );
        String oauthToken = System.getenv("YOUTUBE_OAUTH_TOKEN");
        if (oauthToken != null && !oauthToken.isBlank()) {
            System.out.println("[YouTube] Using saved OAuth token.");
            youtubeSource.useOauth2(oauthToken, true);
        } else {
            System.out.println("=================================================");
            System.out.println("  YOUTUBE OAUTH SETUP REQUIRED");
            System.out.println("  Watch for a URL + code below from the library.");
            System.out.println("  Open the URL, enter the code, authorize it.");
            System.out.println("  Then copy the refresh token that gets printed");
            System.out.println("  and save it as YOUTUBE_OAUTH_TOKEN in Pterodactyl");
            System.out.println("  Startup tab, then restart the server.");
            System.out.println("=================================================");
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
