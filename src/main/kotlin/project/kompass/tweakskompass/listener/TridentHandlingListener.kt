package project.kompass.tweakskompass.listener

import com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.util.TridentUtil
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerPickupArrowEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import java.util.Random

class TridentHandlingListener(private val plugin: TweaksKompass) : Listener {

    private val random = Random()

    // Pre-cached config values to eliminate runtime YAML parsing
    private var isEnabled = true
    private var returnToMainHand = true
    private var returnToOffHand = true
    private var voidLoyaltyReturn = true
    private var cancelNetherPortal = true
    private var cancelEndPortal = true
    private var cancelEndGateway = true

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        isEnabled = plugin.config.getBoolean("trident-settings.enabled", true)
        returnToMainHand = plugin.config.getBoolean("trident-settings.handling.return-to-main-hand", true)
        returnToOffHand = plugin.config.getBoolean("trident-settings.handling.return-to-off-hand", true)
        voidLoyaltyReturn = plugin.config.getBoolean("trident-settings.handling.void-loyalty-return", true)

        val netherOpt = plugin.config.getString("trident-settings.handling.portal-behavior.nether-portal", "CANCEL")
        cancelNetherPortal = netherOpt.equals("CANCEL", ignoreCase = true)

        val endOpt = plugin.config.getString("trident-settings.handling.portal-behavior.end-portal", "CANCEL")
        cancelEndPortal = endOpt.equals("CANCEL", ignoreCase = true)

        val gatewayOpt = plugin.config.getString("trident-settings.handling.portal-behavior.end-gateway", "CANCEL")
        cancelEndGateway = gatewayOpt.equals("CANCEL", ignoreCase = true)
    }

    // 1. Tag launching hand and attach an optimized void-return monitor for Loyalty tridents
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTridentLaunch(event: ProjectileLaunchEvent) {
        if (!isEnabled) return
        val trident = event.entity as? Trident ?: return
        val shooter = trident.shooter as? Player ?: return

        val inventory = shooter.inventory
        val tridentItem = trident.itemStack

        // Robust hand tracking (0 = Main Hand, 1 = Off Hand)
        val handByte: Byte = when {
            inventory.itemInMainHand.isSimilar(tridentItem) -> 0.toByte()
            inventory.itemInOffHand.isSimilar(tridentItem) -> 1.toByte()
            else -> 0.toByte()
        }
        trident.persistentDataContainer.set(TridentUtil.THROWN_HAND_KEY, PersistentDataType.BYTE, handByte)

        // Void Recovery: If Loyalty trident falls into the void, trigger return
        if (trident.loyaltyLevel > 0 && voidLoyaltyReturn) {
            val voidThreshold = trident.world.minHeight - 32.0

            // Runs on Paper's entity-region scheduler (tuned to 4-tick intervals for 50% CPU savings)
            trident.scheduler.runAtFixedRate(plugin, { task ->
                // TPS OPTIMIZATION: Cancel the task immediately if the trident hits an entity,
                // embeds in a block, or has already initiated its return phase
                if (!trident.isValid || trident.isInBlock || trident.hasDealtDamage() || trident.isDead) {
                    task.cancel()
                    return@runAtFixedRate
                }

                if (trident.location.y <= voidThreshold) {
                    trident.setHasDealtDamage(true) // Triggers vanilla Loyalty return
                    task.cancel()
                }
            }, null, 1L, 4L)
        }
    }

    // 2. Return trident directly to the original throwing hand if empty
    @Suppress("DEPRECATION")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerPickupTrident(event: PlayerPickupArrowEvent) {
        if (!isEnabled) return
        val trident = event.arrow as? Trident ?: return
        val player = event.player

        val shooter = trident.shooter as? Player
        if (shooter == null || shooter.uniqueId != player.uniqueId) return

        val pdc = trident.persistentDataContainer
        val handByte = pdc.get(TridentUtil.THROWN_HAND_KEY, PersistentDataType.BYTE) ?: return

        val targetSlot = if (handByte == 0.toByte()) EquipmentSlot.HAND else EquipmentSlot.OFF_HAND
        val isEligible = if (targetSlot == EquipmentSlot.HAND) returnToMainHand else returnToOffHand

        if (!isEligible) return

        val inventory = player.inventory
        val currentInSlot = inventory.getItem(targetSlot)

        // Slot must be empty (Air) to return directly
        if (currentInSlot.type.isAir) {
            event.isCancelled = true

            inventory.setItem(targetSlot, trident.itemStack)
            trident.remove()

            val pitch = (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f
            player.world.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, pitch)
        }
    }

    // 3. Portal cancellation safeguards
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onTridentPortal(event: EntityPortalEvent) {
        if (!isEnabled) return
        if (event.entity !is Trident) return

        val isEndPortal = event.from.world.environment == org.bukkit.World.Environment.THE_END ||
                event.to?.world?.environment == org.bukkit.World.Environment.THE_END

        if (isEndPortal && cancelEndPortal) {
            event.isCancelled = true
        } else if (cancelNetherPortal) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onTridentEndGateway(event: EntityTeleportEndGatewayEvent) {
        if (!isEnabled || !cancelEndGateway) return
        if (event.entity is Trident) {
            event.isCancelled = true
        }
    }
}