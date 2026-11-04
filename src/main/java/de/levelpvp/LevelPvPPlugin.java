package de.levelpvp;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.ban.BanList;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class LevelPvPPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<UUID, String> pluginBans = new LinkedHashMap<>();
    private final Map<UUID, UUID> pendingUnban = new HashMap<>();
    private final Map<UUID, org.bukkit.inventory.EquipmentSlot> pendingSoulHand = new HashMap<>();
    private NamespacedKey itemKey, targetKey;
    private File playersFile, globalFile;

    @Override public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "special_item");
        targetKey = new NamespacedKey(this, "unban_target");
        playersFile = new File(getDataFolder(), "players.yml");
        globalFile = new File(getDataFolder(), "global.yml");
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("stats")).setExecutor(this);
        Objects.requireNonNull(getCommand("pvpmod")).setExecutor(this);
        Objects.requireNonNull(getCommand("pvpmod")).setTabCompleter(this);
        registerRecipes();
        for (Player player : Bukkit.getOnlinePlayers()) {
            data(player.getUniqueId());
            applyMaxHealth(player);
        }
        getLogger().info("LevelPvP wurde aktiviert. Es ist kein künstliches Damage-Cap aktiv.");
    }

    @Override public void onDisable() { saveData(); }

    private PlayerData data(UUID id) {
        return players.computeIfAbsent(id, ignored -> new PlayerData());
    }

    private void loadData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        if (playersFile.exists()) {
            var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playersFile);
            for (String key : yaml.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    PlayerData d = new PlayerData();
                    String p = key + ".";
                    d.level = yaml.getInt(p + "level", 0);
                    d.kills = yaml.getInt(p + "kills", 0);
                    d.deaths = yaml.getInt(p + "deaths", 0);
                    d.lastName = yaml.getString(p + "name", "Unknown");
                    players.put(id, d);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (globalFile.exists()) {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(globalFile);
            var section = y.getConfigurationSection("plugin-bans");
            if (section != null) for (String key : section.getKeys(false)) {
                try { pluginBans.put(UUID.fromString(key), section.getString(key, "Unknown")); }
                catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void saveData() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        players.forEach((id, d) -> {
            String p = id.toString() + ".";
            y.set(p + "level", d.level); y.set(p + "kills", d.kills);
            y.set(p + "deaths", d.deaths); y.set(p + "name", d.lastName);
        });
        saveAtomically(y, playersFile);
        var g = new org.bukkit.configuration.file.YamlConfiguration();
        pluginBans.forEach((id, name) -> g.set("plugin-bans." + id, name));
        saveAtomically(g, globalFile);
    }

    private void saveAtomically(org.bukkit.configuration.file.YamlConfiguration y, File file) {
        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            y.save(tmp);
            try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ex) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) { getLogger().severe("Daten konnten nicht gespeichert werden: " + ex.getMessage()); }
    }

    private int clamp(int level) {
        return Math.max(getConfig().getInt("settings.min-level", -10),
                Math.min(getConfig().getInt("settings.max-level", 30), level));
    }
    private double attackMultiplier(int level) { return 1.0 + level / 30.0; }
    private double xpMultiplier(int level) {
        double mult = 1.0 + (4.0 * level / 30.0);
        return level < 0 ? Math.max(getConfig().getDouble("settings.negative-xp-minimum", 0.10), mult) : mult;
    }
    private double reduction(int level) { return Math.max(0, level) / 30.0 * 0.05; }
    private double maxHealth(int level) {
        if (level >= 0) return 20.0 + level;
        return Math.max(1.0, 20.0 + level * 2.0);
    }
    private void changeLevel(UUID id, int amount) {
        PlayerData d = data(id);
        d.level = clamp(d.level + amount);
        Player p = Bukkit.getPlayer(id);
        if (p != null) applyMaxHealth(p);
        saveData();
    }
    private void setLevel(UUID id, int level) {
        data(id).level = clamp(level);
        Player p = Bukkit.getPlayer(id);
        if (p != null) applyMaxHealth(p);
        saveData();
    }
    private void applyMaxHealth(Player p) {
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double value = maxHealth(data(p.getUniqueId()).level);
        attr.setBaseValue(value);
        if (p.getHealth() > value) p.setHealth(value);
    }

    private void registerRecipes() {
        ShapedRecipe crystal = new ShapedRecipe(new NamespacedKey(this, "rebirth_crystal"), special(Material.AMETHYST_SHARD, "Wiedergeburts-Kristall", "crystal"));
        crystal.shape("DGD", "GAG", "DGD");
        crystal.setIngredient('D', Material.DIAMOND);
        crystal.setIngredient('G', Material.GOLD_BLOCK);
        crystal.setIngredient('A', Material.GOLDEN_APPLE);
        Bukkit.addRecipe(crystal);

        ShapedRecipe soul = new ShapedRecipe(new NamespacedKey(this, "soul_liberation"), special(Material.NETHER_STAR, "Seelenbefreiung", "soul"));
        soul.shape("SDS", "DND", "SDS");
        soul.setIngredient('S', Material.NETHERITE_SCRAP);
        soul.setIngredient('D', Material.DIAMOND_BLOCK);
        soul.setIngredient('N', Material.NETHER_STAR);
        Bukkit.addRecipe(soul);
    }

    private ItemStack special(Material material, String name, String id) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(List.of(ChatColor.GRAY + "LevelPvP-Spezialitem"));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }
    private String specialId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(itemKey, PersistentDataType.STRING, "");
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        data(e.getPlayer().getUniqueId()).lastName = e.getPlayer().getName();
        applyMaxHealth(e.getPlayer());
        saveData();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        PlayerData vd = data(victim.getUniqueId());
        vd.deaths++;
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            PlayerData kd = data(killer.getUniqueId());
            kd.kills++;
            changeLevel(killer.getUniqueId(), 1);
            boolean atMinBeforeDeath = vd.level <= getConfig().getInt("settings.min-level", -10);
            changeLevel(victim.getUniqueId(), -1);
            if (atMinBeforeDeath && getConfig().getBoolean("settings.ban-at-min-level", true)) {
                pluginBans.put(victim.getUniqueId(), victim.getName());
                Bukkit.getBanList(BanList.Type.NAME).addBan(victim.getName(),
                        "Du wurdest auf Level -10 im PvP besiegt.", null, "LevelPvP");
                victim.kickPlayer(ChatColor.RED + "Du wurdest gebannt, weil du auf Level -10 im PvP gestorben bist.");
            }
        }
        saveData();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker = resolvePlayer(e.getDamager());
        if (attacker != null) e.setDamage(e.getDamage() * attackMultiplier(data(attacker.getUniqueId()).level));
        if (e.getEntity() instanceof Player victim) {
            e.setDamage(e.getDamage() * (1.0 - reduction(data(victim.getUniqueId()).level)));
        }
    }
    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler public void onXp(PlayerExpChangeEvent e) {
        double multiplier = xpMultiplier(data(e.getPlayer().getUniqueId()).level);
        e.setAmount(Math.max(0, (int) Math.round(e.getAmount() * multiplier)));
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        String id = specialId(e.getItem());
        if (id.equals("crystal")) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            PlayerData d = data(p.getUniqueId());
            if (d.level >= 0) { p.sendMessage(ChatColor.RED + "Der Kristall funktioniert nur bei negativem Level."); return; }
            changeLevel(p.getUniqueId(), 1);
            consumeOne(p, e.getHand());
            p.sendMessage(ChatColor.GREEN + "Dein Level wurde um 1 erhöht.");
        } else if (id.equals("soul")) {
            e.setCancelled(true);
            if (e.getHand() != null) pendingSoulHand.put(e.getPlayer().getUniqueId(), e.getHand());
            openSoulGui(e.getPlayer(), 0);
        }
    }
    private void consumeOne(Player p, org.bukkit.inventory.EquipmentSlot hand) {
        if (hand == null) return;
        ItemStack item = hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (item.getAmount() <= 1) item.setAmount(0); else item.setAmount(item.getAmount() - 1);
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private void openStats(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("settings.stats-title", "&8Spieler-Statistik")));
        PlayerData d = data(p.getUniqueId());
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(p);
        sm.setDisplayName(ChatColor.GOLD + p.getName());
        sm.setLore(List.of(ChatColor.GRAY + "Deine aktuellen Spielerdaten"));
        head.setItemMeta(sm);
        inv.setItem(4, head);
        inv.setItem(10, named(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Level: " + d.level,
                List.of(ChatColor.GRAY + "Bereich: -10 bis +30")));
        inv.setItem(11, named(Material.IRON_SWORD, ChatColor.YELLOW + "Kills: " + d.kills, List.of("")));
        inv.setItem(12, named(Material.SKELETON_SKULL, ChatColor.RED + "Tode: " + d.deaths, List.of("")));
        double kd = d.deaths == 0 ? d.kills : (double)d.kills / d.deaths;
        inv.setItem(13, named(Material.GOLD_INGOT, ChatColor.GOLD + "K/D: " + String.format(Locale.US, "%.2f", kd), List.of("")));
        inv.setItem(14, named(Material.RED_DYE, ChatColor.RED + "Herzen: " + String.format(Locale.US, "%.1f", maxHealth(d.level)/2.0),
                List.of(ChatColor.GRAY + "Maximale Lebenspunkte")));
        inv.setItem(15, named(Material.IRON_AXE, ChatColor.YELLOW + "Angriff: " + String.format(Locale.US, "%.0f%%", attackMultiplier(d.level)*100),
                List.of(ChatColor.GRAY + "Kein künstliches Damage-Cap")));
        inv.setItem(16, named(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "XP: " + String.format(Locale.US, "%.0f%%", xpMultiplier(d.level)*100), List.of("")));
        inv.setItem(22, named(Material.SHIELD, ChatColor.GREEN + "Schadensreduzierung: " + String.format(Locale.US, "%.2f%%", reduction(d.level)*100),
                List.of(ChatColor.GRAY + "Maximal 5 %")));
        List<String> buffs = new ArrayList<>(), debuffs = new ArrayList<>();
        for (PotionEffect effect : p.getActivePotionEffects()) {
            String line = effect.getType().getKey().getKey() + " " + (effect.getAmplifier()+1);
            if (Set.of("speed","haste","strength","jump_boost","regeneration","resistance","fire_resistance","water_breathing","invisibility","night_vision","absorption","saturation").contains(effect.getType().getKey().getKey()))
                buffs.add(ChatColor.GREEN + line);
            else debuffs.add(ChatColor.RED + line);
        }
        List<String> potionLore = new ArrayList<>();
        potionLore.add(ChatColor.GREEN + "Buffs:");
        potionLore.addAll(buffs.isEmpty() ? List.of(ChatColor.GRAY + "Keine") : buffs);
        potionLore.add(ChatColor.RED + "Debuffs:");
        potionLore.addAll(debuffs.isEmpty() ? List.of(ChatColor.GRAY + "Keine") : debuffs);
        inv.setItem(26, named(Material.POTION, ChatColor.LIGHT_PURPLE + "Effekte", potionLore));
        p.openInventory(inv);
    }

    private void openSoulGui(Player p, int page) {
        List<Map.Entry<UUID,String>> entries = new ArrayList<>(pluginBans.entrySet());
        int pages = Math.max(1, (entries.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("settings.soul-gui-title", "&8Seelenbefreiung")) + " " + (page+1));
        int start = page * 45;
        for (int i = start; i < Math.min(entries.size(), start+45); i++) {
            var entry = entries.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            OfflinePlayer off = Bukkit.getOfflinePlayer(entry.getKey());
            meta.setOwningPlayer(off);
            meta.setDisplayName(ChatColor.YELLOW + entry.getValue());
            meta.setLore(List.of(ChatColor.GRAY + "Klicken, um Entbannung vorzubereiten"));
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, entry.getKey().toString());
            head.setItemMeta(meta);
            inv.setItem(i-start, head);
        }
        if (page > 0) inv.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
        inv.setItem(49, named(Material.BARRIER, ChatColor.RED + "Schließen", List.of()));
        if (page < pages-1) inv.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Nächste Seite", List.of()));
        if (entries.isEmpty()) inv.setItem(22, named(Material.BARRIER, ChatColor.RED + "Keine gebannten Spieler", List.of()));
        p.openInventory(inv);
    }
    private void openConfirm(Player p, UUID target) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("settings.confirm-title", "&8Entbannung bestätigen")));
        pendingUnban.put(p.getUniqueId(), target);
        inv.setItem(11, named(Material.LIME_CONCRETE, ChatColor.GREEN + "Bestätigen", List.of(ChatColor.GRAY + "Spieler entbannen und Level auf 0 setzen")));
        inv.setItem(15, named(Material.RED_CONCRETE, ChatColor.RED + "Abbrechen", List.of()));
        p.openInventory(inv);
    }
    @EventHandler public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title.startsWith(ChatColor.translateAlternateColorCodes('&', getConfig().getString("settings.stats-title", "&8Spieler-Statistik")))
                || title.startsWith(ChatColor.translateAlternateColorCodes('&', getConfig().getString("settings.soul-gui-title", "&8Seelenbefreiung")))
                || title.equals(ChatColor.translateAlternateColorCodes('&', getConfig().getString("settings.confirm-title", "&8Entbannung bestätigen")))) {
            e.setCancelled(true);
            if (title.startsWith(ChatColor.translateAlternateColorCodes('&', getConfig().getString("settings.soul-gui-title", "&8Seelenbefreiung")))) {
                if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
                if (e.getSlot() == 49) { p.closeInventory(); return; }
                int page;
                try { page = Integer.parseInt(title.substring(title.lastIndexOf(' ') + 1)) - 1; }
                catch (NumberFormatException ex) { page = 0; }
                if (e.getSlot() == 45 && page > 0) { openSoulGui(p, page - 1); return; }
                if (e.getSlot() == 53) { openSoulGui(p, page + 1); return; }
                String target = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
                if (target != null) openConfirm(p, UUID.fromString(target));
            } else if (title.equals(ChatColor.translateAlternateColorCodes('&', getConfig().getString("settings.confirm-title", "&8Entbannung bestätigen")))) {
                UUID target = pendingUnban.get(p.getUniqueId());
                if (target == null) { p.closeInventory(); return; }
                if (e.getSlot() == 11) {
                    boolean success = unban(target);
                    if (success) {
                        org.bukkit.inventory.EquipmentSlot hand = pendingSoulHand.get(p.getUniqueId());
                        if (hand != null) consumeOne(p, hand);
                        p.sendMessage(ChatColor.GREEN + "Spieler wurde entbannt.");
                    } else p.sendMessage(ChatColor.RED + "Entbannung nicht möglich; Spieler ist nicht mehr in der Plugin-Bannliste.");
                    pendingUnban.remove(p.getUniqueId()); pendingSoulHand.remove(p.getUniqueId()); p.closeInventory();
                } else if (e.getSlot() == 15) {
                    pendingUnban.remove(p.getUniqueId()); openSoulGui(p, 0);
                }
            }
        }
    }
    private boolean unban(UUID id) {
        String name = pluginBans.remove(id);
        if (name == null) return false;
        Bukkit.getBanList(BanList.Type.NAME).pardon(name);
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        if (Bukkit.hasWhitelist()) off.setWhitelisted(true);
        setLevel(id, 0);
        saveData();
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler können /stats verwenden."); return true; }
            openStats(p); return true;
        }
        if (!sender.hasPermission("levelpvp.admin")) { sender.sendMessage(ChatColor.RED + "Keine Berechtigung."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig(); sender.sendMessage(ChatColor.GREEN + "Konfiguration neu geladen."); return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("unban")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!pluginBans.containsKey(target.getUniqueId())) { sender.sendMessage(ChatColor.RED + "Nicht in der Plugin-Bannliste."); return true; }
            unban(target.getUniqueId()); sender.sendMessage(ChatColor.GREEN + "Entbannt."); return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("stats")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            PlayerData d = data(target.getUniqueId());
            sender.sendMessage(ChatColor.GOLD + target.getName() + ": Level " + d.level + " | Kills " + d.kills + " | Tode " + d.deaths);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            players.put(target.getUniqueId(), new PlayerData());
            Player online = target.getPlayer(); if (online != null) applyMaxHealth(online);
            saveData(); sender.sendMessage(ChatColor.GREEN + "Daten zurückgesetzt."); return true;
        }
        if (args.length >= 3 && Set.of("setlevel","addlevel","removelevel").contains(args[0].toLowerCase(Locale.ROOT))) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            int amount;
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Ungültige Zahl."); return true; }
            if (args[0].equalsIgnoreCase("setlevel")) setLevel(target.getUniqueId(), amount);
            else if (args[0].equalsIgnoreCase("addlevel")) changeLevel(target.getUniqueId(), amount);
            else changeLevel(target.getUniqueId(), -amount);
            sender.sendMessage(ChatColor.GREEN + "Neues Level: " + data(target.getUniqueId()).level);
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Verwendung: /pvpmod reload|setlevel|addlevel|removelevel|stats|unban|reset");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload","setlevel","addlevel","removelevel","stats","unban","reset");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        return Collections.emptyList();
    }

    private static final class PlayerData {
        int level = 0, kills = 0, deaths = 0;
        String lastName = "Unknown";
    }
}
