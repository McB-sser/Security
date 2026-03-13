package de.mcbesser.security;

import de.mcbesser.security.command.SecurityCommandExecutor;
import de.mcbesser.security.listener.ChatModerationListener;
import de.mcbesser.security.listener.PlayerSessionListener;
import de.mcbesser.security.moderation.SmartWordFilter;
import de.mcbesser.security.storage.PlayerStatsRepository;
import de.mcbesser.security.storage.SanctionRepository;
import de.mcbesser.security.vote.VoteManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SecurityPlugin extends JavaPlugin {
    private PlayerStatsRepository playerStatsRepository;
    private SanctionRepository sanctionRepository;
    private VoteManager voteManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.playerStatsRepository = new PlayerStatsRepository(this);
        this.sanctionRepository = new SanctionRepository(this);
        SmartWordFilter smartWordFilter = new SmartWordFilter(getConfig().getStringList("chat.blocked-patterns"));
        this.voteManager = new VoteManager(this, playerStatsRepository, sanctionRepository);

        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this, playerStatsRepository, sanctionRepository), this);
        getServer().getPluginManager().registerEvents(new ChatModerationListener(this, playerStatsRepository, sanctionRepository, smartWordFilter), this);

        SecurityCommandExecutor executor = new SecurityCommandExecutor(this, voteManager);
        register("votekick", executor);
        register("voteban", executor);
        register("securityvote", executor);
        register("securitystatus", executor);
        register("karmalist", executor);

        getServer().getScheduler().runTaskTimer(this, () -> {
            sanctionRepository.cleanupExpired();
            voteManager.expireVotes();
        }, 20L, 20L * 15);
    }

    @Override
    public void onDisable() {
        if (playerStatsRepository != null) {
            playerStatsRepository.flushActiveSessions();
        }
        if (sanctionRepository != null) {
            sanctionRepository.cleanupExpired();
        }
    }

    private void register(String name, SecurityCommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Missing command in plugin.yml: " + name);
        }
    }
}
