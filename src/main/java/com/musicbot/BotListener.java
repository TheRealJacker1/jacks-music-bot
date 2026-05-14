package com.musicbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Set;
import java.util.stream.Collectors;

public class BotListener extends ListenerAdapter {

    private final ChannelConfig channelConfig = new ChannelConfig();
    private final PrefixConfig prefixConfig = new PrefixConfig();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String guildId = event.getGuild().getId();
        String prefix = prefixConfig.getPrefix(guildId);
        String content = event.getMessage().getContentRaw();

        if (!content.startsWith(prefix)) return;

        String command = content.substring(prefix.length()).trim().toLowerCase();
        String channelId = event.getChannel().getId();

        // Admin commands bypass the channel allowlist
        if (isAdminCommand(command)) {
            handleAdminCommand(event, command, guildId, prefix);
            return;
        }

        if (!channelConfig.isAllowed(guildId, channelId)) return;

        switch (command) {
            case "ping" -> event.getChannel().sendMessage("Pong!").queue();
            case "hello" -> event.getChannel().sendMessage("Hello, " + event.getAuthor().getAsMention() + "!").queue();
            case "help" -> event.getChannel().sendMessage(
                    "**Commands** (prefix: `" + prefix + "`)\n" +
                    "`" + prefix + "ping` — check if the bot is alive\n" +
                    "`" + prefix + "hello` — say hello\n" +
                    "`" + prefix + "help` — show this message\n\n" +
                    "**Admin commands** (requires Manage Server):\n" +
                    "`" + prefix + "setprefix <prefix>` — change the command prefix\n" +
                    "`" + prefix + "resetprefix` — reset prefix back to `!`\n" +
                    "`" + prefix + "allowchannel [#channel]` — restrict bot to a channel\n" +
                    "`" + prefix + "removechannel [#channel]` — remove a channel from the allowlist\n" +
                    "`" + prefix + "allowedchannels` — list allowed channels\n" +
                    "`" + prefix + "allowall` — remove all channel restrictions"
            ).queue();
            default -> event.getChannel().sendMessage("Unknown command. Try `" + prefix + "help`.").queue();
        }
    }

    private boolean isAdminCommand(String command) {
        return command.startsWith("setprefix") || command.equals("resetprefix")
                || command.startsWith("allowchannel") || command.startsWith("removechannel")
                || command.equals("allowedchannels") || command.equals("allowall");
    }

    private void handleAdminCommand(MessageReceivedEvent event, String command, String guildId, String prefix) {
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.getChannel().sendMessage("You need the **Manage Server** permission to use this command.").queue();
            return;
        }

        if (command.startsWith("setprefix ")) {
            String newPrefix = command.substring("setprefix ".length()).trim();
            if (newPrefix.isEmpty() || newPrefix.length() > 5) {
                event.getChannel().sendMessage("Prefix must be between 1 and 5 characters.").queue();
                return;
            }
            prefixConfig.setPrefix(guildId, newPrefix);
            event.getChannel().sendMessage("Prefix changed to `" + newPrefix + "`").queue();
            return;
        }

        if (command.equals("resetprefix")) {
            prefixConfig.resetPrefix(guildId);
            event.getChannel().sendMessage("Prefix reset to `!`").queue();
            return;
        }

        if (command.equals("allowedchannels")) {
            Set<String> channels = channelConfig.getChannels(guildId);
            if (channels.isEmpty()) {
                event.getChannel().sendMessage("No restrictions set — the bot responds in **all channels**.").queue();
            } else {
                String list = channels.stream().map(id -> "<#" + id + ">").collect(Collectors.joining(", "));
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
