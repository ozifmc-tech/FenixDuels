package com.fenixDuels.model;

import org.bukkit.Location;

public class Arena {
    private final String name;
    private final String kitName;
    private Location spawn1;
    private Location spawn2;

    // YANGI: Arenaning hududini belgilash uchun koordinatalar
    private Location minLoc;
    private Location maxLoc;

    public Arena(String name, String kitName) {
        this.name = name;
        this.kitName = kitName;
    }

    public String getName() { return name; }
    public String getKitName() { return kitName; }
    public String getAllowedKitName() { return kitName; }

    public Location getSpawn1() { return spawn1; }
    public void setSpawn1(Location spawn1) { this.spawn1 = spawn1; }
    public Location getSpawn2() { return spawn2; }
    public void setSpawn2(Location spawn2) { this.spawn2 = spawn2; }

    // YANGI GETTER VA SETTERLAR
    public Location getMinLoc() { return minLoc; }
    public Location getMaxLoc() { return maxLoc; }

    public void setRegion(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) return;
        this.minLoc = new Location(loc1.getWorld(),
                Math.min(loc1.getBlockX(), loc2.getBlockX()),
                Math.min(loc1.getBlockY(), loc2.getBlockY()),
                Math.min(loc1.getBlockZ(), loc2.getBlockZ()));

        this.maxLoc = new Location(loc1.getWorld(),
                Math.max(loc1.getBlockX(), loc2.getBlockX()),
                Math.max(loc1.getBlockY(), loc2.getBlockY()),
                Math.max(loc1.getBlockZ(), loc2.getBlockZ()));
    }

    // Blok shu arena hududiga kiradimi yo'qmi tekshirish
    public boolean isInRegion(Location loc) {
        if (minLoc == null || maxLoc == null) return false;
        if (!loc.getWorld().equals(minLoc.getWorld())) return false;
        return loc.getBlockX() >= minLoc.getBlockX() && loc.getBlockX() <= maxLoc.getBlockX()
                && loc.getBlockY() >= minLoc.getBlockY() && loc.getBlockY() <= maxLoc.getBlockY()
                && loc.getBlockZ() >= minLoc.getBlockZ() && loc.getBlockZ() <= maxLoc.getBlockZ();
    }
}