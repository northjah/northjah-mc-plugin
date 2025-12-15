package com.northjah.mc.handler;

import com.northjah.mc.Application;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 新手大礼包
 *
 * @author Elijah
 */
public class BeginnerGiftPackHandler implements Listener {
    private final Application app;

    public BeginnerGiftPackHandler(Application app) {
        this.app = app;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 判断是否第一次加入
        if (player.hasPlayedBefore()) {
            return;
        }
        app.getLogger().info("§b[Server] 新玩家 §e" + player.getName() + " §c第一次加入，发放新手礼包！");
        player.sendMessage("§b[Server] 欢迎新玩家 §e" + player.getName() + " §c第一次加入，发放新手礼包！");
        // 延迟执行，确保玩家完全加载.
        app.getServer().getScheduler().runTaskLater(app, () -> {
            var inv = player.getInventory();
            inv.addItem(new ItemStack(Material.STONE_PICKAXE));   // 石镐
            inv.addItem(new ItemStack(Material.STONE_AXE));      // 石斧
            inv.addItem(new ItemStack(Material.STONE_SHOVEL));   // 石铲
            inv.addItem(new ItemStack(Material.STONE_SWORD));    // 石剑
            inv.addItem(new ItemStack(Material.BREAD, 32));      // 面包 32 个
            inv.addItem(new ItemStack(Material.TORCH, 32));      // 火把 64 个
            inv.addItem(new ItemStack(Material.OAK_LOG, 32));    // 橡木原木 32 个
            inv.addItem(new ItemStack(Material.COBBLESTONE, 32));// 圆石 64 个
            //(1秒) = (20tick)
        }, 20L);
    }
}
