package com.example.port;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PortPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey portKey;
    private TrapManager traps;

    @Override
    public void onEnable() {
        portKey = new NamespacedKey(this, "port_item");
        traps = new TrapManager(this);
        traps.load();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("port").setExecutor(this);
    }

    // ---------- the item ----------

    private ItemStack createPortItem() {
        ItemStack item = new ItemStack(Material.WITHER_ROSE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Port", NamedTextColor.BLACK)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(portKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isPort(ItemStack item) {
        if (item == null || item.getType() != Material.WITHER_ROSE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(portKey, PersistentDataType.BYTE);
    }

    // ---------- trap: hit a player with the rose ----------

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof Player victim)) return;
        if (!isPort(attacker.getInventory().getItemInMainHand())) return;

        e.setCancelled(true); // the rose does no damage
        if (!attacker.hasPermission("port.use")) return;
        if (traps.isTrapped(victim)) return;

        traps.trap(attacker, victim);
        attacker.sendMessage(Component.text("You sent " + victim.getName() + " to the void.", NamedTextColor.GRAY));
        victim.sendMessage(Component.text("You have been trapped...", NamedTextColor.DARK_GRAY));
    }

    // ---------- release: right-click a block with the rose ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        if (!isPort(e.getItem())) return;

        e.setCancelled(true); // don't plant the rose
        Player player = e.getPlayer();
        if (!player.hasPermission("port.use")) return;

        int freed = traps.releaseAllBy(player);
        player.sendMessage(Component.text(
                freed == 0 ? "Nobody to release." : "Released " + freed + " player(s).", NamedTextColor.GRAY));
    }

    // ---------- keep trapped players trapped ----------

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() != null && traps.isTrapped(e.getPlayer()) && !traps.isBlackWorld(e.getTo().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (traps.isTrapped(e.getPlayer())) {
            e.setRespawnLocation(traps.blackSpawn());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        getServer().getScheduler().runTask(this, () -> traps.onJoin(e.getPlayer()));
    }

    // ---------- protect the black room from being dug out ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (traps.isBlackWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (traps.isBlackWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (traps.isBlackWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    // ---------- /port ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /port give [player] | /port release <player>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (!sender.hasPermission("port.give")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(createPortItem());
                sender.sendMessage(Component.text("Gave Port to " + target.getName() + ".", NamedTextColor.GRAY));
            }
            case "release" -> {
                if (!sender.hasPermission("port.release")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /port release <player>"));
                    return true;
                }
                OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (off == null || !traps.release(off.getUniqueId())) {
                    sender.sendMessage(Component.text("That player isn't trapped.", NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("Released " + args[1] + ".", NamedTextColor.GRAY));
                }
            }
            default -> sender.sendMessage(Component.text("Usage: /port give [player] | /port release <player>"));
        }
        return true;
    }
}
