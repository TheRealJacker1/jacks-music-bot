package com.musicbot;

import java.io.*;
import java.util.*;

public class ChannelConfig {

    private static final String CONFIG_FILE = "allowed_channels.properties";
    private final Properties props = new Properties();

    public ChannelConfig() {
        load();
    }

    public boolean isAllowed(String guildId, String channelId) {
        Set<String> channels = getChannels(guildId);
        return channels.isEmpty() || channels.contains(channelId);
    }

    public void addChannel(String guildId, String channelId) {
        Set<String> channels = getChannels(guildId);
        channels.add(channelId);
        props.setProperty(guildId, String.join(",", channels));
        save();
    }

    public void removeChannel(String guildId, String channelId) {
        Set<String> channels = getChannels(guildId);
        channels.remove(channelId);
        if (channels.isEmpty()) {
            props.remove(guildId);
        } else {
            props.setProperty(guildId, String.join(",", channels));
        }
        save();
    }

    public void clearChannels(String guildId) {
        props.remove(guildId);
        save();
    }

    public Set<String> getChannels(String guildId) {
        String value = props.getProperty(guildId, "").trim();
        if (value.isEmpty()) return new LinkedHashSet<>();
        return new LinkedHashSet<>(Arrays.asList(value.split(",")));
    }

    private void load() {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (FileNotFoundException ignored) {
        } catch (IOException e) {
            System.err.println("Failed to load channel config: " + e.getMessage());
        }
    }

    private void save() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Allowed channels per guild (guildId=channelId1,channelId2,...)");
        } catch (IOException e) {
            System.err.println("Failed to save channel config: " + e.getMessage());
        }
    }
}
