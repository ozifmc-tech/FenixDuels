package com.fenixDuels.command;

import com.fenixDuels.FenixDuels;
import com.fenixDuels.manager.ArenaManager;
import com.fenixDuels.model.DuelSession;
import com.fenixDuels.model.Kit;
import com.fenixDuels.listener.DuelListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DuelCommand implements CommandExecutor, TabCompleter {
    private final FenixDuels plugin;

    // Dueldan oldingi joylashuvni saqlash uchun kesh
    private static final Map<UUID, Location> beforeDuelLocations = new HashMap<>();

    public static class DuelInvite {
        public UUID sender;
        public String kitName;
        public int rounds;
        public long timestamp; // So'rov yuborilgan vaqtni millisekundda saqlaydi

        public DuelInvite(UUID sender, String kitName, int rounds) {
            this.sender = sender;
            this.kitName = kitName;
            this.rounds = rounds;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static final Map<UUID, DuelInvite> incomingInvites = new HashMap<>();

    public DuelCommand(FenixDuels plugin) {
        this.plugin = plugin;

        // Configdan so'rov muddati soniyasini olish, topilmasa default 120 soniya
        long expireSeconds = plugin.getConfig().getLong("timers.invite-expire-seconds", 120L);
        // Taymer muddati tugaganini har 2 soniyada (40 tick) orqa fonda tekshirib turuvchi mexanizm
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiredInvites, 40L, 40L);
    }

    // Configdagi rang kodlarini (& -> §) o'girib matnni olish yordamchi metodi
    private String msg(String path) {
        String raw = plugin.getConfig().getString("messages." + path);
        if (raw == null) return "§cMissing config: messages." + path;
        return raw.replace("&", "§");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfig().getString("messages.player-only", "§cBu buyruqni faqat o'yinchilar ishlata oladi!").replace("&", "§"));
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            // O'yinchi uchun oddiy yordam menyusini ko'p qatorli qilib chiqarish
            for (String line : plugin.getConfig().getStringList("messages.help-menu")) {
                p.sendMessage(line.replace("&", "§"));
            }
            // Agar admin bo'lsa, adminlar uchun alohida yordam menyusini ham chiqarish
            if (p.hasPermission("fenixduels.admin")) {
                for (String line : plugin.getConfig().getStringList("messages.admin-help-menu")) {
                    p.sendMessage(line.replace("&", "§"));
                }
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        // ---- /duel leave buyrug'i ----
        if (sub.equals("leave")) {
            if (!plugin.activeSessions.containsKey(p.getUniqueId())) {
                p.sendMessage(msg("not-in-duel"));
                return true;
            }

            DuelSession session = plugin.activeSessions.get(p.getUniqueId());

            // Raqibni (G'olibni) aniqlash
            Player winner = null;
            for (UUID uuid : plugin.activeSessions.keySet()) {
                if (plugin.activeSessions.get(uuid).equals(session) && !uuid.equals(p.getUniqueId())) {
                    winner = Bukkit.getPlayer(uuid);
                    break;
                }
            }

            if (winner == null || !winner.isOnline()) {
                plugin.activeSessions.remove(p.getUniqueId());
                p.sendMessage(msg("opponent-not-found"));
                return true;
            }

            // DuelSession klassi ichidan kitName o'zgaruvchisini xavfsiz olish (Reflection)
            String currentKitName = "own";
            try {
                Field kitField = DuelSession.class.getDeclaredField("kitName");
                kitField.setAccessible(true);
                currentKitName = (String) kitField.get(session);
            } catch (Exception e) {
                try {
                    for (Field field : DuelSession.class.getDeclaredFields()) {
                        if (field.getType() == String.class) {
                            field.setAccessible(true);
                            String val = (String) field.get(session);
                            if (val != null && !val.contains(".") && !val.equals(session.toString())) {
                                currentKitName = val;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            boolean isOwnKit = currentKitName.equalsIgnoreCase("own");

            // Dueldan oldingi joylarni olish
            Location pBackLoc = beforeDuelLocations.remove(p.getUniqueId());
            Location wBackLoc = beforeDuelLocations.remove(winner.getUniqueId());
            if (pBackLoc == null) pBackLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            if (wBackLoc == null) wBackLoc = Bukkit.getWorlds().get(0).getSpawnLocation();

            if (isOwnKit) {
                p.sendMessage(msg("leave-lose-own"));
                winner.sendMessage(msg("leave-win-own").replace("%player%", p.getName()));

                // SOXTA O'LIM TIZIMI (Fake Death)
                Location dropLoc = p.getLocation();

                for (ItemStack item : p.getInventory().getContents()) {
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        p.getWorld().dropItemNaturally(dropLoc, item.clone());
                    }
                }
                for (ItemStack armor : p.getInventory().getArmorContents()) {
                    if (armor != null && armor.getType() != org.bukkit.Material.AIR) {
                        p.getWorld().dropItemNaturally(dropLoc, armor.clone());
                    }
                }
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);

                int expToDrop = Math.min(100, p.getLevel() * 7);
                if (expToDrop > 0) {
                    ExperienceOrb orb = p.getWorld().spawn(dropLoc, ExperienceOrb.class);
                    orb.setExperience(expToDrop);
                }
                p.setLevel(0);
                p.setExp(0);

                for (PotionEffect effect : p.getActivePotionEffects()) {
                    p.removePotionEffect(effect.getType());
                }

                p.getWorld().playSound(dropLoc, Sound.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
                p.playEffect(org.bukkit.EntityEffect.DEATH);

                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.setFireTicks(0);

                plugin.activeSessions.remove(p.getUniqueId());
                plugin.activeSessions.remove(winner.getUniqueId());

                p.teleport(pBackLoc);

                // Configdan g'olibning kutish vaqtini olish (Soniya * 20 Tick)
                int staySeconds = plugin.getConfig().getInt("timers.winner-stay-seconds", 20);
                Player finalWinner = winner;
                Location finalWBackLoc = wBackLoc;
                DuelSession finalSession = session;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (finalWinner.isOnline()) {
                        finalWinner.teleport(finalWBackLoc);
                        finalWinner.sendMessage(msg("winner-stay-time-out").replace("%time%", String.valueOf(staySeconds)));
                    }
                    clearDroppedItemsInSession(finalSession);
                }, staySeconds * 20L);
            } else {
                p.sendMessage(msg("leave-lose-kit"));
                winner.sendMessage(msg("leave-win-kit").replace("%player%", p.getName()));

                try {
                    session.restorePlayerInventory(p);
                    session.restorePlayerInventory(winner);
                } catch (Exception ignored) {}

                plugin.activeSessions.remove(p.getUniqueId());
                plugin.activeSessions.remove(winner.getUniqueId());

                p.teleport(pBackLoc);
                winner.teleport(wBackLoc);

                clearDroppedItemsInSession(session);
            }
            return true;
        }

        // ---- /duel see buyrug'i ----
        if (sub.equals("see")) {
            if (!p.hasPermission("fenixduels.admin")) {
                p.sendMessage(msg("no-permission"));
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(msg("prefix") + "§7Iltimos, kit nomini yozing!");
                return true;
            }
            String kitName = args[1].toLowerCase();
            Kit kit = plugin.getKitManager().getKits().get(kitName);
            if (kit == null) {
                p.sendMessage(msg("kit-not-found"));
                return true;
            }
            openKitPreviewGUI(p, kitName, kit);
            return true;
        }

        // ---- /duel accept buyrug'i ----
        if (sub.equals("accept")) {
            if (!incomingInvites.containsKey(p.getUniqueId())) {
                p.sendMessage(msg("no-invite"));
                return true;
            }
            DuelInvite invite = incomingInvites.remove(p.getUniqueId());
            Player challenger = Bukkit.getPlayer(invite.sender);
            if (challenger == null || !challenger.isOnline()) {
                p.sendMessage(msg("player-not-found"));
                return true;
            }

            String freeArena = plugin.getArenaManager().findFreeArena(invite.kitName);
            if (freeArena == null) {
                p.sendMessage(msg("arenas-busy"));
                challenger.sendMessage(msg("duel-cancelled-busy"));
                return true;
            }

            ArenaManager am = plugin.getArenaManager();
            Location s1 = am.deserializeLoc(am.getArenaConfig().getString("arenas." + freeArena + ".spawn1"));
            Location s2 = am.deserializeLoc(am.getArenaConfig().getString("arenas." + freeArena + ".spawn2"));
            Location pos1Loc = am.deserializeLoc(am.getArenaConfig().getString("arenas." + freeArena + ".pos1"));
            Location pos2Loc = am.deserializeLoc(am.getArenaConfig().getString("arenas." + freeArena + ".pos2"));

            if (s1 == null || s2 == null) {
                p.sendMessage(msg("arena-not-configured"));
                return true;
            }

            beforeDuelLocations.put(challenger.getUniqueId(), challenger.getLocation());
            beforeDuelLocations.put(p.getUniqueId(), p.getLocation());

            DuelSession session = new DuelSession(freeArena, challenger, p, invite.kitName, invite.rounds, pos1Loc, pos2Loc);

            if (!invite.kitName.equalsIgnoreCase("own")) {
                session.savePlayerInventory(challenger);
                session.savePlayerInventory(p);

                Kit kit = plugin.getKitManager().getKits().get(invite.kitName);
                if (kit != null) {
                    challenger.getInventory().clear();
                    p.getInventory().clear();
                    challenger.getInventory().setContents(kit.getContents());
                    challenger.getInventory().setArmorContents(kit.getArmorContents());
                    p.getInventory().setContents(kit.getContents());
                    p.getInventory().setArmorContents(kit.getArmorContents());
                }
            }

            DuelListener.saveAndClearEffects(challenger);
            DuelListener.saveAndClearEffects(p);

            plugin.activeSessions.put(challenger.getUniqueId(), session);
            plugin.activeSessions.put(p.getUniqueId(), session);

            challenger.teleport(s1);
            p.teleport(s2);

            String kitDisplay = invite.kitName.equalsIgnoreCase("own") ? "O'z Inventari" : invite.kitName;

            String startMessage = msg("duel-start")
                    .replace("%kit%", kitDisplay)
                    .replace("%rounds%", String.valueOf(invite.rounds));

            challenger.sendMessage(startMessage);
            p.sendMessage(startMessage);
            return true;
        }

        // ---- /duel deny buyrug'i ----
        if (sub.equals("deny")) {
            if (incomingInvites.containsKey(p.getUniqueId())) {
                DuelInvite invite = incomingInvites.remove(p.getUniqueId());
                Player senderPlayer = Bukkit.getPlayer(invite.sender);

                if (senderPlayer != null && senderPlayer.isOnline()) {
                    senderPlayer.sendMessage(msg("duel-denied-sender").replace("%player%", p.getName()));
                }
                p.sendMessage(msg("duel-denied-target"));
            } else {
                p.sendMessage(msg("no-invite"));
            }
            return true;
        }

        // ---- Duel So'rovi Chaqirish (Asosiy buyruq) ----
        Player target = Bukkit.getPlayer(args[0]);
        if (target != null) {
            if (args.length < 3) { p.sendMessage(msg("prefix") + "§cIshlatilishi: /duel <nik> <kit/own> <raund>"); return true; }
            if (target.equals(p)) { p.sendMessage(msg("cannot-duel-self")); return true; }

            String kitName = args[1].toLowerCase();
            int rounds = Integer.parseInt(args[2]);

            if (kitName.equals("own")) {
                rounds = 1;
            } else {
                if (!plugin.getKitManager().getKits().containsKey(kitName)) {
                    p.sendMessage(msg("kit-not-found"));
                    return true;
                }
                if (rounds % 2 == 0 || rounds < 1 || rounds > 33) {
                    p.sendMessage(msg("invalid-rounds"));
                    return true;
                }
            }

            incomingInvites.put(target.getUniqueId(), new DuelInvite(p.getUniqueId(), kitName, rounds));
            p.sendMessage(msg("invite-sent").replace("%player%", target.getName()));

            String kitDisplay = kitName.equals("own") ? "O'z Inventari" : kitName;

            // Invite formatini configdagi ko'p qatorli listdan o'qib yuborish
            for (String line : plugin.getConfig().getStringList("invite-format")) {
                target.sendMessage(line.replace("&", "§")
                        .replace("%player%", p.getName())
                        .replace("%kit%", kitDisplay)
                        .replace("%rounds%", String.valueOf(rounds)));
            }
            return true;
        }

        // ---- ADMIN BUYRUQLARI ----
        if (p.hasPermission("fenixduels.admin")) {
            switch (sub) {
                case "wand":
                    p.getInventory().addItem(plugin.getArenaManager().getWand());
                    p.sendMessage(msg("admin-wand-given"));
                    break;
                case "createarena":
                    if (args.length < 3) { p.sendMessage(msg("prefix") + "§cIshlatilishi: /duel createarena <nomi> <kit/own>"); return true; }
                    plugin.getArenaManager().createArena(args[1], args[2]);
                    p.sendMessage(msg("admin-arena-created").replace("%arena%", args[1]).replace("%kit%", args[2]));
                    break;
                case "setregion":
                    if (args.length < 2) { p.sendMessage(msg("prefix") + "§c/duel setregion <arena>"); return true; }
                    if (!plugin.pos1.containsKey(p.getUniqueId()) || !plugin.pos2.containsKey(p.getUniqueId())) {
                        p.sendMessage(msg("admin-points-missing"));
                        return true;
                    }
                    plugin.getArenaManager().saveRegion(args[1], plugin.pos1.get(p.getUniqueId()), plugin.pos2.get(p.getUniqueId()));
                    p.sendMessage(msg("admin-region-saved"));
                    break;
                case "setspawn":
                    if (args.length < 3) return true;
                    plugin.getArenaManager().saveSpawn(args[1], args[2], p.getLocation());
                    p.sendMessage(msg("admin-spawn-saved").replace("%spawn%", args[2]));
                    break;
                case "createkit":
                    if (args.length < 2) return true;
                    plugin.getKitManager().createKit(args[1], p.getInventory().getContents(), p.getInventory().getArmorContents());
                    p.sendMessage(msg("admin-kit-saved"));
                    break;
                case "deletearena":
                    if (args.length < 2) return true;
                    plugin.getArenaManager().deleteArena(args[1]);
                    p.sendMessage(msg("admin-arena-deleted"));
                    break;
                case "deletekit":
                    if (args.length < 2) return true;
                    plugin.getKitManager().getKits().remove(args[1].toLowerCase());
                    plugin.getKitManager().saveKits();
                    p.sendMessage(msg("admin-kit-deleted"));
                    break;
                case "list":
                    if (args.length > 1 && args[1].equalsIgnoreCase("arenas")) {
                        p.sendMessage(msg("admin-list-arenas-header"));
                        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getArenaManager().getArenaConfig();
                        if (cfg.contains("arenas")) {
                            for (String key : cfg.getConfigurationSection("arenas").getKeys(false)) {
                                String boundKit = cfg.getString("arenas." + key + ".kit");
                                p.sendMessage(" §7- §b" + key + " §f(" + boundKit + ")");
                            }
                        }
                    }
                    if (args.length > 1 && args[1].equalsIgnoreCase("kits")) {
                        p.sendMessage(msg("admin-list-kits-header") + plugin.getKitManager().getKits().keySet());
                    }
                    break;
            }
        }
        return true;
    }

    // ---- 120 soniya o'tgach so'rovlarni avtomatik o'chirish taymeri ----
    private void checkExpiredInvites() {
        long now = System.currentTimeMillis();
        long expireLimitSeconds = plugin.getConfig().getLong("timers.invite-expire-seconds", 120L);
        long expireLimitMs = expireLimitSeconds * 1000L;

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, DuelInvite> entry : incomingInvites.entrySet()) {
            if ((now - entry.getValue().timestamp) > expireLimitMs) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID targetUUID : toRemove) {
            DuelInvite invite = incomingInvites.remove(targetUUID);
            Player target = Bukkit.getPlayer(targetUUID);
            Player sender = Bukkit.getPlayer(invite.sender);

            String msgSender = msg("invite-expired-sender").replace("%time%", String.valueOf(expireLimitSeconds));
            String msgTarget = msg("invite-expired-target").replace("%time%", String.valueOf(expireLimitSeconds));

            if (sender != null && sender.isOnline()) {
                sender.sendMessage(msgSender.replace("%player%", target != null ? target.getName() : "O'yinchi"));
            }
            if (target != null && target.isOnline()) {
                target.sendMessage(msgTarget.replace("%player%", sender != null ? sender.getName() : "O'yinchi"));
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;
        Player p = (Player) sender;

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            completions.add("accept");
            completions.add("deny");
            completions.add("leave");
            completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));

            if (p.hasPermission("fenixduels.admin")) {
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
            return completions.stream().filter(s -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("see") || sub.equals("deletekit")) {
                return new ArrayList<>(plugin.getKitManager().getKits().keySet()).stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("deletearena") || sub.equals("setregion")) {
                if (plugin.getArenaManager().getArenaConfig().contains("arenas")) {
                    return new ArrayList<>(plugin.getArenaManager().getArenaConfig().getConfigurationSection("arenas").getKeys(false)).stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
            }
            if (sub.equals("list")) {
                completions.add("arenas");
                completions.add("kits");
                return completions.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }

            if (Bukkit.getPlayer(args[0]) != null) {
                completions.addAll(plugin.getKitManager().getKits().keySet());
                completions.add("own");
                return completions.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (Bukkit.getPlayer(args[0]) != null) {
                String chosenKit = args[1].toLowerCase();
                if (chosenKit.equals("own")) {
                    completions.add("1");
                } else {
                    completions.add("1");
                    completions.add("3");
                    completions.add("5");
                }
                return completions.stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
            }
        }
        return completions;
    }

    /**
     * Arena regionidagi barcha drop itemlarni va EXP sharlarini o'chirish (Har raund va o'yin oxirida chaqiriladi)
     */
    public static void clearDroppedItemsInSession(DuelSession session) {
        try {
            Field p1Field = DuelSession.class.getDeclaredField("pos1");
            Field p2Field = DuelSession.class.getDeclaredField("pos2");
            p1Field.setAccessible(true);
            p2Field.setAccessible(true);

            Location pos1 = (Location) p1Field.get(session);
            Location pos2 = (Location) p2Field.get(session);

            if (pos1 == null || pos2 == null || pos1.getWorld() == null) return;

            double minX = Math.min(pos1.getX(), pos2.getX());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY()) - 2;
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 5;
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());

            for (Entity entity : pos1.getWorld().getEntities()) {
                if (entity instanceof Item || entity instanceof ExperienceOrb) {
                    Location loc = entity.getLocation();
                    if (loc.getX() >= minX && loc.getX() <= maxX &&
                            loc.getY() >= minY && loc.getY() <= maxY &&
                            loc.getZ() >= minZ && loc.getZ() <= maxZ) {

                        entity.remove();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void openKitPreviewGUI(Player p, String kitName, Kit kit) {
        Inventory previewGui = Bukkit.createInventory(null, 45, "§0🔍 Kit: " + kitName.toUpperCase());
        ItemStack[] contents = kit.getContents();
        ItemStack[] armor = kit.getArmorContents();

        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && i < 36) {
                previewGui.setItem(i, contents[i].clone());
            }
        }

        if (armor != null) {
            if (armor.length > 3 && armor[3] != null) previewGui.setItem(36, armor[3].clone());
            if (armor.length > 2 && armor[2] != null) previewGui.setItem(37, armor[2].clone());
            if (armor.length > 1 && armor[1] != null) previewGui.setItem(38, armor[1].clone());
            if (armor.length > 0 && armor[0] != null) previewGui.setItem(39, armor[0].clone());
        }

        p.openInventory(previewGui);
    }
}