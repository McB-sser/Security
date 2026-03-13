package de.mcbesser.security.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

public final class SanctionRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public SanctionRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "sanctions.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void applyTempBan(UUID uuid, Duration duration, String reason) {
        yaml.set(path(uuid, "ban.until"), System.currentTimeMillis() + Math.max(1000L, duration.toMillis()));
        yaml.set(path(uuid, "ban.reason"), reason);
        saveQuietly();
    }

    public synchronized void applyTempMute(UUID uuid, Duration duration, String reason) {
        yaml.set(path(uuid, "mute.until"), System.currentTimeMillis() + Math.max(1000L, duration.toMillis()));
        yaml.set(path(uuid, "mute.reason"), reason);
        saveQuietly();
    }

    public synchronized long getRemainingBanMillis(UUID uuid) {
        long until = yaml.getLong(path(uuid, "ban.until"), 0L);
        if (until <= System.currentTimeMillis()) {
            yaml.set(path(uuid, "ban"), null);
            return 0L;
        }
        return until - System.currentTimeMillis();
    }

    public synchronized long getRemainingMuteMillis(UUID uuid) {
        long until = yaml.getLong(path(uuid, "mute.until"), 0L);
        if (until <= System.currentTimeMillis()) {
            yaml.set(path(uuid, "mute"), null);
            return 0L;
        }
        return until - System.currentTimeMillis();
    }

    public synchronized boolean isBanned(UUID uuid) {
        return getRemainingBanMillis(uuid) > 0L;
    }

    public synchronized boolean isMuted(UUID uuid) {
        return getRemainingMuteMillis(uuid) > 0L;
    }

    public synchronized String getBanReason(UUID uuid) {
        return yaml.getString(path(uuid, "ban.reason"), "Community sanction");
    }

    public synchronized String getMuteReason(UUID uuid) {
        return yaml.getString(path(uuid, "mute.reason"), "Chat moderation");
    }

    public synchronized void cleanupExpired() {
        var section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                getRemainingBanMillis(uuid);
                getRemainingMuteMillis(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }
        saveQuietly();
    }

    private String path(UUID uuid, String leaf) {
        return "players." + uuid + "." + leaf;
    }

    private void saveQuietly() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save sanctions: " + e.getMessage());
        }
    }
}
