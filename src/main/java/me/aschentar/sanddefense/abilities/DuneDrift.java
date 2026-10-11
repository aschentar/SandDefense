package me.aschentar.sanddefense.abilities;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.SandAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class DuneDrift extends SandAbility implements AddonAbility {

    private enum Mode {
        DASH,
        SURF
    }

    private static final BlockData SAND_BLOCK_DATA = Material.SAND.createBlockData();
    private static final BlockData RED_SAND_BLOCK_DATA = Material.RED_SAND.createBlockData();

    private static final double DASH_MIN_VERTICAL = 0.32;
    private static final double RAY_START_OFFSET = 0.6;
    private static final double RAY_LENGTH = 3.6;
    private static final double MAX_STEP_UP = 1.1;
    private static final int TRAIL_LIFETIME = 12;
    private static final int MAX_TRAIL_BLOCKS = 28;

    private static final Set<Material> SAND_MATERIALS = EnumSet.of(
            Material.SAND,
            Material.RED_SAND,
            Material.SUSPICIOUS_SAND,
            Material.SANDSTONE,
            Material.RED_SANDSTONE,
            Material.CHISELED_SANDSTONE,
            Material.SMOOTH_SANDSTONE,
            Material.CHISELED_RED_SANDSTONE,
            Material.SMOOTH_RED_SANDSTONE,
            Material.CUT_SANDSTONE,
            Material.CUT_RED_SANDSTONE
    );

    @Attribute(Attribute.COOLDOWN)
    private long cooldown = 6000;

    @Attribute(Attribute.DURATION)
    private long duration = 6000;

    @Attribute(Attribute.SPEED)
    private double surfSpeed = 0.5;

    @Attribute("DashForce")
    private double dashForce = 2.1;

    @Attribute(Attribute.HEIGHT)
    private double hoverHeight = 0.5;

    private int offSandTicks = 6;
    private boolean requireSneak = false;

    private Mode currentMode;
    private int dashTicksRemaining;
    private int soundCounter;
    private int graceTicks;
    private int jumpFreeTicks;

    private double lastY;
    private boolean hasLastY;

    private BlockData trailData = SAND_BLOCK_DATA;

    private final List<TrailBlock> trailBlocks = new ArrayList<>();
    private final Location reusablePlayerLoc = new Location(null, 0, 0, 0);
    private final Location reusableParticleLoc = new Location(null, 0, 0, 0);
    private final Vector reusableVelocity = new Vector();

    private static final class TrailBlock {
        private final BlockDisplay display;
        private final float startSize;
        private final float driftX;
        private final float driftZ;
        private final float fall;
        private int age = 0;
        private boolean falling = false;

        private TrailBlock(BlockDisplay display, float startSize, float driftX, float driftZ, float fall) {
            this.display = display;
            this.startSize = startSize;
            this.driftX = driftX;
            this.driftZ = driftZ;
            this.fall = fall;
        }
    }

    public DuneDrift(Player player) {
        this(player, false);
    }

    public DuneDrift(Player player, boolean isClick) {
        super(player);

        if (player == null) {
            return;
        }

        if (!bPlayer.canBend(this) || !bPlayer.canSandbend()) {
            return;
        }

        DuneDrift runningAbility = getAbility(player, DuneDrift.class);
        if (runningAbility != null) {
            if (isClick && runningAbility.currentMode == Mode.SURF) {
                runningAbility.triggerDashFromSurf();
            } else if (!isClick && runningAbility.currentMode == Mode.SURF && !runningAbility.requireSneak) {
                runningAbility.remove();
            }
            return;
        }

        setFields();

        player.getLocation(this.reusablePlayerLoc);
        if (!isSandBelow(this.reusablePlayerLoc)) {
            return;
        }

        if (RegionProtection.isRegionProtected(player, this.reusablePlayerLoc, this)) {
            return;
        }

        this.currentMode = isClick ? Mode.DASH : Mode.SURF;
        this.graceTicks = this.offSandTicks;
        this.reusableParticleLoc.setWorld(this.reusablePlayerLoc.getWorld());

        if (this.currentMode == Mode.DASH) {
            initiateDash();
        }

        start();
    }

    private void setFields() {
        String path = "ExtraAbilities." + getName() + ".";
        this.cooldown = ConfigManager.defaultConfig.get().getLong(path + "Cooldown", this.cooldown);
        this.duration = ConfigManager.defaultConfig.get().getLong(path + "Duration", this.duration);
        this.surfSpeed = ConfigManager.defaultConfig.get().getDouble(path + "SurfSpeed", this.surfSpeed);
        this.dashForce = ConfigManager.defaultConfig.get().getDouble(path + "DashForce", this.dashForce);
        this.hoverHeight = ConfigManager.defaultConfig.get().getDouble(path + "HoverHeight", this.hoverHeight);
        this.offSandTicks = ConfigManager.defaultConfig.get().getInt(path + "OffSandTicks", this.offSandTicks);
        this.requireSneak = ConfigManager.defaultConfig.get().getBoolean(path + "RequireSneak", this.requireSneak);
    }

    private RayTraceResult rayDown(World world, double x, double y, double z) {
        Location start = new Location(world, x, y + RAY_START_OFFSET, z);
        return world.rayTraceBlocks(start, new Vector(0, -1, 0), RAY_LENGTH, FluidCollisionMode.NEVER, true);
    }

    private boolean isSandHit(RayTraceResult hit) {
        return hit != null && hit.getHitBlock() != null && SAND_MATERIALS.contains(hit.getHitBlock().getType());
    }

    private boolean isSandBelow(Location loc) {
        RayTraceResult hit = rayDown(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ());
        return isSandHit(hit) && (loc.getY() - hit.getHitPosition().getY()) <= 1.6;
    }

    private void updateTrailData(RayTraceResult hit) {
        if (hit == null || hit.getHitBlock() == null) {
            return;
        }
        Material type = hit.getHitBlock().getType();
        this.trailData = type.name().contains("RED") ? RED_SAND_BLOCK_DATA : SAND_BLOCK_DATA;
    }

    private void initiateDash() {
        player.getLocation(this.reusablePlayerLoc);

        double yawRad = Math.toRadians(this.reusablePlayerLoc.getYaw());
        double pitchRad = Math.toRadians(this.reusablePlayerLoc.getPitch());
        double cosPitch = Math.cos(pitchRad);

        double dirX = -Math.sin(yawRad) * cosPitch;
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * cosPitch;

        this.reusableVelocity.setX(dirX * this.dashForce);
        this.reusableVelocity.setY(Math.max(DASH_MIN_VERTICAL, dirY * this.dashForce * 0.5));
        this.reusableVelocity.setZ(dirZ * this.dashForce);

        GeneralMethods.setVelocity(this, player, this.reusableVelocity);
        player.setFallDistance(0.0f);

        World world = this.reusablePlayerLoc.getWorld();
        if (world != null) {
            world.playSound(this.reusablePlayerLoc, Sound.BLOCK_SAND_BREAK, 1.2f, 0.8f);
            world.playSound(this.reusablePlayerLoc, Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.2f);

            this.reusableParticleLoc.set(this.reusablePlayerLoc.getX(), this.reusablePlayerLoc.getY() + 0.2, this.reusablePlayerLoc.getZ());
            world.spawnParticle(Particle.FALLING_DUST, this.reusableParticleLoc, 20, 0.4, 0.2, 0.4, 0.05, this.trailData);
        }

        this.dashTicksRemaining = 8;
    }

    private void triggerDashFromSurf() {
        initiateDash();
        this.currentMode = Mode.DASH;
    }

    @Override
    public void progress() {
        if (!player.isOnline() || player.isDead() || !bPlayer.canBend(this)) {
            remove();
            return;
        }

        if (this.currentMode == Mode.SURF && this.requireSneak && !player.isSneaking()) {
            remove();
            return;
        }

        if (System.currentTimeMillis() - getStartTime() > this.duration) {
            remove();
            return;
        }

        player.getLocation(this.reusablePlayerLoc);

        this.reusableParticleLoc.set(this.reusablePlayerLoc.getX(), this.reusablePlayerLoc.getY() + player.getEyeHeight(), this.reusablePlayerLoc.getZ());
        if (this.reusableParticleLoc.getBlock().isLiquid()) {
            remove();
            return;
        }

        World world = this.reusablePlayerLoc.getWorld();
        if (world == null) {
            remove();
            return;
        }

        int chunkX = this.reusablePlayerLoc.getBlockX() >> 4;
        int chunkZ = this.reusablePlayerLoc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            remove();
            return;
        }

        if (RegionProtection.isRegionProtected(player, this.reusablePlayerLoc, this)) {
            remove();
            return;
        }

        tickTrailBlocks();

        if (this.currentMode == Mode.DASH) {
            progressDash();
        } else {
            progressSurf();
        }
    }

    private void progressDash() {
        player.setFallDistance(0.0f);

        Vector vel = player.getVelocity();
        double horizontal = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
        if (horizontal > 0.05) {
            spawnTrailBlocks(vel.getX() / horizontal, vel.getZ() / horizontal, 3);
        }

        this.dashTicksRemaining--;
        if (this.dashTicksRemaining <= 0) {
            remove();
        }
    }

    private void progressSurf() {
        player.setFallDistance(0.0f);

        World world = this.reusablePlayerLoc.getWorld();
        double x = this.reusablePlayerLoc.getX();
        double y = this.reusablePlayerLoc.getY();
        double z = this.reusablePlayerLoc.getZ();

        double yawRad = Math.toRadians(this.reusablePlayerLoc.getYaw());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        RayTraceResult underHit = rayDown(world, x, y, z);
        boolean onSand = isSandHit(underHit) && (y - underHit.getHitPosition().getY()) <= 1.6;

        if (onSand) {
            this.graceTicks = this.offSandTicks;
            updateTrailData(underHit);
        } else {
            this.graceTicks--;
            if (this.graceTicks < 0) {
                remove();
                return;
            }
        }

        double groundY = Double.NaN;
        if (underHit != null) {
            groundY = underHit.getHitPosition().getY();
        }

        double lookAhead = this.surfSpeed + 0.35;
        RayTraceResult aheadHit = rayDown(world, x + dirX * lookAhead, y, z + dirZ * lookAhead);
        if (aheadHit != null) {
            double aheadY = aheadHit.getHitPosition().getY();
            if (aheadY - y <= MAX_STEP_UP && (Double.isNaN(groundY) || aheadY > groundY)) {
                groundY = aheadY;
            }
        }

        double dy = this.hasLastY ? y - this.lastY : 0.0;
        this.lastY = y;
        this.hasLastY = true;

        double naturalVy = Math.max(-3.0, Math.min(1.0, (dy - 0.08) * 0.98));
        double vy;

        if (this.jumpFreeTicks > 0) {
            this.jumpFreeTicks--;
            vy = naturalVy;
        } else if (!Double.isNaN(groundY)) {
            double err = (groundY + this.hoverHeight) - y;
            if (dy > 0.2 && err < -0.1) {
                this.jumpFreeTicks = 8;
                vy = naturalVy;
            } else {
                vy = Math.max(-0.35, Math.min(0.42, err * 0.45));
            }
        } else {
            vy = naturalVy;
        }

        this.reusableVelocity.setX(dirX * this.surfSpeed);
        this.reusableVelocity.setY(vy);
        this.reusableVelocity.setZ(dirZ * this.surfSpeed);

        GeneralMethods.setVelocity(this, player, this.reusableVelocity);

        spawnTrailBlocks(dirX, dirZ, 2);

        this.soundCounter++;
        if (this.soundCounter % 4 == 0) {
            world.playSound(this.reusablePlayerLoc, Sound.BLOCK_SAND_STEP, 0.7f, 1.1f);
        }
    }

    private void spawnTrailBlocks(double dirX, double dirZ, int count) {
        World world = this.reusablePlayerLoc.getWorld();
        if (world == null) {
            return;
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        double baseX = this.reusablePlayerLoc.getX();
        double baseY = this.reusablePlayerLoc.getY();
        double baseZ = this.reusablePlayerLoc.getZ();

        for (int i = 0; i < count; i++) {
            if (this.trailBlocks.size() >= MAX_TRAIL_BLOCKS) {
                return;
            }

            double back = 0.4 + rnd.nextDouble() * 1.0;
            double lateral = (rnd.nextDouble() - 0.5) * 0.8;

            double px = baseX - dirX * back + (-dirZ) * lateral;
            double pz = baseZ - dirZ * back + dirX * lateral;
            double py = baseY + 0.1 + rnd.nextDouble() * 0.35;

            float size = 0.22f + rnd.nextFloat() * 0.2f;
            float rx = rnd.nextFloat() * 6.28f;
            float ry = rnd.nextFloat() * 6.28f;
            float rz = rnd.nextFloat() * 6.28f;
            BlockData data = this.trailData;

            Location spawnLoc = new Location(world, px, py, pz);
            BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, d -> {
                d.setBlock(data);
                d.setPersistent(false);
                d.setShadowRadius(0.0f);
                d.setShadowStrength(0.0f);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(TRAIL_LIFETIME - 2);
                d.setTransformation(new Transformation(
                        new Vector3f(-size / 2.0f, -size / 2.0f, -size / 2.0f),
                        new Quaternionf().rotateXYZ(rx, ry, rz),
                        new Vector3f(size, size, size),
                        new Quaternionf()
                ));
            });

            float drift = 0.4f + rnd.nextFloat() * 0.5f;
            float driftX = (float) (-dirX * drift);
            float driftZ = (float) (-dirZ * drift);
            float fall = 0.6f + rnd.nextFloat() * 0.3f;

            this.trailBlocks.add(new TrailBlock(display, size, driftX, driftZ, fall));
        }
    }

    private void startFall(TrailBlock block) {
        if (block.falling || !block.display.isValid()) {
            return;
        }
        block.falling = true;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float end = 0.04f;

        block.display.setInterpolationDelay(0);
        block.display.setInterpolationDuration(TRAIL_LIFETIME - 2);
        block.display.setTransformation(new Transformation(
                new Vector3f(-end / 2.0f + block.driftX, -end / 2.0f - block.fall, -end / 2.0f + block.driftZ),
                new Quaternionf().rotateXYZ(rnd.nextFloat() * 6.28f, rnd.nextFloat() * 6.28f, rnd.nextFloat() * 6.28f),
                new Vector3f(end, end, end),
                new Quaternionf()
        ));
    }

    private void tickTrailBlocks() {
        Iterator<TrailBlock> iterator = this.trailBlocks.iterator();
        while (iterator.hasNext()) {
            TrailBlock block = iterator.next();
            block.age++;

            if (!block.display.isValid()) {
                iterator.remove();
                continue;
            }

            if (block.age == 1) {
                startFall(block);
            }

            if (block.age >= TRAIL_LIFETIME) {
                block.display.remove();
                iterator.remove();
            }
        }
    }

    @Override
    public void remove() {
        super.remove();

        if (!this.trailBlocks.isEmpty()) {
            final List<TrailBlock> leftovers = new ArrayList<>(this.trailBlocks);
            this.trailBlocks.clear();
            for (TrailBlock block : leftovers) {
                startFall(block);
            }
            Bukkit.getScheduler().runTaskLater(ProjectKorra.plugin, () -> {
                for (TrailBlock block : leftovers) {
                    if (block.display.isValid()) {
                        block.display.remove();
                    }
                }
            }, TRAIL_LIFETIME);
        }

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
        return true;
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
        return "DuneDrift";
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
                "Harness sand currents to traverse terrain with rapid bursts and fluid dune surfing."
        );
    }

    @Override
    public String getInstructions() {
        return ConfigManager.languageConfig.get().getString(
                "ExtraAbilities." + getName() + ".Instructions",
                "Left-click on sand to dash forward. Press Sneak (Shift) on sand to engage surfing, and press Sneak again to disengage."
        );
    }

    @Override
    public void load() {
        String path = "ExtraAbilities." + getName() + ".";
        ConfigManager.defaultConfig.get().addDefault(path + "Cooldown", 6000L);
        ConfigManager.defaultConfig.get().addDefault(path + "Duration", 6000L);
        ConfigManager.defaultConfig.get().addDefault(path + "SurfSpeed", 0.5);
        ConfigManager.defaultConfig.get().addDefault(path + "DashForce", 2.1);
        ConfigManager.defaultConfig.get().addDefault(path + "HoverHeight", 0.5);
        ConfigManager.defaultConfig.get().addDefault(path + "OffSandTicks", 6);
        ConfigManager.defaultConfig.get().addDefault(path + "RequireSneak", false);
        ConfigManager.defaultConfig.save();

        String langPath = "ExtraAbilities." + getName() + ".";
        ConfigManager.languageConfig.get().addDefault(langPath + "Description", "Harness sand currents to traverse terrain with rapid bursts and fluid dune surfing.");
        ConfigManager.languageConfig.get().addDefault(langPath + "Instructions", "Left-click on sand to dash forward. Press Sneak (Shift) on sand to engage surfing, and press Sneak again to disengage.");
        ConfigManager.languageConfig.save();
    }

    @Override
    public void stop() {
        for (TrailBlock block : this.trailBlocks) {
            if (block.display != null && block.display.isValid()) {
                block.display.remove();
            }
        }
        this.trailBlocks.clear();
    }
}