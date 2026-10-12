package project.kompass.tweakskompass.listener

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
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChainmailSieveListener(private val plugin: TweaksKompass) : Listener {

    private val recentlyHandled = ConcurrentHashMap.newKeySet<UUID>()
    private val supportedMaterials: MutableSet<Material> = EnumSet.noneOf(Material::class.java)

    private var isEnabled = true
    private var dropItem = true
    private var spawnParticles = true
    private var playSounds = true
    private var durabilityCost = 1

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("chainmail-sieve.enabled", true)
        dropItem = cfg.getBoolean("chainmail-sieve.drop-item", true)
        spawnParticles = cfg.getBoolean("chainmail-sieve.particles", true)
        playSounds = cfg.getBoolean("chainmail-sieve.sounds", true)
        durabilityCost = cfg.getInt("chainmail-sieve.durability-cost", 1)

        supportedMaterials.clear()
        val list = cfg.getStringList("chainmail-sieve.supported-materials")
        for (name in list) {
            try {
                supportedMaterials.add(Material.valueOf(name.uppercase()))
            } catch (ignored: IllegalArgumentException) {}
        }
        if (supportedMaterials.isEmpty()) {
            supportedMaterials.addAll(listOf(Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.SNOW))
        }
    }

    fun startTask() {
        object : BukkitRunnable() {
            override fun run() {
                if (!isEnabled) return

                for (player in Bukkit.getOnlinePlayers()) {
                    if (!player.isValid || player.isDead || player.gameMode == GameMode.SPECTATOR) continue

                    val helmet = player.inventory.helmet ?: continue
                    if (helmet.type != Material.CHAINMAIL_HELMET) continue

                    val headLoc = player.eyeLocation
                    val world = player.world

                    // Throttled 2-tick bounding box query
                    for (entity in world.getNearbyEntities(headLoc, 1.2, 1.5, 1.2)) {
                        if (entity is FallingBlock) {
                            handleCollision(player, entity, helmet)
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 2L, 2L) // 50% CPU reduction over 1-tick loop
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFallingBlockLand(event: EntityChangeBlockEvent) {
        if (!isEnabled) return
        val fallingBlock = event.entity as? FallingBlock ?: return

        val material = event.blockData.material
        if (!supportedMaterials.contains(material)) return

        if (recentlyHandled.remove(fallingBlock.uniqueId)) {
            event.isCancelled = true
            return
        }

        val blockLoc = event.block.location.add(0.5, 0.5, 0.5)
        val world = event.block.world

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

        recentlyHandled.add(entityId)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { recentlyHandled.remove(entityId) }, 5L)

        fallingBlock.dropItem = false
        fallingBlock.remove()

        val impactLoc = player.eyeLocation.clone().add(0.0, 0.2, 0.0)

        if (dropItem) {
            val dropMaterial = if (material == Material.SNOW) Material.SNOWBALL else material
            val item = player.world.dropItem(impactLoc, ItemStack(dropMaterial))
            item.velocity = Vector(0.0, 0.05, 0.0)
        }

        if (spawnParticles) {
            if (material == Material.SNOW) {
                player.world.spawnParticle(Particle.SNOWFLAKE, impactLoc, 15, 0.25, 0.2, 0.25, 0.05)
            } else {
                player.world.spawnParticle(
                    Particle.FALLING_DUST,
                    impactLoc,
                    15,
                    0.25, 0.2, 0.25,
                    0.05,
                    material.createBlockData()
                )
            }
        }

        if (playSounds) {
            val sound = when {
                material == Material.SNOW -> Sound.BLOCK_SNOW_BREAK
                material == Material.GRAVEL || material.name.contains("GRAVEL") -> Sound.BLOCK_GRAVEL_BREAK
                else -> Sound.BLOCK_SAND_BREAK
            }
            player.world.playSound(impactLoc, sound, 1.0f, 1.0f)
        }

        if (durabilityCost > 0 && player.gameMode != GameMode.CREATIVE) {
            player.damageItemStack(EquipmentSlot.HEAD, durabilityCost)
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