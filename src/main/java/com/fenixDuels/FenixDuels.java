package com.fenixDuels;

import com.fenixDuels.command.DuelCommand;
import com.fenixDuels.command.DuelTabCompleter;
import com.fenixDuels.command.SpecCommand;
import com.fenixDuels.listener.DuelListener;
import com.fenixDuels.manager.ArenaManager;
import com.fenixDuels.manager.KitManager;
import com.fenixDuels.model.DuelSession;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FenixDuels extends JavaPlugin {
    private ArenaManager arenaManager;
    private KitManager kitManager;

    public final Map<UUID, Location> pos1 = new HashMap<>();
    public final Map<UUID, Location> pos2 = new HashMap<>();
    public final Map<UUID, DuelSession> activeSessions = new HashMap<>();

    @Override
    public void onEnable() {
        // Konfiguratsiyani saqlash (Agar hali bo'lmasa)
        saveDefaultConfig();

        this.arenaManager = new ArenaManager(this);
        this.kitManager = new KitManager(this);
        this.kitManager.loadKits();

        // Listenerlar
        getServer().getPluginManager().registerEvents(new DuelListener(this), this);

        // Buyruqlarni ro'yxatdan o'tkazish
        DuelCommand duelCmd = new DuelCommand(this);
        getCommand("duel").setExecutor(duelCmd);
        getCommand("duel").setTabCompleter(new DuelTabCompleter(this));

        SpecCommand specCmd = new SpecCommand(this);
        getCommand("spec").setExecutor(specCmd);
        getCommand("spec").setTabCompleter(specCmd);

        getLogger().info("FenixDuels muvaffaqiyatli ishga tushdi!");
    }

    @Override
    public void onDisable() {
        // Kitlarni saqlash
        if (kitManager != null) kitManager.saveKits();

        // Sessiyalarni tozalash (Server o'chganda xotira tozaligi uchun)
        activeSessions.clear();

        getLogger().info("FenixDuels to'xtatildi.");
    }

    public ArenaManager getArenaManager() { return arenaManager; }
    public KitManager getKitManager() { return kitManager; }
}