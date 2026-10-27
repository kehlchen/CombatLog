package com.example.combatlog;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;

public final class CombatListener implements Listener {

    private static final String BYPASS_RESTRICTIONS = "combatlog.bypass.restrictions";

    private final CombatManager manager;

    public CombatListener(CombatManager manager) {
        this.manager = manager;
    }

    // ------------------------------------------------------------ Combat-Tag

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        Settings s = manager.settings();
        if (e.getFinalDamage() < s.minDamage) {
            return;
        }
        if (!manager.isEligible(victim) || !manager.isEligible(attacker)) {
            return;
        }
        if (s.tagVictim) {
            manager.tag(victim, attacker);
        }
        if (s.tagAttacker) {
            manager.tag(attacker, victim);
        }
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) {
            return p;
        }
        return null;
    }

    // ---------------------------------------------------- Logout / Tod

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent e) {
        manager.handleQuit(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getPlayer();
        Settings s = manager.settings();

        if (manager.isPunishing(dead.getUniqueId()) && s.hideDeathMessage) {
            e.deathMessage(null);
        }

        Player killer = dead.getKiller();
        manager.untag(dead, false);

        if (killer != null && s.untagKiller
                && dead.getUniqueId().equals(manager.opponentOf(killer.getUniqueId()))) {
            manager.untag(killer, true);
        }
    }

    // ---------------------------------------------------------- Commands

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!manager.isInCombat(p.getUniqueId()) || p.hasPermission("combatlog.bypass.commands")) {
            return;
        }
        String label = extractLabel(e.getMessage());
        if (label.isEmpty() || !manager.isCommandBlocked(label)) {
            return;
        }
        e.setCancelled(true);
        manager.message(p, "command-blocked", manager.timeTag(p.getUniqueId()));
    }

    private static String extractLabel(String message) {
        String msg = message.stripLeading();
        if (msg.startsWith("/")) {
            msg = msg.substring(1).stripLeading();
        }
        int space = msg.indexOf(' ');
        String label = (space == -1 ? msg : msg.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon != -1) {
            label = label.substring(colon + 1); // "minecraft:tp" -> "tp"
        }
        return label;
    }

    // ------------------------------------------------------ Einschränkungen

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!manager.isInCombat(p.getUniqueId()) || p.hasPermission(BYPASS_RESTRICTIONS)) {
            return;
        }
        if (!manager.settings().blockedTeleportCauses.contains(e.getCause())) {
            return;
        }
        e.setCancelled(true);
        manager.message(p, "teleport-blocked");
    }

    /** Verhindert, dass Enderperle/Chorusfrucht verbraucht werden, wenn der Teleport ohnehin gesperrt ist. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = e.getItem();
        if (item == null) {
            return;
        }
        Set<PlayerTeleportEvent.TeleportCause> causes = manager.settings().blockedTeleportCauses;
        Material type = item.getType();
        boolean blocked =
                (type == Material.ENDER_PEARL && causes.contains(PlayerTeleportEvent.TeleportCause.ENDER_PEARL))
                || (type == Material.CHORUS_FRUIT && causes.contains(PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT));
        if (!blocked) {
            return;
        }
        Player p = e.getPlayer();
        if (!manager.isInCombat(p.getUniqueId()) || p.hasPermission(BYPASS_RESTRICTIONS)) {
            return;
        }
        e.setUseItemInHand(Event.Result.DENY);
        manager.message(p, "teleport-blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!e.isGliding() || !(e.getEntity() instanceof Player p)) {
            return;
        }
        if (!manager.settings().disableElytra
                || !manager.isInCombat(p.getUniqueId())
                || p.hasPermission(BYPASS_RESTRICTIONS)) {
            return;
        }
        e.setCancelled(true);
        manager.message(p, "elytra-blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        GameMode gm = p.getGameMode();
        if (!e.isFlying() || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return;
        }
        if (!manager.settings().disableFlight
                || !manager.isInCombat(p.getUniqueId())
                || p.hasPermission(BYPASS_RESTRICTIONS)) {
            return;
        }
        e.setCancelled(true);
        manager.message(p, "elytra-blocked");
    }
}
