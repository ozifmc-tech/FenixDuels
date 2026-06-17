package com.fenixDuels.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import com.fenixDuels.FenixDuels;
import com.fenixDuels.model.Kit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KitManager {
    private final FenixDuels plugin;
    private final Map<String, Kit> kits = new HashMap<>();

    public KitManager(FenixDuels plugin) {
        this.plugin = plugin;
    }

    public Map<String, Kit> getKits() {
        return kits;
    }

    public void createKit(String name, ItemStack[] contents, ItemStack[] armorContents) {
        Kit kit = new Kit(name, contents, armorContents);
        kits.put(name.toLowerCase(), kit);
        saveKits();
    }

    public void saveKits() {
        FileConfiguration config = plugin.getConfig();
        // Eskilarini butkul o'chirib, qaytadan toza yozish
        config.set("kits", null);

        for (Map.Entry<String, Kit> entry : kits.entrySet()) {
            String key = entry.getKey();
            Kit kit = entry.getValue();
            config.set("kits." + key + ".name", kit.getName());
            config.set("kits." + key + ".contents", kit.getContents());
            config.set("kits." + key + ".armor", kit.getArmorContents());
        }
        plugin.saveConfig();
    }

    public void loadKits() {
        kits.clear(); // Yuklashdan oldin xotirani tozalash (reload bo'lganda dublikat bo'lmasligi uchun)
        FileConfiguration config = plugin.getConfig();
        if (!config.contains("kits")) return;

        for (String key : config.getConfigurationSection("kits").getKeys(false)) {
            String name = config.getString("kits." + key + ".name");

            // Bukkit xavfsiz kasting orqali listni ItemStack sifatida o'qiymiz
            List<?> contentsList = config.getList("kits." + key + ".contents");
            List<?> armorList = config.getList("kits." + key + ".armor");

            ItemStack[] contents = new ItemStack[0];
            ItemStack[] armor = new ItemStack[0];

            if (contentsList != null) {
                contents = contentsList.toArray(new ItemStack[0]);
            }
            if (armorList != null) {
                armor = armorList.toArray(new ItemStack[0]);
            }

            Kit kit = new Kit(name, contents, armor);
            kits.put(key.toLowerCase(), kit);
        }
    }
}