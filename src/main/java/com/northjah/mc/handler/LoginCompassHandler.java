package com.northjah.mc.handler;

import com.northjah.mc.Application;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
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
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

/**
 * 登录指南针
 *
 * @author Elijah
 */
public class LoginCompassHandler implements Listener {

    private final Application app;

    public LoginCompassHandler(Application app) {
        this.app = app;
    }


    //等待验证的玩家
    private final Set<UUID> pendingLogin = new HashSet<>();
    // 玩家加入时的位置（用于冻结）
    private final Map<UUID, Location> joinLocation = new HashMap<>();

    // === 原始玩家数据 ===
    private final Map<UUID, ItemStack[]> inventoryContents = new HashMap<>(); // 主背包 36 格
    private final Map<UUID, ItemStack[]> armorContents = new HashMap<>(); // 盔甲栏
    private final Map<UUID, ItemStack> offHandItem = new HashMap<>(); // 副手（单个物品）

    private final Map<UUID, Boolean> rookies = new HashMap<>(); // 是否是菜鸟

    // 记录玩家加入时原始的失明效果（时长+等级），没有则为 null
    private final Map<UUID, PotionEffect> originalBlindness = new HashMap<>();

    // 记录玩家加入时原始的夜视效果（时长+等级），没有则为 null
    private final Map<UUID, PotionEffect> originalNightVision = new HashMap<>();

    // 专用指南针唯一标识（防止绕过）
    private static final String COMPASS_FLAG = "§b§l[登录专用]";

    private static final String SYSTEM_LOGIN_COMPASS = "system_login_compass";

