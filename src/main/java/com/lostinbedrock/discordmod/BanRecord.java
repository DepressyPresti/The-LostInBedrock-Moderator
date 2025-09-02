package com.lostinbedrock.discordmod;

import java.util.List;

public class BanRecord {
    private final String minecraftName;
    private final String discordId;
    private final String reason;
    private final List<String> proofUrls;
    private final long issuedAtEpoch;
    private final long expiresAtEpoch;
    private final boolean permanent;
    private final long notifyMessageId;
    private final long activeMessageId;

    public BanRecord(String minecraftName, String discordId, String reason, List<String> proofUrls,
                     long issuedAtEpoch, long expiresAtEpoch, boolean permanent,
                     long notifyMessageId, long activeMessageId) {
        this.minecraftName = minecraftName;
        this.discordId = discordId;
        this.reason = reason;
        this.proofUrls = proofUrls;
        this.issuedAtEpoch = issuedAtEpoch;
        this.expiresAtEpoch = expiresAtEpoch;
        this.permanent = permanent;
        this.notifyMessageId = notifyMessageId;
        this.activeMessageId = activeMessageId;
    }

    public String getMinecraftName() { return minecraftName; }
    public String getDiscordId() { return discordId; }
    public String getReason() { return reason; }
    public List<String> getProofUrls() { return proofUrls; }
    public long getIssuedAtEpoch() { return issuedAtEpoch; }
    public long getExpiresAtEpoch() { return expiresAtEpoch; }
    public boolean isPermanent() { return permanent; }
    public long getNotifyMessageId() { return notifyMessageId; }
    public long getActiveMessageId() { return activeMessageId; }
}
