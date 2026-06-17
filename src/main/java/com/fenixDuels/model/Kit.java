package com.fenixDuels.model;

import org.bukkit.inventory.ItemStack;

public class Kit {
    private final String name;
    private final ItemStack[] contents;
    private final ItemStack[] armorContents;

    public Kit(String name, ItemStack[] contents, ItemStack[] armorContents) {
        this.name = name;
        this.contents = contents;
        this.armorContents = armorContents;
    }

    public String getName() { return name; }
    public ItemStack[] getContents() { return contents; }
    public ItemStack[] getArmorContents() { return armorContents; }
}