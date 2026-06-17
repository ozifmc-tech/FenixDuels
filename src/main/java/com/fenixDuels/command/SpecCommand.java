package com.fenixDuels.command;

import com.fenixDuels.FenixDuels;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpecCommand implements CommandExecutor, TabCompleter {
    private final FenixDuels plugin;

    private static final Map<UUID, Location> lastLocations = new HashMap<>();
    private static final Map<UUID, GameMode> lastGameModes = new HashMap<>();

    public SpecCommand(FenixDuels plugin) {
        this.plugin = plugin;
    }

    // Yangilangan msg metodi
    private String msg(String path) {
        String message = plugin.getConfig().getString("messages." + path);

        if (message == null) {
            plugin.getLogger().warning("Xabar topilmadi: messages." + path);
            return "§cError: " + path;
        }

        return message.replace("&", "§");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ushbu buyruq faqat o'yinchilar uchun!");
            return true;
        }
        Player p = (Player) sender;

        if (args.length < 1) {
            p.sendMessage(msg("prefix") + msg("spec-usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        // KUZATISHDAN CHIQISH (/spec leave)
        if (sub.equals("leave")) {
            if (p.getGameMode() != GameMode.SPECTATOR || !lastLocations.containsKey(p.getUniqueId())) {
                p.sendMessage(msg("prefix") + msg("not-spectating"));
                return true;
            }

            Location backLoc = lastLocations.remove(p.getUniqueId());
            GameMode backGm = lastGameModes.getOrDefault(p.getUniqueId(), GameMode.SURVIVAL);
            lastGameModes.remove(p.getUniqueId());

            p.teleport(backLoc);
            p.setGameMode(backGm);
            p.sendMessage(msg("prefix") + msg("spec-leave-success"));
            return true;
        }

        // KUZATISHGA KIRISH (/spec <o'yinchi>)
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            p.sendMessage(msg("prefix") + msg("player-not-found"));
            return true;
        }

        if (!plugin.activeSessions.containsKey(targetPlayer.getUniqueId())) {
            p.sendMessage(msg("prefix") + msg("not-in-duel"));
            return true;
        }

        if (plugin.activeSessions.containsKey(p.getUniqueId())) {
            p.sendMessage(msg("prefix") + msg("cannot-spec-while-in-duel"));
            return true;
        }

        if (!lastLocations.containsKey(p.getUniqueId())) {
            lastLocations.put(p.getUniqueId(), p.getLocation());
            lastGameModes.put(p.getUniqueId(), p.getGameMode());
        }

        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(targetPlayer.getLocation());

        // Xabarni to'g'ri shaklda yuborish
        String joinMsg = msg("spec-join-success").replace("%player%", targetPlayer.getName());
        p.sendMessage(msg("prefix") + joinMsg);

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("leave");
            completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            return completions.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}