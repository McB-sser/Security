package de.mcbesser.security.vote;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VoteSession {
    private final VoteType type;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID starterUuid;
    private final String starterName;
    private final long expiresAtMillis;
    private final double requiredPoints;
    private final int requiredVoters;
    private final int requiredParticipants;
    private final int quorumEligibleOnline;
    private final Map<UUID, Double> yesVotes = new HashMap<>();
    private final Map<UUID, Double> noVotes = new HashMap<>();

    public VoteSession(
            VoteType type,
            UUID targetUuid,
            String targetName,
            UUID starterUuid,
            String starterName,
            long expiresAtMillis,
            double requiredPoints,
            int requiredVoters,
            int requiredParticipants,
            int quorumEligibleOnline
    ) {
        this.type = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.starterUuid = starterUuid;
        this.starterName = starterName;
        this.expiresAtMillis = expiresAtMillis;
        this.requiredPoints = requiredPoints;
        this.requiredVoters = requiredVoters;
        this.requiredParticipants = requiredParticipants;
        this.quorumEligibleOnline = quorumEligibleOnline;
    }

    public boolean castVote(UUID voterUuid, double weight, boolean support) {
        boolean wasParticipant = yesVotes.containsKey(voterUuid) || noVotes.containsKey(voterUuid);
        yesVotes.remove(voterUuid);
        noVotes.remove(voterUuid);
        if (support) {
            yesVotes.put(voterUuid, Math.max(0.01D, weight));
        } else {
            noVotes.put(voterUuid, Math.max(0.01D, weight));
        }
        return !wasParticipant;
    }

    public boolean isExpired(long now) {
        return now >= expiresAtMillis;
    }

    public boolean isPassed() {
        return getYesPoints() >= requiredPoints
                && getYesVoterCount() >= requiredVoters
                && getParticipantCount() >= requiredParticipants;
    }

    public double getYesPoints() {
        return yesVotes.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getNoPoints() {
        return noVotes.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public int getYesVoterCount() {
        return yesVotes.size();
    }

    public int getNoVoterCount() {
        return noVotes.size();
    }

    public int getParticipantCount() {
        return yesVotes.size() + noVotes.size();
    }

    public Set<UUID> getYesVoterIds() {
        return new HashSet<>(yesVotes.keySet());
    }

    public Set<UUID> getNoVoterIds() {
        return new HashSet<>(noVotes.keySet());
    }

    public VoteType getType() {
        return type;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public double getRequiredPoints() {
        return requiredPoints;
    }

    public int getRequiredVoters() {
        return requiredVoters;
    }

    public int getRequiredParticipants() {
        return requiredParticipants;
    }

    public int getQuorumEligibleOnline() {
        return quorumEligibleOnline;
    }

    public UUID getStarterUuid() {
        return starterUuid;
    }

    public String getStarterName() {
        return starterName;
    }

    public String key() {
        return type.name() + ":" + targetUuid;
    }
}
