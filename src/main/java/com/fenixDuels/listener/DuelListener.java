package com.fenixDuels.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import com.fenixDuels.FenixDuels;
import com.fenixDuels.model.DuelSession;
import com.fenixDuels.model.Kit;
import java.lang.reflect.Field;
import java.util.*;

public class DuelListener implements Listener {
    private final FenixDuels plugin;
    private static final Map<UUID, Collection<PotionEffect>> savedEffects = new HashMap<>();

    public DuelListener(FenixDuels plugin) {
        this.plugin = plugin;
    }

    public static void saveAndClearEffects(Player p) {
        savedEffects.put(p.getUniqueId(), new ArrayList<>(p.getActivePotionEffects()));
        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }
    }

    public static void restoreEffects(Player p) {
        Collection<PotionEffect> effects = savedEffects.remove(p.getUniqueId());
        if (effects != null) {
            for (PotionEffect effect : effects) {
                p.addPotionEffect(effect);
            }
        }
    }

    // Configdan rang kodlarini o'girib matn olish yordamchisi
    private String msg(String path) {
        String raw = plugin.getConfig().getString("messages." + path);
        if (raw == null) return "§cMissing config: messages." + path;
        return raw.replace("&", "§");
    }

    // Faqat xabarni o'zini olish (Prefix-siz joylar uchun)
    private String rawMsg(String path, String def) {
        String raw = plugin.getConfig().getString(path, def);
        return raw.replace("&", "§");
    }

    @EventHandler
    public void onWandUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("fenixduels.admin")) return;
        if (e.getItem() == null || e.getItem().getType() != Material.WOODEN_AXE) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            plugin.pos1.put(p.getUniqueId(), b.getLocation());
            p.sendMessage(rawMsg("messages.prefix", "§a§lFenixDuels §8» ") + "§a1-nuqta belgilandi.");
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            plugin.pos2.put(p.getUniqueId(), b.getLocation());
            p.sendMessage(rawMsg("messages.prefix", "§a§lFenixDuels §8» ") + "§a2-nuqta belgilandi.");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (plugin.activeSessions.containsKey(p.getUniqueId())) {
            DuelSession session = plugin.activeSessions.get(p.getUniqueId());
            if (session.isInRegion(e.getBlock().getLocation())) {
                session.getChangedBlocks().add(e.getBlock().getState());
            } else {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (plugin.activeSessions.containsKey(p.getUniqueId())) {
            DuelSession session = plugin.activeSessions.get(p.getUniqueId());
            if (session.isInRegion(e.getBlock().getLocation())) {
                session.getChangedBlocks().add(e.getBlockReplacedState());
            } else {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (plugin.activeSessions.containsKey(p.getUniqueId())) {
            String cmd = e.getMessage().toLowerCase().split(" ")[0];
            if (cmd.equals("/tp") || cmd.equals("/tpa") || cmd.equals("/tpaccept") ||
                    cmd.equals("/spawn") || cmd.equals("/home") || cmd.equals("/warp") ||
                    cmd.equals("/back") || cmd.equals("/tphere")) {
                e.setCancelled(true);
                p.sendMessage(rawMsg("messages.prefix", "§a§lFenixDuels §8» ") + "§cDuel vaqtida teleport buyruqlaridan foydalanish taqiqlangan!");
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player loser = (Player) event.getEntity();
        if (!plugin.activeSessions.containsKey(loser.getUniqueId())) return;

        if (loser.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);

            DuelSession session = plugin.activeSessions.get(loser.getUniqueId());
            Player winner = session.getChallenger().equals(loser) ? session.getTarget() : session.getChallenger();

            int wWins, lWins;
            if (winner.equals(session.getChallenger())) {
                session.addChallengerWin();
                wWins = session.getChallengerWins();
                lWins = session.getTargetWins();
            } else {
                session.addTargetWin();
                wWins = session.getTargetWins();
                lWins = session.getChallengerWins();
            }

            winner.sendTitle("§a§lRAUND G'OLIBI!", "§7Hisob: §e" + wWins + " - " + lWins, 5, 30, 10);
            loser.sendTitle("§c§lRAUND MAG'LUBI!", "§7Hisob: §e" + lWins + " - " + wWins, 5, 30, 10);

            List<Item> droppedItems = new ArrayList<>();
            for (int i = 0; i < loser.getInventory().getSize(); i++) {
                ItemStack item = loser.getInventory().getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    droppedItems.add(loser.getWorld().dropItemNaturally(loser.getLocation(), item.clone()));
                    loser.getInventory().setItem(i, null);
                }
            }
            for (ItemStack item : loser.getInventory().getArmorContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    droppedItems.add(loser.getWorld().dropItemNaturally(loser.getLocation(), item.clone()));
                }
            }

            loser.getInventory().clear();
            loser.getInventory().setArmorContents(new ItemStack[4]);

            loser.setGameMode(GameMode.SPECTATOR);
            loser.setHealth(20.0);
            winner.setHealth(20.0);

            // Raundlar orasidagi pauza vaqtini configdan olish (Default: 3 soniya -> 60 tick)
            int breakSeconds = plugin.getConfig().getInt("timers.round-break-seconds", 3);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (session.hasEnded()) {
                        if (session.getKitName().equalsIgnoreCase("own")) {
                            // G'olib o'z narsalarini terib olishi uchun configdagi kutish vaqti (Default: 20 soniya)
                            int staySeconds = plugin.getConfig().getInt("timers.winner-stay-seconds", 20);
                            winner.sendMessage(rawMsg("messages.prefix", "§a§lFenixDuels §8» ") + "§a§lDuel tugadi! §eG'olib bo'ldingiz. O'ljalarni yig'ish uchun " + staySeconds + " soniya vaqt!");

                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    // Muddat tugagach drop itemlarni tozalash
                                    for (Item item : droppedItems) {
                                        if (item.isValid()) item.remove();
                                    }
                                    clearAllDropsInRegion(session); // Qo'shimcha tozalash
                                    rollbackBlocksGradually(session.getChangedBlocks());
                                    endWholeDuel(session, winner, loser);
                                }
                            }.runTaskLater(plugin, staySeconds * 20L);

                        } else {
                            // Kit rejimida duel to'liq tugasa yerga tushgan hamma narsani darhol tozalash
                            for (Item item : droppedItems) {
                                if (item.isValid()) item.remove();
                            }
                            clearAllDropsInRegion(session);
                            rollbackBlocksGradually(session.getChangedBlocks());
                            endWholeDuel(session, winner, loser);
                        }
                    } else {
                        // ---- NAVBATDAGI RAUNDGA UTISHDAN OLDIN DROP ITEMLARNI TOZALASH ----
                        for (Item item : droppedItems) {
                            if (item.isValid()) item.remove();
                        }
                        clearAllDropsInRegion(session); // Region ichidagi tasodifiy drop/exp ballarini ham tozalaydi
                        winner.sendMessage(msg("round-cleaned"));
                        loser.sendMessage(msg("round-cleaned"));

                        rollbackBlocksGradually(session.getChangedBlocks());
                        session.nextRound();
                        startNextRound(session);
                    }
                }
            }.runTaskLater(plugin, breakSeconds * 20L);
        }
    }

    /**
     * Raund tugaganda yoki o'yin tugaganda region ichidagi barcha drop-itemlar va tajriba (EXP) sharlarini tozalash mexanizmi
     */
    private void clearAllDropsInRegion(DuelSession session) {
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

    private void startNextRound(DuelSession session) {
        session.getChallenger().setGameMode(GameMode.SURVIVAL);
        session.getTarget().setGameMode(GameMode.SURVIVAL);

        if (!session.getKitName().equalsIgnoreCase("own")) {
            Kit kit = plugin.getKitManager().getKits().get(session.getKitName());
            if (kit != null) {
                session.getChallenger().getInventory().setContents(kit.getContents());
                session.getChallenger().getInventory().setArmorContents(kit.getArmorContents());
                session.getTarget().getInventory().setContents(kit.getContents());
                session.getTarget().getInventory().setArmorContents(kit.getArmorContents());
            }
        }

        com.fenixDuels.manager.ArenaManager am = plugin.getArenaManager();
        Location s1 = am.deserializeLoc(am.getArenaConfig().getString("arenas." + session.getArenaName() + ".spawn1"));
        Location s2 = am.deserializeLoc(am.getArenaConfig().getString("arenas." + session.getArenaName() + ".spawn2"));

        if (s1 != null) session.getChallenger().teleport(s1);
        if (s2 != null) session.getTarget().teleport(s2);
    }

    private void endWholeDuel(DuelSession session, Player winner, Player loser) {
        plugin.activeSessions.remove(winner.getUniqueId());
        plugin.activeSessions.remove(loser.getUniqueId());

        Location mainSpawn = winner.getWorld().getSpawnLocation();

        if (!session.getKitName().equalsIgnoreCase("own")) {
            session.restorePlayerInventory(winner);
            session.restorePlayerInventory(loser);
        }

        winner.teleport(mainSpawn);
        loser.teleport(mainSpawn);
        winner.setGameMode(GameMode.SURVIVAL);
        loser.setGameMode(GameMode.SURVIVAL);

        restoreEffects(winner);
        restoreEffects(loser);
    }

    private void rollbackBlocksGradually(List<BlockState> blocks) {
        if (blocks.isEmpty()) return;

        new BukkitRunnable() {
            private final List<BlockState> toUpdates = new ArrayList<>(blocks);
            private final int blocksPerTick = 15;

            @Override
            public void run() {
                int count = 0;
                while (!toUpdates.isEmpty() && count < blocksPerTick) {
                    BlockState bs = toUpdates.remove(toUpdates.size() - 1);
                    bs.update(true, false);
                    count++;
                }

                if (toUpdates.isEmpty()) {
                    blocks.clear();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onKitPreviewClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e.getView().getTitle().startsWith("§0🔍 Kit: ")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player leaver = e.getPlayer();
        if (plugin.activeSessions.containsKey(leaver.getUniqueId())) {
            DuelSession session = plugin.activeSessions.remove(leaver.getUniqueId());
            Player stayer = session.getChallenger().equals(leaver) ? session.getTarget() : session.getChallenger();
            plugin.activeSessions.remove(stayer.getUniqueId());

            clearAllDropsInRegion(session); // Chiqib ketganda ham hamma narsani tozalash
            rollbackBlocksGradually(session.getChangedBlocks());

            if (!session.getKitName().equalsIgnoreCase("own")) {
                session.restorePlayerInventory(stayer);
            }

            Location spawn = stayer.getWorld().getSpawnLocation();
            stayer.teleport(spawn);
            stayer.setGameMode(GameMode.SURVIVAL);

            restoreEffects(leaver);
            restoreEffects(stayer);
        }
    }
}