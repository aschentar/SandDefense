package me.aschentar.sanddefense.abilities;

import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.SandAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.earthbending.EarthArmor;
import com.projectkorra.projectkorra.util.TempArmor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SandArmor extends SandAbility implements AddonAbility {

    private static final Color SAND_ARMOR_COLOR = Color.fromRGB(218, 197, 139);
    private static final BlockData SAND_DATA = Material.SAND.createBlockData();

    @Attribute(Attribute.COOLDOWN)
    private long cooldown = 20000;

    @Attribute(Attribute.DURATION)
    private long duration = 30000;

    private int speedAmplifier = 0;
    private int resistanceAmplifier = 0;
    private PotionEffect speedEffect;
    private PotionEffect resistanceEffect;

    private TempArmor tempArmor;
    private int tickCounter = 0;

    public SandArmor(Player player) {
        super(player);

        if (player == null) {
            return;
        }

        if (!bPlayer.canBendIgnoreCooldowns(this) || !bPlayer.canSandbend()) {
            return;
        }

        if (hasAbility(player, SandArmor.class)) {
            return;
        }

        EarthArmor earthArmor = getAbility(player, EarthArmor.class);
        if (earthArmor != null) {
            earthArmor.remove();
        }

        setFields();
        applySandArmor();
        start();
    }

    private void setFields() {
        String path = "ExtraAbilities." + getName() + ".";
        this.cooldown = ConfigManager.defaultConfig.get().getLong(path + "Cooldown", this.cooldown);
        this.duration = ConfigManager.defaultConfig.get().getLong(path + "Duration", this.duration);
        this.speedAmplifier = ConfigManager.defaultConfig.get().getInt(path + "SpeedAmplifier", this.speedAmplifier);
        this.resistanceAmplifier = ConfigManager.defaultConfig.get().getInt(path + "ResistanceAmplifier", this.resistanceAmplifier);

        this.speedEffect = new PotionEffect(PotionEffectType.SPEED, 40, this.speedAmplifier, false, false, true);
        this.resistanceEffect = new PotionEffect(PotionEffectType.RESISTANCE, 40, this.resistanceAmplifier, false, false, true);
    }

    private void applySandArmor() {
        ItemStack boots = createColoredPiece(Material.LEATHER_BOOTS);
        ItemStack leggings = createColoredPiece(Material.LEATHER_LEGGINGS);
        ItemStack chestplate = createColoredPiece(Material.LEATHER_CHESTPLATE);
        ItemStack helmet = createColoredPiece(Material.LEATHER_HELMET);

        ItemStack[] armorPieces = new ItemStack[]{boots, leggings, chestplate, helmet};
        this.tempArmor = new TempArmor(player, 0L, this, armorPieces);
    }

    private ItemStack createColoredPiece(Material material) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(SAND_ARMOR_COLOR);
            meta.setDisplayName("§eSand Armor");
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void progress() {
        if (!player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (System.currentTimeMillis() - getStartTime() > this.duration) {
            remove();
            return;
        }

        this.tickCounter++;

        if (this.tickCounter % 20 == 0) {
            if (this.speedEffect != null) {
                player.addPotionEffect(this.speedEffect);
            }
            if (this.resistanceEffect != null) {
                player.addPotionEffect(this.resistanceEffect);
            }
        }

        if (this.tickCounter % 3 == 0) {
            player.getWorld().spawnParticle(
                    Particle.FALLING_DUST,
                    player.getX(),
                    player.getY() + 1.0,
                    player.getZ(),
                    3,
                    0.25, 0.4, 0.25,
                    0.0,
                    SAND_DATA
            );
        }
    }

    @Override
    public void remove() {
        if (isRemoved()) {
            return;
        }

        super.remove();

        if (this.tempArmor != null) {
            TempArmor armorToRevert = this.tempArmor;
            this.tempArmor = null;
            armorToRevert.revert();
        }

        if (player.isOnline()) {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.RESISTANCE);
        }

        if (bPlayer != null) {
            bPlayer.addCooldown(this, this.cooldown);
        }
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public Location getLocation() {
        return player != null ? player.getLocation() : null;
    }

    @Override
    public String getName() {
        return "SandArmor";
    }

    @Override
    public boolean isHiddenAbility() {
        return false;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public String getAuthor() {
        return "Aschentar";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return ConfigManager.languageConfig.get().getString(
                "ExtraAbilities." + getName() + ".Description",
                "Encase yourself in reinforced sand armor, granting increased movement speed and resistance to physical attacks."
        );
    }

    @Override
    public String getInstructions() {
        return ConfigManager.languageConfig.get().getString(
                "ExtraAbilities." + getName() + ".Instructions",
                "Sneak (Shift) while standing on sand or sandstone to equip the armor."
        );
    }

    @Override
    public void load() {
        String configPath = "ExtraAbilities." + getName() + ".";
        ConfigManager.defaultConfig.get().addDefault(configPath + "Cooldown", 20000L);
        ConfigManager.defaultConfig.get().addDefault(configPath + "Duration", 30000L);
        ConfigManager.defaultConfig.get().addDefault(configPath + "SpeedAmplifier", 0);
        ConfigManager.defaultConfig.get().addDefault(configPath + "ResistanceAmplifier", 0);
        ConfigManager.defaultConfig.save();

        String langPath = "ExtraAbilities." + getName() + ".";
        ConfigManager.languageConfig.get().addDefault(langPath + "Description", "Encase yourself in reinforced sand armor, granting increased movement speed and resistance to physical attacks.");
        ConfigManager.languageConfig.get().addDefault(langPath + "Instructions", "Sneak (Shift) while standing on sand or sandstone to equip the armor.");
        ConfigManager.languageConfig.save();
    }

    @Override
    public void stop() {
        if (this.tempArmor != null) {
            TempArmor armorToRevert = this.tempArmor;
            this.tempArmor = null;
            armorToRevert.revert();
        }
    }
}