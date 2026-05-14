package com.musicbot;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BotListener extends ListenerAdapter {

    private final ChannelConfig channelConfig = new ChannelConfig();
    private final PrefixConfig prefixConfig = new PrefixConfig();
    private final MusicManager musicManager = MusicManager.getInstance();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String guildId = event.getGuild().getId();
        String prefix = prefixConfig.getPrefix(guildId);
        String content = event.getMessage().getContentRaw();

        if (!content.startsWith(prefix)) return;

        String full = content.substring(prefix.length()).trim();
        String command = full.toLowerCase().split("\\s+")[0];
        String args = full.contains(" ") ? full.substring(full.indexOf(" ") + 1).trim() : "";
        String channelId = event.getChannel().getId();

        if (isAdminCommand(command)) {
            handleAdminCommand(event, command, args, guildId, prefix);
            return;
        }

        if (!channelConfig.isAllowed(guildId, channelId)) return;

        switch (command) {
            case "ping"   -> event.getChannel().sendMessage("Pong!").queue();
            case "hello"  -> event.getChannel().sendMessage("Hello, " + event.getAuthor().getAsMention() + "!").queue();
            case "help"   -> sendHelp(event, prefix);

            case "play"   -> handlePlay(event, args);
            case "skip"   -> handleSkip(event);
            case "stop"   -> handleStop(event);
            case "pause"  -> handlePause(event);
            case "resume" -> handleResume(event);
            case "queue", "q" -> handleQueue(event);
            case "nowplaying", "np" -> handleNowPlaying(event);
            case "volume", "vol"    -> handleVolume(event, args);
            case "leave"  -> handleLeave(event);

            default -> event.getChannel().sendMessage("Unknown command. Try `" + prefix + "help`.").queue();
        }
    }

    // ── Music commands ────────────────────────────────────────────────────────

    private void handlePlay(MessageReceivedEvent event, String args) {
        if (args.isBlank()) {
            event.getChannel().sendMessage("Usage: `play <url or search query>`").queue();
            return;
        }
        if (!joinVoiceChannel(event)) return;
        musicManager.loadAndPlay((TextChannel) event.getChannel(), event.getGuild(), args);
    }

    private void handleSkip(MessageReceivedEvent event) {
        GuildMusicManager manager = musicManager.getGuildMusicManager(event.getGuild());
        if (manager.player.getPlayingTrack() == null) {
            event.getChannel().sendMessage("Nothing is playing.").queue();
            return;
        }
        manager.scheduler.skip();
        event.getChannel().sendMessage("Skipped.").queue();
    }

    private void handleStop(MessageReceivedEvent event) {
        GuildMusicManager manager = musicManager.getGuildMusicManager(event.getGuild());
        manager.scheduler.stop();
        event.getGuild().getAudioManager().closeAudioConnection();
        event.getChannel().sendMessage("Stopped and disconnected.").queue();
    }

    private void handlePause(MessageReceivedEvent event) {
        GuildMusicManager manager = musicManager.getGuildMusicManager(event.getGuild());
        if (manager.player.getPlayingTrack() == null) {
            event.getChannel().sendMessage("Nothing is playing.").queue();
            return;
        }
        manager.player.setPaused(true);
        event.getChannel().sendMessage("Paused.").queue();
    }

    private void handleResume(MessageReceivedEvent event) {
        GuildMusicManager manager = musicManager.getGuildMusicManager(event.getGuild());
        if (!manager.player.isPaused()) {
            event.getChannel().sendMessage("Playback is not paused.").queue();
            return;
        }
        manager.player.setPaused(false);
        event.getChannel().sendMessage("Resumed.").queue();
    }

    private void handleQueue(MessageReceivedEvent event) {
        GuildMusicManager manager = musicManager.getGuildMusicManager(event.getGuild());
        AudioTrack current = manager.player.getPlayingTrack();
        List<AudioTrack> queue = manager.scheduler.getQueueAsList();

        if (current == null && queue.isEmpty()) {
            event.getChannel().sendMessage("The queue is empty.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (current != null) {
            sb.append("**Now playing:** ").append(current.getInfo().title).append("\n\n");
        }
        if (!queue.isEmpty()) {
            sb.append("**Up next:**\n");
            int limit = Math.min(queue.size(), 10);
            for (int i = 0; i < limit; i++) {
                sb.append("`").append(i + 1).append(".` ").append(queue.get(i).getInfo().title).append("\n");
            }
            if (queue.size() > 10) {
                sb.append("*...and ").append(queue.size() - 10).append(" more*");
            }
        }
        event.getChannel().sendMessage(sb.toString()).queue();
    }

    private void handleNowPlaying(MessageReceivedEvent event) {
        GuildMusicManager manager = musicManager.getGuildMusicManager(event.getGuild());
        AudioTrack track = manager.player.getPlayingTrack();
        if (track == null) {
            event.getChannel().sendMessage("Nothing is playing.").queue();
            return;
        }
        long pos = track.getPosition() / 1000;
        long len = track.getDuration() / 1000;
        event.getChannel().sendMessage(
                "**Now playing:** " + track.getInfo().title
                + "\n" + formatTime(pos) + " / " + formatTime(len)
        ).queue();
    }

    private void handleVolume(MessageReceivedEvent event, String args) {
        GuildMusicManager manager = musicManager.getGuildMusicManager(event.getGuild());
        if (args.isBlank()) {
            event.getChannel().sendMessage("Current volume: **" + manager.player.getVolume() + "%**").queue();
            return;
        }
        try {
            int volume = Integer.parseInt(args.trim());
            if (volume < 0 || volume > 100) {
                event.getChannel().sendMessage("Volume must be between 0 and 100.").queue();
                return;
            }
            manager.player.setVolume(volume);
            event.getChannel().sendMessage("Volume set to **" + volume + "%**").queue();
        } catch (NumberFormatException e) {
            event.getChannel().sendMessage("Usage: `volume <0-100>`").queue();
        }
    }

    private void handleLeave(MessageReceivedEvent event) {
        event.getGuild().getAudioManager().closeAudioConnection();
        event.getChannel().sendMessage("Left the voice channel.").queue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean joinVoiceChannel(MessageReceivedEvent event) {
        GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.getChannel().sendMessage("You need to be in a voice channel first.").queue();
            return false;
        }
        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            audioManager.openAudioConnection(voiceState.getChannel());
        }
        return true;
    }

    private String formatTime(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private void sendHelp(MessageReceivedEvent event, String prefix) {
        String p = prefix;
        event.getChannel().sendMessage(
                "**Music commands:**\n" +
                "`" + p + "play <url/search>` — play a song or add it to the queue\n" +
                "`" + p + "skip` — skip the current song\n" +
                "`" + p + "stop` — stop playback and clear the queue\n" +
                "`" + p + "pause` — pause playback\n" +
                "`" + p + "resume` — resume playback\n" +
                "`" + p + "queue` — show the current queue\n" +
                "`" + p + "nowplaying` — show the current song\n" +
                "`" + p + "volume <0-100>` — set or check the volume\n" +
                "`" + p + "leave` — disconnect from voice\n\n" +
                "**General:**\n" +
                "`" + p + "ping` — check if the bot is alive\n" +
                "`" + p + "hello` — say hello\n\n" +
                "**Admin commands** (requires Manage Server):\n" +
                "`" + p + "setprefix <prefix>` — change the command prefix\n" +
                "`" + p + "resetprefix` — reset prefix back to `!`\n" +
                "`" + p + "allowchannel [#channel]` — restrict bot to a channel\n" +
                "`" + p + "removechannel [#channel]` — remove a channel from the allowlist\n" +
                "`" + p + "allowedchannels` — list allowed channels\n" +
                "`" + p + "allowall` — remove all channel restrictions"
        ).queue();
    }

    // ── Admin commands ────────────────────────────────────────────────────────

    private boolean isAdminCommand(String command) {
        return command.equals("setprefix") || command.equals("resetprefix")
                || command.equals("allowchannel") || command.equals("removechannel")
                || command.equals("allowedchannels") || command.equals("allowall");
    }

    private void handleAdminCommand(MessageReceivedEvent event, String command, String args, String guildId, String prefix) {
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.getChannel().sendMessage("You need the **Manage Server** permission to use this command.").queue();
            return;
        }

        switch (command) {
            case "setprefix" -> {
                if (args.isBlank() || args.length() > 5) {
                    event.getChannel().sendMessage("Prefix must be between 1 and 5 characters.").queue();
                    return;
                }
                prefixConfig.setPrefix(guildId, args);
                event.getChannel().sendMessage("Prefix changed to `" + args + "`").queue();
            }
            case "resetprefix" -> {
                prefixConfig.resetPrefix(guildId);
                event.getChannel().sendMessage("Prefix reset to `!`").queue();
            }
            case "allowedchannels" -> {
                Set<String> channels = channelConfig.getChannels(guildId);
                if (channels.isEmpty()) {
                    event.getChannel().sendMessage("No restrictions set — the bot responds in **all channels**.").queue();
                } else {
                    String list = channels.stream().map(id -> "<#" + id + ">").collect(Collectors.joining(", "));
                    event.getChannel().sendMessage("Bot is allowed in: " + list).queue();
                }
            }
            case "allowall" -> {
                channelConfig.clearChannels(guildId);
                event.getChannel().sendMessage("Restrictions cleared — the bot will respond in **all channels**.").queue();
            }
            case "allowchannel", "removechannel" -> {
                TextChannel target;
                if (!event.getMessage().getMentions().getChannels().isEmpty()) {
                    target = (TextChannel) event.getMessage().getMentions().getChannels().get(0);
                } else {
                    target = (TextChannel) event.getChannel();
                }
                if (command.equals("allowchannel")) {
                    channelConfig.addChannel(guildId, target.getId());
                    event.getChannel().sendMessage(target.getAsMention() + " added to the allowlist.").queue();
                } else {
                    channelConfig.removeChannel(guildId, target.getId());
                    Set<String> remaining = channelConfig.getChannels(guildId);
                    if (remaining.isEmpty()) {
                        event.getChannel().sendMessage(target.getAsMention() + " removed. Bot now responds in **all channels**.").queue();
                    } else {
                        event.getChannel().sendMessage(target.getAsMention() + " removed from the allowlist.").queue();
                    }
                }
            }
        }
    }
}
