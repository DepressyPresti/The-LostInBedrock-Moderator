package com.lostinbedrock.discordmod;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DataStore {
    private final File file;
    private FileConfiguration cfg;

    public DataStore(File file) {
        this.file = file;
        this.cfg = new YamlConfiguration();
    }

    public void load() throws IOException {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (!file.exists()) {
            file.createNewFile();
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.isConfigurationSection("mappings")) cfg.createSection("mappings");
        if (!cfg.isConfigurationSection("bans")) cfg.createSection("bans");
        if (!cfg.isConfigurationSection("warns")) cfg.createSection("warns");
        if (!cfg.isConfigurationSection("mutes")) cfg.createSection("mutes");
        save();
    }

    public void save() throws IOException {
        cfg.save(file);
    }

    public void saveMapping(String discordId, String mcName) {
        cfg.set("mappings." + discordId, mcName.toLowerCase(Locale.ROOT));
        trySave();
    }

    public String getMcNameForDiscord(String discordId) {
        return cfg.getString("mappings." + discordId, null);
    }

    public String getDiscordIdForMcName(String mcName) {
        String target = mcName.toLowerCase(Locale.ROOT);
        if (!cfg.isConfigurationSection("mappings")) return null;
        for (String k : Objects.requireNonNull(cfg.getConfigurationSection("mappings")).getKeys(false)) {
            if (target.equalsIgnoreCase(cfg.getString("mappings." + k))) return k;
        }
        return null;
    }

    public void recordBan(BanRecord rec) {
        String path = "bans." + rec.getMinecraftName().toLowerCase(Locale.ROOT);
        cfg.set(path + ".discordId", rec.getDiscordId());
        cfg.set(path + ".reason", rec.getReason());
        cfg.set(path + ".issuedAt", rec.getIssuedAtEpoch());
        cfg.set(path + ".expiresAt", rec.getExpiresAtEpoch());
        cfg.set(path + ".permanent", rec.isPermanent());
        cfg.set(path + ".proofUrls", rec.getProofUrls());
        cfg.set(path + ".notifyMessageId", rec.getNotifyMessageId());
        cfg.set(path + ".activeMessageId", rec.getActiveMessageId());
        trySave();
    }

    public BanRecord getBanByMcName(String mcName) {
        String path = "bans." + mcName.toLowerCase(Locale.ROOT);
        if (!cfg.isConfigurationSection(path)) return null;
        String discordId = cfg.getString(path + ".discordId", "");
        String reason = cfg.getString(path + ".reason", "");
        long issuedAt = cfg.getLong(path + ".issuedAt", 0L);
        long expiresAt = cfg.getLong(path + ".expiresAt", 0L);
        boolean permanent = cfg.getBoolean(path + ".permanent", false);
        List<String> proof = cfg.getStringList(path + ".proofUrls");
        long msgId = cfg.getLong(path + ".notifyMessageId", 0L);
        long activeMsgId = cfg.getLong(path + ".activeMessageId", 0L);
        return new BanRecord(mcName, discordId, reason, proof, issuedAt, expiresAt, permanent, msgId, activeMsgId);
    }

    public void removeBan(String mcName) {
        cfg.set("bans." + mcName.toLowerCase(Locale.ROOT), null);
        trySave();
    }

    public List<BanRecord> getAllBans() {
        if (!cfg.isConfigurationSection("bans")) return Collections.emptyList();
        return cfg.getConfigurationSection("bans").getKeys(false).stream()
                .map(this::getBanByMcName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public int incrementWarn(String discordId) {
        int current = cfg.getInt("warns." + discordId, 0);
        current += 1;
        cfg.set("warns." + discordId, current);
        trySave();
        return current;
    }

    public int getWarnCount(String discordId) {
        return cfg.getInt("warns." + discordId, 0);
    }

    public void recordMute(String discordId, long expiresAtEpoch) {
        cfg.set("mutes." + discordId, expiresAtEpoch);
        trySave();
    }

    public Long getMuteExpires(String discordId) {
        if (!cfg.isSet("mutes." + discordId)) return null;
        return cfg.getLong("mutes." + discordId);
    }

    public Set<String> getAllMuteIds() {
        if (!cfg.isConfigurationSection("mutes")) return Collections.emptySet();
        return new HashSet<>(cfg.getConfigurationSection("mutes").getKeys(false));
    }

    public void removeMute(String discordId) {
        cfg.set("mutes." + discordId, null);
        trySave();
    }

    private void trySave() {
        try { save(); } catch (Exception ignored) {}
    }
}
