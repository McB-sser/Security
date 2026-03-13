package de.mcbesser.security.command;

import de.mcbesser.security.storage.PlayerStatsRepository;
import de.mcbesser.security.vote.VoteManager;
import de.mcbesser.security.vote.VoteType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SecurityCommandExecutor implements CommandExecutor, TabCompleter {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final VoteManager voteManager;

    public SecurityCommandExecutor(org.bukkit.plugin.java.JavaPlugin plugin, VoteManager voteManager) {
        this.plugin = plugin;
        this.voteManager = voteManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen diesen Befehl nutzen.");
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "votekick" -> handleStart(player, args, VoteType.KICK);
            case "voteban" -> handleStart(player, args, VoteType.BAN);
            case "securityvote" -> handleVote(player, args);
            case "securitystatus" -> handleStatus(player, args);
            case "karmalist" -> handleKarmaList(player, args);
            default -> false;
        };
    }

    private boolean handleStart(Player player, String[] args, VoteType type) {
        if (args.length < 1) {
            player.sendMessage("§eNutzung: /" + (type == VoteType.KICK ? "votekick" : "voteban") + " <spieler>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cSpieler nicht online: " + args[0]);
            return true;
        }
        String error = voteManager.startVote(player, target, type);
        if (error != null) {
            player.sendMessage("§c" + error);
        }
        return true;
    }

    private boolean handleVote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eNutzung: /securityvote <yes|no> <spieler> [kick|ban]");
            return true;
        }

        boolean support;
        String direction = args[0].toLowerCase(Locale.ROOT);
        if (Arrays.asList("yes", "ja", "y", "+").contains(direction)) {
            support = true;
        } else if (Arrays.asList("no", "nein", "n", "-").contains(direction)) {
            support = false;
        } else {
            player.sendMessage("§cErster Parameter muss yes/ja oder no/nein sein.");
            return true;
        }

        VoteType typeHint = null;
        if (args.length >= 3) {
            String t = args[2].toLowerCase(Locale.ROOT);
            if (t.equals("kick")) {
                typeHint = VoteType.KICK;
            } else if (t.equals("ban")) {
                typeHint = VoteType.BAN;
            } else {
                player.sendMessage("§cOptionaler Typ muss kick oder ban sein.");
                return true;
            }
        }

        String error = voteManager.castVote(player, args[1], typeHint, support);
        if (error != null) {
            player.sendMessage("§c" + error);
        }
        return true;
    }

    private boolean handleStatus(Player player, String[] args) {
        player.sendMessage("§6[Security] §eAktive Abstimmungen:");
        for (String line : voteManager.getStatusLines()) {
            player.sendMessage("§7- §f" + line);
        }

        OfflinePlayer inspect = args.length >= 1 ? Bukkit.getOfflinePlayer(args[0]) : player;
        String displayName = inspect.getName() != null ? inspect.getName() : inspect.getUniqueId().toString();
        player.sendMessage("§6[Security] §eKarma von §b" + displayName + "§e: §a" + voteManager.getWeightFor(inspect) +
                " Punkte §7(" + voteManager.getFormattedPlaytimeFor(inspect) + " Tage Spielzeit)");
        return true;
    }

    private boolean handleKarmaList(Player player, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
                player.sendMessage("§cSeite muss eine Zahl sein.");
                return true;
            }
        }

        int pageSize = 10;
        List<PlayerStatsRepository.KarmaEntry> all = voteManager.getKarmaTopList(0);
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        page = Math.min(page, totalPages);

        player.sendMessage("§6[Security] §eKarma-Topliste Seite " + page + "/" + totalPages);
        if (all.isEmpty()) {
            player.sendMessage("§7Keine Eintraege vorhanden.");
            return true;
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(all.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            PlayerStatsRepository.KarmaEntry entry = all.get(i);
            player.sendMessage("§7#" + (i + 1) + " §f" + entry.name() + " §8- §a" + entry.karma() + " Karma §8(" + entry.playtimeDays() + " Tage)");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("securityvote")) {
            if (args.length == 1) {
                return partial(args[0], List.of("yes", "no", "ja", "nein"));
            }
            if (args.length == 2) {
                return onlineNames(args[1]);
            }
            if (args.length == 3) {
                return partial(args[2], List.of("kick", "ban"));
            }
            return Collections.emptyList();
        }
        if ((name.equals("votekick") || name.equals("voteban") || name.equals("securitystatus")) && args.length == 1) {
            return onlineNames(args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> onlineNames(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            names.add(online.getName());
        }
        return partial(prefix, names);
    }

    private List<String> partial(String prefix, List<String> values) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(value);
            }
        }
        return out;
    }
}
