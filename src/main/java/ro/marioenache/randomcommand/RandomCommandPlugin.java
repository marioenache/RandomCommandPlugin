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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RandomCommandPlugin extends JavaPlugin {
    private FileConfiguration config;
    private List<String> allCommands;
    private Map<String, List<String>> commandCategories;
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
        commandCategories = new HashMap<>();

        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        if (commandsSection == null) {
            getLogger().warning("No commands section found in config.yml!");
            return;
        }

        Set<String> categoryKeys = commandsSection.getKeys(false);
        for (String category : categoryKeys) {
            List<String> categoryCommands = commandsSection.getStringList(category);
            if (categoryCommands != null && !categoryCommands.isEmpty()) {
                // Store commands for this category
                commandCategories.put(category.toLowerCase(), new ArrayList<>(categoryCommands));
                // Add to all commands list
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
                sender.sendMessage(ChatColor.RED + "Usage: /randomcommand <player_name> or /randomcommand <category> <player_name>");
                return false;
            }

            if (args.length == 1) {
                // Single argument format: /randomcommand <player_name>
                String playerName = args[0];

                // Check if player exists
                Player targetPlayer = Bukkit.getPlayer(playerName);
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player " + playerName + " is not online!");
                    return true;
                }

                // Execute random commands asynchronously from all categories
                executeRandomCommandsAsync(sender, playerName, null);
                return true;
            } else {
                // Two argument format: /randomcommand <category> <player_name>
                String category = args[0].toLowerCase();
                String playerName = args[1];

                // Check if player exists
                Player targetPlayer = Bukkit.getPlayer(playerName);
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player " + playerName + " is not online!");
                    return true;
                }

                // Check if category exists
                if (!commandCategories.containsKey(category)) {
                    sender.sendMessage(ChatColor.RED + "Category '" + category + "' does not exist!");
                    sender.sendMessage(ChatColor.YELLOW + "Available categories: " + String.join(", ", commandCategories.keySet()));
                    return true;
                }

                // Execute random commands asynchronously from the specified category
                executeRandomCommandsAsync(sender, playerName, category);
                return true;
            }
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

    private void executeRandomCommandsAsync(CommandSender sender, String playerName, String category) {
        // Run task asynchronously
        CompletableFuture.runAsync(() -> {
            List<String> selectedCommands;

            if (category == null) {
                // Select from all commands
                selectedCommands = selectRandomCommands(allCommands);
            } else {
                // Select from specific category
                List<String> categoryCommands = commandCategories.get(category);
                selectedCommands = selectRandomCommands(categoryCommands);
            }

            if (selectedCommands.isEmpty()) {
                if (category == null) {
                    sender.sendMessage(ChatColor.RED + "No commands configured!");
                } else {
                    sender.sendMessage(ChatColor.RED + "No commands configured for category '" + category + "'!");
                }
                return;
            }

            // Execute the selected commands on the main thread
            Bukkit.getScheduler().runTask(this, () -> {
                executeCommands(sender, playerName, selectedCommands, category);
            });
        });
    }

    private List<String> selectRandomCommands(List<String> commandPool) {
        if (commandPool == null || commandPool.isEmpty()) {
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
        if (preventDuplicates && commandsToExecute > commandPool.size()) {
            commandsToExecute = commandPool.size();
        }

        List<String> selectedCommands;

        if (preventDuplicates) {
            // Select unique random commands
            List<String> shuffledCommands = new ArrayList<>(commandPool);
            Collections.shuffle(shuffledCommands);
            selectedCommands = shuffledCommands.subList(0, commandsToExecute);
        } else {
            // Allow duplicate selections
            selectedCommands = new ArrayList<>();
            for (int i = 0; i < commandsToExecute; i++) {
                int index = random.nextInt(commandPool.size());
                selectedCommands.add(commandPool.get(index));
            }
        }

        return selectedCommands;
    }

    private void executeCommands(CommandSender sender, String playerName, List<String> selectedCommands, String category) {
        // Execute the selected commands
        for (int i = 0; i < selectedCommands.size(); i++) {
            String command = selectedCommands.get(i);

            // Replace {player} placeholder with actual player name
            String processedCommand = command.replace("{player}", playerName);

            // Execute the command with debug information
            String logMessage = "Executing command: " + processedCommand;
            if (showCommandIdentifiers && selectedCommands.size() > 1) {
                logMessage = "[" + (i + 1) + "/" + selectedCommands.size() + "] " + logMessage;
                sender.sendMessage(ChatColor.YELLOW + "Executing command " + (i + 1) + ": " + ChatColor.WHITE + processedCommand);
            }

            // Log detailed debug information
            getLogger().info("DEBUG: " + logMessage);
            getLogger().info("DEBUG: Original command: " + command);
            getLogger().info("DEBUG: Processed command: " + processedCommand);

            try {
                // Try to execute the command
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);

                if (!success) {
                    getLogger().warning("Command execution failed! Command: " + processedCommand);
                    sender.sendMessage(ChatColor.RED + "Command execution failed: " + processedCommand);
                }
            } catch (Exception e) {
                // Log any exceptions that occur during command execution
                getLogger().severe("Error executing command: " + processedCommand);
                getLogger().severe("Exception: " + e.getMessage());
                e.printStackTrace();

                // Notify the sender about the error
                sender.sendMessage(ChatColor.RED + "Error executing command: " + processedCommand);
                sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
            }
        }

        if (category == null) {
            sender.sendMessage(ChatColor.GREEN + "Executed " + selectedCommands.size() + " random command(s) on " + playerName);
        } else {
            sender.sendMessage(ChatColor.GREEN + "Executed " + selectedCommands.size() + " random '" + category + "' command(s) on " + playerName);
        }
    }
}