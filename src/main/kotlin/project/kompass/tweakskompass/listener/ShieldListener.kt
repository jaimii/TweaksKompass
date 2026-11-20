package project.kompass.tweakskompass.listener

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.EquipmentSlot
import project.kompass.tweakskompass.TweaksKompass
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ShieldListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        fun patchPaperGlobal(plugin: TweaksKompass) {
            if (!plugin.config.getBoolean("shield-settings.auto-patch-paper-stun", true)) return

            val serverRoot = plugin.dataFolder.absoluteFile.parentFile?.parentFile ?: File(".")
            val paperGlobalFile = File(serverRoot, "config/paper-global.yml")
            if (!paperGlobalFile.exists()) return

            try {
                val path = paperGlobalFile.toPath()
                val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
                var modified = false

                for (i in lines.indices) {
                    val line = lines[i]
                    val trimmed = line.trim()
                    if (trimmed.startsWith("skip-vanilla-damage-tick-when-shield-blocked:") && trimmed.contains("false")) {
                        lines[i] = line.replace("false", "true")
                        modified = true
                        break
                    }
                }

                if (modified) {
                    Files.write(path, lines, StandardCharsets.UTF_8)
                    plugin.logger.info("[ShieldSystem] Enabled 'skip-vanilla-damage-tick-when-shield-blocked' in config/paper-global.yml.")
                    plugin.logger.warning("[ShieldSystem] Please restart the server once for Paper's shield-stun setting to take effect.")
                }
            } catch (e: IOException) {
                plugin.logger.warning("[ShieldSystem] Failed to read or write config/paper-global.yml: ${e.message}")
            } catch (e: SecurityException) {
                plugin.logger.warning("[ShieldSystem] Missing file permissions for config/paper-global.yml: ${e.message}")
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (!plugin.config.getBoolean("shield-settings.instant-block", true)) return

        val player = event.entity as? Player ?: return
        if (player.isBlocking || !player.isHandRaised) return

        val shieldSlot = resolveActiveShieldSlot(player) ?: return
        val shieldItem = player.inventory.getItem(shieldSlot)
        if (shieldItem.type.isAir) return

        if (!isFacingAttacker(player, event.damager)) return

        event.isCancelled = true

        val isAxe = isAxeAttack(event.damager)
        val axeCooldownTicks = plugin.config.getInt("shield-settings.axe-disable-ticks", 100)

        if (isAxe) {
            player.setCooldown(Material.SHIELD, axeCooldownTicks)
            player.world.playSound(player.location, Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f)
        } else {
            player.world.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f)
        }

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

        val toAttacker = attackerLoc.toVector().subtract(playerEye.toVector()).setY(0.0)
        val playerFacing = playerEye.direction.setY(0.0)

        if (toAttacker.lengthSquared() == 0.0 || playerFacing.lengthSquared() == 0.0) return true

        toAttacker.normalize()
        playerFacing.normalize()

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