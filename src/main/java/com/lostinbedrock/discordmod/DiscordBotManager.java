package com.lostinbedrock.discordmod;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.bukkit.Bukkit;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DiscordBotManager extends ListenerAdapter {
    private final LibDiscordModeration plugin;
    private JDA jda;
    private Guild guild;
    private long notifyChannelId;
    private long activeBansChannelId;
    private long mutedRoleId;
    private Set<Long> allowedRoles;

    public DiscordBotManager(LibDiscordModeration plugin) {
        this.plugin = plugin;
    }

    public void startAsync() {
        String token = plugin.getConfig().getString("discord.token");
        long guildId = Long.parseLong(plugin.getConfig().getString("discord.guild_id", "0"));
        this.notifyChannelId = Long.parseLong(plugin.getConfig().getString("discord.notify_channel_id", "0"));
        this.activeBansChannelId = Long.parseLong(plugin.getConfig().getString("discord.active_bans_channel_id", "0"));
        this.mutedRoleId = Long.parseLong(plugin.getConfig().getString("discord.muted_role_id", "0"));
        this.allowedRoles = plugin.getConfig().getStringList("discord.allowed_role_ids")
                .stream().map(Long::parseLong).collect(Collectors.toSet());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                this.jda = JDABuilder.createDefault(token)
                        .enableIntents(
                                GatewayIntent.GUILD_MEMBERS,
                                GatewayIntent.GUILD_MODERATION,
                                GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT,
                                GatewayIntent.DIRECT_MESSAGES
                        )
                        .addEventListeners(this)
                        .build();
                this.jda.awaitReady();
                this.guild = jda.getGuildById(guildId);
                if (this.guild == null) {
                    plugin.getLogger().severe("Guild not found. Check guild_id in config.yml.");
                    return;
                }
                registerSlashCommands();
                plugin.getLogger().info("Discord bot ready as " + jda.getSelfUser().getAsTag());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to start Discord bot: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void shutdown() {
        if (jda != null) jda.shutdownNow();
    }

    /**
     * Utility to guarantee JDA's required-before-optional rule.
     */
    private static OptionData[] reqFirst(OptionData... options) {
        List<OptionData> required = new ArrayList<>();
        List<OptionData> optional = new ArrayList<>();
        for (OptionData od : options) {
            if (od.isRequired()) required.add(od); else optional.add(od);
        }
        List<OptionData> out = new ArrayList<>(required.size() + optional.size());
        out.addAll(required);
        out.addAll(optional);
        return out.toArray(new OptionData[0]);
    }

    private void registerSlashCommands() {
        guild.updateCommands().addCommands(
                // mcban: all REQUIRED first (discord_user, minecraft_user, reason) then optional (duration, proofs)
                Commands.slash("mcban", "Ban a Minecraft/Discord user (perm if no duration).")
                        .addOptions(reqFirst(
                                new OptionData(OptionType.USER, "discord_user", "Target Discord user", true),
                                new OptionData(OptionType.STRING, "minecraft_user", "Minecraft username", true),
                                new OptionData(OptionType.STRING, "reason", "Reason for ban", true),
                                new OptionData(OptionType.STRING, "duration", "e.g. 7d, 1w2d3h (omit for permanent)", false),
                                new OptionData(OptionType.ATTACHMENT, "proof1", "Proof image 1", false),
                                new OptionData(OptionType.ATTACHMENT, "proof2", "Proof image 2", false),
                                new OptionData(OptionType.ATTACHMENT, "proof3", "Proof image 3", false)
                        )),

                // mcunban: both optional; order doesn't matter but we still normalize.
                Commands.slash("mcunban", "Unban/unmute by Discord or Minecraft user.")
                        .addOptions(reqFirst(
                                new OptionData(OptionType.USER, "discord_user", "Discord user to unban/unmute", false),
                                new OptionData(OptionType.STRING, "minecraft_user", "Minecraft username to unban", false)
                        )),

                // mcwarn: required required, then optional proofs (already valid ordering; still normalized).
                Commands.slash("mcwarn", "Warn a Discord user.")
                        .addOptions(reqFirst(
                                new OptionData(OptionType.USER, "discord_user", "Target Discord user", true),
                                new OptionData(OptionType.STRING, "reason", "Reason for warning", true),
                                new OptionData(OptionType.ATTACHMENT, "proof1", "Proof image 1", false),
                                new OptionData(OptionType.ATTACHMENT, "proof2", "Proof image 2", false),
                                new OptionData(OptionType.ATTACHMENT, "proof3", "Proof image 3", false)
                        ))
        ).queue();
    }

    private boolean hasAllowedRole(Member m) {
        if (m == null) return false;
        for (Role r : m.getRoles()) {
            if (allowedRoles.contains(r.getIdLong())) return true;
        }
        return false;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        if (guild == null || e.getGuild() == null || e.getGuild().getIdLong() != guild.getIdLong()) return;
        switch (e.getName()) {
            case "mcban" -> handleBan(e);
            case "mcunban" -> handleUnban(e);
            case "mcwarn" -> handleWarn(e);
        }
    }

    private void handleBan(SlashCommandInteractionEvent e) {
        if (!hasAllowedRole(e.getMember())) {
            e.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        User target = e.getOption("discord_user").getAsUser();
        String mcName = e.getOption("minecraft_user").getAsString();
        String reason = e.getOption("reason").getAsString();
        OptionMapping durationOpt = e.getOption("duration");

        long nowEpoch = Instant.now().getEpochSecond();
        Long expiresEpoch = null;
        boolean permanent = true;
        if (durationOpt != null) {
            long millis = DurationParser.parseMillis(durationOpt.getAsString());
            if (millis > 0) {
                permanent = false;
                expiresEpoch = (nowEpoch + (millis / 1000L));
            }
        }
        final Long expiresEpochFinal = expiresEpoch;

        List<String> proofUrls = new ArrayList<>();
        for (String key : List.of("proof1", "proof2", "proof3")) {
            OptionMapping om = e.getOption(key);
            if (om != null && om.getAsAttachment() != null) {
                proofUrls.add(om.getAsAttachment().getUrl());
            }
        }

        plugin.getDataStore().saveMapping(target.getId(), mcName);

        final String banReason = reason + (permanent
                ? " [Permanent]"
                : " [Until " + TimeFormat.DATE_TIME_LONG.atTimestamp(expiresEpochFinal) + "]");

        McActions.banPlayer(mcName, banReason, expiresEpochFinal);

        if (permanent) {
            sendBanEmbeds(target, mcName, reason, true, expiresEpochFinal, proofUrls, e.getUser());
            if (guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
                guild.ban(target, 0, TimeUnit.SECONDS)
                        .reason(reason)
                        .queue(
                                v -> {},
                                ex -> plugin.getLogger().warning("Failed to ban user on Discord: " + ex.getMessage())
                        );
            }
        } else {
            Member member = guild.getMember(target);
            if (member != null && mutedRoleId != 0L) {
                Role muted = guild.getRoleById(mutedRoleId);
                if (muted != null) {
                    guild.addRoleToMember(member, muted).queue(
                            v -> plugin.getDataStore().recordMute(target.getId(), expiresEpochFinal),
                            ex -> plugin.getLogger().warning("Failed to add muted role: " + ex.getMessage())
                    );
                }
            }
            sendBanEmbeds(target, mcName, reason, false, expiresEpochFinal, proofUrls, e.getUser());
        }

        e.reply("Ban processed for **" + mcName + "** / " + target.getAsTag()
                        + (permanent ? " (permanent)" : " (until " + TimeFormat.RELATIVE.atTimestamp(expiresEpochFinal) + ")"))
                .setEphemeral(true).queue();
    }

    private void sendBanEmbeds(User target, String mcName, String reason, boolean permanent, Long expiresEpoch,
                               List<String> proofUrls, User staff) {
        String title = permanent ? "Permanent Ban Issued" : "Temporary Ban Issued";
        Color color = permanent ? new Color(0xD9534F) : new Color(0xF0AD4E);
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setDescription("User: " + target.getAsMention() + "\nMinecraft: **" + mcName + "**")
                .addField("Reason", reason, false);

        if (permanent) {
            eb.addField("Duration", "Permanent", true);
        } else {
            eb.addField("Duration", TimeFormat.RELATIVE.atTimestamp(expiresEpoch).toString(), true);
            eb.addField("Until", TimeFormat.DATE_TIME_LONG.atTimestamp(expiresEpoch).toString(), true);
        }

        if (!proofUrls.isEmpty()) {
            String proofText = proofUrls.stream()
                    .map(u -> "[" + (u.length() > 24 ? u.substring(u.length() - 24) : u) + "](" + u + ")")
                    .collect(Collectors.joining(" • "));
            eb.addField("Proof", proofText, false);
        }

        eb.setFooter("Issued by " + staff.getAsTag(), staff.getEffectiveAvatarUrl());

        TextChannel notify = guild.getTextChannelById(notifyChannelId);
        TextChannel active = guild.getTextChannelById(activeBansChannelId);

        long notifyMsgId = 0L;
        long activeMsgId = 0L;

        if (notify != null) {
            var action = notify.sendMessageEmbeds(eb.build());
            var future = action.submit();
            try { notifyMsgId = future.get().getIdLong(); } catch (Exception ignored) {}
        }
        if (active != null) {
            var action = active.sendMessageEmbeds(eb.build());
            var future = action.submit();
            try { activeMsgId = future.get().getIdLong(); } catch (Exception ignored) {}
        }

        plugin.getDataStore().recordBan(new BanRecord(
                mcName, target.getId(), reason, proofUrls,
                Instant.now().getEpochSecond(),
                permanent ? 0L : (expiresEpoch == null ? 0L : expiresEpoch),
                permanent,
                notifyMsgId, activeMsgId
        ));

        target.openPrivateChannel().flatMap(pc -> pc.sendMessageEmbeds(eb.build())).queue(
                v -> {},
                ex -> plugin.getLogger().info("Could not DM user: " + ex.getMessage())
        );
    }

    private void handleUnban(SlashCommandInteractionEvent e) {
        if (!hasAllowedRole(e.getMember())) {
            e.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        OptionMapping duser = e.getOption("discord_user");
        OptionMapping mcuser = e.getOption("minecraft_user");
        if (duser == null && mcuser == null) {
            e.reply("Provide either `discord_user` or `minecraft_user`.").setEphemeral(true).queue();
            return;
        }

        String mcName = mcuser != null ? mcuser.getAsString() : null;
        String discordId = duser != null ? duser.getAsUser().getId() : null;

        if (mcName == null && discordId != null) {
            String mapped = plugin.getDataStore().getMcNameForDiscord(discordId);
            if (mapped != null) mcName = mapped;
        }
        if (discordId == null && mcName != null) {
            String mapped = plugin.getDataStore().getDiscordIdForMcName(mcName);
            if (mapped != null) discordId = mapped;
        }
        final String discordIdFinal = discordId;

        if (mcName != null) {
            McActions.unbanPlayer(mcName);
            BanRecord rec = plugin.getDataStore().getBanByMcName(mcName);
            if (rec != null) {
                long activeId = rec.getActiveMessageId();
                TextChannel active = guild.getTextChannelById(activeBansChannelId);
                if (active != null && activeId != 0L) {
                    active.deleteMessageById(activeId).queue(
                            v -> {},
                            ex -> plugin.getLogger().info("Could not delete active-ban message: " + ex.getMessage())
                    );
                }
            }
            plugin.getDataStore().removeBan(mcName);
        }

        if (discordIdFinal != null) {
            Member m = guild.getMemberById(discordIdFinal);
            Role muted = (mutedRoleId != 0L) ? guild.getRoleById(mutedRoleId) : null;
            if (m != null && muted != null && m.getRoles().contains(muted)) {
                guild.removeRoleFromMember(m, muted).queue();
                plugin.getDataStore().removeMute(discordIdFinal);
            }
            guild.retrieveBan(UserSnowflake.fromId(discordIdFinal)).queue(
                    ban -> guild.unban(UserSnowflake.fromId(discordIdFinal)).queue(),
                    ex -> {} // not banned
            );
        }

        e.reply("Unban processed.").setEphemeral(true).queue();
    }

    private void handleWarn(SlashCommandInteractionEvent e) {
        if (!hasAllowedRole(e.getMember())) {
            e.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        User target = e.getOption("discord_user").getAsUser();
        String reason = e.getOption("reason").getAsString();
        List<String> proofUrls = new ArrayList<>();
        for (String key : List.of("proof1", "proof2", "proof3")) {
            OptionMapping om = e.getOption(key);
            if (om != null && om.getAsAttachment() != null) {
                proofUrls.add(om.getAsAttachment().getUrl());
            }
        }

        int total = plugin.getDataStore().incrementWarn(target.getId());

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Warning Issued")
                .setDescription("User: " + target.getAsMention())
                .setColor(new Color(0xF0AD4E))
                .addField("Reason", reason, false)
                .addField("Total Warnings", String.valueOf(total), true)
                .setFooter("Issued by " + e.getUser().getAsTag(), e.getUser().getEffectiveAvatarUrl())
                .setTimestamp(Instant.now());

        if (!proofUrls.isEmpty()) {
            String proofText = proofUrls.stream()
                    .map(u -> "[" + (u.length() > 24 ? u.substring(u.length() - 24) : u) + "](" + u + ")")
                    .collect(Collectors.joining(" • "));
            eb.addField("Proof", proofText, false);
        }

        TextChannel notify = guild.getTextChannelById(notifyChannelId);
        TextChannel active = guild.getTextChannelById(activeBansChannelId);
        if (notify != null) notify.sendMessageEmbeds(eb.build()).queue();
        if (active != null) active.sendMessageEmbeds(eb.build()).queue();

        target.openPrivateChannel().flatMap(pc -> pc.sendMessageEmbeds(eb.build())).queue(
                v -> {},
                ex -> plugin.getLogger().info("Could not DM user: " + ex.getMessage())
        );

        e.reply("Warning sent to " + target.getAsTag() + ".").setEphemeral(true).queue();
    }

    public void sweepExpirations() {
        long now = Instant.now().getEpochSecond();
        for (BanRecord rec : plugin.getDataStore().getAllBans()) {
            if (!rec.isPermanent()) {
                long exp = rec.getExpiresAtEpoch();
                if (exp > 0 && now >= exp) {
                    McActions.unbanPlayer(rec.getMinecraftName());
                    String discordId = rec.getDiscordId();
                    Role muted = (mutedRoleId != 0L) ? guild.getRoleById(mutedRoleId) : null;
                    Member m = guild.getMemberById(discordId);
                    if (m != null && muted != null && m.getRoles().contains(muted)) {
                        guild.removeRoleFromMember(m, muted).queue();
                        plugin.getDataStore().removeMute(discordId);
                    }
                    long activeId = rec.getActiveMessageId();
                    TextChannel active = guild.getTextChannelById(activeBansChannelId);
                    if (active != null && activeId != 0L) {
                        active.deleteMessageById(activeId).queue(
                                v -> {},
                                ex -> plugin.getLogger().info("Could not delete active-ban message: " + ex.getMessage())
                        );
                    }
                    plugin.getDataStore().removeBan(rec.getMinecraftName());

                    TextChannel notify = guild.getTextChannelById(notifyChannelId);
                    if (notify != null) {
                        EmbedBuilder eb = new EmbedBuilder()
                                .setTitle("Temporary Ban Expired")
                                .setColor(new Color(0x5CB85C))
                                .setTimestamp(Instant.now())
                                .setDescription("Minecraft: **" + rec.getMinecraftName() + "** has been unbanned automatically.");
                        notify.sendMessageEmbeds(eb.build()).queue();
                    }
                }
            }
        }

        for (String discordId : plugin.getDataStore().getAllMuteIds()) {
            Long exp = plugin.getDataStore().getMuteExpires(discordId);
            if (exp != null && now >= exp) {
                Role muted = (mutedRoleId != 0L) ? guild.getRoleById(mutedRoleId) : null;
                Member m = guild.getMemberById(discordId);
                if (m != null && muted != null && m.getRoles().contains(muted)) {
                    guild.removeRoleFromMember(m, muted).queue();
                }
                plugin.getDataStore().removeMute(discordId);
            }
        }
    }
}
