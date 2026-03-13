package de.mcbesser.security.storage;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerStatsRepository {
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, Long> activeSessions = new HashMap<>();

    public PlayerStatsRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "player-stats.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void onJoin(Player player) {
        activeSessions.put(player.getUniqueId(), System.currentTimeMillis());
        yaml.set(path(player.getUniqueId(), "name"), player.getName());
        yaml.set(path(player.getUniqueId(), "lastSeen"), System.currentTimeMillis());
        if (!yaml.getBoolean(path(player.getUniqueId(), "joinCountInitialized"), false)) {
            yaml.set(path(player.getUniqueId(), "joinCount"), 0);
            yaml.set(path(player.getUniqueId(), "joinCountInitialized"), true);
        }
        yaml.set(path(player.getUniqueId(), "joinCount"), yaml.getInt(path(player.getUniqueId(), "joinCount"), 0) + 1);
        saveQuietly();
    }

    public synchronized void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Long joinedAt = activeSessions.remove(uuid);
        if (joinedAt == null) {
            return;
        }
        long delta = Math.max(0L, System.currentTimeMillis() - joinedAt);
        long total = yaml.getLong(path(uuid, "playtimeMillis"), 0L) + delta;
        yaml.set(path(uuid, "playtimeMillis"), total);
        yaml.set(path(uuid, "name"), player.getName());
        yaml.set(path(uuid, "lastSeen"), System.currentTimeMillis());
        saveQuietly();
    }

    public synchronized void flushActiveSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(activeSessions).entrySet()) {
            UUID uuid = entry.getKey();
            long delta = Math.max(0L, now - entry.getValue());
            long total = yaml.getLong(path(uuid, "playtimeMillis"), 0L) + delta;
            yaml.set(path(uuid, "playtimeMillis"), total);
            yaml.set(path(uuid, "lastSeen"), now);
            activeSessions.put(uuid, now);
        }
        saveQuietly();
    }

    public synchronized long getEffectivePlaytimeMillis(UUID uuid) {
        long stored = yaml.getLong(path(uuid, "playtimeMillis"), 0L);
        Long joinedAt = activeSessions.get(uuid);
        if (joinedAt != null) {
            stored += Math.max(0L, System.currentTimeMillis() - joinedAt);
        }
        return stored;
    }

    public synchronized int getPlayedDaysScore(UUID uuid) {
        long millis = getEffectivePlaytimeMillis(uuid);
        if (millis <= 0L) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(millis / (double) DAY_MILLIS));
    }

    public synchronized int getPlayedDaysScore(OfflinePlayer player) {
        return getPlayedDaysScore(player.getUniqueId());
    }

    public synchronized String formatPlaytimeDays(UUID uuid) {
        double days = getEffectivePlaytimeMillis(uuid) / (double) DAY_MILLIS;
        return String.format(java.util.Locale.ROOT, "%.2f", days);
    }

    public synchronized int getKarma(UUID uuid) {
        long millis = getEffectivePlaytimeMillis(uuid);
        double hoursPerPoint = Math.max(1.0D, plugin.getConfig().getDouble("karma.base-hours-per-point", 24.0D));
        int playtimeKarma = Math.max(1, (int) Math.ceil(millis / (hoursPerPoint * 60D * 60D * 1000D)));
        int modifier = yaml.getInt(path(uuid, "karmaModifier"), 0);
        return playtimeKarma + modifier;
    }

    public synchronized int getKarma(OfflinePlayer player) {
        return getKarma(player.getUniqueId());
    }

    public synchronized int adjustKarma(UUID uuid, String playerName, int delta, String reason) {
        if (delta == 0) {
            return getKarma(uuid);
        }
        if (playerName != null && !playerName.isBlank()) {
            yaml.set(path(uuid, "name"), playerName);
        }
        int currentModifier = yaml.getInt(path(uuid, "karmaModifier"), 0);
        yaml.set(path(uuid, "karmaModifier"), currentModifier + delta);
        if (reason != null && !reason.isBlank()) {
            yaml.set(path(uuid, "lastKarmaReason"), reason);
            yaml.set(path(uuid, "lastKarmaChange"), delta);
            yaml.set(path(uuid, "lastKarmaAt"), System.currentTimeMillis());
        }
        saveQuietly();
        return getKarma(uuid);
    }

    public synchronized int getJoinCount(UUID uuid) {
        return Math.max(0, yaml.getInt(path(uuid, "joinCount"), 0));
    }

    public synchronized double getVotingPower(UUID uuid) {
        int karma = getKarma(uuid);
        double basePower = Math.max(0.05D, Math.max(0, karma));

        int joins = getJoinCount(uuid);
        double trustMultiplier;
        if (joins <= 3) {
            trustMultiplier = 0.01D;
        } else if (joins <= 10) {
            trustMultiplier = 0.05D;
        } else if (joins <= 25) {
            trustMultiplier = 0.20D;
        } else {
            trustMultiplier = 1.0D;
        }

        long playtimeMillis = getEffectivePlaytimeMillis(uuid);
        long sixHoursMillis = 6L * 60L * 60L * 1000L;
        long oneDayMillis = 24L * 60L * 60L * 1000L;
        if (playtimeMillis < sixHoursMillis) {
            trustMultiplier = Math.min(trustMultiplier, 0.02D);
        } else if (playtimeMillis < oneDayMillis) {
            trustMultiplier = Math.min(trustMultiplier, 0.10D);
        }

        return Math.max(0.01D, basePower * trustMultiplier);
    }

    public synchronized double getNegativeKarmaSanctionMultiplier(UUID uuid) {
        int karma = getKarma(uuid);
        if (karma >= 0) {
            return 1.0D;
        }
        double step = Math.max(1.0D, plugin.getConfig().getDouble("karma.negative-scaling.step", 10.0D));
        double maxMultiplier = Math.max(1.0D, plugin.getConfig().getDouble("karma.negative-scaling.max-multiplier", 32.0D));
        double exponent = Math.abs(karma) / step;
        return Math.min(maxMultiplier, Math.pow(2.0D, exponent));
    }

    public synchronized boolean markVoteParticipationRewarded(UUID uuid, String voteKey) {
        String path = path(uuid, "voteRewards." + voteKey);
        if (yaml.getBoolean(path, false)) {
            return false;
        }
        yaml.set(path, true);
        saveQuietly();
        return true;
    }

    public synchronized List<KarmaEntry> getTopKarmaEntries(int limit) {
        List<KarmaEntry> entries = new ArrayList<>();
        var section = yaml.getConfigurationSection("players");
        if (section == null) {
            return entries;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = yaml.getString(path(uuid, "name"), uuid.toString());
                entries.add(new KarmaEntry(uuid, name, getKarma(uuid), formatPlaytimeDays(uuid)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        entries.sort(Comparator.comparingInt(KarmaEntry::karma).reversed().thenComparing(KarmaEntry::name, String.CASE_INSENSITIVE_ORDER));
        if (limit > 0 && entries.size() > limit) {
            return new ArrayList<>(entries.subList(0, limit));
        }
        return entries;
    }

    private String path(UUID uuid, String leaf) {
        return "players." + uuid + "." + leaf;
    }

    private void saveQuietly() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player stats: " + e.getMessage());
        }
    }

    public record KarmaEntry(UUID uuid, String name, int karma, String playtimeDays) {
    }
}
