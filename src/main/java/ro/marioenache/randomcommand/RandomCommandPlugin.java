package ro.marioenache.randomcommand;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RandomCommandPlugin extends JavaPlugin {
    private FileConfiguration config;
    private List<String> allCommands;
    private Map<String, List<String>> commandGroups;
    private int minCommands;
    private int maxCommands;
    private boolean preventDuplicates;
    private boolean showCommandIdentifiers;
    private Random random;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Load configuration
        reloadPluginConfig();
        
        // Initialize random number generator
        random = new Random();
        
        getLogger().info("RandomCommandPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("RandomCommandPlugin has been disabled!");
    }
    
    private void reloadPluginConfig() {
        // Reload config from file
        reloadConfig();
        config = getConfig();
        
        // Load settings
        minCommands = config.getInt("settings.min-commands", 1);
        maxCommands = config.getInt("settings.max-commands", 1);
        preventDuplicates = config.getBoolean("settings.prevent-duplicates", true);
        showCommandIdentifiers = config.getBoolean("settings.show-command-identifiers", true);
        
        // Ensure min <= max
        if (minCommands > maxCommands) {
            minCommands = maxCommands;
            getLogger().warning("min-commands was greater than max-commands. Setting min-commands to " + maxCommands);
        }
        
        // Load commands from categorized structure
        loadCommands();
        
        if (allCommands.isEmpty()) {
            getLogger().warning("No commands found in config.yml!");
        }
    }
    
    private void loadCommands() {
        allCommands = new ArrayList<>();
        
        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        if (commandsSection == null) {
            getLogger().warning("No commands section found in config.yml!");
            return;
        }
        
        Set<String> categoryKeys = commandsSection.getKeys(false);
        for (String category : categoryKeys) {
            List<String> categoryCommands = commandsSection.getStringList(category);
            if (categoryCommands != null && !categoryCommands.isEmpty()) {
                allCommands.addAll(categoryCommands);
                getLogger().info("Loaded " + categoryCommands.size() + " commands from category '" + category + "'");
            }
        }
        
        getLogger().info("Total commands loaded: " + allCommands.size());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("randomcommand")) {
            // Check if sender has permission
            if (!sender.hasPermission("randomcommand.use")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
            
            // Check if args length is correct
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /randomcommand <player_name>");
                return false;
            }
            
            // Get player name from args
            String playerName = args[0];
            
            // Check if player exists
            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Player " + playerName + " is not online!");
                return true;
            }
            
            // Execute random commands asynchronously
            executeRandomCommandsAsync(sender, playerName);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("randomcommandreload")) {
            // Check if sender has permission
            if (!sender.hasPermission("randomcommand.reload")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
            
            // Reload configuration
            reloadPluginConfig();
            sender.sendMessage(ChatColor.GREEN + "RandomCommandPlugin configuration reloaded!");
            return true;
        }
        return false;
    }
    
    private void executeRandomCommandsAsync(CommandSender sender, String playerName) {
        // Run task asynchronously
        CompletableFuture.runAsync(() -> {
            List<String> selectedCommands = selectRandomCommands();
            if (selectedCommands.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No commands configured!");
                return;
            }
            
            // Execute the selected commands on the main thread
            Bukkit.getScheduler().runTask(this, () -> {
                executeCommands(sender, playerName, selectedCommands);
            });
        });
    }
    
    private List<String> selectRandomCommands() {
        if (allCommands.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Determine how many commands to execute
        int commandsToExecute = 0;
        if (minCommands == maxCommands) {
            commandsToExecute = minCommands;
        } else {
            commandsToExecute = random.nextInt(maxCommands - minCommands + 1) + minCommands;
        }
        
        // Make sure we don't try to execute more unique commands than exist
        if (preventDuplicates && commandsToExecute > allCommands.size()) {
            commandsToExecute = allCommands.size();
        }
        
        List<String> selectedCommands;
        
        if (preventDuplicates) {
            // Select unique random commands
            List<String> shuffledCommands = new ArrayList<>(allCommands);
            Collections.shuffle(shuffledCommands);
            selectedCommands = shuffledCommands.subList(0, commandsToExecute);
        } else {
            // Allow duplicate selections
            selectedCommands = new ArrayList<>();
            for (int i = 0; i < commandsToExecute; i++) {
                int index = random.nextInt(allCommands.size());
                selectedCommands.add(allCommands.get(index));
            }
        }
        
        return selectedCommands;
    }
    
    private void executeCommands(CommandSender sender, String playerName, List<String> selectedCommands) {
        // Execute the selected commands
        for (int i = 0; i < selectedCommands.size(); i++) {
            String command = selectedCommands.get(i);
            
            // Replace {player} placeholder with actual player name
            String processedCommand = command.replace("{player}", playerName);
            
            // Execute the command
            String logMessage = "Executing command: " + processedCommand;
            if (showCommandIdentifiers && selectedCommands.size() > 1) {
                logMessage = "[" + (i + 1) + "/" + selectedCommands.size() + "] " + logMessage;
                sender.sendMessage(ChatColor.YELLOW + "Executing command " + (i + 1) + ": " + ChatColor.WHITE + processedCommand);
            }
            
            getLogger().info(logMessage);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
        }
        
        sender.sendMessage(ChatColor.GREEN + "Executed " + selectedCommands.size() + " random command(s) on " + playerName);
    }
}