    private static final String SYSTEM_LOGIN_MENU_BAD = "system_login_menu_bad";



    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        // 定时补空气：只在水中且空气不足时补满（高效 + 精准）
        protectFromSuffocation();
        //加载gui
        initLoginGui(e);
    }

    //新手大礼包
    private void beginnerGiftPack(Player p) {
        //新手大礼包
        boolean flag = rookies.getOrDefault(p.getUniqueId(), false);
        if (flag) {
            app.getLogger().info("§b[Server] 新玩家 §e" + p.getName() + " §c第一次加入，发放新手礼包！");
            p.sendMessage("§b[Server] 欢迎新玩家 §e" + p.getName() + " §c第一次加入，发放新手礼包！");
            // 延迟执行，确保玩家完全加载.
            app.getServer().getScheduler().runTaskLater(app, () -> {
                var inventory = p.getInventory();
                inventory.addItem(new ItemStack(Material.STONE_PICKAXE));   // 石镐
                inventory.addItem(new ItemStack(Material.STONE_AXE));      // 石斧
                inventory.addItem(new ItemStack(Material.STONE_SHOVEL));   // 石铲
                inventory.addItem(new ItemStack(Material.STONE_SWORD));    // 石剑
                inventory.addItem(new ItemStack(Material.BREAD, 32));      // 面包 32 个
                inventory.addItem(new ItemStack(Material.TORCH, 32));      // 火把 64 个
                inventory.addItem(new ItemStack(Material.OAK_LOG, 32));    // 橡木原木 32 个
                inventory.addItem(new ItemStack(Material.COBBLESTONE, 32));// 圆石 64 个
                //(1秒) = (20tick)
            }, 20L);
        }
    }

    //初始化登录的物品 指南针 红石，
    private void initLoginGui(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        Location location = p.getLocation();
        PlayerInventory inv = p.getInventory();
        pendingLogin.add(uuid);
        joinLocation.put(uuid, location);

        // 原始玩家数据  主背包 36 格  盔甲栏 副手（单个物品）
        inventoryContents.put(uuid, inv.getContents());
        armorContents.put(uuid, inv.getArmorContents());
        offHandItem.put(uuid, inv.getItemInOffHand());

        if (!p.hasPlayedBefore()) {
            //是不是第一次加入
            rookies.put(uuid, Boolean.TRUE);
        } else {
            rookies.put(uuid, Boolean.FALSE);
        }


        // 设置保护状态
        p.setInvulnerable(true);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFreezeTicks(0);
        p.setRemainingAir(300);
        p.setGameMode(GameMode.ADVENTURE);

        //失明 + 去除夜视 start============================================================================
        // 保存原始失明
        PotionEffect oldBlind = p.getPotionEffect(PotionEffectType.BLINDNESS);
        originalBlindness.put(uuid, oldBlind != null ? oldBlind : null);
// 保存原始夜视
        PotionEffect oldNight = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
        originalNightVision.put(uuid, oldNight != null ? oldNight : null);

// 强制黑屏：失明 II 永久 + 移除夜视（世界更黑）
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                Integer.MAX_VALUE,
                2,                   // 失明 II 最黑
                false, false, false
        ));
        //失明 + 去除夜视 end============================================================================

        //清空背包
        inv.clear();

        //指南针
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();


        meta.displayName(Component.text()
                .content("◆ 点击指南针登录")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(" ◆ ", NamedTextColor.YELLOW))
                .build());

        List<Component> lore = Arrays.asList(
                Component.text("---------------------").color(TextColor.color(0xAAAAAA)).decorate(TextDecoration.STRIKETHROUGH),
                Component.text("右键打开登录界面").color(TextColor.color(0xFFFF55)).decorate(TextDecoration.BOLD),
                Component.text("---------------------").color(TextColor.color(0xAAAAAA)).decorate(TextDecoration.STRIKETHROUGH),
                Component.text(COMPASS_FLAG)
        );

        NamespacedKey key = new NamespacedKey(app, SYSTEM_LOGIN_COMPASS);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.lore(lore);
        compass.setItemMeta(meta);
        inv.setItem(2, compass);

        // 提示
        p.showTitle(Title.title(
                Component.text("请进行登录操作")
                        .color(TextColor.color(0xFF5555))
                        .decorate(TextDecoration.BOLD),

                Component.text("右键手中的")
                        .color(TextColor.color(0xAAAAAA))
                        .append(
                                Component.text("指南针")
                                        .color(TextColor.color(0x55FF55))
                        )
                        .append(
                                Component.text("进行验证")
                                        .color(TextColor.color(0xAAAAAA))
                        ),

                Title.Times.times(
                        Duration.ofMillis(500),   // 淡入 0.5 秒
                        Duration.ofSeconds(2),    // 显示 2 秒 2秒后消失
                        Duration.ofMillis(500)    // 淡出 0.5 秒
                )
        ));

        //提示信息
        p.sendMessage(Component.text("请点击")
                .color(TextColor.color(0xFFD700)) // 金黄色
                .append(Component.text("指南针")
                        .color(TextColor.color(0x00FFFF)) // 青色
                        .append(Component.text("登录")
                                .color(TextColor.color(0xFF69B4)))));

    }


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

        NamespacedKey key = new NamespacedKey(app, SYSTEM_LOGIN_COMPASS);
        // 判断是否是系统发的特殊指南针
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            //打开登录背包
            openLoginBackpack(p);
        }
    }

    //打开登录背包
    private void openLoginBackpack(Player p) {
        // 创建 9 格单行背包
        Inventory gui = Bukkit.createInventory(
                null,
                9,
                Component.text("登录选项")
                        .color(TextColor.color(0xFF0000))
                        .decorate(TextDecoration.BOLD)
        );

        // 左边床
        ItemStack bed = new ItemStack(Material.RED_BED);
        ItemMeta bedMeta = bed.getItemMeta();
        bedMeta.displayName(Component.text("点击加入游戏")
                .color(TextColor.color(0xFF5555))
                .decorate(TextDecoration.BOLD));
        NamespacedKey key = new NamespacedKey(app, SYSTEM_LOGIN_MENU_BAD);
        bedMeta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        bed.setItemMeta(bedMeta);

        // 右边绿宝石
        ItemStack emerald = new ItemStack(Material.EMERALD);
        ItemMeta emMeta = emerald.getItemMeta();
        emMeta.displayName(Component.text("赞助服务器")
                .color(TextColor.color(0x55FF55))
                .decorate(TextDecoration.BOLD));
        emerald.setItemMeta(emMeta);

        // 放置物品
        gui.setItem(1, bed);       // 左边格子（0 是最左边）
        gui.setItem(7, emerald);   // 右边格子（8 是最右边，这里 7 接近右边）

        p.openInventory(gui);
    }

    //登录后的处理
    private void postLoginAction(Player p){
        //加入游戏
        UUID uuid = p.getUniqueId();
        pendingLogin.remove(uuid);
        joinLocation.remove(uuid);
        p.closeInventory();
        p.setInvulnerable(false);
        //清空背包
        PlayerInventory inv = p.getInventory();
        inv.clear();
        // 恢复原始背包
        ItemStack[] contents = inventoryContents.remove(uuid);
        if (contents != null) inv.setContents(contents);
        ItemStack[] armor = armorContents.remove(uuid);
        if (armor != null) inv.setArmorContents(armor);
        ItemStack off = offHandItem.remove(uuid);
        if (off != null) inv.setItemInOffHand(off);
        p.setGameMode(GameMode.SURVIVAL);

        // ====== 精准恢复原始失明状态 ======
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        PotionEffect savedBlind = originalBlindness.remove(uuid);
        if (savedBlind != null) {
            p.addPotionEffect(savedBlind);
        }

// ====== 精准恢复原始夜视状态 ======
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        PotionEffect savedNight = originalNightVision.remove(uuid);
        if (savedNight != null) {
            p.addPotionEffect(savedNight);
        }
// =====================================
        //新手大礼包
        beginnerGiftPack(p);
        //放烟花
        Location loc = p.getLocation().add(0, 2, 0); // 玩家头顶2格位置
        Firework fw = (Firework) p.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta fwm = fw.getFireworkMeta();
// 设置烟花效果：大星星型、多彩颜色、闪烁
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE, Color.PURPLE)
                .withFade(Color.ORANGE, Color.AQUA)
                .with(FireworkEffect.Type.BALL_LARGE) // 大球型，也可以改 STAR、BURST 等
                .flicker(true) // 闪烁
                .trail(true)   // 拖尾
                .build();

        fwm.addEffect(effect);
        fwm.setPower(1); // 爆炸高度（1就够炫了）
        fw.setFireworkMeta(fwm);

