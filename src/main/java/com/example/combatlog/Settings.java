package com.example.combatlog;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Unveränderlicher Snapshot der config.yml. Wird bei /combatlog reload neu erstellt. */
public final class Settings {

    // combat
    public final int duration;
    public final boolean refreshOnHit;
    public final boolean tagVictim;
    public final boolean tagAttacker;
    public final double minDamage;
    public final boolean ignoreCreative;
    public final boolean ignoreNpcs;
    public final boolean untagKiller;
    public final boolean worldWhitelist;
    public final Set<String> worldList;

    // logout
    public final boolean logoutPunish;
    public final boolean logoutKill;
    public final boolean hideDeathMessage;
    public final boolean logoutLightning;
    public final String quitMessage;
    public final Set<PlayerQuitEvent.QuitReason> logoutReasons;
    public final List<String> logoutCommands;

    // commands
    public final boolean commandModeAll;
    public final Set<String> allowedCommands;
    public final Set<String> blockedCommands;

    // restrictions
    public final boolean disableFlight;
    public final boolean disableElytra;
    public final Set<PlayerTeleportEvent.TeleportCause> blockedTeleportCauses;

    // display
    public final boolean actionBarEnabled;
    public final String actionBarFormat;
    public final boolean bossBarEnabled;
    public final String bossBarFormat;
    public final BossBar.Color bossBarColor;
    public final BossBar.Overlay bossBarOverlay;
    public final String soundStart;
    public final String soundEnd;

    // messages
    public final String prefix;
    private final Map<String, String> messages = new HashMap<>();

    public Settings(FileConfiguration c, Logger log) {
        duration = Math.max(1, c.getInt("combat.duration", 15));
        refreshOnHit = c.getBoolean("combat.refresh-on-hit", true);
        tagVictim = c.getBoolean("combat.tag-victim", true);
        tagAttacker = c.getBoolean("combat.tag-attacker", true);
        minDamage = c.getDouble("combat.min-damage", 0.0);
        ignoreCreative = c.getBoolean("combat.ignore-creative", true);
        ignoreNpcs = c.getBoolean("combat.ignore-npcs", true);
        untagKiller = c.getBoolean("combat.untag-killer-on-kill", true);
        worldWhitelist = "WHITELIST".equalsIgnoreCase(c.getString("combat.worlds.mode", "BLACKLIST"));
        worldList = lowerSet(c.getStringList("combat.worlds.list"));

        logoutPunish = c.getBoolean("logout.punish", true);
        logoutKill = c.getBoolean("logout.kill", true);
        hideDeathMessage = c.getBoolean("logout.hide-death-message", true);
        logoutLightning = c.getBoolean("logout.lightning", true);
        quitMessage = c.getString("logout.quit-message", "");
        List<String> reasons = c.contains("logout.reasons")
                ? c.getStringList("logout.reasons")
                : List.of("DISCONNECTED", "TIMED_OUT");
        logoutReasons = enumSet(PlayerQuitEvent.QuitReason.class, reasons, log);
        logoutCommands = new ArrayList<>(c.getStringList("logout.commands"));

        commandModeAll = !"BLACKLIST".equalsIgnoreCase(c.getString("commands.mode", "ALL"));
        allowedCommands = lowerSet(c.getStringList("commands.allowed"));
        blockedCommands = lowerSet(c.getStringList("commands.blocked"));

        disableFlight = c.getBoolean("restrictions.disable-flight", true);
        disableElytra = c.getBoolean("restrictions.disable-elytra", true);
        Set<PlayerTeleportEvent.TeleportCause> causes = enumSet(PlayerTeleportEvent.TeleportCause.class,
                c.getStringList("restrictions.blocked-teleport-causes"), log);
        if (c.getBoolean("restrictions.block-ender-pearls", true)) {
            causes.add(PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        }
        if (c.getBoolean("restrictions.block-chorus-fruit", true)) {
            causes.add(PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT);
        }
        if (c.getBoolean("restrictions.block-portals", false)) {
            causes.add(PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
            causes.add(PlayerTeleportEvent.TeleportCause.END_PORTAL);
            causes.add(PlayerTeleportEvent.TeleportCause.END_GATEWAY);
        }
        blockedTeleportCauses = causes;

        actionBarEnabled = c.getBoolean("display.action-bar.enabled", true);
        actionBarFormat = c.getString("display.action-bar.format", "<red>Combat <white><time>s");
        bossBarEnabled = c.getBoolean("display.boss-bar.enabled", false);
        bossBarFormat = c.getString("display.boss-bar.format", "<red>Combat: <white><time>s");
        bossBarColor = parseEnum(BossBar.Color.class, c.getString("display.boss-bar.color", "RED"), BossBar.Color.RED, log);
        bossBarOverlay = parseEnum(BossBar.Overlay.class, c.getString("display.boss-bar.overlay", "PROGRESS"), BossBar.Overlay.PROGRESS, log);
        soundStart = c.getString("display.sounds.start", "");
        soundEnd = c.getString("display.sounds.end", "");

        ConfigurationSection sec = c.getConfigurationSection("messages");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                messages.put(key, sec.getString(key, ""));
            }
        }
        prefix = messages.getOrDefault("prefix", "");
    }

    public String message(String key) {
        return messages.getOrDefault(key, "");
    }

    private static Set<String> lowerSet(List<String> in) {
        Set<String> out = new HashSet<>();
        for (String s : in) {
            String v = s.trim().toLowerCase(Locale.ROOT);
            if (v.startsWith("/")) {
                v = v.substring(1);
            }
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static <E extends Enum<E>> Set<E> enumSet(Class<E> type, List<String> raw, Logger log) {
        Set<E> set = EnumSet.noneOf(type);
        for (String s : raw) {
            try {
                set.add(Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                log.warning("Unbekannter Wert '" + s + "' für " + type.getSimpleName() + " – ignoriert.");
            }
        }
        return set;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback, Logger log) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.warning("Unbekannter Wert '" + raw + "' für " + type.getSimpleName() + " – nutze " + fallback + ".");
            return fallback;
        }
    }
}
