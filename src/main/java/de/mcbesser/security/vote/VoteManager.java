package de.mcbesser.security.vote;

import de.mcbesser.security.storage.PlayerStatsRepository;
import de.mcbesser.security.storage.SanctionRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VoteManager {
    private final JavaPlugin plugin;
    private final PlayerStatsRepository playerStatsRepository;
    private final SanctionRepository sanctionRepository;
    private final Map<String, VoteSession> activeVotes = new HashMap<>();
    private final Map<UUID, Long> startCooldowns = new HashMap<>();

    public VoteManager(JavaPlugin plugin, PlayerStatsRepository playerStatsRepository, SanctionRepository sanctionRepository) {
        this.plugin = plugin;
        this.playerStatsRepository = playerStatsRepository;
        this.sanctionRepository = sanctionRepository;
    }

    public synchronized String startVote(Player starter, Player target, VoteType type) {
        if (starter.getUniqueId().equals(target.getUniqueId())) {
            return "Du kannst nicht gegen dich selbst abstimmen lassen.";
        }
        if (sanctionRepository.isBanned(target.getUniqueId())) {
            return "Zielspieler ist bereits tempor\u00e4r gebannt.";
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = startCooldowns.getOrDefault(starter.getUniqueId(), 0L);
        if (cooldownUntil > now) {
            return "Start-Cooldown aktiv: noch " + formatDuration(cooldownUntil - now);
        }

        String key = type.name() + ":" + target.getUniqueId();
        if (activeVotes.containsKey(key)) {
            return "Es l\u00e4uft bereits eine " + type.displayName() + "-Abstimmung gegen " + target.getName() + ".";
        }

        int minVoters = Math.max(3, plugin.getConfig().getInt("vote.min-voters", 3));
        int targetKarma = playerStatsRepository.getKarma(target.getUniqueId());
        double requiredPoints = Math.max(minVoters, Math.max(1D, targetKarma));

        int quorumMinKarma = Math.max(1, plugin.getConfig().getInt("vote.quorum-min-karma", 3));
        double quorumPercent = Math.max(0.1D, Math.min(1.0D, plugin.getConfig().getDouble("vote.quorum-percent", 0.5D)));
        int quorumEligibleOnline = countQuorumEligibleOnline(quorumMinKarma);
        int requiredParticipants = Math.max(minVoters, (int) Math.ceil(quorumEligibleOnline * quorumPercent));

        long durationSeconds = Math.max(60L, plugin.getConfig().getLong("vote.duration-seconds", 600L));
        VoteSession session = new VoteSession(
                type,
                target.getUniqueId(),
                target.getName(),
                starter.getUniqueId(),
                starter.getName(),
                now + durationSeconds * 1000L,
                requiredPoints,
                minVoters,
                requiredParticipants,
                quorumEligibleOnline
        );

        double starterWeight = playerStatsRepository.getVotingPower(starter.getUniqueId());
        boolean firstVote = session.castVote(starter.getUniqueId(), starterWeight, true);
        activeVotes.put(key, session);
        rewardVoteParticipationIfEligible(starter, firstVote);

        long cooldownSeconds = Math.max(30L, plugin.getConfig().getLong("vote.initiator-cooldown-seconds", 300L));
        startCooldowns.put(starter.getUniqueId(), now + cooldownSeconds * 1000L);

        broadcast("Â§6[Security] Â§e" + starter.getName() + " startet " + type.displayName() + " gegen Â§c" + target.getName() + "Â§e.");
        broadcast("Â§6[Security] Â§7N\u00f6tig: Â§b" + fmt(requiredPoints) + " Karma-Ja-PunkteÂ§7, Â§b" + minVoters + " Ja-SpielerÂ§7, " +
                "Â§b" + requiredParticipants + " Teilnehmer gesamtÂ§7 (50% von " + quorumEligibleOnline + " karma-berechtigten Anwesenden, min Karma " + quorumMinKarma + ").");
        broadcast("Â§6[Security] Â§7Hinweis: Ne\u00fc Spieler (wenige Joins / wenig Spielzeit) haben absichtlich stark reduzierte Vote-Power.");
        sendVoteButtons(session);
        broadcastProgress(session);
        resolveIfPassed(session);
        return null;
    }

    public synchronized String castVote(Player voter, String targetName, VoteType typeHint, boolean support) {
        List<VoteSession> matches = findMatches(targetName);
        if (matches.isEmpty()) {
            return "Keine aktive Abstimmung gegen " + targetName + ".";
        }

        VoteSession chosen = null;
        if (typeHint != null) {
            for (VoteSession session : matches) {
                if (session.getType() == typeHint) {
                    chosen = session;
                    break;
                }
            }
            if (chosen == null) {
                return "Keine passende Abstimmung f\u00fcr Typ " + typeHint.name().toLowerCase() + " gefunden.";
            }
        } else if (matches.size() == 1) {
            chosen = matches.get(0);
        } else {
            return "Mehrere Abstimmungen aktiv. Nutze /securityvote <yes|no> <spieler> <kick|ban>.";
        }

        double weight = playerStatsRepository.getVotingPower(voter.getUniqueId());
        boolean firstVote = chosen.castVote(voter.getUniqueId(), weight, support);
        rewardVoteParticipationIfEligible(voter, firstVote);

        broadcast("Â§6[Security] Â§e" + voter.getName() + " stimmt " + (support ? "Â§aJA" : "Â§cNEIN") + "Â§e f\u00fcr " +
                chosen.getType().displayName() + " gegen Â§c" + chosen.getTargetName() + "Â§e (Karma-Gewicht " + fmt(weight) + ").");
        broadcastProgress(chosen);
        resolveIfPassed(chosen);
        return null;
    }

    public synchronized void expireVotes() {
        long now = System.currentTimeMillis();
        List<VoteSession> expired = new ArrayList<>();
        for (VoteSession session : activeVotes.values()) {
            if (session.isExpired(now)) {
                expired.add(session);
            }
        }
        for (VoteSession session : expired) {
            activeVotes.remove(session.key());
            broadcast("Â§6[Security] Â§7" + session.getType().displayName() + " gegen " + session.getTargetName() + " ist abgelaufen: " +
                    "Ja " + fmt(session.getYesPoints()) + "/" + fmt(session.getRequiredPoints()) + " Karma, " +
                    "Ja-Spieler " + session.getYesVoterCount() + "/" + session.getRequiredVoters() + ", " +
                    "Teilnehmer " + session.getParticipantCount() + "/" + session.getRequiredParticipants() + ".");
        }
    }

    public synchronized List<String> getStatusLines() {
        List<VoteSession> sessions = new ArrayList<>(activeVotes.values());
        sessions.sort(Comparator.comparingLong(VoteSession::getExpiresAtMillis));
        List<String> out = new ArrayList<>();
        if (sessions.isEmpty()) {
            out.add("Keine aktiven Abstimmungen");
            return out;
        }

        long now = System.currentTimeMillis();
        for (VoteSession session : sessions) {
            out.add(session.getType().displayName() + " gegen " + session.getTargetName() +
                    " | Ja-Spieler " + session.getYesVoterCount() + "/" + session.getRequiredVoters() +
                    " | Teilnehmer " + session.getParticipantCount() + "/" + session.getRequiredParticipants() +
                    " (eligible " + session.getQuorumEligibleOnline() + ")" +
                    " | Nein " + fmt(session.getNoPoints()) +
                    " | Rest " + formatDuration(session.getExpiresAtMillis() - now));
        }
        return out;
    }

    public int getWeightFor(OfflinePlayer player) {
        return playerStatsRepository.getKarma(player.getUniqueId());
    }

    public String getFormattedPlaytimeFor(OfflinePlayer player) {
        return playerStatsRepository.formatPlaytimeDays(player.getUniqueId());
    }

    public List<PlayerStatsRepository.KarmaEntry> getKarmaTopList(int limit) {
        return playerStatsRepository.getTopKarmaEntries(limit);
    }

    private void resolveIfPassed(VoteSession session) {
        if (!session.isPassed()) {
            return;
        }

        activeVotes.remove(session.key());

        Duration duration;
        String reason;
        int karmaPenalty;
        if (session.getType() == VoteType.KICK) {
            long minutes = Math.max(1L, plugin.getConfig().getLong("vote.kick-ban-minutes", 5L));
            duration = Duration.ofMinutes(minutes);
            reason = "VoteKick";
            karmaPenalty = Math.max(0, plugin.getConfig().getInt("karma.penalties.vote-kick", 5));
        } else {
            long hours = Math.max(1L, plugin.getConfig().getLong("vote.ban-duration-hours", 24L));
            duration = Duration.ofHours(hours);
            reason = "VoteBan";
            karmaPenalty = Math.max(0, plugin.getConfig().getInt("karma.penalties.vote-ban", 20));
        }

        double sanctionMultiplier = playerStatsRepository.getNegativeKarmaSanctionMultiplier(session.getTargetUuid());
        if (sanctionMultiplier > 1.0D) {
            duration = scaleDuration(duration, sanctionMultiplier);
        }

        sanctionRepository.applyTempBan(session.getTargetUuid(), duration, reason);
        if (karmaPenalty > 0) {
            playerStatsRepository.adjustKarma(session.getTargetUuid(), session.getTargetName(), -karmaPenalty, reason);
        }

        int starterReward = Math.max(0, plugin.getConfig().getInt("karma.rewards.successful-vote-starter", 1));
        if (starterReward > 0) {
            playerStatsRepository.adjustKarma(session.getStarterUuid(), session.getStarterName(), starterReward, "Erfolgreiche Abstimmung gestartet");
        }

        broadcast("Â§6[Security] Â§c" + session.getTargetName() + " wurde durch " + session.getType().displayName() +
                " sanktioniert. Da\u00fcr: Â§e" + formatDuration(duration.toMillis()) + "Â§c." +
                (sanctionMultiplier > 1.0D ? " (x" + fmt(sanctionMultiplier) + " wegen negativem Karma)" : "") +
                (karmaPenalty > 0 ? " Karma -" + karmaPenalty + "." : ""));

        Player online = Bukkit.getPlayer(session.getTargetUuid());
        if (online != null) {
            String kickMsg = "Â§cCommunity-Sanktion: " + reason + "\nÂ§7Da\u00fcr: " + formatDuration(duration.toMillis());
            Bukkit.getScheduler().runTask(plugin, () -> online.kickPlayer(kickMsg));
        }
    }

    private void rewardVoteParticipationIfEligible(Player player, boolean firstVoteInSession) {
        if (!firstVoteInSession) {
            return;
        }
        int reward = Math.max(0, plugin.getConfig().getInt("karma.rewards.vote-participation", 1));
        if (reward <= 0) {
            return;
        }
        playerStatsRepository.adjustKarma(player.getUniqueId(), player.getName(), reward, "Teilnahme an Community-Abstimmung");
    }

    private int countQuorumEligibleOnline(int quorumMinKarma) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerStatsRepository.getKarma(player.getUniqueId()) >= quorumMinKarma) {
                count++;
            }
        }
        return count;
    }

    private void broadcastProgress(VoteSession session) {
        broadcast("Â§6[Security] Â§7Status: Â§aJa " + fmt(session.getYesPoints()) + "/" + fmt(session.getRequiredPoints()) + " Karma" +
                "Â§7 | Â§fJa-Spieler " + session.getYesVoterCount() + "/" + session.getRequiredVoters() +
                "Â§7 | Â§bTeilnehmer " + session.getParticipantCount() + "/" + session.getRequiredParticipants() +
                "Â§7 | Â§cNein " + fmt(session.getNoPoints()) + " Karma");
    }

    private void sendVoteButtons(VoteSession session) {
        String typeToken = session.getType() == VoteType.KICK ? "kick" : "ban";
        String yesCmd = "/securityvote yes " + session.getTargetName() + " " + typeToken;
        String noCmd = "/securityvote no " + session.getTargetName() + " " + typeToken;

        Component line = Component.text("[Security] ", NamedTextColor.GOLD)
                .append(Component.text(session.getType().displayName() + " " + session.getTargetName() + " ", NamedTextColor.YELLOW))
                .append(voteButton(" [JA] ", NamedTextColor.GREEN, yesCmd))
                .append(Component.text(" "))
                .append(voteButton(" [NEIN] ", NamedTextColor.RED, noCmd))
                .append(Component.text("  (Klick oder Command)", NamedTextColor.GRAY));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(line);
        }
    }

    private Component voteButton(String label, NamedTextColor color, String command) {
        return Component.text(label, color)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.WHITE)));
    }

    private List<VoteSession> findMatches(String targetName) {
        List<VoteSession> matches = new ArrayList<>();
        for (VoteSession session : activeVotes.values()) {
            if (session.getTargetName().equalsIgnoreCase(targetName)) {
                matches.add(session);
            }
        }
        return matches;
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    private Duration scaleDuration(Duration base, double multiplier) {
        double maxMultiplier = Math.max(1.0D, plugin.getConfig().getDouble("karma.negative-scaling.max-multiplier", 32.0D));
        double applied = Math.min(maxMultiplier, Math.max(1.0D, multiplier));
        long scaledMillis = (long) Math.min(Long.MAX_VALUE / 2D, Math.ceil(base.toMillis() * applied));
        return Duration.ofMillis(Math.max(1000L, scaledMillis));
    }

    private static String fmt(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long sec = seconds % 60L;
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + "d");
        }
        if (hours > 0) {
            parts.add(hours + "h");
        }
        if (minutes > 0) {
            parts.add(minutes + "m");
        }
        if (sec > 0 || parts.isEmpty()) {
            parts.add(sec + "s");
        }
        return String.join(" ", parts);
    }
}
