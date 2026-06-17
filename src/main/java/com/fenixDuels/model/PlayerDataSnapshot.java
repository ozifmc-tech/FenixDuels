package com.fenixDuels.model;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerDataSnapshot {
    private final ItemStack[] contents;
    private final ItemStack[] armorContents;
    private final double health;
    private final int foodLevel;
    private final int level;
    private final float exp;
    private final GameMode gameMode;

    public PlayerDataSnapshot(Player player) {
        this.contents = player.getInventory().getContents().clone();
        this.armorContents = player.getInventory().getArmorContents().clone();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.gameMode = player.getGameMode();
    }

    public void restore(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(armorContents);
        player.setHealth(Math.min(health, player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
        player.setFoodLevel(foodLevel);
        player.setLevel(level);
        player.setExp(exp);
        player.setGameMode(gameMode);
        player.updateInventory();
    }
}