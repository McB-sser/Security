package de.mcbesser.security.listener;

import de.mcbesser.security.SecurityPlugin;
import de.mcbesser.security.moderation.SmartWordFilter;
import de.mcbesser.security.storage.PlayerStatsRepository;
import de.mcbesser.security.storage.SanctionRepository;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatModerationListener implements Listener {
    private final SecurityPlugin plugin;
    private final PlayerStatsRepository playerStatsRepository;
    private final SanctionRepository sanctionRepository;
    private final SmartWordFilter smartWordFilter;
    private final PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
    private final Map<UUID, Deque<Long>> messageTimes = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastNormalizedMessages = new ConcurrentHashMap<>();

    public ChatModerationListener(
            SecurityPlugin plugin,
            PlayerStatsRepository playerStatsRepository,
            SanctionRepository sanctionRepository,
            SmartWordFilter smartWordFilter
    ) {
        this.plugin = plugin;
        this.playerStatsRepository = playerStatsRepository;
        this.sanctionRepository = sanctionRepository;
        this.smartWordFilter = smartWordFilter;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String plainMessage = serializer.serialize(event.message());

        long muteRemaining = sanctionRepository.getRemainingMuteMillis(uuid);
        if (muteRemaining > 0L) {
            event.setCancelled(true);
            notifyPlayer(player, "§cDu bist stummgeschaltet. Restzeit: " + Math.max(1, Math.round(muteRemaining / 60000.0)) + " Min.");
            return;
        }

        if (isSpam(uuid, plainMessage)) {
            event.setCancelled(true);
            long minutes = Math.max(1L, plugin.getConfig().getLong("chat.spam-mute-minutes", 2L));
            double multiplier = playerStatsRepository.getNegativeKarmaSanctionMultiplier(uuid);
            Duration muteDuration = scaleDuration(Duration.ofMinutes(minutes), multiplier);
            sanctionRepository.applyTempMute(uuid, muteDuration, "Spam/Flood");
            adjustKarma(uuid, player.getName(), -Math.max(0, plugin.getConfig().getInt("karma.penalties.spam-mute", 2)), "Spam/Flood");
            notifyPlayer(player, "§cSpam erkannt. Mute fuer " + Math.max(1, Math.round(muteDuration.toMinutes())) + " Minuten.");
            return;
        }

        if (hasExcessiveCaps(plainMessage)) {
            event.setCancelled(true);
            notifyPlayer(player, "§eZu viele Grossbuchstaben (Caps). Nachricht wurde blockiert.");
            return;
        }

        if (smartWordFilter.matches(plainMessage)) {
            event.setCancelled(true);
            long minutes = Math.max(1L, plugin.getConfig().getLong("chat.mute-minutes-offensive", 5L));
            double multiplier = playerStatsRepository.getNegativeKarmaSanctionMultiplier(uuid);
            Duration muteDuration = scaleDuration(Duration.ofMinutes(minutes), multiplier);
            sanctionRepository.applyTempMute(uuid, muteDuration, "Ungeeignete Sprache");
            adjustKarma(uuid, player.getName(), -Math.max(0, plugin.getConfig().getInt("karma.penalties.offensive-mute", 3)), "Ungeeignete Sprache");
            notifyPlayer(player, "§cUngeeignete Sprache erkannt. Nachricht blockiert. Mute: " + Math.max(1, Math.round(muteDuration.toMinutes())) + " Minuten.");
            Bukkit.getLogger().warning("[Security] Blocked offensive message from " + player.getName() + ": " + plainMessage);
        }
    }

    private boolean isSpam(UUID uuid, String message) {
        long now = System.currentTimeMillis();
        long windowMs = Math.max(5L, plugin.getConfig().getLong("chat.spam-window-seconds", 20L)) * 1000L;
        int limit = Math.max(3, plugin.getConfig().getInt("chat.spam-message-limit", 6));

        Deque<Long> times = messageTimes.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > windowMs) {
                times.removeFirst();
            }
            if (times.size() > limit) {
                return true;
            }
        }

        String normalized = smartWordFilter.normalizeForComparison(message);
        String last = lastNormalizedMessages.put(uuid, normalized);
        return last != null && !normalized.isBlank() && normalized.equals(last);
    }

    private boolean hasExcessiveCaps(String message) {
        int letters = 0;
        int upper = 0;
        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        if (letters < 12) {
            return false;
        }
        double ratio = upper / (double) letters;
        return ratio >= plugin.getConfig().getDouble("chat.max-caps-ratio", 0.8D);
    }

    private void adjustKarma(UUID uuid, String playerName, int delta, String reason) {
        if (delta != 0) {
            playerStatsRepository.adjustKarma(uuid, playerName, delta, reason);
        }
    }

    private Duration scaleDuration(Duration base, double multiplier) {
        double cap = Math.max(1.0D, plugin.getConfig().getDouble("karma.negative-scaling.max-multiplier", 32.0D));
        double applied = Math.min(cap, Math.max(1.0D, multiplier));
        long scaledMillis = (long) Math.ceil(base.toMillis() * applied);
        return Duration.ofMillis(Math.max(1000L, scaledMillis));
    }

    private void notifyPlayer(Player player, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§6[Security] " + message));
    }
}
