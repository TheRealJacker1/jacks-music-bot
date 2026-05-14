package com.musicbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main {

    public static void main(String[] args) throws Exception {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: DISCORD_TOKEN environment variable is not set.");
            System.exit(1);
        }

        MusicManager.getInstance();

        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .addEventListeners(new BotListener())
                .build();

        jda.awaitReady();
        System.out.println("Bot is online as: " + jda.getSelfUser().getAsTag());
    }
}
