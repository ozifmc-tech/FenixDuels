package com.fenixDuels.model;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DuelSession {
    private final String arenaName;
    private final Player challenger;
    private final Player target;
    private final String kitName;
    private final int totalRounds;

    private int challengerWins = 0;
    private int targetWins = 0;
    private int currentRound = 1;

    private final List<BlockState> changedBlocks = new ArrayList<>();
    private final Location pos1;
    private final Location pos2;

    // Kitli duellar uchun o'yinchilarning duelgacha bo'lgan shaxsiy inventarlarini saqlash
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();

    public DuelSession(String arenaName, Player challenger, Player target, String kitName, int totalRounds, Location pos1, Location pos2) {
        this.arenaName = arenaName;
        this.challenger = challenger;
        this.target = target;
        this.kitName = kitName;
        this.totalRounds = totalRounds;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public String getArenaName() { return arenaName; }
    public Player getChallenger() { return challenger; }
    public Player getTarget() { return target; }
    public String getKitName() { return kitName; }
    public int getCurrentRound() { return currentRound; }
    public void nextRound() { this.currentRound++; }

    public void addChallengerWin() { challengerWins++; }
    public void addTargetWin() { targetWins++; }

    public boolean hasEnded() {
        int maxWinsNeeded = (totalRounds / 2) + 1;
        return challengerWins >= maxWinsNeeded || targetWins >= maxWinsNeeded || currentRound > totalRounds;
    }

    public List<BlockState> getChangedBlocks() { return changedBlocks; }

    public void savePlayerInventory(Player p) {
        savedInventories.put(p.getUniqueId(), p.getInventory().getContents().clone());
        savedArmor.put(p.getUniqueId(), p.getInventory().getArmorContents().clone());
    }

    public void restorePlayerInventory(Player p) {
        if (savedInventories.containsKey(p.getUniqueId())) {
            p.getInventory().setContents(savedInventories.get(p.getUniqueId()));
            p.getInventory().setArmorContents(savedArmor.get(p.getUniqueId()));
        }
    }

    public boolean isInRegion(Location loc) {
        if (pos1 == null || pos2 == null) return false;
        if (!loc.getWorld().equals(pos1.getWorld())) return false;
        int x1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int y1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int z1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int x2 = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y2 = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int z2 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        return loc.getBlockX() >= x1 && loc.getBlockX() <= x2 &&
                loc.getBlockY() >= y1 && loc.getBlockY() <= y2 &&
                loc.getBlockZ() >= z1 && loc.getBlockZ() <= z2;
    }
    public int getChallengerWins() { return challengerWins; }
    public int getTargetWins() { return targetWins; }

}