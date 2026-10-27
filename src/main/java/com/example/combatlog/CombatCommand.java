package com.example.combatlog;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "status", "tag", "untag", "list");
    private static final Set<String> NEEDS_PLAYER = Set.of("status", "tag", "untag");

    private final CombatLogPlugin plugin;
    private final CombatManager manager;

    public CombatCommand(CombatLogPlugin plugin, CombatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            manager.message(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                manager.message(sender, "reloaded");
            }
            case "status" -> status(sender, args);
            case "tag" -> tag(sender, args);
            case "untag" -> untag(sender, args);
            case "list" -> list(sender);
            default -> manager.message(sender, "usage");
        }
        return true;
    }

    private void status(CommandSender sender, String[] args) {
        Player target = args.length > 1
                ? Bukkit.getPlayerExact(args[1])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            manager.message(sender, "player-not-found");
            return;
        }
        var name = Placeholder.unparsed("player", target.getName());
        if (manager.isInCombat(target.getUniqueId())) {
            manager.message(sender, "status-in-combat", name, manager.timeTag(target.getUniqueId()));
        } else {
            manager.message(sender, "status-not-in-combat", name);
        }
    }

    private void tag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            manager.message(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            manager.message(sender, "player-not-found");
            return;
        }
        int seconds = manager.settings().duration;
        if (args.length > 2) {
            try {
                seconds = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ex) {
                manager.message(sender, "usage");
                return;
            }
        }
        manager.tag(target, null, seconds, true);
        manager.message(sender, "admin-tagged",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("time", String.valueOf(seconds)));
    }

    private void untag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            manager.message(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            manager.message(sender, "player-not-found");
            return;
        }
        manager.untag(target, true);
        manager.message(sender, "admin-untagged", Placeholder.unparsed("player", target.getName()));
    }

    private void list(CommandSender sender) {
        Map<UUID, Long> snapshot = manager.snapshot();
        if (snapshot.isEmpty()) {
            manager.message(sender, "list-empty");
            return;
        }
        manager.message(sender, "list-header", Placeholder.unparsed("time", String.valueOf(snapshot.size())));
        for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : entry.getKey().toString();
            manager.message(sender, "list-entry",
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("time", String.valueOf(entry.getValue())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && NEEDS_PLAYER.contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
