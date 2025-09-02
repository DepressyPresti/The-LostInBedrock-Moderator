# LostInBedrock Mod

Paper 1.21.7 plugin that embeds a Discord bot (JDA 5) to run `/mcban`, `/mcunban`, and `/mcwarn` as Discord slash commands,
keeping Minecraft bans in sync and persisting data to `plugins/LostInBedrock Mod/data.yml`.

## Build
- Requires **Java 21+** and **Maven 3.9+**
- `mvn -q -U clean package`
- Drop the resulting `target/LostInBedrock Mod-1.0.0-shaded.jar` into your Paper `plugins/` folder.

## Configure
- Start the server once to generate `config.yml`, then stop the server.
- Edit `plugins/LostInBedrock Mod/config.yml`:
  - `discord.token`: your bot token
  - `discord.guild_id`: your Discord server (guild) ID
  - `discord.notify_channel_id`: channel ID to post staff notifications
  - `discord.active_bans_channel_id`: channel where **bans and warns** are mirrored
  - `discord.muted_role_id`: role ID to assign for timed bans
  - `discord.allowed_role_ids`: list of role IDs that may use commands

## Slash Commands (Discord)
- `/mcban <discord_user> <minecraft_user> [duration] <reason> [proof1] [proof2] [proof3]`
  - No duration → **Permanent**: bans from **Minecraft** and **Discord**
  - With duration (e.g., `7d`, `1w2d3h`, `90m`) → temp **Minecraft** ban until expiry + assigns **Muted** role on Discord
  - Sends professional embeds to Notify + Active Bans channels and DMs the user (mentions included)
- `/mcunban [discord_user] [minecraft_user]`
  - Provide at least one; undoes ban/mute on both platforms
  - Also deletes the corresponding **Active Bans** message if recorded
- `/mcwarn <discord_user> <reason> [proof1] [proof2] [proof3]`
  - DMs the user and posts in both channels; shows **total warnings**

## Persistence
- All data lives in `data.yml` (bans, warns, mappings, temp mutes, message IDs). Minecraft uses the built-in BanList too.

## Notes
- Duration format: any combo of `w d h m s` (e.g., `7d`, `1w2d3h`, `90m`).
- Bot permissions needed: **Ban Members**, **Manage Roles**, **Send Messages**, **Embed Links**, **Attach Files**, **Read Message History**.
- Unban button was removed per request; use `/mcunban` instead.
