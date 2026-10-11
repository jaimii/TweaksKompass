package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.*
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChainmailSieveListener(private val plugin: TweaksKompass) : Listener {

    private val recentlyHandled = ConcurrentHashMap.newKeySet<UUID>()
    private val supportedMaterials: MutableSet<Material> = EnumSet.noneOf(Material::class.java)

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        supportedMaterials.clear()
        val list = plugin.config.getStringList("chainmail-sieve.supported-materials")
        for (name in list) {
            try {
                supportedMaterials.add(Material.valueOf(name.uppercase()))
            } catch (ignored: IllegalArgumentException) {}
        }
        if (supportedMaterials.isEmpty()) {
            supportedMaterials.addAll(listOf(Material.SAND, Material.RED_SAND, Material.GRAVEL))
        }
    }

    fun startTask() {
        object : BukkitRunnable() {
            override fun run() {
                if (!plugin.config.getBoolean("chainmail-sieve.enabled", true)) return

                // TPS OPTIMIZATION: Only scans localized head-space of players actually wearing a Chainmail Helmet
                for (player in Bukkit.getOnlinePlayers()) {
                    if (!player.isValid || player.isDead || player.gameMode == GameMode.SPECTATOR) continue

                    val helmet = player.inventory.helmet
                    if (helmet == null || helmet.type != Material.CHAINMAIL_HELMET) continue

                    val world = player.world
                    val headLoc = player.eyeLocation

                    // Tight localized query directly above the player's head
                    for (entity in world.getNearbyEntities(headLoc, 1.2, 1.5, 1.2)) {
                        if (entity is FallingBlock) {
                            handleCollision(player, entity, helmet)
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFallingBlockLand(event: EntityChangeBlockEvent) {
        if (!plugin.config.getBoolean("chainmail-sieve.enabled", true)) return
        val fallingBlock = event.entity as? FallingBlock ?: return

        val material = event.blockData.material
        if (!supportedMaterials.contains(material)) return

        if (recentlyHandled.remove(fallingBlock.uniqueId)) {
            event.isCancelled = true
            return
        }

        val blockLoc = event.block.location.add(0.5, 0.5, 0.5)
        val world = event.block.world

        // Check if landing onto a player with a chainmail helmet
        for (entity in world.getNearbyEntities(blockLoc, 1.2, 1.5, 1.2)) {
            if (entity is Player && entity.isValid && !entity.isDead && entity.gameMode != GameMode.SPECTATOR) {
                val helmet = entity.inventory.helmet
                if (helmet != null && helmet.type == Material.CHAINMAIL_HELMET) {
                    event.isCancelled = true
                    handleCollision(entity, fallingBlock, helmet)
                    return
                }
            }
        }
    }

    private fun handleCollision(player: Player, fallingBlock: FallingBlock, helmet: ItemStack) {
        val material = fallingBlock.blockData.material
        if (!supportedMaterials.contains(material)) return

        val entityId = fallingBlock.uniqueId
        if (recentlyHandled.contains(entityId)) return

        val helmetZone = getHelmetZone(player)
        if (!fallingBlock.boundingBox.overlaps(helmetZone)) return

        // Prevent duplicate processing
        recentlyHandled.add(entityId)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { recentlyHandled.remove(entityId) }, 5L)

        fallingBlock.dropItem = false
        fallingBlock.remove()

        val impactLoc = player.eyeLocation.clone().add(0.0, 0.2, 0.0)

        // 1. Drop broken item
        if (plugin.config.getBoolean("chainmail-sieve.drop-item", true)) {
            val item = player.world.dropItem(impactLoc, ItemStack(material))
            item.velocity = Vector(0.0, 0.05, 0.0)
        }

        // 2. Play particle effect
        if (plugin.config.getBoolean("chainmail-sieve.particles", true)) {
            player.world.spawnParticle(
                Particle.FALLING_DUST,
                impactLoc,
                15,
                0.25, 0.2, 0.25,
                0.05,
                material.createBlockData()
            )
        }

        // 3. Play sound effect
        if (plugin.config.getBoolean("chainmail-sieve.sounds", true)) {
            val sound = if (material == Material.GRAVEL || material.name.contains("GRAVEL")) {
                Sound.BLOCK_GRAVEL_BREAK
            } else {
                Sound.BLOCK_SAND_BREAK
            }
            player.world.playSound(impactLoc, sound, 1.0f, 1.0f)
        }

        // 4. Damage chainmail helmet
        val cost = plugin.config.getInt("chainmail-sieve.durability-cost", 1)
        if (cost > 0 && player.gameMode != GameMode.CREATIVE) {
            player.damageItemStack(EquipmentSlot.HEAD, cost)
        }
    }

    private fun getHelmetZone(player: Player): BoundingBox {
        val box = player.boundingBox
        val minY = (box.maxY - 0.45).coerceAtLeast(box.minY)
        return BoundingBox(
            box.minX - 0.05,
            minY,
            box.minZ - 0.05,
            box.maxX + 0.05,
            box.maxY + 0.35,
            box.maxZ + 0.05
        )
    }
}