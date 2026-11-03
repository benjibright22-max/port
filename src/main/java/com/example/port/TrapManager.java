package com.example.port;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrapManager {

    public static final String WORLD_NAME = "port_void";
    /** Gives trapped players night vision so they can actually see the room. */
    private static final boolean NIGHT_VISION = true;

    private record Entry(UUID trapper, Location origin, boolean released) {}

    private final JavaPlugin plugin;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private File file;

    public TrapManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- setup / persistence ----------

    public void load() {
        World w = Bukkit.getWorld(WORLD_NAME);
        if (w == null) {
            w = new WorldCreator(WORLD_NAME)
                    .environment(World.Environment.NORMAL)
                    .generator(new BlackWorldGenerator())
                    .createWorld();
        }
        if (w != null) {
            w.setSpawnFlags(false, false); // no mobs
            w.setSpawnLocation(0, BlackWorldGenerator.FLOOR_Y + 1, 0);
            w.setStorm(false);
        }

        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "trapped.yml");
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("trapped");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID victim = UUID.fromString(key);
                UUID trapper = UUID.fromString(sec.getString(key + ".trapper", ""));
                Location origin = sec.getLocation(key + ".origin");
                boolean released = sec.getBoolean(key + ".released", false);
                entries.put(victim, new Entry(trapper, origin, released));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping bad trapped.yml entry: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> en : entries.entrySet()) {
            String p = "trapped." + en.getKey();
            yml.set(p + ".trapper", en.getValue().trapper().toString());
            yml.set(p + ".origin", en.getValue().origin());
            yml.set(p + ".released", en.getValue().released());
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save trapped.yml: " + ex.getMessage());
        }
    }

    // ---------- queries ----------

    public World world() {
        return Bukkit.getWorld(WORLD_NAME);
    }

    public boolean isBlackWorld(World w) {
        return w != null && w.getName().equals(WORLD_NAME);
    }

    public boolean isTrapped(Player p) {
        Entry e = entries.get(p.getUniqueId());
        return e != null && !e.released();
    }

    public Location blackSpawn() {
        return new Location(world(), 0.5, BlackWorldGenerator.FLOOR_Y + 1, 0.5);
    }

    // ---------- actions ----------

    public void trap(Player trapper, Player victim) {
        entries.put(victim.getUniqueId(), new Entry(trapper.getUniqueId(), victim.getLocation(), false));
        save();
        sendToBlackWorld(victim);
    }

    private void sendToBlackWorld(Player victim) {
        victim.setFallDistance(0);
        victim.teleport(blackSpawn());
        if (NIGHT_VISION) {
            victim.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
    }

    /** Frees one player. Works for offline players too (they're moved when they next join). */
    public boolean release(UUID victimId) {
        Entry e = entries.get(victimId);
        if (e == null || e.released()) return false;

        Player online = Bukkit.getPlayer(victimId);
        if (online != null) {
            entries.remove(victimId);
            save();
            sendHome(online, e);
        } else {
            entries.put(victimId, new Entry(e.trapper(), e.origin(), true));
            save();
        }
        return true;
    }

    /** Frees everyone this player trapped. Returns how many were freed. */
    public int releaseAllBy(Player trapper) {
        int count = 0;
        for (UUID victim : new ArrayList<>(entries.keySet())) {
            Entry e = entries.get(victim);
            if (e != null && !e.released() && e.trapper().equals(trapper.getUniqueId())) {
                if (release(victim)) count++;
            }
        }
        return count;
    }

    private void sendHome(Player p, Entry e) {
        Location dest = e.origin();
        if (dest == null || dest.getWorld() == null) {
            dest = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        p.setFallDistance(0);
        p.teleport(dest);
    }

    /** Keeps players consistent across logouts, restarts and offline releases. */
    public void onJoin(Player p) {
        Entry e = entries.get(p.getUniqueId());
        if (e == null) {
            // Stranded in the black world with no record: send to main world spawn.
            if (isBlackWorld(p.getWorld())) {
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        } else if (e.released()) {
            entries.remove(p.getUniqueId());
            save();
            sendHome(p, e);
        } else if (!isBlackWorld(p.getWorld())) {
            sendToBlackWorld(p);
        }
    }
}
