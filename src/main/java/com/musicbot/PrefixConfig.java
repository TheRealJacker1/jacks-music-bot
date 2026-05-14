package com.musicbot;

import java.io.*;
import java.util.Properties;

public class PrefixConfig {

    private static final String CONFIG_FILE = "prefixes.properties";
    private static final String DEFAULT_PREFIX = "!";
    private final Properties props = new Properties();

    public PrefixConfig() {
        load();
    }

    public String getPrefix(String guildId) {
        return props.getProperty(guildId, DEFAULT_PREFIX);
    }

    public void setPrefix(String guildId, String prefix) {
        props.setProperty(guildId, prefix);
        save();
    }

    public void resetPrefix(String guildId) {
        props.remove(guildId);
        save();
    }

    private void load() {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (FileNotFoundException ignored) {
        } catch (IOException e) {
            System.err.println("Failed to load prefix config: " + e.getMessage());
        }
    }

    private void save() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Per-guild command prefixes");
        } catch (IOException e) {
            System.err.println("Failed to save prefix config: " + e.getMessage());
        }
    }
}
