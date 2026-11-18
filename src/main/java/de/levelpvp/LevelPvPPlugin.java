package de.levelpvp;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import io.papermc.paper.ban.BanListType;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.util.*;

public final class LevelPvPPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID,Integer> level=new HashMap<>(), kills=new HashMap<>(), deaths=new HashMap<>(), personalCaps=new HashMap<>();
    private final Set<String> banned=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<UUID,List<ItemStack[]>> inbox=new HashMap<>();
    private YamlConfiguration store, banStore, deliveryStore;
    private File storeFile, banFile, deliveryFile;
    private NamespacedKey bottleTag, bottleAmount, reviveTag;
    private int globalCap;

    public void onEnable() {
        saveDefaultConfig(); globalCap=getConfig().getInt("global-level-cap",30);
        bottleTag=new NamespacedKey(this,"level_bottle"); bottleAmount=new NamespacedKey(this,"stored_levels");
        reviveTag=new NamespacedKey(this,"revive_star");
        loadData(); recipes();
        for(String c:List.of("stats","levelcap","levelpvp","extract","delivery")) getCommand(c).setExecutor(this);
        getServer().getPluginManager().registerEvents(this,this);
        Bukkit.getOnlinePlayers().forEach(this::syncHealth);
    }
    public void onDisable(){saveData();}
    private void loadData(){
        getDataFolder().mkdirs();
        storeFile=new File(getDataFolder(),"players.yml"); banFile=new File(getDataFolder(),"banned.yml"); deliveryFile=new File(getDataFolder(),"deliveries.yml");
        store=YamlConfiguration.loadConfiguration(storeFile); banStore=YamlConfiguration.loadConfiguration(banFile); deliveryStore=YamlConfiguration.loadConfiguration(deliveryFile);
        for(String s:store.getConfigurationSection("players")==null?Set.<String>of():store.getConfigurationSection("players").getKeys(false)){
            try{UUID u=UUID.fromString(s); String p="players."+s+"."; level.put(u,store.getInt(p+"level")); kills.put(u,store.getInt(p+"kills")); deaths.put(u,store.getInt(p+"deaths")); if(store.contains(p+"cap"))personalCaps.put(u,store.getInt(p+"cap"));}catch(Exception ignored){}
        }
        banned.addAll(banStore.getStringList("names"));
        var sec=deliveryStore.getConfigurationSection("inbox");
        if(sec!=null)for(String id:sec.getKeys(false))try{UUID u=UUID.fromString(id);List<ItemStack[]> l=new ArrayList<>();for(String k:sec.getConfigurationSection(id).getKeys(false)){Object o=deliveryStore.get("inbox."+id+"."+k);if(o instanceof ItemStack[] a)l.add(a);}inbox.put(u,l);}catch(Exception ignored){}
    }
    private void saveData(){
        store.set("players",null);
        Set<UUID> ids=new HashSet<>();ids.addAll(level.keySet());ids.addAll(kills.keySet());ids.addAll(deaths.keySet());ids.addAll(personalCaps.keySet());
        for(UUID u:ids){String p="players."+u+".";store.set(p+"level",lv(u));store.set(p+"kills",kills.getOrDefault(u,0));store.set(p+"deaths",deaths.getOrDefault(u,0));if(personalCaps.containsKey(u))store.set(p+"cap",personalCaps.get(u));}
        banStore.set("names",new ArrayList<>(banned));deliveryStore.set("inbox",null);
        inbox.forEach((u,list)->{for(int i=0;i<list.size();i++)deliveryStore.set("inbox."+u+"."+i,list.get(i));});
        save(store,storeFile);save(banStore,banFile);save(deliveryStore,deliveryFile);
    }
    private void save(YamlConfiguration y,File f){try{y.save(f);}catch(IOException e){getLogger().severe(e.getMessage());}}
    private int lv(UUID u){return level.getOrDefault(u,0);}
    private int cap(UUID u){return Math.min(globalCap,personalCaps.getOrDefault(u,globalCap));}
    private void set(Player p,int n){level.put(p.getUniqueId(),Math.max(-10,Math.min(cap(p.getUniqueId()),n)));syncHealth(p);saveData();}
    private double hearts(int n){if(n<=-10)return .5;if(n<0)return 10+n;if(n<=30)return 10+Math.floor(n/3.0);return 20+Math.floor((n-30)/2.0);}
    private void syncHealth(Player p){var a=p.getAttribute(Attribute.MAX_HEALTH);if(a!=null){double h=hearts(lv(p.getUniqueId()))*2;a.setBaseValue(Math.max(1,h));if(p.getHealth()>h)p.setHealth(h);}}
    private double xp(int n){return n<=0?1:n<=30?1+4.0*n/30:5+.25*(n-30);}
    private double dmg(int n){return n<=0?1:n<=30?1+n/30.0:2+.05*(n-30);}
    private double resist(int n){return n<=0?0:n<=30?.05*n/30:Math.min(.9,.05+.0025*(n-30));}
    private ItemStack bottle(int count){ItemStack i=new ItemStack(Material.EXPERIENCE_BOTTLE);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.LIGHT_PURPLE+"Level in a Bottle");m.setLore(List.of(ChatColor.GRAY+"Enthält "+count+" Level",ChatColor.YELLOW+"Rechtsklick zum Einlösen"));m.getPersistentDataContainer().set(bottleTag,PersistentDataType.BYTE,(byte)1);m.getPersistentDataContainer().set(bottleAmount,PersistentDataType.INTEGER,count);i.setItemMeta(m);return i;}
    private ItemStack revive(){ItemStack i=new ItemStack(Material.NETHER_STAR);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.AQUA+"ReviveStar");m.setLore(List.of(ChatColor.GRAY+"Klicke, um gebannte Spieler zu entbannen"));m.getPersistentDataContainer().set(reviveTag,PersistentDataType.BYTE,(byte)1);i.setItemMeta(m);return i;}
    private boolean tagged(ItemStack i,NamespacedKey k){return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(k,PersistentDataType.BYTE);}
    private void recipes(){
        ShapedRecipe b=new ShapedRecipe(new NamespacedKey(this,"level_in_a_bottle"),bottle(1));b.shape("GDG","DAD","GDG");b.setIngredient('G',Material.GOLD_BLOCK);b.setIngredient('D',Material.DIAMOND);b.setIngredient('A',Material.GOLDEN_APPLE);Bukkit.addRecipe(b);
        ShapedRecipe r=new ShapedRecipe(new NamespacedKey(this,"revivestar"),revive());r.shape("DAD","ANA","DAD");r.setIngredient('D',Material.DIAMOND_BLOCK);r.setIngredient('A',Material.NETHERITE_SCRAP);r.setIngredient('N',Material.NETHER_STAR);Bukkit.addRecipe(r);
    }
    @EventHandler public void join(PlayerJoinEvent e){syncHealth(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){
        Player v=e.getEntity(),k=v.getKiller();UUID id=v.getUniqueId();deaths.merge(id,1,Integer::sum);
        if(k!=null){kills.merge(k.getUniqueId(),1,Integer::sum);set(k,lv(k.getUniqueId())+1);
            if(lv(id)<=-10){banned.add(v.getName());Bukkit.getBanList(BanListType.PROFILE).addBan(Bukkit.createProfile(v.getUniqueId()),"LevelPvP: Level -10",(java.util.Date)null,"LevelPvP");e.setDeathMessage(ChatColor.RED+v.getName()+" wurde bei Level -10 gebannt.");}
            else level.put(id,Math.max(-10,lv(id)-1));
        }
        Bukkit.getScheduler().runTask(this,()->syncHealth(v));saveData();
    }
    @EventHandler public void exp(PlayerExpChangeEvent e){e.setAmount((int)Math.round(e.getAmount()*xp(lv(e.getPlayer().getUniqueId()))));}
    @EventHandler public void damage(EntityDamageByEntityEvent e){
        if(!(e.getEntity() instanceof Player v))return;Player a=null;
        if(e.getDamager() instanceof Player p)a=p;
        else if(e.getDamager() instanceof org.bukkit.entity.Projectile pr&&pr.getShooter() instanceof Player p)a=p;
        if(a!=null)e.setDamage(e.getDamage()*dmg(lv(a.getUniqueId()))*(1-resist(lv(v.getUniqueId()))));
    }
    @EventHandler public void interact(PlayerInteractEvent e){
        if(!e.getAction().isRightClick())return;ItemStack i=e.getItem();Player p=e.getPlayer();
        if(tagged(i,bottleTag)){e.setCancelled(true);int n=lv(p.getUniqueId());if(n>=0){p.sendMessage(ChatColor.RED+"Du kannst das Item nicht einsetzen, dein Level ist höher als -1.");return;}
            int amount=i.getItemMeta().getPersistentDataContainer().getOrDefault(bottleAmount,PersistentDataType.INTEGER,1);
            if(n+amount>cap(p.getUniqueId())){p.sendMessage(ChatColor.RED+"Damit würdest du dein Level-Limit überschreiten.");return;}
            set(p,n+amount);consume(p,i);p.sendMessage(ChatColor.GREEN+"Du hast "+amount+" Level erhalten.");
        }else if(tagged(i,reviveTag)){e.setCancelled(true);openRevive(p);}
    }
    private void consume(Player p,ItemStack i){if(i.getAmount()>1)i.setAmount(i.getAmount()-1);else if(p.getInventory().getItemInMainHand().isSimilar(i))p.getInventory().setItemInMainHand(null);else p.getInventory().setItemInOffHand(null);}
    private int number(String s){try{return Integer.parseInt(s);}catch(Exception ex){return Integer.MIN_VALUE;}}
    public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        switch(c.getName()){
            case "stats"->{if(!(s instanceof Player p)){s.sendMessage("Nur Spieler.");return true;}int n=lv(p.getUniqueId());p.sendMessage(ChatColor.LIGHT_PURPLE+"Level: "+n+" | Kills: "+kills.getOrDefault(p.getUniqueId(),0)+" | Tode: "+deaths.getOrDefault(p.getUniqueId(),0));p.sendMessage("Herzen: "+hearts(n)+" | XP: "+String.format("%.2fx",xp(n))+" | Schaden: "+String.format("%.2fx",dmg(n))+" | Resistenz: "+String.format("%.2f%%",resist(n)*100));return true;}
            case "levelcap"->{if(!s.hasPermission("levelpvp.admin"))return true;if(a.length==2&&a[0].equalsIgnoreCase("global")){int n=number(a[1]);if(n<1){s.sendMessage("Ungültiges Limit.");return true;}globalCap=n;getConfig().set("global-level-cap",n);saveConfig();for(Player p:Bukkit.getOnlinePlayers())if(lv(p.getUniqueId())>cap(p.getUniqueId()))set(p,cap(p.getUniqueId()));s.sendMessage("Globales Limit: "+n);return true;}if(a.length==3&&a[0].equalsIgnoreCase("player")){Player p=Bukkit.getPlayerExact(a[1]);int n=number(a[2]);if(p==null||n<1){s.sendMessage("Spieler muss online sein und Limit gültig.");return true;}personalCaps.put(p.getUniqueId(),n);if(lv(p.getUniqueId())>cap(p.getUniqueId()))set(p,cap(p.getUniqueId()));saveData();s.sendMessage("Persönliches Limit gesetzt.");return true;}s.sendMessage("/levelcap global <Level> | /levelcap player <Spieler> <Level>");return true;}
            case "levelpvp"->{if(!s.hasPermission("levelpvp.admin"))return true;if(a.length==2&&a[0].equalsIgnoreCase("unban")){Bukkit.getBanList(BanListType.PROFILE).pardon(Bukkit.createProfile(a[1]));banned.remove(a[1]);saveData();s.sendMessage(a[1]+" wurde entbannt.");return true;}s.sendMessage("/levelpvp unban <Spieler>");return true;}
            case "extract"->{if(!(s instanceof Player p)||a.length!=1)return true;int n=number(a[0]);if(n<=0||lv(p.getUniqueId())-n< -10){p.sendMessage("Ungültige Anzahl.");return true;}set(p,lv(p.getUniqueId())-n);Map<Integer,ItemStack> rest=p.getInventory().addItem(bottle(n));rest.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x));return true;}
            case "delivery"->{if(s instanceof Player p)deliveryHome(p);return true;}
        }return false;
    }
    private Inventory menu(int size,String title,String type){Inventory i=Bukkit.createInventory(new Holder(type,null),size,title);return i;}
    private ItemStack named(Material m,String name){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();x.setDisplayName(name);i.setItemMeta(x);return i;}
    private void openRevive(Player p){Inventory i=menu(54,"ReviveStar – Gebannte Spieler","revive");int slot=0;for(String n:banned){if(slot==54)break;ItemStack h=new ItemStack(Material.PLAYER_HEAD);SkullMeta m=(SkullMeta)h.getItemMeta();m.setOwningPlayer(Bukkit.getOfflinePlayer(n));m.setDisplayName(ChatColor.YELLOW+n);h.setItemMeta(m);i.setItem(slot++,h);}if(slot==0)i.setItem(22,named(Material.BARRIER,"Keine gebannten Spieler"));p.openInventory(i);}
    private void deliveryHome(Player p){Inventory i=menu(27,"Delivery","home");i.setItem(11,named(Material.CHEST,"Lieferungen abholen"));i.setItem(15,named(Material.OAK_SIGN,"Etwas versenden"));p.openInventory(i);}
    private void recipients(Player p){Inventory i=menu(54,"Empfänger auswählen","recipients");int s=0;for(OfflinePlayer o:Bukkit.getOfflinePlayers()){if(o.getName()==null||o.getUniqueId().equals(p.getUniqueId())||s>=54)continue;ItemStack h=new ItemStack(Material.PLAYER_HEAD);SkullMeta m=(SkullMeta)h.getItemMeta();m.setOwningPlayer(o);m.setDisplayName(o.getName());h.setItemMeta(m);i.setItem(s++,h);}p.openInventory(i);}
    private void inbox(Player p){List<ItemStack[]> l=inbox.get(p.getUniqueId());if(l==null||l.isEmpty()){p.sendMessage("Du hast keine Lieferungen.");return;}Inventory i=menu(27,"Deine Lieferungen","inbox");i.setItem(13,named(Material.CHEST,"Alle Lieferungen abholen"));p.openInventory(i);}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;
        if(h.type.equals("send")){if(e.isShiftClick())e.setCancelled(true);return;}
        e.setCancelled(true);if(e.getClickedInventory()!=e.getView().getTopInventory()||e.getCurrentItem()==null)return;ItemStack i=e.getCurrentItem();
        if(h.type.equals("revive")&&i.getType()==Material.PLAYER_HEAD){String n=ChatColor.stripColor(i.getItemMeta().getDisplayName());Bukkit.getBanList(BanListType.PROFILE).pardon(Bukkit.createProfile(n));banned.remove(n);saveData();p.sendMessage(n+" wurde entbannt.");p.closeInventory();ItemStack main=p.getInventory().getItemInMainHand();ItemStack off=p.getInventory().getItemInOffHand();if(tagged(main,reviveTag))consume(p,main);else if(tagged(off,reviveTag))consume(p,off);}
        if(h.type.equals("home")){if(i.getType()==Material.OAK_SIGN)recipients(p);if(i.getType()==Material.CHEST)inbox(p);}
        if(h.type.equals("recipients")&&i.getType()==Material.PLAYER_HEAD){String n=ChatColor.stripColor(i.getItemMeta().getDisplayName());UUID id=Bukkit.getOfflinePlayer(n).getUniqueId();p.openInventory(Bukkit.createInventory(new Holder("send",id),27,"Items hineinlegen"));}
        if(h.type.equals("inbox")){List<ItemStack[]> l=inbox.remove(p.getUniqueId());if(l!=null)for(ItemStack[] a:l){Map<Integer,ItemStack> rem=p.getInventory().addItem(a);rem.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x));}saveData();p.closeInventory();}
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder h&&!h.type.equals("send"))e.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent e){if(!(e.getPlayer() instanceof Player p)||!(e.getView().getTopInventory().getHolder() instanceof Holder h)||!h.type.equals("send"))return;inbox.computeIfAbsent(h.recipient,k->new ArrayList<>()).add(e.getView().getTopInventory().getContents().clone());saveData();OfflinePlayer o=Bukkit.getOfflinePlayer(h.recipient);p.sendMessage("Lieferung gespeichert.");if(o.isOnline()&&o.getPlayer()!=null)o.getPlayer().sendMessage(p.getName()+" hat dir etwas gesendet.");}
    private static final class Holder implements InventoryHolder{final String type;final UUID recipient;Holder(String t,UUID r){type=t;recipient=r;}public Inventory getInventory(){return null;}}
}
