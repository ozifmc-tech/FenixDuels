package com.fenixDuels.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.fenixDuels.FenixDuels;
import com.fenixDuels.model.DuelSession;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;

public class ArenaManager {
    private final FenixDuels plugin;
    private File arenaFile;
    private FileConfiguration arenaConfig;
    private Location mainSpawnLocation;

    public ArenaManager(FenixDuels plugin) {
        this.plugin = plugin;
        createArenaConfig();
        loadArenaData();
    }

    private void createArenaConfig() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        arenaFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!arenaFile.exists()) {
            try { arenaFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        arenaConfig = YamlConfiguration.loadConfiguration(arenaFile);
    }

    public FileConfiguration getArenaConfig() { return arenaConfig; }

    public void saveArenaConfig() {
        try { if (arenaFile != null && arenaConfig != null) arenaConfig.save(arenaFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadArenaData() {
        if (arenaConfig != null && arenaConfig.contains("main-spawn")) {
            mainSpawnLocation = deserializeLoc(arenaConfig.getString("main-spawn"));
        }
    }

    public Location getMainSpawnLocation() { return mainSpawnLocation; }

    public void setMainSpawnLocation(Location loc) {
        this.mainSpawnLocation = loc;
        if (arenaConfig != null) {
            arenaConfig.set("main-spawn", serializeLoc(loc));
            saveArenaConfig();
        }
    }

    public void createArena(String arenaName, String boundKit) {
        String path = "arenas." + arenaName;
        arenaConfig.set(path + ".kit", boundKit.toLowerCase());
        saveArenaConfig();
    }

    public void saveRegion(String arenaName, Location p1, Location p2) {
        if (p1 == null || p2 == null) return;
        String path = "arenas." + arenaName;
        arenaConfig.set(path + ".pos1", serializeLoc(p1));
        arenaConfig.set(path + ".pos2", serializeLoc(p2));
        saveArenaConfig();
    }

    public void saveSpawn(String arenaName, String pos, Location loc) {
        // "spawn1" yoki "spawn2" dagi raqamni to'g'rilash (masalan, "spawn1" kelsa faqat "1" ni ajratib olish)
        String cleanPos = pos.toLowerCase().replace("spawn", "");
        String path = "arenas." + arenaName + ".spawn" + cleanPos;
        arenaConfig.set(path, serializeLoc(loc));
        saveArenaConfig();
    }

    public String findFreeArena(String kitName) {
        if (!arenaConfig.contains("arenas")) return null;
        for (String arena : arenaConfig.getConfigurationSection("arenas").getKeys(false)) {
            String boundKit = arenaConfig.getString("arenas." + arena + ".kit");
            if (boundKit != null && boundKit.equalsIgnoreCase(kitName)) {
                boolean isBusy = false;
                for (DuelSession session : plugin.activeSessions.values()) {
                    if (session.getArenaName().equalsIgnoreCase(arena)) {
                        isBusy = true;
                        break;
                    }
                }
                if (!isBusy) return arena;
            }
        }
        return null;
    }

    public List<String> getArenaList() {
        if (!arenaConfig.contains("arenas")) return new ArrayList<>();
        return new ArrayList<>(arenaConfig.getConfigurationSection("arenas").getKeys(false));
    }

    public void deleteArena(String arenaName) {
        arenaConfig.set("arenas." + arenaName, null);
        saveArenaConfig();
    }

    /**
     * Region boltasi (Wand) sozlamalarini to'g'ridan-to'g'ri config.yml dan o'qiydi.
     */
    public ItemStack getWand() {
        // Configdan material nomini olish, xatolik bo'lsa WOODEN_AXE default qilinadi
        String matName = plugin.getConfig().getString("wand-settings.material", "WOODEN_AXE");
        Material material;
        try {
            material = Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.WOODEN_AXE;
        }

        ItemStack wand = new ItemStack(material);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            // Nomi configdan olinadi
            String displayName = plugin.getConfig().getString("wand-settings.display-name", "&d&lDuel Region Wand");
            meta.setDisplayName(displayName.replace("&", "§"));

            // Lore (Tavsif) list ko'rinishida configdan olinadi
            List<String> lore = plugin.getConfig().getStringList("wand-settings.lore");
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(line.replace("&", "§"));
                }
                meta.setLore(coloredLore);
            }

            wand.setItemMeta(meta);
        }
        return wand;
    }

    public String serializeLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    public Location deserializeLoc(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            String[] parts = str.split(",");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0.0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0.0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) { return null; }
    }
}