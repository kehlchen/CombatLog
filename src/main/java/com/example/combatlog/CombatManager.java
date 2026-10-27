package com.example.combatlog;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Hält den gesamten Combat-Zustand und die Logik (Tag, Untag, Anzeige, Strafe). */
public final class CombatManager {

    private static final class Entry {
        long expiresAt;
        long duration;
        UUID opponent;
        BossBar bar;
    }

    private final CombatLogPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Set<UUID> flightRestore = new HashSet<>();
    private final Set<UUID> punishing = new HashSet<>();
    private volatile Settings settings;
    private BukkitTask task;

    public CombatManager(CombatLogPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    public void applySettings(Settings newSettings) {
        this.settings = newSettings;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (UUID id : new ArrayList<>(entries.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                untag(p, false);
            }
        }
        entries.clear();
        flightRestore.clear();
    }

    // ------------------------------------------------------------------ Status

    public boolean isInCombat(UUID id) {
        Entry e = entries.get(id);
        return e != null && e.expiresAt > System.currentTimeMillis();
    }

    public boolean isPunishing(UUID id) {
        return punishing.contains(id);
    }

    public UUID opponentOf(UUID id) {
        Entry e = entries.get(id);
        return e == null ? null : e.opponent;
    }

    public long remainingSeconds(UUID id) {
        Entry e = entries.get(id);
        if (e == null) {
            return 0;
        }
        return Math.max(0, (e.expiresAt - System.currentTimeMillis() + 999) / 1000);
    }

    public TagResolver timeTag(UUID id) {
        return Placeholder.unparsed("time", String.valueOf(remainingSeconds(id)));
    }

    public Map<UUID, Long> snapshot() {
        Map<UUID, Long> out = new LinkedHashMap<>();
        for (UUID id : entries.keySet()) {
            out.put(id, remainingSeconds(id));
        }
        return out;
    }

    /** Darf dieser Spieler überhaupt in Combat geraten? */
    public boolean isEligible(Player p) {
        Settings s = settings;
        if (p.hasPermission("combatlog.bypass.tag")) {
            return false;
        }
        if (s.ignoreCreative && isCreativeLike(p)) {
            return false;
        }
        if (s.ignoreNpcs && p.hasMetadata("NPC")) {
            return false;
        }
        boolean listed = s.worldList.contains(p.getWorld().getName().toLowerCase(Locale.ROOT));
        return s.worldWhitelist ? listed : !listed;
    }

    private static boolean isCreativeLike(Player p) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    // --------------------------------------------------------------- Tag/Untag

    public void tag(Player player, Player opponent) {
        tag(player, opponent, settings.duration, false);
    }

    public void tag(Player player, Player opponent, int seconds, boolean forceRefresh) {
        Settings s = settings;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        Entry e = entries.get(id);
        boolean fresh = e == null;
        if (fresh) {
            e = new Entry();
            entries.put(id, e);
        }
        if (opponent != null) {
            e.opponent = opponent.getUniqueId();
        }
        if (fresh || s.refreshOnHit || forceRefresh) {
            e.duration = Math.max(1, seconds) * 1000L;
            e.expiresAt = now + e.duration;
        }
        if (!fresh) {
            return;
        }

        // Combat beginnt gerade erst
        message(player, "combat-start",
                Placeholder.unparsed("opponent", opponent != null ? opponent.getName() : "-"),
                Placeholder.unparsed("time", String.valueOf(seconds)));
        playSound(player, s.soundStart);

        if (s.disableFlight && player.getAllowFlight() && !isCreativeLike(player)) {
            flightRestore.add(id);
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (s.disableElytra && player.isGliding()) {
            player.setGliding(false);
        }
        if (s.bossBarEnabled) {
            e.bar = BossBar.bossBar(Component.empty(), 1.0f, s.bossBarColor, s.bossBarOverlay);
            player.showBossBar(e.bar);
        }
        render(player, e);
    }

    public void untag(Player player, boolean notify) {
        UUID id = player.getUniqueId();
        Entry e = entries.remove(id);
        if (e == null) {
            return;
        }
        if (e.bar != null) {
            player.hideBossBar(e.bar);
        }
        if (flightRestore.remove(id)) {
            player.setAllowFlight(true);
        }
        if (notify) {
            message(player, "combat-end");
            playSound(player, settings.soundEnd);
        }
    }

    // -------------------------------------------------------------------- Tick

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Entry> me : new ArrayList<>(entries.entrySet())) {
            Player p = Bukkit.getPlayer(me.getKey());
            if (p == null) {
                entries.remove(me.getKey());
                flightRestore.remove(me.getKey());
                continue;
            }
            if (now >= me.getValue().expiresAt) {
                untag(p, true);
            } else {
                render(p, me.getValue());
            }
        }
    }

