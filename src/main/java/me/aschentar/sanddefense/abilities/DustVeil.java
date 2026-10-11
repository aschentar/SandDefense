package me.aschentar.sanddefense.abilities;

import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.SandAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DustVeil extends SandAbility implements AddonAbility {

    private static final Color DUST_COLOR = Color.fromRGB(218, 197, 139);
    private static final Particle.DustOptions DUST_OPTIONS = new Particle.DustOptions(DUST_COLOR, 2.0f);
    private static final BlockData SAND_DATA = Material.SAND.createBlockData();

    @Attribute(Attribute.COOLDOWN)
    private long cooldown = 8000;

    @Attribute(Attribute.DURATION)
    private long duration = 7000;

    private double durability = 140.0;
    private double projectileDrain = 15.0;
    private int blindnessTicks = 30;
    private int slownessTicks = 30;
    private int ticksElapsed = 0;

    private PotionEffect blindnessEffect;
    private PotionEffect slownessEffect;

    private Location centerLocation;
    private Location reusableLocation;
    private Vector[] offsets;
    private BoundingBox hitbox;
    private final Set<UUID> interceptedProjectiles = new HashSet<>();

    public DustVeil(Player player) {
        super(player);

        if (player == null) {
            return;
        }

        if (hasAbility(player, DustVeil.class)) {
            return;
        }

        if (!bPlayer.canBend(this) || !bPlayer.canSandbend()) {
            return;
        }

        setFields();

        Vector direction = player.getEyeLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.001) {
            direction = player.getLocation().getDirection().setY(0);
        }
        direction.normalize();

        Location calculatedCenter = player.getLocation().add(direction.clone().multiply(3.0));
        Block centerBlock = calculatedCenter.getBlock();
        if (centerBlock.getType().isSolid()) {
            calculatedCenter = player.getLocation().add(direction.clone().multiply(1.5));
        }

        if (RegionProtection.isRegionProtected(player, calculatedCenter, this)) {
            return;
        }

        this.centerLocation = calculatedCenter;
        this.reusableLocation = calculatedCenter.clone();

        Vector tangent = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        List<Vector> pointList = new ArrayList<>();

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        double[] horizontalSteps = {-4.0, -3.2, -2.4, -1.6, -0.8, 0.0, 0.8, 1.6, 2.4, 3.2, 4.0};
        double[] verticalSteps = {0.0, 0.7, 1.4, 2.1, 2.8, 3.5};

        for (double h : horizontalSteps) {
            double curveOffset = -(h * h) * 0.06;
            for (double v : verticalSteps) {
                Vector offset = tangent.clone().multiply(h)
                        .add(direction.clone().multiply(curveOffset))
                        .add(new Vector(0, v, 0));
                pointList.add(offset);

                double px = centerLocation.getX() + offset.getX();
                double py = centerLocation.getY() + offset.getY();
                double pz = centerLocation.getZ() + offset.getZ();

                if (px < minX) minX = px;
                if (px > maxX) maxX = px;
                if (py < minY) minY = py;
                if (py > maxY) maxY = py;
                if (pz < minZ) minZ = pz;
                if (pz > maxZ) maxZ = pz;
            }
        }

        this.offsets = pointList.toArray(new Vector[0]);
        this.hitbox = new BoundingBox(minX - 0.4, minY - 0.2, minZ - 0.4, maxX + 0.4, maxY + 0.2, maxZ + 0.4);

        start();
    }

    private void setFields() {
        String path = "ExtraAbilities." + getName() + ".";
        this.cooldown = ConfigManager.defaultConfig.get().getLong(path + "Cooldown", this.cooldown);
        this.duration = ConfigManager.defaultConfig.get().getLong(path + "Duration", this.duration);
        this.durability = ConfigManager.defaultConfig.get().getDouble(path + "Durability", this.durability);
        this.projectileDrain = ConfigManager.defaultConfig.get().getDouble(path + "ProjectileDrain", this.projectileDrain);
        this.blindnessTicks = ConfigManager.defaultConfig.get().getInt(path + "BlindnessTicks", this.blindnessTicks);
        this.slownessTicks = ConfigManager.defaultConfig.get().getInt(path + "SlownessTicks", this.slownessTicks);

        this.blindnessEffect = new PotionEffect(PotionEffectType.BLINDNESS, this.blindnessTicks, 0, false, false, true);
        this.slownessEffect = new PotionEffect(PotionEffectType.SLOWNESS, this.slownessTicks, 1, false, false, true);
    }

    @Override
    public void progress() {
        if (!player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (centerLocation == null || offsets == null || hitbox == null) {
            remove();
            return;
        }

        World world = centerLocation.getWorld();
        if (world == null || !world.isChunkLoaded(centerLocation.getBlockX() >> 4, centerLocation.getBlockZ() >> 4)) {
            remove();
            return;
        }

        durability -= 1.0;
        ticksElapsed++;

        if (durability <= 0 || ticksElapsed >= (duration / 50)) {
            remove();
            return;
        }

        for (Vector offset : offsets) {
            reusableLocation.set(
                    centerLocation.getX() + offset.getX(),
                    centerLocation.getY() + offset.getY(),
                    centerLocation.getZ() + offset.getZ()
            );
            world.spawnParticle(Particle.DUST, reusableLocation, 1, 0.05, 0.05, 0.05, 0.0, DUST_OPTIONS);
        }

        if (ticksElapsed % 3 == 0) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, centerLocation.getX(), centerLocation.getY() + 1.5, centerLocation.getZ(), 2, 1.2, 0.6, 1.2, 0.005);
        }

        if (ticksElapsed % 2 == 0) {
            for (Entity entity : world.getNearbyEntities(hitbox)) {
                if (entity instanceof Projectile projectile) {
                    if (projectile.getShooter() instanceof Entity shooter && shooter.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }

                    if (interceptedProjectiles.add(projectile.getUniqueId())) {
                        projectile.setVelocity(projectile.getVelocity().multiply(0.05).setY(-0.15));
                        world.spawnParticle(Particle.FALLING_DUST, projectile.getLocation(), 6, 0.1, 0.1, 0.1, 0.03, SAND_DATA);
                        world.playSound(projectile.getLocation(), Sound.BLOCK_SAND_HIT, 1.0f, 1.2f);
                        durability -= projectileDrain;
                    }
                } else if (entity instanceof LivingEntity living && !living.getUniqueId().equals(player.getUniqueId())) {
                    if (RegionProtection.isRegionProtected(player, living.getLocation(), this)) {
                        continue;
                    }

                    if (blindnessEffect != null) {
                        living.addPotionEffect(blindnessEffect);
                    }
                    if (slownessEffect != null) {
                        living.addPotionEffect(slownessEffect);
                    }
                }
            }
        }
    }

    @Override
    public void remove() {
        super.remove();
        interceptedProjectiles.clear();
        if (bPlayer != null) {
            bPlayer.addCooldown(this);
        }
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public Location getLocation() {
        return centerLocation != null ? centerLocation : (player != null ? player.getLocation() : null);
    }

    @Override
    public String getName() {
        return "DustVeil";
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public boolean isSneakAbility() {
        return false;
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
                "Raise an expansive curved wall of dense dust that intercepts projectiles and blinds enemies pushing through it."
        );
    }

    @Override
    public String getInstructions() {
        return ConfigManager.languageConfig.get().getString(
                "ExtraAbilities." + getName() + ".Instructions",
                "Left-click with DustVeil bound to erect a protective dust wall in front of you."
        );
    }

    @Override
    public void load() {
        String configPath = "ExtraAbilities." + getName() + ".";
        ConfigManager.defaultConfig.get().addDefault(configPath + "Cooldown", 8000L);
        ConfigManager.defaultConfig.get().addDefault(configPath + "Duration", 7000L);
        ConfigManager.defaultConfig.get().addDefault(configPath + "Durability", 140.0);
        ConfigManager.defaultConfig.get().addDefault(configPath + "ProjectileDrain", 15.0);
        ConfigManager.defaultConfig.get().addDefault(configPath + "BlindnessTicks", 30);
        ConfigManager.defaultConfig.get().addDefault(configPath + "SlownessTicks", 30);
        ConfigManager.defaultConfig.save();

        String langPath = "ExtraAbilities." + getName() + ".";
        ConfigManager.languageConfig.get().addDefault(langPath + "Description", "Raise an expansive curved wall of dense dust that intercepts projectiles and blinds enemies pushing through it.");
        ConfigManager.languageConfig.get().addDefault(langPath + "Instructions", "Left-click with DustVeil bound to erect a protective dust wall in front of you.");
        ConfigManager.languageConfig.save();
    }

    @Override
    public void stop() {
        interceptedProjectiles.clear();
    }
}