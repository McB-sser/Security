package de.mcbesser.security.listener;

import de.mcbesser.security.SecurityPlugin;
import de.mcbesser.security.storage.PlayerStatsRepository;
import de.mcbesser.security.storage.SanctionRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.TimeUnit;

public final class PlayerSessionListener implements Listener {
    private final SecurityPlugin plugin;
    private final PlayerStatsRepository playerStatsRepository;
    private final SanctionRepository sanctionRepository;

    public PlayerSessionListener(SecurityPlugin plugin, PlayerStatsRepository playerStatsRepository, SanctionRepository sanctionRepository) {
        this.plugin = plugin;
        this.playerStatsRepository = playerStatsRepository;
        this.sanctionRepository = sanctionRepository;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        long remaining = sanctionRepository.getRemainingBanMillis(event.getUniqueId());
        if (remaining <= 0L) {
            return;
        }
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(remaining));
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                "§cTemp-Ban aktiv\n§7Grund: " + sanctionRepository.getBanReason(event.getUniqueId()) +
                        "\n§7Restzeit: " + minutes + " Minuten");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playerStatsRepository.onJoin(event.getPlayer());
        long muteRemaining = sanctionRepository.getRemainingMuteMillis(event.getPlayer().getUniqueId());
        if (muteRemaining > 0L) {
            long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(muteRemaining));
            plugin.getServer().getScheduler().runTask(plugin, () -> event.getPlayer().sendMessage(
                    "§6[Security] §eDu bist noch §c" + minutes + " Minuten §estummgeschaltet."
            ));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerStatsRepository.onQuit(event.getPlayer());
    }
}
