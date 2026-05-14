package com.musicbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Set;
import java.util.stream.Collectors;

public class BotListener extends ListenerAdapter {

    private static final String PREFIX = "!";
    private final ChannelConfig channelConfig = new ChannelConfig();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String guildId = event.getGuild().getId();
        String channelId = event.getChannel().getId();

        String content = event.getMessage().getContentRaw();
        if (!content.startsWith(PREFIX)) return;

        String command = content.substring(PREFIX.length()).trim().toLowerCase();

        // Channel config commands bypass the allowlist so admins can always manage it
        if (command.startsWith("allowchannel") || command.startsWith("removechannel")
                || command.equals("allowedchannels") || command.equals("allowall")) {
            handleAdminCommand(event, command, guildId);
            return;
        }

        if (!channelConfig.isAllowed(guildId, channelId)) return;

        switch (command) {
            case "ping" -> event.getChannel().sendMessage("Pong!").queue();
            case "hello" -> event.getChannel().sendMessage("Hello, " + event.getAuthor().getAsMention() + "!").queue();
            case "help" -> event.getChannel().sendMessage("""
                    **Commands:**
                    `!ping` — check if the bot is alive
                    `!hello` — say hello
                    `!help` — show this message

                    **Admin commands** (requires Manage Channels):
                    `!allowchannel` — restrict bot to this channel
                    `!allowchannel #channel` — restrict bot to a mentioned channel
                    `!removechannel` — remove this channel from the allowlist
                    `!removechannel #channel` — remove a mentioned channel
                    `!allowedchannels` — list all allowed channels
                    `!allowall` — remove all restrictions
                    """).queue();
            default -> event.getChannel().sendMessage("Unknown command. Try `!help`.").queue();
        }
    }

    private void handleAdminCommand(MessageReceivedEvent event, String command, String guildId) {
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.getChannel().sendMessage("You need the **Manage Channels** permission to use this command.").queue();
            return;
        }

        if (command.equals("allowedchannels")) {
            Set<String> channels = channelConfig.getChannels(guildId);
            if (channels.isEmpty()) {
                event.getChannel().sendMessage("No restrictions set — the bot responds in **all channels**.").queue();
            } else {
                String list = channels.stream()
                        .map(id -> "<#" + id + ">")
                        .collect(Collectors.joining(", "));
                event.getChannel().sendMessage("Bot is allowed in: " + list).queue();
            }
            return;
        }

        if (command.equals("allowall")) {
            channelConfig.clearChannels(guildId);
            event.getChannel().sendMessage("Restrictions cleared — the bot will respond in **all channels**.").queue();
            return;
        }

        // Resolve target channel: mentioned channel or current channel
        TextChannel target;
        if (!event.getMessage().getMentions().getChannels().isEmpty()) {
            target = (TextChannel) event.getMessage().getMentions().getChannels().get(0);
        } else {
            target = (TextChannel) event.getChannel();
        }

        if (command.startsWith("allowchannel")) {
            channelConfig.addChannel(guildId, target.getId());
            event.getChannel().sendMessage(target.getAsMention() + " added to the allowlist.").queue();
        } else if (command.startsWith("removechannel")) {
            channelConfig.removeChannel(guildId, target.getId());
            Set<String> remaining = channelConfig.getChannels(guildId);
            if (remaining.isEmpty()) {
                event.getChannel().sendMessage(target.getAsMention() + " removed. No restrictions remain — bot responds in **all channels**.").queue();
            } else {
                event.getChannel().sendMessage(target.getAsMention() + " removed from the allowlist.").queue();
            }
        }
    }
}
