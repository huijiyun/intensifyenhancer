package cn.huiji.intensifyenhancer;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class IntensifyEnhancer extends JavaPlugin implements Listener, TabCompleter {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Inventory> openInv = new HashMap<>();

    private PlayerPointsAPI pointsAPI;

    private int moneyCost, expCost;
    private boolean cmdCostEnable;
    private int cmdMoneyCost, cmdExpCost;

    private String successMsg, failMsg, noItemMsg, alreadyMsg, noPermMsg;

    // ⭐ 新增
    private String expMode; // points / level

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
            getLogger().severe("未找到 PlayerPoints！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pointsAPI = PlayerPoints.getInstance().getAPI();

        saveDefaultConfig();
        loadConfigValue();

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("ie")).setTabCompleter(this);

        getLogger().info("强化插件已启用！");
        int pluginId = 31013;
        Metrics metrics = new Metrics(this, pluginId);

        // Optional: Add custom charts
        metrics.addCustomChart(
                new SimplePie("chart_id", () -> "My value"));

    }


    private void loadConfigValue() {
        reloadConfig();

        moneyCost = getConfig().getInt("money-cost", 10);
        expCost = getConfig().getInt("exp-cost", 5);

        cmdCostEnable = getConfig().getBoolean("command.enable-cost", true);
        cmdMoneyCost = getConfig().getInt("command.money-cost", 20);
        cmdExpCost = getConfig().getInt("command.exp-cost", 10);

        successMsg = getConfig().getString("success-message");
        failMsg = getConfig().getString("fail-message");
        noItemMsg = getConfig().getString("no-item-message");
        alreadyMsg = getConfig().getString("already-message");
        noPermMsg = getConfig().getString("no-permission");

        // ⭐ 新增读取
        expMode = getConfig().getString("exp-mode", "points");
    }

    private void msg(Player p, String s) {
        p.sendMessage(mm.deserialize(s));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家能使用！");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("gui")) {
            openGUI(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("ie.admin")) {
                msg(player, noPermMsg);
                return true;
            }
            loadConfigValue();
            msg(player, "<yellow>配置已重载！");
            return true;
        }

        // ===== 命令强化 =====
        if (args.length == 1 && args[0].equalsIgnoreCase("un")) {

            if (!player.hasPermission("ie.un")) {
                msg(player, noPermMsg);
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();

            if (item == null || item.getType().isAir()) {
                msg(player, noItemMsg);
                return true;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) return true;

            if (meta.isUnbreakable()) {
                msg(player, alreadyMsg);
                return true;
            }

            if (cmdCostEnable) {
                int points = pointsAPI.look(player.getUniqueId());

                boolean enoughExp;

                if (expMode.equalsIgnoreCase("level")) {
                    enoughExp = player.getLevel() >= cmdExpCost;
                } else {
                    enoughExp = player.getTotalExperience() >= cmdExpCost;
                }

                if (points < cmdMoneyCost || !enoughExp) {
                    msg(player, failMsg);
                    return true;
                }

                pointsAPI.take(player.getUniqueId(), cmdMoneyCost);

                if (expMode.equalsIgnoreCase("level")) {
                    player.setLevel(player.getLevel() - cmdExpCost);
                } else {
                    player.giveExp(-cmdExpCost);
                }
            }

            meta.setUnbreakable(true);
            item.setItemMeta(meta);

            msg(player, successMsg);
            return true;
        }

        msg(player, "<red>用法: /ie gui | /ie un");
        return true;
    }

    private void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "强化界面");

        int points = pointsAPI.look(player.getUniqueId());
        int exp = player.getTotalExperience();
        int level = player.getLevel();

        for (int i = 0; i < 27; i++) {
            if (i == 11 || i == 13 || i == 15) continue;
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        inv.setItem(13, null);

        // 点券
        List<String> moneyLore = Arrays.asList(
                "§7消耗: " + (points >= moneyCost ? "§e" : "§c") + moneyCost + " 点券",
                "§7当前: §a" + points + " 点券",
                "",
                points < moneyCost ? "§c点券不足！" : "§a点击强化",
                "§7效果: §a无法破坏"
        );

        inv.setItem(11, createItem(Material.GOLD_INGOT, "§6点券强化", moneyLore));

        // ⭐ 经验模式
        boolean enough;
        String costText;
        String currentText;

        if (expMode.equalsIgnoreCase("level")) {
            enough = level >= expCost;
            costText = expCost + " 等级";
            currentText = level + " 等级";
        } else {
            enough = exp >= expCost;
            costText = expCost + " 经验";
            currentText = exp + " 经验 §8(等级:" + level + ")";
        }

        List<String> expLore = Arrays.asList(
                "§7消耗: " + (enough ? "§e" : "§c") + costText,
                "§7当前: §a" + currentText,
                "",
                enough ? "§a点击强化" : "§c经验不足！",
                "§7效果: §a无法破坏"
        );

        inv.setItem(15, createItem(Material.EXPERIENCE_BOTTLE, "§a经验强化", expLore));

        openInv.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory inv = openInv.get(player.getUniqueId());
        if (inv == null || !e.getView().getTopInventory().equals(inv)) return;

        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory().equals(player.getInventory())) return;

        e.setCancelled(true);

        if (e.getSlot() == 13) {
            e.setCancelled(false);
            return;
        }

        ItemStack target = inv.getItem(13);
        if (target == null || target.getType().isAir()) {
            msg(player, "<red>请先放入武器！");
            return;
        }

        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;

        // 点券
        if (e.getSlot() == 11) {
            int points = pointsAPI.look(player.getUniqueId());
            if (points < moneyCost) {
                msg(player, failMsg);
                return;
            }

            pointsAPI.take(player.getUniqueId(), moneyCost);
            meta.setUnbreakable(true);
            target.setItemMeta(meta);

            msg(player, successMsg);
            player.updateInventory();
        }

        // ⭐ 经验强化（已支持模式）
        if (e.getSlot() == 15) {

            boolean enough;

            if (expMode.equalsIgnoreCase("level")) {
                enough = player.getLevel() >= expCost;
            } else {
                enough = player.getTotalExperience() >= expCost;
            }

            if (!enough) {
                msg(player, failMsg);
                return;
            }

            if (expMode.equalsIgnoreCase("level")) {
                player.setLevel(player.getLevel() - expCost);
            } else {
                player.giveExp(-expCost);
            }

            meta.setUnbreakable(true);
            target.setItemMeta(meta);

            msg(player, successMsg);
            player.updateInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        Inventory inv = openInv.remove(player.getUniqueId());
        if (inv == null) return;

        ItemStack item = inv.getItem(13);

        if (item != null && !item.getType().isAir()) {
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(item);

            if (!left.isEmpty()) {
                left.values().forEach(i ->
                        player.getWorld().dropItemNaturally(player.getLocation(), i)
                );
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if ("gui".startsWith(args[0])) list.add("gui");
            if ("reload".startsWith(args[0])) list.add("reload");
            if ("un".startsWith(args[0])) list.add("un");
            return list;
        }
        return Collections.emptyList();
    }
}