package com.fenixDuels.command;

import com.fenixDuels.FenixDuels;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DuelTabCompleter implements TabCompleter {
    private final FenixDuels plugin;

    public DuelTabCompleter(FenixDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;
        Player p = (Player) sender;

        // 1-Argument
        if (args.length == 1) {
            completions.add("accept");
            completions.add("deny");
            completions.add("leave");
            completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));

            if (p.hasPermission("fenixduels.admin")) {
                completions.add("spec");
                completions.add("see");
                completions.add("wand");
                completions.add("createarena");
                completions.add("deletearena");
                completions.add("setregion");
                completions.add("setspawn");
                completions.add("createkit");
                completions.add("deletekit");
                completions.add("list");
            }
            return completions.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        // 2-Argument
        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if (sub.equals("spec")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("see") || sub.equals("deletekit")) {
                return new ArrayList<>(plugin.getKitManager().getKits().keySet()).stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("deletearena") || sub.equals("setregion") || sub.equals("setspawn")) {
                if (plugin.getArenaManager().getArenaConfig().contains("arenas")) {
                    return new ArrayList<>(plugin.getArenaManager().getArenaConfig().getConfigurationSection("arenas").getKeys(false))
                            .stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
            }
            if (sub.equals("list")) {
                completions.add("arenas");
                completions.add("kits");
                return completions.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }

            // O'yinchi tanlangan bo'lsa kitlar ro'yxati
            if (Bukkit.getPlayer(args[0]) != null) {
                completions.addAll(plugin.getKitManager().getKits().keySet());
                completions.add("own");
                return completions.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        // 3-Argument
        if (args.length == 3) {
            // setspawn uchun maxsus spawn1/spawn2
            if (args[0].equalsIgnoreCase("setspawn")) {
                completions.add("spawn1");
                completions.add("spawn2");
                return completions.stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            // Duel raundlari uchun
            if (Bukkit.getPlayer(args[0]) != null) {
                String chosenKit = args[1].toLowerCase();
                if (!chosenKit.equals("own")) {
                    completions.add("1");
                    completions.add("3");
                    completions.add("5");
                    return completions.stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
                }
            }
        }

        return new ArrayList<>();
    }
}