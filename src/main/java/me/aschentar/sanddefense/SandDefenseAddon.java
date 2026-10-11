package me.aschentar.sanddefense;

import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SandDefenseAddon extends JavaPlugin implements CommandExecutor {

    private static SandDefenseAddon instance;

    @Override
    public void onEnable() {
        instance = this;

        CoreAbility.registerPluginAbilities(this, "me.aschentar.sanddefense.abilities");
        getServer().getPluginManager().registerEvents(new SandDefenseListener(), this);

        if (getCommand("sanddefense") != null) {
            getCommand("sanddefense").setExecutor(this);
        }

        // Запуск проверки с отслеживанием
        Bukkit.getScheduler().runTaskLater(this, this::syncBendingGuiLanguage, 40L);

        getLogger().info("SandDefense addon has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SandDefense addon has been disabled.");
        instance = null;
    }

    public static SandDefenseAddon getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§e[SandDefense] Syncing descriptions with BendingGUI...");
        syncBendingGuiLanguage();
        sender.sendMessage("§a[SandDefense] Synchronization completed.");
        return true;
    }

    public void syncBendingGuiLanguage() {
        File pluginsDir = getDataFolder().getParentFile();
        if (pluginsDir == null || !pluginsDir.exists()) {
            pluginsDir = new File("plugins");
        }

        File guiLangFile = new File(pluginsDir, "BendingGUI/language.yml");
        getLogger().info("Looking for BendingGUI language file at: " + guiLangFile.getAbsolutePath());

        if (!guiLangFile.exists()) {
            getLogger().warning("BendingGUI language.yml was NOT found at: " + guiLangFile.getAbsolutePath());
            return;
        }

        try {
            YamlConfiguration guiConfig = YamlConfiguration.loadConfiguration(guiLangFile);
            boolean modified = false;

            Map<String, String> abilities = new LinkedHashMap<>();
            abilities.put("DuneDrift", "Harness sand currents to traverse terrain with rapid bursts and fluid dune surfing.");
            abilities.put("DustVeil", "Raise an expansive curved wall of dense dust that intercepts projectiles and blinds enemies pushing through it.");
            abilities.put("SandShield", "Condense sand into an omnidirectional protective dome or project a focused kinetic disc to deflect incoming assaults.");
            abilities.put("SandArmor", "Encase yourself in reinforced sand armor, granting increased movement speed and resistance to physical attacks.");

            for (Map.Entry<String, String> entry : abilities.entrySet()) {
                String path = "Abilities." + entry.getKey();
                String current = guiConfig.getString(path, "");

                if (!guiConfig.contains(path) || current.isBlank() || isOutdated(current)) {
                    guiConfig.set(path, entry.getValue());
                    getLogger().info("Updating " + entry.getKey() + " description in BendingGUI/language.yml");
                    modified = true;
                }
            }

            if (modified) {
                guiConfig.save(guiLangFile);
                getLogger().info("Successfully updated and saved BendingGUI/language.yml!");

                Plugin bendingGui = Bukkit.getPluginManager().getPlugin("BendingGUI");
                if (bendingGui != null && bendingGui.isEnabled()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gui reload");
                    getLogger().info("Executed 'gui reload' command.");
                }
            } else {
                getLogger().info("All ability descriptions in BendingGUI are already up-to-date.");
            }
        } catch (IOException e) {
            getLogger().warning("Error saving BendingGUI/language.yml: " + e.getMessage());
        }
    }

    private boolean isOutdated(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeBlock.of(text.charAt(i)).equals(Character.UnicodeBlock.CYRILLIC)) {
                return true;
            }
        }
        String lower = text.toLowerCase();
        return lower.contains("shift") || lower.contains("пкм") || lower.contains("лкм") || lower.contains("sneak");
    }
}