// 可选：0.5秒后强制引爆（确保立即看到效果）
        new BukkitRunnable() {
            @Override
            public void run() {
                if (fw.isValid()) fw.detonate();
            }
        }.runTaskLater(app, 10L); // 0.5秒后引爆
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        UUID uuid = p.getUniqueId();
        if (!pendingLogin.contains(uuid)) return;
        Component title = e.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(title);
        if (!plainTitle.equals("登录选项")) return;

        // 阻止移动物品
        e.setCancelled(true);

        //是不是床点击了
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        // 判断床
        NamespacedKey bedKey = new NamespacedKey(app, SYSTEM_LOGIN_MENU_BAD);
        if (meta.getPersistentDataContainer().has(bedKey, PersistentDataType.BYTE)) {
            postLoginAction(p);
        }

    }

    //如果玩家出生在水中,还没有进行登录指南针,不让玩家溺水
    private void protectFromSuffocation() {
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
        }.runTaskTimer(app, 0L, 5L);
    }


    // 封禁未验证时的各种操作
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (pendingLogin.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && pendingLogin.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (pendingLogin.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && pendingLogin.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && pendingLogin.contains(p.getUniqueId())) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    // 冻结位置（不允许移动超过1格）
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        if (!pendingLogin.contains(e.getPlayer().getUniqueId())) return;
        Location from = joinLocation.get(e.getPlayer().getUniqueId());
        if (from != null && e.getTo().distance(from) > 1.0) {
            Location fixed = from.clone();
            fixed.setYaw(e.getTo().getYaw());
            fixed.setPitch(e.getTo().getPitch());
            e.setTo(fixed);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer().getUniqueId());
    }

    //清空信息
    private void cleanup(UUID uuid) {
        pendingLogin.remove(uuid);
        joinLocation.remove(uuid);
        inventoryContents.remove(uuid);
        armorContents.remove(uuid);
        offHandItem.remove(uuid);
    }

}
