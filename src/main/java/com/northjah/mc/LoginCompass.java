package com.northjah.mc;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

public class LoginCompass extends JavaPlugin implements Listener {

    private final Set<UUID> pendingLogin = new HashSet<>();
    private final Map<UUID, Location> joinLocation = new HashMap<>();

    // 仅保存背包数据
    private final Map<UUID, ItemStack[]> inventoryContents = new HashMap<>();
    private final Map<UUID, ItemStack[]> armorContents     = new HashMap<>();
    private final Map<UUID, ItemStack>   offHandItem       = new HashMap<>();

    // 专用指南针隐藏标记
    private static final String COMPASS_FLAG = "§b§l[登录验证专用]";
    // 赞助红石块隐藏标记
    private static final String SPONSOR_FLAG = "§c§l[服务器赞助]";

    // 缓存二维码图片
    private BufferedImage sponsorQRCode;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        // 加载 resources 中的二维码图片
        try {
            sponsorQRCode = ImageIO.read(getResource("help_me.png"));
            getLogger().info("§a赞助二维码加载成功！");
        } catch (IOException | NullPointerException e) {
            getLogger().warning("§c未找到 help_me.png！请放到 src/main/resources/ 文件夹");
            sponsorQRCode = null;
        }

        // 定时补空气（只在水中且空气不足时）
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(pendingLogin)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isUnderWater() && p.getRemainingAir() < 300) {
                        p.setRemainingAir(300);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 5L);

        getLogger().info("§aLoginCompass 赞助版已加载！");
    }

    @Override
    public void onDisable() {
        pendingLogin.clear();
        joinLocation.clear();
        inventoryContents.clear();
        armorContents.clear();
        offHandItem.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        PlayerInventory inv = p.getInventory();

        pendingLogin.add(uuid);
        joinLocation.put(uuid, p.getLocation().clone());

        // 保存原始背包
        inventoryContents.put(uuid, inv.getContents().clone());
        armorContents.put(uuid, inv.getArmorContents().clone());
        offHandItem.put(uuid, inv.getItemInOffHand());

        // 保护状态
        p.setInvulnerable(true);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFreezeTicks(0);
        p.setRemainingAir(300);
        p.setGameMode(GameMode.ADVENTURE);
        inv.clear();

        // 槽位 0：验证指南针
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta cMeta = compass.getItemMeta();
        cMeta.setDisplayName("§a§l§e◆ §6点击指南针§a验证登录 §e◆");
        cMeta.setLore(Arrays.asList(
                "§7§m---------------------",
                "§e§l右键打开验证界面",
                "§7§m---------------------",
                COMPASS_FLAG
        ));
        compass.setItemMeta(cMeta);
        inv.setItem(0, compass);

        // 槽位 1：赞助红石块
        ItemStack sponsor = new ItemStack(Material.REDSTONE);
        ItemMeta sMeta = sponsor.getItemMeta();
        sMeta.setDisplayName("§c§l❤ 赞助支持服务器 ❤");
        sMeta.setLore(Arrays.asList(
                "§7感谢大佬支持服务器发展！",
                "§e右键查看赞助二维码",
                SPONSOR_FLAG
        ));
        sponsor.setItemMeta(sMeta);
        inv.setItem(5, sponsor);

        // 提示
        p.sendTitle("§c§l请验证登录", "§7右键手中的§a指南针§7进行验证", 20, 200, 20);
        p.sendMessage("§6§l[§a登录验证§6§l] §7请右键手中的§a指南针§7打开验证界面");
        p.sendMessage("§c§l❤ §7旁边红石块可查看赞助方式 ~");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer().getUniqueId());
    }

    private void cleanup(UUID uuid) {
        pendingLogin.remove(uuid);
        joinLocation.remove(uuid);
        inventoryContents.remove(uuid);
        armorContents.remove(uuid);
        offHandItem.remove(uuid);
    }

    // 位置冻结
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        if (!pendingLogin.contains(e.getPlayer().getUniqueId())) return;
        Location from = joinLocation.get(e.getPlayer().getUniqueId());
        if (from != null && e.getTo() != null && e.getTo().distance(from) > 1.0) {
            Location fixed = from.clone();
            fixed.setYaw(e.getTo().getYaw());
            fixed.setPitch(e.getTo().getPitch());
            e.setTo(fixed);
        }
    }

    // 交互处理：指南针验证 + 红石块赞助二维码
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!pendingLogin.contains(p.getUniqueId())) return;

        if (e.getAction() != Action.RIGHT_CLICK_AIR &&
                e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            return;
        }

        ItemStack item = e.getItem();
        if (item == null) {
            e.setCancelled(true);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            e.setCancelled(true);
            return;
        }

        List<String> lore = meta.getLore();

        // 专用指南针 → 打开验证GUI
        if (item.getType() == Material.COMPASS && lore.contains(COMPASS_FLAG)) {
            e.setCancelled(true);
            openVerifyGUI(p);
            return;
        }

        // 赞助红石块 → 显示二维码地图
        if (item.getType() == Material.REDSTONE && lore.contains(SPONSOR_FLAG)) {
            e.setCancelled(true);

            if (sponsorQRCode == null) {
                p.sendMessage("§c赞助二维码加载失败，请联系管理员");
                return;
            }

            // 保存当前主手物品
            ItemStack oldHand = p.getInventory().getItemInMainHand().clone();

            // 创建填充二维码的地图
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
            MapView view = Bukkit.createMap(p.getWorld());
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(new QRCodeMapRenderer(sponsorQRCode));
            mapMeta.setMapView(view);
            mapItem.setItemMeta(mapMeta);

            // 临时替换主手为地图
            p.getInventory().setItemInMainHand(mapItem);
            p.sendMessage("§a§l正在显示赞助二维码，手持查看 ~");

            // 5秒后自动恢复原物品
//            new BukkitRunnable() {
//                @Override
//                public void run() {
//                    if (p.isOnline() && pendingLogin.contains(p.getUniqueId())) {
//                        if (p.getInventory().getItemInMainHand().getType() == Material.FILLED_MAP) {
//                            p.getInventory().setItemInMainHand(oldHand);
//                            p.sendMessage("§7二维码地图已自动关闭");
//                        }
//                    }
//                }
//            }.runTaskLater(LoginCompass.this, 100L); // 5秒

            return;
        }

        // 其他所有交互禁止
        e.setCancelled(true);
    }

    // 二维码地图渲染器
    private static class QRCodeMapRenderer extends MapRenderer {
        private final BufferedImage image;
        private boolean rendered = false;

        public QRCodeMapRenderer(BufferedImage image) {
            this.image = image;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (rendered) return;
            canvas.drawImage(0, 0, image);
            rendered = true;
        }
    }

    // 打开验证GUI
    private void openVerifyGUI(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27, "§a§l登录验证");

        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);

        for (int i = 0; i < 27; i++) {
            if (i != 13) gui.setItem(i, glass);
        }

        ItemStack emerald = new ItemStack(Material.EMERALD);
        ItemMeta em = emerald.getItemMeta();
        em.setDisplayName("§a§l✨ 点击加入游戏 ✨");
        emerald.setItemMeta(em);
        gui.setItem(13, emerald);

        p.openInventory(gui);
    }

    // 点击绿宝石验证通过
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        UUID uuid = p.getUniqueId();
        if (!pendingLogin.contains(uuid)) return;
        if (!e.getView().getTitle().equals("§a§l登录验证")) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.EMERALD) return;

        pendingLogin.remove(uuid);
        joinLocation.remove(uuid);
        p.closeInventory();
        p.setInvulnerable(false);

        PlayerInventory inv = p.getInventory();
        inv.clear();

        // 恢复原始背包
        ItemStack[] contents = inventoryContents.remove(uuid);
        if (contents != null) inv.setContents(contents);
        ItemStack[] armor = armorContents.remove(uuid);
        if (armor != null) inv.setArmorContents(armor);
        ItemStack off = offHandItem.remove(uuid);
        if (off != null) inv.setItemInOffHand(off);

        p.sendTitle("§a§l验证成功！", "§7欢迎加入游戏~", 10, 70, 20);
        p.sendMessage("§a§l[登录验证] §a验证通过！背包已恢复");
    }

    // 封禁未验证时的各种操作
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (pendingLogin.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && pendingLogin.contains(p.getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent e) { if (pendingLogin.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && pendingLogin.contains(p.getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onFood(FoodLevelChangeEvent e) { if (e.getEntity() instanceof Player p && pendingLogin.contains(p.getUniqueId())) { e.setCancelled(true); p.setFoodLevel(20); } }
}