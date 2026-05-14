package com.musicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class GuildMusicManager {

    public final AudioPlayer player;
    public final TrackScheduler scheduler;
    public final AudioPlayerSendHandler sendHandler;

    public TextChannel boundChannel;

    public GuildMusicManager(AudioPlayerManager manager) {
        player = manager.createPlayer();
        scheduler = new TrackScheduler(player, this);
        player.addListener(scheduler);
        sendHandler = new AudioPlayerSendHandler(player);
    }
}
