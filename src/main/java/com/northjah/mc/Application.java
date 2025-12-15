
package com.northjah.mc;

import com.northjah.mc.handler.BeginnerGiftPackHandler;
import com.northjah.mc.handler.LoginCompassHandler;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 主启动类
 *
 * @author Elijah
 */
public final class Application extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("§a[northjah-mc-plugin] 插件启动中...");

        // ============ 在这里配置和注册所有模块 ============

        // 实例化新手礼包处理器
       // BeginnerGiftPackHandler beginnerGiftPackHandler = new BeginnerGiftPackHandler(this);
        //登录指南针
        LoginCompassHandler loginCompassHandler = new LoginCompassHandler(this);

        // 注册事件监听器
      //  getServer().getPluginManager().registerEvents(beginnerGiftPackHandler, this);
        getServer().getPluginManager().registerEvents(loginCompassHandler, this);

        // ================================================
        getLogger().info("§a[northjah-mc-plugin] 插件启动完成！");
    }

    @Override
    public void onDisable() {
        getLogger().info("§c[northjah-mc-plugin] 插件已卸载。");
    }
}


