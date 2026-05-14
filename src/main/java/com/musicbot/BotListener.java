package com.musicbot;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class BotListener extends ListenerAdapter {

    private static final String PREFIX = "!";

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        if (!content.startsWith(PREFIX)) return;

        String command = content.substring(PREFIX.length()).trim().toLowerCase();

        switch (command) {
            case "ping" -> event.getChannel().sendMessage("Pong!").queue();
            case "hello" -> event.getChannel().sendMessage("Hello, " + event.getAuthor().getAsMention() + "!").queue();
            case "help" -> event.getChannel().sendMessage("""
                    **Commands:**
                    `!ping` — check if the bot is alive
                    `!hello` — say hello
                    `!help` — show this message
                    """).queue();
            default -> event.getChannel().sendMessage("Unknown command. Try `!help`.").queue();
        }
    }
}