    private void render(Player p, Entry e) {
        Settings s = settings;
        long remaining = Math.max(0, e.expiresAt - System.currentTimeMillis());
        TagResolver time = Placeholder.unparsed("time", String.valueOf((remaining + 999) / 1000));
        if (s.actionBarEnabled) {
            p.sendActionBar(mm.deserialize(s.actionBarFormat, time));
        }
        if (e.bar != null) {
            e.bar.name(mm.deserialize(s.bossBarFormat, time));
            double progress = remaining / (double) Math.max(1L, e.duration);
            e.bar.progress((float) Math.min(1.0, Math.max(0.0, progress)));
        }
    }

    // ------------------------------------------------------------------ Logout

    public void handleQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        Entry e = entries.get(id);
        if (e == null) {
            return;
        }
        boolean active = e.expiresAt > System.currentTimeMillis();
        UUID oppId = e.opponent;
        untag(p, false); // stellt auch Flugstatus wieder her, bevor Spielerdaten gespeichert werden

        Settings s = settings;
        boolean punish = active
                && s.logoutPunish
                && !Bukkit.isStopping()
                && s.logoutReasons.contains(event.getReason())
                && !p.hasPermission("combatlog.bypass.logout")
                && !p.isDead();
        if (!punish) {
            return;
        }

        String oppName = "?";
        if (oppId != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(oppId);
            if (op.getName() != null) {
                oppName = op.getName();
            }
        }
        TagResolver[] tags = {
                Placeholder.unparsed("player", p.getName()),
                Placeholder.unparsed("opponent", oppName)
        };

        if (!s.quitMessage.isEmpty()) {
            event.quitMessage(mm.deserialize(s.quitMessage, tags));
        }
        if (s.logoutLightning) {
            p.getWorld().strikeLightningEffect(p.getLocation());
        }
        if (s.logoutKill) {
            punishing.add(id);
            try {
                p.setHealth(0.0);
            } finally {
                punishing.remove(id);
            }
        }
        for (String cmd : s.logoutCommands) {
            String parsed = cmd.replace("%player%", p.getName()).replace("%opponent%", oppName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        plugin.getLogger().info(p.getName() + " hat sich im Combat ausgeloggt (Gegner: " + oppName + ").");
    }

    // ---------------------------------------------------------------- Commands

    /** @param label bereits kleingeschrieben und ohne Namespace-Präfix */
    public boolean isCommandBlocked(String label) {
        Settings s = settings;
        Set<String> names = new HashSet<>();
        names.add(label);
        Command c = Bukkit.getCommandMap().getCommand(label);
        if (c != null) {
            names.add(c.getName().toLowerCase(Locale.ROOT));
            for (String alias : c.getAliases()) {
                names.add(alias.toLowerCase(Locale.ROOT));
            }
        }
        if (s.commandModeAll) {
            for (String n : names) {
                if (s.allowedCommands.contains(n)) {
                    return false;
                }
            }
            return true;
        }
        for (String n : names) {
            if (s.blockedCommands.contains(n)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Helfer

    public void message(CommandSender to, String key, TagResolver... tags) {
        String raw = settings.message(key);
        if (raw.isEmpty()) {
            return;
        }
        to.sendMessage(mm.deserialize(settings.prefix + raw, tags));
    }

    private void playSound(Player p, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            p.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1.0f, 1.0f));
        } catch (Exception ex) {
            plugin.getLogger().warning("Ungültiger Sound in der Config: " + key);
        }
    }
}
