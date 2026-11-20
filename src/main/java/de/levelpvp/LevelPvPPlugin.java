package de.levelpvp;

import org.bukkit.*;
import io.papermc.paper.potion.PotionMix;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class LevelPvPPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Integer> levels = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, Integer> personalCaps = new HashMap<>();
    private final Map<UUID, List<ItemStack>> deliveries = new HashMap<>();
    private final Set<UUID> banned = new HashSet<>();
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey itemKey;
    private int globalCap = 20;
    private final Map<UUID, UUID> deliveryTargets = new HashMap<>();

    @Override public void onEnable() {
        itemKey = new NamespacedKey(this, "levelpvp_item");
        saveDefaultConfig();
        loadData();
        for (String cmd : List.of("delivery", "withdraw", "stats", "levelcap", "setlevel", "revivestar", "levelpvp")) {
            Objects.requireNonNull(getCommand(cmd), "Missing command " + cmd).setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        registerRecipes();
        registerPotionMixes();
        for (Player p : Bukkit.getOnlinePlayers()) applyPlayer(p);
        getLogger().info("LevelPvP enabled.");
    }

    @Override public void onDisable() { saveData(); }

    private void loadData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        globalCap = data.getInt("global-cap", getConfig().getInt("global-cap", 20));
        ConfigurationSection ps = data.getConfigurationSection("players");
        if (ps != null) for (String key : ps.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                levels.put(id, ps.getInt(key + ".level", 0));
                kills.put(id, ps.getInt(key + ".kills", 0));
                deaths.put(id, ps.getInt(key + ".deaths", 0));
                if (ps.contains(key + ".cap")) personalCaps.put(id, ps.getInt(key + ".cap"));
                if (ps.getBoolean(key + ".banned", false)) banned.add(id);
                List<?> raw = ps.getList(key + ".deliveries", List.of());
                List<ItemStack> items = new ArrayList<>();
                for (Object o : raw) if (o instanceof ItemStack it) items.add(it);
                if (!items.isEmpty()) deliveries.put(id, items);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveData() {
        if (data == null) return;
        data.set("global-cap", globalCap);
        data.set("players", null);
        for (UUID id : allKnownPlayers()) {
            String path = "players." + id;
            data.set(path + ".level", levels.getOrDefault(id, 0));
            data.set(path + ".kills", kills.getOrDefault(id, 0));
            data.set(path + ".deaths", deaths.getOrDefault(id, 0));
            if (personalCaps.containsKey(id)) data.set(path + ".cap", personalCaps.get(id));
            data.set(path + ".banned", banned.contains(id));
            data.set(path + ".deliveries", deliveries.getOrDefault(id, List.of()));
        }
        try { data.save(dataFile); } catch (IOException e) { getLogger().severe("Could not save data.yml: " + e.getMessage()); }
    }

    private Set<UUID> allKnownPlayers() {
        Set<UUID> ids = new HashSet<>(); ids.addAll(levels.keySet()); ids.addAll(kills.keySet()); ids.addAll(deaths.keySet());
        ids.addAll(personalCaps.keySet()); ids.addAll(deliveries.keySet()); ids.addAll(banned); return ids;
    }
    private int level(UUID id) { return levels.getOrDefault(id, 0); }
    private int cap(UUID id) { return personalCaps.getOrDefault(id, globalCap); }
    private int clamp(UUID id, int value) { return Math.max(-10, Math.min(cap(id), value)); }
    private void changeLevel(Player p, int delta) { setLevel(p, level(p.getUniqueId()) + delta); }
    private void setLevel(Player p, int value) {
        levels.put(p.getUniqueId(), clamp(p.getUniqueId(), value)); applyPlayer(p); saveData();
    }

    private void applyPlayer(Player p) {
        int lvl = level(p.getUniqueId());
        double hp = lvl >= 0 ? 20.0 + lvl : Math.max(1.0, 20.0 + (2.0 * lvl));
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp);
            if (p.getHealth() > hp) p.setHealth(hp);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (banned.contains(id)) { e.getPlayer().kickPlayer(ChatColor.RED + "Du bist in LevelPvP gebannt. Ein Admin muss dich wiederbeleben."); return; }
        levels.putIfAbsent(id, 0); kills.putIfAbsent(id, 0); deaths.putIfAbsent(id, 0);
        applyPlayer(e.getPlayer());
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(this, () -> { if (e.getPlayer().isOnline()) applyPlayer(e.getPlayer()); });
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity(); Player killer = victim.getKiller();
        UUID vid = victim.getUniqueId(); deaths.merge(vid, 1, Integer::sum);
        if (killer != null && !killer.getUniqueId().equals(vid)) {
            UUID kid = killer.getUniqueId(); kills.merge(kid, 1, Integer::sum);
            changeLevel(killer, 1);
            if (level(kid) >= cap(kid)) killer.getWorld().dropItemNaturally(killer.getLocation(), makeBottle(1));
            e.setDeathMessage(ChatColor.GRAY + victim.getName() + " wurde von " + killer.getName() + " besiegt. " + ChatColor.GREEN + killer.getName() + " +1 Level");
        }
        if (level(vid) <= -10) {
            banned.add(vid);
            Bukkit.getScheduler().runTask(this, () -> victim.kickPlayer(ChatColor.RED + "Du wurdest wegen eines Todes auf Level -10 gebannt."));
        } else { changeLevel(victim, -1); }
        saveData();
    }

    @EventHandler public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p) attacker = p;
        if (attacker != null) e.setDamage(e.getDamage() * (1.0 + 0.05 * level(attacker.getUniqueId())));
        // Resistance is a separate incoming multiplier: +1% reduction per level.
        e.setDamage(e.getDamage() * (1.0 - 0.01 * level(victim.getUniqueId())));
    }

    @EventHandler public void onXp(PlayerExpChangeEvent e) {
        int lvl = level(e.getPlayer().getUniqueId());
        e.setAmount(Math.max(0, (int)Math.round(e.getAmount() * (1.0 + 0.25 * lvl))));
    }

    @EventHandler public void onConsume(PlayerItemConsumeEvent e) {
        String kind = customType(e.getItem());
        if ("crafted_level".equals(kind)) {
            e.setCancelled(true); Player p = e.getPlayer();
            if (level(p.getUniqueId()) >= 0) { p.sendMessage(ChatColor.RED + "Dieses Item funktioniert nur bei negativen Leveln."); return; }
            ItemStack hand = e.getItem(); if (hand.getAmount() > 1) hand.setAmount(hand.getAmount()-1); else p.getInventory().setItem(e.getHand(), null);
            changeLevel(p, 1); p.sendMessage(ChatColor.GREEN + "+1 Level.");
        }
    }

    private ItemStack tagged(Material material, String name, String type, int amount) {
        ItemStack item = new ItemStack(material, amount); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + name); meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type);
        meta.setLore(List.of(ChatColor.GRAY + "LevelPvP Custom Item")); item.setItemMeta(meta); return item;
    }
    private String customType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(itemKey, PersistentDataType.STRING, "");
    }
    private ItemStack makeBottle(int amount) { return tagged(Material.EXPERIENCE_BOTTLE, "Level in a Bottle", "level_bottle", amount); }

    private void registerRecipes() {
        // Revive Star: nether star center, netherite scraps in corners, diamond blocks on the edges.
        ItemStack revive = tagged(Material.NETHER_STAR, "Revive Star", "revive_star", 1);
        ShapedRecipe rr = new ShapedRecipe(new NamespacedKey(this, "revive_star"), revive);
        rr.shape("NDN", "DSD", "NDN"); rr.setIngredient('N', Material.NETHERITE_SCRAP); rr.setIngredient('D', Material.DIAMOND_BLOCK); rr.setIngredient('S', Material.NETHER_STAR); Bukkit.addRecipe(rr);
        ItemStack crafted = tagged(Material.GOLDEN_APPLE, "Crafted Level", "crafted_level", 1);
        ShapedRecipe cr = new ShapedRecipe(new NamespacedKey(this, "crafted_level"), crafted);
        cr.shape("DGD", "GAG", "DGD"); cr.setIngredient('D', Material.DIAMOND); cr.setIngredient('G', Material.GOLD_BLOCK); cr.setIngredient('A', Material.GOLDEN_APPLE); Bukkit.addRecipe(cr);
        ItemStack extender = tagged(Material.DRAGON_EGG, "Level Extender", "level_extender", 1);
        ShapedRecipe er = new ShapedRecipe(new NamespacedKey(this, "level_extender"), extender);
        er.shape("DDD", "DED", "DDD"); er.setIngredient('D', Material.DIAMOND_BLOCK); er.setIngredient('E', Material.DRAGON_EGG); Bukkit.addRecipe(er);
    }

    private void registerPotionMixes() {
        // Redstone extends the upgraded Speed II / Strength II potions to 8 minutes.
        ItemStack speedResult = customLongPotion(PotionEffectType.SPEED, "Speed II – 8:00");
        ItemStack strengthResult = customLongPotion(PotionEffectType.STRENGTH, "Stärke II – 8:00");
        RecipeChoice speedInput = PotionMix.createPredicateChoice(item -> isStrongPotion(item, PotionType.STRONG_SWIFTNESS));
        RecipeChoice strengthInput = PotionMix.createPredicateChoice(item -> isStrongPotion(item, PotionType.STRONG_STRENGTH));
        RecipeChoice redstone = new RecipeChoice.MaterialChoice(Material.REDSTONE);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(new NamespacedKey(this, "long_speed_two"), speedResult, speedInput, redstone));
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(new NamespacedKey(this, "long_strength_two"), strengthResult, strengthInput, redstone));
    }

    private boolean isStrongPotion(ItemStack item, PotionType type) {
        if (item == null || item.getType() != Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
        return meta.getBasePotionType() == type;
    }

    private ItemStack customLongPotion(PotionEffectType effect, String name) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        meta.addCustomEffect(new PotionEffect(effect, 8 * 60 * 20, 1), true);
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + name);
        potion.setItemMeta(meta);
        return potion;
    }

    @EventHandler public void onCraft(CraftItemEvent e) {
        String type = customType(e.getCurrentItem());
        if (type.isEmpty()) return;
        if (e.isShiftClick()) { e.setCancelled(true); return; }
        if ("revive_star".equals(type) || "crafted_level".equals(type) || "level_extender".equals(type)) {
            for (ItemStack ingredient : e.getInventory().getMatrix()) {
                if (ingredient != null && ingredient.getType() != Material.AIR && customType(ingredient).length() > 0) { e.setCancelled(true); return; }
            }
        }
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        ItemStack item = e.getItem(); String type = customType(item);
        if (type.isEmpty()) return;
        if (type.equals("level_bottle")) {
            e.setCancelled(true); Player p=e.getPlayer(); consumeHand(p,e.getHand()); changeLevel(p,1); p.sendMessage(ChatColor.GREEN+"+1 Level durch Level in a Bottle.");
        } else if (type.equals("level_extender")) {
            e.setCancelled(true); Player p=e.getPlayer(); UUID id=p.getUniqueId();
            personalCaps.put(id, cap(id)+10); consumeHand(p,e.getHand()); p.sendMessage(ChatColor.LIGHT_PURPLE+"Dein persönliches Levelcap wurde um 10 erhöht: "+cap(id)); saveData();
        } else if (type.equals("revive_star")) {
            e.setCancelled(true); openReviveGui(e.getPlayer());
        }
    }
    private void consumeHand(Player p, EquipmentSlot hand) {
        ItemStack i = p.getInventory().getItem(hand); if (i == null) return;
        if (i.getAmount() <= 1) p.getInventory().setItem(hand, null); else i.setAmount(i.getAmount()-1);
    }

    private void openMainDelivery(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA+"Delivery");
        inv.setItem(11, named(Material.OAK_SIGN, ChatColor.GREEN+"Versenden", "Wähle einen Spieler und lege Items in die Versand-GUI."));
        inv.setItem(15, named(Material.CHEST, ChatColor.GOLD+"Einfordern", "Hole deine empfangenen Lieferungen ab.")); p.openInventory(inv);
    }
    private ItemStack named(Material m, String name, String lore) { ItemStack i=new ItemStack(m); ItemMeta x=i.getItemMeta(); x.setDisplayName(name); x.setLore(List.of(ChatColor.GRAY+lore)); i.setItemMeta(x); return i; }
    private void openPlayerSelect(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,ChatColor.DARK_AQUA+"Empfänger auswählen");
        for(OfflinePlayer target:Bukkit.getOfflinePlayers()) { if(target.getUniqueId().equals(p.getUniqueId()) || (!target.hasPlayedBefore() && !target.isOnline())) continue; if(inv.firstEmpty() < 0) break; ItemStack h=new ItemStack(Material.PLAYER_HEAD); SkullMeta sm=(SkullMeta)h.getItemMeta(); sm.setOwningPlayer(target); sm.setDisplayName(ChatColor.YELLOW+(target.getName()==null?target.getUniqueId().toString():target.getName())); h.setItemMeta(sm); inv.addItem(h); }
        p.openInventory(inv);
    }
    private void openSendGui(Player p, UUID target) {
        deliveryTargets.put(p.getUniqueId(), target);
        Inventory inv=Bukkit.createInventory(null,54,ChatColor.DARK_AQUA+"Items versenden");
        inv.setItem(49,named(Material.LIME_WOOL,ChatColor.GREEN+"Lieferung abschicken","Klicke hier, um alle Items zu versenden.")); p.openInventory(inv);
    }
    private void openClaimGui(Player p) {
        List<ItemStack> list=deliveries.getOrDefault(p.getUniqueId(),new ArrayList<>());
        Inventory inv=Bukkit.createInventory(null,54,ChatColor.DARK_AQUA+"Lieferungen");
        for(ItemStack it:list) if(it!=null) inv.addItem(it.clone());
        if(list.isEmpty()) inv.setItem(22,named(Material.BARRIER,ChatColor.RED+"Keine Lieferungen","Dir wurden noch keine Items geschickt."));
        p.openInventory(inv);
    }
    private void openStats(Player p) {
        UUID id=p.getUniqueId(); int lvl=level(id);
        Inventory inv=Bukkit.createInventory(null,27,ChatColor.DARK_AQUA+"Deine LevelPvP-Stats");
        inv.setItem(10,named(Material.EXPERIENCE_BOTTLE,ChatColor.GREEN+"Level: "+lvl,"Levelcap: "+cap(id)));
        inv.setItem(12,named(Material.RED_DYE,ChatColor.RED+"Schaden: "+String.format(Locale.US,"%.0f%%",lvl*5.0),"Schadensbonus pro Level: 5%"));
        inv.setItem(13,named(Material.GOLDEN_APPLE,ChatColor.GOLD+"Herzen: "+String.format(Locale.US,"%.1f",p.getAttribute(Attribute.MAX_HEALTH).getBaseValue()/2.0),"Maximale Lebenspunkte"));
        inv.setItem(14,named(Material.SHIELD,ChatColor.AQUA+"Resistenz: "+String.format(Locale.US,"%.0f%%",lvl*1.0),"Schadensreduktion pro Level: 1%"));
        inv.setItem(15,named(Material.EXPERIENCE_BOTTLE,ChatColor.LIGHT_PURPLE+"XP: "+String.format(Locale.US,"%.0f%%",lvl*25.0),"XP-Bonus pro Level: 25%"));
        inv.setItem(16,named(Material.IRON_SWORD,ChatColor.YELLOW+"K/D: "+kills.getOrDefault(id,0)+" / "+deaths.getOrDefault(id,0),"Kills / Tode")); p.openInventory(inv);
    }
    private void openReviveGui(Player admin) {
        Inventory inv=Bukkit.createInventory(null,54,ChatColor.DARK_PURPLE+"Spieler wiederbeleben");
        for(UUID id:banned) {
            OfflinePlayer op=Bukkit.getOfflinePlayer(id); ItemStack h=new ItemStack(Material.PLAYER_HEAD); SkullMeta sm=(SkullMeta)h.getItemMeta(); sm.setOwningPlayer(op); sm.setDisplayName(ChatColor.RED+(op.getName()==null?id.toString():op.getName()));
            sm.setLore(List.of(ChatColor.GRAY+"Klicken zum Entbannen")); sm.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,"revive_target:"+id); h.setItemMeta(sm); inv.addItem(h);
        }
        if(banned.isEmpty()) inv.setItem(22,named(Material.BARRIER,ChatColor.GRAY+"Keine gebannten Spieler","Aktuell ist niemand gebannt.")); admin.openInventory(inv);
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title=e.getView().getTitle();
        if (title.equals(ChatColor.DARK_AQUA+"Delivery")) {
            e.setCancelled(true); if(e.getRawSlot()==11) openPlayerSelect(p); else if(e.getRawSlot()==15) openClaimGui(p); return;
        }
        if (title.equals(ChatColor.DARK_AQUA+"Empfänger auswählen")) {
            e.setCancelled(true); ItemStack it=e.getCurrentItem(); if(it==null || !(it.getItemMeta() instanceof SkullMeta sm) || sm.getOwningPlayer()==null) return;
            openSendGui(p,sm.getOwningPlayer().getUniqueId()); return;
        }
        if (title.equals(ChatColor.DARK_AQUA+"Items versenden")) {
            if(e.getRawSlot()==49) { e.setCancelled(true); UUID target=deliveryTargets.remove(p.getUniqueId()); if(target==null) return;
                List<ItemStack> items=new ArrayList<>(); Inventory top=e.getView().getTopInventory();
                for(int slot=0;slot<top.getSize();slot++) if(slot!=49 && top.getItem(slot)!=null) items.add(top.getItem(slot).clone());
                if(items.isEmpty()) { p.sendMessage(ChatColor.RED+"Du hast keine Items eingelegt."); return; }
                deliveries.computeIfAbsent(target,k->new ArrayList<>()).addAll(items); top.clear(); p.closeInventory(); p.sendMessage(ChatColor.GREEN+"Lieferung gespeichert.");
                Player online=Bukkit.getPlayer(target); if(online!=null) online.sendMessage(ChatColor.GOLD+"Du hast eine neue Lieferung erhalten!"); saveData(); return;
            }
            if(e.getClickedInventory()==e.getView().getTopInventory()) e.setCancelled(false);
            return;
        }
        if (title.equals(ChatColor.DARK_AQUA+"Lieferungen")) {
            // Claiming is done by taking items; save remaining contents when the GUI closes.
            return;
        }
        if (title.equals(ChatColor.DARK_PURPLE+"Spieler wiederbeleben")) {
            e.setCancelled(true); ItemStack it=e.getCurrentItem(); if(it==null || !it.hasItemMeta()) return;
            String value=it.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING);
            if(value!=null && value.startsWith("revive_target:")) { UUID id=UUID.fromString(value.substring("revive_target:".length())); revive(id); p.closeInventory(); p.sendMessage(ChatColor.GREEN+"Spieler wurde entbannt."); }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent e) {
        if(e.getView().getTitle().equals(ChatColor.DARK_AQUA+"Items versenden")) {
            UUID target=deliveryTargets.remove(e.getPlayer().getUniqueId());
            if(target!=null) for(ItemStack it:e.getInventory().getContents()) if(it!=null && it.getType()!=Material.AIR && !it.isSimilar(named(Material.LIME_WOOL,ChatColor.GREEN+"Lieferung abschicken","Klicke hier, um alle Items zu versenden."))) ((Player)e.getPlayer()).getInventory().addItem(it.clone());
        }
        if(e.getView().getTitle().equals(ChatColor.DARK_AQUA+"Lieferungen")) {
            UUID id=e.getPlayer().getUniqueId(); List<ItemStack> remaining=new ArrayList<>();
            for(ItemStack it:e.getInventory().getContents()) if(it!=null && it.getType()!=Material.AIR) remaining.add(it.clone());
            if(remaining.isEmpty()) deliveries.remove(id); else deliveries.put(id,remaining); saveData();
        }
    }

    private void revive(UUID id) { banned.remove(id); levels.put(id,0); saveData(); Player online=Bukkit.getPlayer(id); if(online!=null) { online.setHealth(Math.min(20,online.getAttribute(Attribute.MAX_HEALTH).getBaseValue())); applyPlayer(online); } }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String c=command.getName().toLowerCase(Locale.ROOT);
        if(c.equals("delivery") || c.equals("stats")) {
            if(!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler können diesen Befehl nutzen."); return true; }
            if(c.equals("delivery")) openMainDelivery(p); else openStats(p); return true;
        }
        if(c.equals("withdraw")) {
            if(!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
            if(args.length!=1) { p.sendMessage(ChatColor.RED+"Verwendung: /withdraw <Anzahl>"); return true; }
            try { int amount=Integer.parseInt(args[0]); if(amount<=0) throw new NumberFormatException();
                int lvl=level(p.getUniqueId()); if(lvl<=-10) { p.sendMessage(ChatColor.RED+"Auf Level -10 kannst du keine Level abziehen."); return true; }
                if(lvl-amount < -10) { p.sendMessage(ChatColor.RED+"Du kannst nicht unter Level -10 gehen."); return true; }
                setLevel(p,lvl-amount); int left=amount;
                while(left>0) { int stack=Math.min(64,left); p.getInventory().addItem(makeBottle(stack)); left-=stack; }
               p.sendMessage(ChatColor.GREEN.toString() + amount + " Level wurden in Level in a Bottle umgewandelt.");
            } catch(NumberFormatException ex) { p.sendMessage(ChatColor.RED+"Bitte gib eine positive ganze Zahl an."); } return true;
        }
        if(!sender.isOp()) { sender.sendMessage(ChatColor.RED+"Dafür brauchst du Operator-Rechte."); return true; }
        if(c.equals("setlevel")) {
            if(args.length!=2) { sender.sendMessage("/setlevel <Spieler> <Level>"); return true; }
            OfflinePlayer target=Bukkit.getOfflinePlayer(args[0]); if(!target.hasPlayedBefore() && !target.isOnline()) { sender.sendMessage("Spieler nicht gefunden."); return true; }
            try { int value=Integer.parseInt(args[1]); UUID id=target.getUniqueId(); levels.put(id,Math.max(-10,Math.min(personalCaps.getOrDefault(id,globalCap),value))); Player online=target.getPlayer(); if(online!=null) applyPlayer(online); saveData(); sender.sendMessage("Level gesetzt."); }
            catch(NumberFormatException ex) { sender.sendMessage("Ungültiges Level."); } return true;
        }
        if(c.equals("levelcap")) {
            if(args.length==1) { try { globalCap=Math.max(0,Integer.parseInt(args[0])); getConfig().set("global-cap",globalCap); saveConfig(); sender.sendMessage("Globales Levelcap: "+globalCap); for(Player p:Bukkit.getOnlinePlayers()) if(level(p.getUniqueId())>cap(p.getUniqueId())) setLevel(p,cap(p.getUniqueId())); saveData(); } catch(NumberFormatException ex){sender.sendMessage("/levelcap <Zahl> oder /levelcap <Spieler> <Zahl|reset>");} return true; }
            if(args.length==2) { OfflinePlayer target=Bukkit.getOfflinePlayer(args[0]); UUID id=target.getUniqueId(); if(args[1].equalsIgnoreCase("reset")) personalCaps.remove(id); else try { personalCaps.put(id,Math.max(0,Integer.parseInt(args[1]))); } catch(NumberFormatException ex){sender.sendMessage("Ungültiges Cap.");return true;} Player online=target.getPlayer(); if(online!=null && level(id)>cap(id)) setLevel(online,cap(id)); saveData(); sender.sendMessage("Persönliches Levelcap aktualisiert."); return true; }
            sender.sendMessage("/levelcap <Zahl> | /levelcap <Spieler> <Zahl|reset>"); return true;
        }
        if(c.equals("revivestar")) {
            if(args.length==0) { if(sender instanceof Player p) openReviveGui(p); else sender.sendMessage("/revivestar <Spieler>"); return true; }
            UUID id=Bukkit.getOfflinePlayer(args[0]).getUniqueId(); if(!banned.contains(id)) { sender.sendMessage("Dieser Spieler ist nicht durch LevelPvP gebannt."); return true; } revive(id); sender.sendMessage("Spieler entbannt."); return true;
        }
        if(c.equals("levelpvp")) { if(args.length==1 && args[0].equalsIgnoreCase("reload")) { reloadConfig(); globalCap=Math.max(0,getConfig().getInt("global-cap",globalCap)); for(Player online:Bukkit.getOnlinePlayers()) if(level(online.getUniqueId())>cap(online.getUniqueId())) setLevel(online,cap(online.getUniqueId())); saveData(); sender.sendMessage("LevelPvP-Konfiguration wurde neu geladen."); } else sender.sendMessage("/levelpvp reload"); return true; }
        return false;
    }
}
