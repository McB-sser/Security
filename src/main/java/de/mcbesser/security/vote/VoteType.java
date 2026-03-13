package de.mcbesser.security.vote;

public enum VoteType {
    KICK,
    BAN;

    public String displayName() {
        return this == KICK ? "VoteKick" : "VoteBan";
    }
}
