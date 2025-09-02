package com.lostinbedrock.discordmod;

import net.kyori.adventure.text.Component;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Date;

public class McActions {

    public static void banPlayer(String mcName, String reason, Long expiresAtEpoch) {
        Bukkit.getScheduler().runTask(LibDiscordModeration.getInstance(), () -> {
            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
            Date expires = (expiresAtEpoch == null) ? null : new Date(expiresAtEpoch * 1000L);
            banList.addBan(mcName, reason, expires, "DiscordBot");
            Player p = Bukkit.getPlayerExact(mcName);
            if (p != null && p.isOnline()) {
                String kickMsg = (expires == null)
                        ? "You have been permanently banned.\nReason: " + reason
                        : "You have been temporarily banned until " + expires + ".\nReason: " + reason;
                p.kick(Component.text(kickMsg));
            }
        });
    }

    public static void unbanPlayer(String mcName) {
        Bukkit.getScheduler().runTask(LibDiscordModeration.getInstance(), () -> {
            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
            banList.pardon(mcName);
        });
    }

    public static boolean isBanExpired(String mcName) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        BanEntry e = banList.getBanEntry(mcName);
        if (e == null) return false;
        Date exp = e.getExpiration();
        if (exp == null) return false;
        return exp.before(new Date());
        }
}
