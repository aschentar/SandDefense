package me.aschentar.sanddefense.abilities;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.SandAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SandShield extends SandAbility implements AddonAbility {

    public enum Mode {
        DOME,
        DISC
    }

    private static final BlockData SAND_DATA = Material.SAND.createBlockData();
    private static final Color DUST_COLOR = Color.fromRGB(218, 197, 139);
    private static final Particle.DustOptions DOME_DUST_OPTIONS = new Particle.DustOptions(DUST_COLOR, 1.6f);
    private static final Particle.DustOptions SWIRL_DUST_OPTIONS = new Particle.DustOptions(Color.fromRGB(235, 215, 160), 1.0f);

    private static final PotionEffect BLINDNESS_EFFECT = new PotionEffect(PotionEffectType.BLINDNESS, 45, 0, false, false, true);
    private static final PotionEffect SLOWNESS_EFFECT = new PotionEffect(PotionEffectType.SLOWNESS, 45, 1, false, false, true);
    private static final PotionEffect CASTER_SLOW = new PotionEffect(PotionEffectType.SLOWNESS, 10, 1, false, false, false);

    private static final Set<Material> VALID_EARTH_MATERIALS = EnumSet.of(
            Material.SAND, Material.RED_SAND, Material.SUSPICIOUS_SAND,
            Material.SANDSTONE, Material.RED_SANDSTONE, Material.SMOOTH_SANDSTONE,
            Material.SMOOTH_RED_SANDSTONE, Material.CHISELED_SANDSTONE, Material.CHISELED_RED_SANDSTONE,
            Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.GRASS_BLOCK,
            Material.PODZOL, Material.MYCELIUM, Material.MUD, Material.CLAY, Material.GRAVEL,
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.DEEPSLATE, Material.TUFF, Material.CALCITE
    );

    @Attribute(Attribute.COOLDOWN)
    private long cooldown = 7500;

    @Attribute("DomeDuration")
    private long domeDuration = 6000;

    @Attribute("DiscDuration")
    private long discDuration = 1600;

    @Attribute("DomeRadius")
    private double domeRadius = 2.8;

    @Attribute("DiscRadius")
    private double discRadius = 1.8;

    @Attribute(Attribute.DAMAGE)
    private double damage = 1.0;

    @Attribute(Attribute.KNOCKBACK)
    private double knockback = 0.42;

    private Mode currentMode;
    private int tickCounter = 0;
    private double rotationPhase = 0.0;

    private final Location reusableCenterLoc = new Location(null, 0, 0, 0);
    private final Location reusableParticleLoc = new Location(null, 0, 0, 0);
    private final Vector reusableDirection = new Vector();
    private final Vector reusableRightVector = new Vector();
    private final Vector reusableUpVector = new Vector();
    private final Vector reusableVelocity = new Vector();
    private final Map<UUID, Long> lastDamageTimes = new HashMap<>();

    public SandShield(Player player) {
        this(player, false);
    }

    public SandShield(Player player, boolean isClick) {
        super(player);

        if (player == null) {
            return;
        }

        if (!bPlayer.canBend(this) || !bPlayer.canSandbend()) {
            return;
        }

        SandShield activeShield = getAbility(player, SandShield.class);
        if (activeShield != null) {
            if (isClick && activeShield.currentMode == Mode.DOME) {
                activeShield.remove();
            }
            return;
        }

        setFields();

        player.getLocation(this.reusableCenterLoc);
        if (!hasEarthSource(this.reusableCenterLoc)) {
            return;
        }

        if (RegionProtection.isRegionProtected(player, this.reusableCenterLoc, this)) {
            return;
        }

        this.currentMode = isClick ? Mode.DISC : Mode.DOME;
        this.reusableParticleLoc.setWorld(this.reusableCenterLoc.getWorld());

        World world = this.reusableCenterLoc.getWorld();
        if (world != null) {
            if (this.currentMode == Mode.DOME) {
                world.playSound(this.reusableCenterLoc, Sound.BLOCK_SAND_BREAK, 1.2f, 0.6f);
                world.playSound(this.reusableCenterLoc, Sound.ENTITY_BREEZE_WIND_BURST, 0.9f, 0.8f);
            } else {
                world.playSound(this.reusableCenterLoc, Sound.BLOCK_SAND_PLACE, 1.1f, 1.4f);
                world.playSound(this.reusableCenterLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
            }
        }

        start();
    }

    private void setFields() {
        String path = "ExtraAbilities." + getName() + ".";
        this.cooldown = ConfigManager.defaultConfig.get().getLong(path + "Cooldown", this.cooldown);
        this.domeDuration = ConfigManager.defaultConfig.get().getLong(path + "DomeDuration", this.domeDuration);
        this.discDuration = ConfigManager.defaultConfig.get().getLong(path + "DiscDuration", this.discDuration);
        this.domeRadius = ConfigManager.defaultConfig.get().getDouble(path + "DomeRadius", this.domeRadius);
        this.discRadius = ConfigManager.defaultConfig.get().getDouble(path + "DiscRadius", this.discRadius);
        this.damage = ConfigManager.defaultConfig.get().getDouble(path + "Damage", this.damage);
        this.knockback = ConfigManager.defaultConfig.get().getDouble(path + "Knockback", this.knockback);
    }

    private boolean isEarthBlock(Block block) {
        return block != null && (VALID_EARTH_MATERIALS.contains(block.getType()) || TempBlock.isTempBlock(block));
    }

    private boolean hasEarthSource(Location center) {
        Block feet = center.getBlock();
        Block under = feet.getRelative(BlockFace.DOWN);
        if (isEarthBlock(feet) || isEarthBlock(under)) {
            return true;
        }

        World world = center.getWorld();
        if (world == null) {
            return false;
        }

        RayTraceResult hit = world.rayTraceBlocks(center, new Vector(0, -1, 0), 3.0, FluidCollisionMode.NEVER, true);
        return hit != null && isEarthBlock(hit.getHitBlock());
    }

    @Override
    public void progress() {
        if (!player.isOnline() || player.isDead() || !bPlayer.canBend(this)) {
            remove();
            return;
        }

        long elapsed = System.currentTimeMillis() - getStartTime();

        if (this.currentMode == Mode.DOME) {
            if (!player.isSneaking() || elapsed > this.domeDuration) {
                remove();
                return;
            }
            player.addPotionEffect(CASTER_SLOW);
        } else {
            if (elapsed > this.discDuration) {
                remove();
                return;
            }
        }

        player.getLocation(this.reusableCenterLoc);
        World world = this.reusableCenterLoc.getWorld();
        if (world == null) {
            remove();
            return;
        }

        int chunkX = this.reusableCenterLoc.getBlockX() >> 4;
        int chunkZ = this.reusableCenterLoc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            remove();
            return;
        }

        if (RegionProtection.isRegionProtected(player, this.reusableCenterLoc, this)) {
            remove();
            return;
        }

        this.tickCounter++;
        this.rotationPhase = (this.rotationPhase + Math.toRadians(18.0)) % (Math.PI * 2.0);

        if (this.currentMode == Mode.DOME) {
            progressDome();
        } else {
            progressDisc();
        }
    }

    private void progressDome() {
        World world = this.reusableCenterLoc.getWorld();
        double cx = this.reusableCenterLoc.getX();
        double cy = this.reusableCenterLoc.getY();
        double cz = this.reusableCenterLoc.getZ();

        double[] heightSteps = {0.1, 0.6, 1.2, 1.8, 2.3, 2.7};
        int[] pointsPerStep = {16, 14, 12, 10, 8, 4};

        for (int i = 0; i < heightSteps.length; i++) {
            double h = heightSteps[i];
            if (h >= this.domeRadius) {
                continue;
            }

            double ringRadius = Math.sqrt(Math.max(0.0, (this.domeRadius * this.domeRadius) - (h * h)));
            int count = pointsPerStep[i];
            double angleStep = (Math.PI * 2.0) / count;
            double ringRotation = this.rotationPhase * ((i % 2 == 0) ? 1.0 : -1.0);

            for (int p = 0; p < count; p++) {
                double angle = ringRotation + (p * angleStep);
                double px = cx + ringRadius * Math.cos(angle);
                double pz = cz + ringRadius * Math.sin(angle);

                this.reusableParticleLoc.set(px, cy + h, pz);
                world.spawnParticle(Particle.DUST, this.reusableParticleLoc, 1, 0.02, 0.02, 0.02, 0.0, DOME_DUST_OPTIONS);
            }
        }

        this.reusableParticleLoc.set(cx, cy + this.domeRadius, cz);
        world.spawnParticle(Particle.DUST, this.reusableParticleLoc, 2, 0.08, 0.02, 0.08, 0.0, DOME_DUST_OPTIONS);

        if (this.tickCounter % 2 == 0) {
            double swirlAngle = -this.rotationPhase * 2.2;
            double swirlRadius = this.domeRadius * 0.95;
            this.reusableParticleLoc.set(
                    cx + swirlRadius * Math.cos(swirlAngle),
                    cy + 0.3,
                    cz + swirlRadius * Math.sin(swirlAngle)
            );
            world.spawnParticle(Particle.DUST, this.reusableParticleLoc, 1, 0.03, 0.03, 0.03, 0.0, SWIRL_DUST_OPTIONS);
        }

        if (this.tickCounter % 14 == 0) {
            world.playSound(this.reusableCenterLoc, Sound.BLOCK_SAND_STEP, 0.65f, 1.15f);
        }

        interceptDomeProjectiles(cx, cy, cz);
        applyDomeCombat(cx, cy, cz);
    }

    private void interceptDomeProjectiles(double cx, double cy, double cz) {
        World world = this.reusableCenterLoc.getWorld();
        double radiusSq = (this.domeRadius + 0.35) * (this.domeRadius + 0.35);

        for (Entity entity : world.getNearbyEntities(this.reusableCenterLoc, this.domeRadius + 0.8, this.domeRadius + 0.8, this.domeRadius + 0.8)) {
            if (!(entity instanceof Projectile projectile)) {
                continue;
            }

            if (projectile.getShooter() != null && projectile.getShooter().equals(player)) {
                continue;
            }

            double dx = projectile.getX() - cx;
            double dy = projectile.getY() - cy;
            double dz = projectile.getZ() - cz;

            if (dy >= -0.2 && dy <= (this.domeRadius + 0.4)) {
                if ((dx * dx + dy * dy + dz * dz) <= radiusSq) {
                    Location pLoc = projectile.getLocation();
                    world.spawnParticle(Particle.FALLING_DUST, pLoc, 6, 0.1, 0.1, 0.1, 0.04, SAND_DATA);
                    world.playSound(pLoc, Sound.BLOCK_SAND_HIT, 1.0f, 1.2f);
                    projectile.remove();
                }
            }
        }
    }

    private void applyDomeCombat(double cx, double cy, double cz) {
        World world = this.reusableCenterLoc.getWorld();
        long now = System.currentTimeMillis();
        double radiusSq = this.domeRadius * this.domeRadius;

        for (Entity entity : world.getNearbyEntities(this.reusableCenterLoc, this.domeRadius + 0.6, this.domeRadius + 0.8, this.domeRadius + 0.6)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) {
                continue;
            }

            if (target instanceof ArmorStand || target.isDead() || !target.isValid()) {
                continue;
            }

            if (target instanceof Player targetPlayer) {
                GameMode gm = targetPlayer.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || Commands.invincible.contains(targetPlayer.getName())) {
                    continue;
                }
            }

            Location targetLoc = target.getLocation();
            if (RegionProtection.isRegionProtected(player, targetLoc, this)) {
                continue;
            }

            double dx = targetLoc.getX() - cx;
            double dy = targetLoc.getY() - cy;
            double dz = targetLoc.getZ() - cz;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (dy >= -0.2 && dy <= (this.domeRadius + 0.5) && distSq <= radiusSq) {
                double dist = Math.sqrt(distSq);
                if (dist > 0.001) {
                    this.reusableVelocity.setX((dx / dist) * this.knockback);
                    this.reusableVelocity.setY(0.18);
                    this.reusableVelocity.setZ((dz / dist) * this.knockback);
                    GeneralMethods.setVelocity(this, target, this.reusableVelocity);
                }

                target.addPotionEffect(BLINDNESS_EFFECT);
                target.addPotionEffect(SLOWNESS_EFFECT);

                long lastHit = this.lastDamageTimes.getOrDefault(target.getUniqueId(), 0L);
                if (now - lastHit >= 800L) {
                    DamageHandler.damageEntity(target, this.damage, this);
                    this.lastDamageTimes.put(target.getUniqueId(), now);
                }
            }
        }
    }

    private void progressDisc() {
        World world = this.reusableCenterLoc.getWorld();
        Location eyeLoc = player.getEyeLocation();

        Vector dir = eyeLoc.getDirection().normalize();
        this.reusableDirection.copy(dir);

        Vector upBase = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.clone().crossProduct(upBase).normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        this.reusableRightVector.copy(right);
        this.reusableUpVector.copy(up);

        Location discCenter = eyeLoc.clone().add(dir.clone().multiply(1.4));
        this.reusableCenterLoc.setX(discCenter.getX());
        this.reusableCenterLoc.setY(discCenter.getY());
        this.reusableCenterLoc.setZ(discCenter.getZ());

        renderDiscParticles(world, discCenter, right, up);
        interceptDiscProjectiles(discCenter, dir);
        applyDiscCombat(discCenter, dir);
    }

    private void renderDiscParticles(World world, Location center, Vector right, Vector up) {
        int rings = 3;
        int pointsPerRing = 10;
        double angleStep = (Math.PI * 2.0) / pointsPerRing;

        for (int r = 1; r <= rings; r++) {
            double currentR = (this.discRadius / rings) * r;
            double rotationOffset = this.rotationPhase * (r % 2 == 0 ? 1.0 : -1.0);

            for (int p = 0; p < pointsPerRing; p++) {
                double angle = rotationOffset + (p * angleStep);
                double cos = Math.cos(angle) * currentR;
                double sin = Math.sin(angle) * currentR;

                this.reusableParticleLoc.set(
                        center.getX() + right.getX() * cos + up.getX() * sin,
                        center.getY() + right.getY() * cos + up.getY() * sin,
                        center.getZ() + right.getZ() * cos + up.getZ() * sin
                );

                world.spawnParticle(Particle.DUST, this.reusableParticleLoc, 1, 0.02, 0.02, 0.02, 0.0, DOME_DUST_OPTIONS);
            }
        }

        world.spawnParticle(Particle.DUST, center, 2, 0.12, 0.12, 0.12, 0.0, SWIRL_DUST_OPTIONS);
    }

    private void interceptDiscProjectiles(Location discCenter, Vector discNormal) {
        World world = discCenter.getWorld();
        if (world == null) {
            return;
        }

        double maxRadiusSq = this.discRadius * this.discRadius;

        for (Entity entity : world.getNearbyEntities(discCenter, this.discRadius + 0.6, this.discRadius + 0.6, this.discRadius + 0.6)) {
            if (!(entity instanceof Projectile projectile)) {
                continue;
            }

            if (projectile.getShooter() != null && projectile.getShooter().equals(player)) {
                continue;
            }

            Vector toProj = projectile.getLocation().toVector().subtract(discCenter.toVector());
            double distAlongNormal = toProj.dot(discNormal);

            if (Math.abs(distAlongNormal) <= 0.8) {
                double planarDistSq = toProj.lengthSquared() - (distAlongNormal * distAlongNormal);
                if (planarDistSq <= maxRadiusSq) {
                    Location pLoc = projectile.getLocation();
                    world.spawnParticle(Particle.FALLING_DUST, pLoc, 6, 0.1, 0.1, 0.1, 0.03, SAND_DATA);
                    world.playSound(pLoc, Sound.BLOCK_SAND_HIT, 1.0f, 1.3f);
                    projectile.remove();
                }
            }
        }
    }

    private void applyDiscCombat(Location discCenter, Vector discNormal) {
        World world = discCenter.getWorld();
        if (world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        double maxRadiusSq = this.discRadius * this.discRadius;

        for (Entity entity : world.getNearbyEntities(discCenter, this.discRadius + 0.5, this.discRadius + 0.5, this.discRadius + 0.5)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) {
                continue;
            }

            if (target instanceof ArmorStand || target.isDead() || !target.isValid()) {
                continue;
            }

            if (target instanceof Player targetPlayer) {
                GameMode gm = targetPlayer.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || Commands.invincible.contains(targetPlayer.getName())) {
                    continue;
                }
            }

            Location targetLoc = target.getLocation();
            if (RegionProtection.isRegionProtected(player, targetLoc, this)) {
                continue;
            }

            Vector toTarget = target.getEyeLocation().toVector().subtract(discCenter.toVector());
            double distAlongNormal = toTarget.dot(discNormal);

            if (Math.abs(distAlongNormal) <= 1.0) {
                double planarDistSq = toTarget.lengthSquared() - (distAlongNormal * distAlongNormal);
                if (planarDistSq <= maxRadiusSq) {
                    this.reusableVelocity.setX(discNormal.getX() * this.knockback * 1.3);
                    this.reusableVelocity.setY(0.20);
                    this.reusableVelocity.setZ(discNormal.getZ() * this.knockback * 1.3);
                    GeneralMethods.setVelocity(this, target, this.reusableVelocity);

                    target.addPotionEffect(BLINDNESS_EFFECT);

                    long lastHit = this.lastDamageTimes.getOrDefault(target.getUniqueId(), 0L);
                    if (now - lastHit >= 800L) {
                        DamageHandler.damageEntity(target, this.damage, this);
                        this.lastDamageTimes.put(target.getUniqueId(), now);
                    }
                }
            }
        }
    }

    @Override
    public void remove() {
        super.remove();
        this.lastDamageTimes.clear();
        if (bPlayer != null) {
            bPlayer.addCooldown(this);
        }
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
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
        return "SandShield";
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
                "Condense sand into an omnidirectional protective dome or project a focused kinetic disc to deflect incoming assaults."
        );
    }

    @Override
    public String getInstructions() {
        return ConfigManager.languageConfig.get().getString(
                "ExtraAbilities." + getName() + ".Instructions",
                "Hold Sneak (Shift) for full 360-degree dome defense. Left-click to deploy a directional kinetic sand disc."
        );
    }

    @Override
    public void load() {
        String configPath = "ExtraAbilities." + getName() + ".";
        ConfigManager.defaultConfig.get().addDefault(configPath + "Cooldown", 7500L);
        ConfigManager.defaultConfig.get().addDefault(configPath + "DomeDuration", 6000L);
        ConfigManager.defaultConfig.get().addDefault(configPath + "DiscDuration", 1600L);
        ConfigManager.defaultConfig.get().addDefault(configPath + "DomeRadius", 2.8);
        ConfigManager.defaultConfig.get().addDefault(configPath + "DiscRadius", 1.8);
        ConfigManager.defaultConfig.get().addDefault(configPath + "Damage", 1.0);
        ConfigManager.defaultConfig.get().addDefault(configPath + "Knockback", 0.42);
        ConfigManager.defaultConfig.save();

        String langPath = "ExtraAbilities." + getName() + ".";
        ConfigManager.languageConfig.get().addDefault(langPath + "Description", "Condense sand into an omnidirectional protective dome or project a focused kinetic disc to deflect incoming assaults.");
        ConfigManager.languageConfig.get().addDefault(langPath + "Instructions", "Hold Sneak (Shift) for full 360-degree dome defense. Left-click to deploy a directional kinetic sand disc.");
        ConfigManager.languageConfig.save();
    }

    @Override
    public void stop() {}
}