package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.io.File
import java.io.IOException

class ShieldListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        /**
         * Automatically verifies and enables the Paper setting required for vanilla shield stun behavior.
         */
        fun patchPaperGlobal(plugin: TweaksKompass) {
            if (!plugin.config.getBoolean("shield-settings.auto-patch-paper-stun", true)) return

            val paperGlobalFile = File("config/paper-global.yml")
            if (!paperGlobalFile.exists()) return

            try {
                val yaml = YamlConfiguration.loadConfiguration(paperGlobalFile)
                val path = "unsupported-settings.skip-vanilla-damage-tick-when-shield-blocked"
                val isAlreadyEnabled = yaml.getBoolean(path, false)

                if (!isAlreadyEnabled) {
                    yaml.set(path, true)
                    yaml.save(paperGlobalFile)
                    plugin.logger.info("[ShieldSystem] Enabled '$path' in paper-global.yml to restore vanilla shield-stun.")
                    plugin.logger.warning("[ShieldSystem] Please restart the server once for Paper's shield-stun setting to take effect.")
                }
            } catch (e: IOException) {
                plugin.logger.warning("[ShieldSystem] Failed to save paper-global.yml patch: ${e.message}")
            } catch (t: Throwable) {
                plugin.logger.warning("[ShieldSystem] Could not inspect paper-global.yml: ${t.message}")
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (!plugin.config.getBoolean("shield-settings.instant-block", true)) return

        val player = event.entity as? Player ?: return

        // Vanilla already handled the block natively if isBlocking is true
        if (player.isBlocking || !player.isHandRaised) return

        // Determine which hand is actively raising the shield
        val shieldSlot = resolveActiveShieldSlot(player) ?: return
        val shieldItem = player.inventory.getItem(shieldSlot) ?: return

        // Verify attacker direction within the forward 180-degree horizontal blocking arc
        if (!isFacingAttacker(player, event.damager)) return

        // All checks passed: Cancel damage to eliminate the 5-tick raise delay
        event.isCancelled = true

        val isAxe = isAxeAttack(event.damager)
        val axeCooldownTicks = plugin.config.getInt("shield-settings.axe-disable-ticks", 100)

        if (isAxe) {
            // Axe hit disables the shield
            player.setCooldown(Material.SHIELD, axeCooldownTicks)
            player.world.playSound(player.location, Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f)
        } else {
            // Normal hit plays block sound
            player.world.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f)
        }

        // Apply shield durability wear
        val damageCost = maxOf(1, event.damage.toInt())
        player.damageItemStack(shieldSlot, damageCost)
    }

    private fun resolveActiveShieldSlot(player: Player): EquipmentSlot? {
        val mainHand = player.inventory.itemInMainHand
        val offHand = player.inventory.itemInOffHand

        if (mainHand.type == Material.SHIELD) {
            return EquipmentSlot.HAND
        }

        if (offHand.type == Material.SHIELD) {
            // If main hand holds a right-clickable usable item (food, bow, potion), player is using that item instead
            val mainType = mainHand.type
            val isMainHandUsable = mainType.isEdible ||
                    mainType == Material.BOW ||
                    mainType == Material.CROSSBOW ||
                    mainType == Material.POTION ||
                    mainType == Material.SPLASH_POTION ||
                    mainType == Material.LINGERING_POTION ||
                    mainType == Material.SPYGLASS ||
                    mainType == Material.TRIDENT

            if (!isMainHandUsable) {
                return EquipmentSlot.OFF_HAND
            }
        }

        return null
    }

    private fun isFacingAttacker(player: Player, damager: Entity): Boolean {
        val playerEye = player.eyeLocation
        val attackerLoc = if (damager is LivingEntity) damager.eyeLocation else damager.location

        // Calculate horizontal direction vectors (Y = 0 avoids vertical tilt errors)
        val toAttacker = attackerLoc.toVector().subtract(playerEye.toVector()).setY(0.0)
        val playerFacing = playerEye.direction.setY(0.0)

        if (toAttacker.lengthSquared() == 0.0 || playerFacing.lengthSquared() == 0.0) return true

        toAttacker.normalize()
        playerFacing.normalize()

        // Dot product >= 0.0 corresponds to an angle <= 90 degrees (180-degree forward arc)
        return playerFacing.dot(toAttacker) >= 0.0
    }

    private fun isAxeAttack(damager: Entity): Boolean {
        if (damager is LivingEntity) {
            val weapon = damager.equipment?.itemInMainHand ?: return false
            return weapon.type.name.endsWith("_AXE")
        }
        return false
    }
}