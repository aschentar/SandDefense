package me.aschentar.sanddefense;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.SandAbility;
import me.aschentar.sanddefense.abilities.DuneDrift;
import me.aschentar.sanddefense.abilities.DustVeil;
import me.aschentar.sanddefense.abilities.SandArmor;
import me.aschentar.sanddefense.abilities.SandShield;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.EnumSet;
import java.util.Set;

public final class SandDefenseListener implements Listener {

    private static final Set<Material> SAND_MATERIALS = EnumSet.of(
            Material.SAND,
            Material.RED_SAND,
            Material.SUSPICIOUS_SAND,
            Material.SANDSTONE,
            Material.RED_SANDSTONE,
            Material.SMOOTH_SANDSTONE,
            Material.SMOOTH_RED_SANDSTONE,
            Material.CHISELED_SANDSTONE,
            Material.CHISELED_RED_SANDSTONE,
            Material.CUT_SANDSTONE,
            Material.CUT_RED_SANDSTONE
    );

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) {
            return;
        }

        String boundAbility = bPlayer.getBoundAbilityName();
        if (boundAbility == null || bPlayer.isOnCooldown(boundAbility)) {
            return;
        }

        if ("SandArmor".equalsIgnoreCase(boundAbility)) {
            if (!bPlayer.canSandbend()) {
                return;
            }
            Block groundBlock = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (isSand(groundBlock)) {
                new SandArmor(player);
            }
        } else if ("SandShield".equalsIgnoreCase(boundAbility)) {
            new SandShield(player, false);
        } else if ("DuneDrift".equalsIgnoreCase(boundAbility)) {
            new DuneDrift(player, false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) {
            return;
        }

        String boundAbility = bPlayer.getBoundAbilityName();
        if (boundAbility == null || bPlayer.isOnCooldown(boundAbility)) {
            return;
        }

        if ("SandShield".equalsIgnoreCase(boundAbility)) {
            new SandShield(player, true);
        } else if ("DuneDrift".equalsIgnoreCase(boundAbility)) {
            new DuneDrift(player, true);
        } else if ("DustVeil".equalsIgnoreCase(boundAbility)) {
            new DustVeil(player);
        }
    }

    private boolean isSand(Block block) {
        return block != null && (SAND_MATERIALS.contains(block.getType()) || SandAbility.isSand(block));
    }
}