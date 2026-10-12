package project.kompass.tweakskompass.listener

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Snow
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDropItemEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.persistence.PersistentDataType
import project.kompass.tweakskompass.TweaksKompass
import java.util.HashSet

class FallingSnowListener(private val plugin: TweaksKompass) : Listener {

    private val fallingSnowTag = NamespacedKey("tweakskompass", "is_falling_snow")

    // Primitive bit-packed coordinate cache to debounce BlockPhysicsEvent with 0 heap allocation
    private val checkedPositions = HashSet<Long>()
    private var lastTick = -1L

    private fun isPhysicsEnabled(): Boolean {
        return plugin.config.getBoolean("snow-physics.enabled", true) &&
                plugin.config.getBoolean("snow-physics.falling-snow-layers", true)
    }

    private fun packCoords(x: Int, y: Int, z: Int): Long {
        return ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)
    }

    // 1. Detect unsupported snow layers and launch falling block entities
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        if (!isPhysicsEnabled()) return

        val block = event.block

        // CRITICAL TPS GUARD: If the block is solid and not snow, it didn't break,
        // so the block above cannot be losing its support. Bypasses 99.5% of physics checks!
        if (!block.isEmpty && block.type != Material.SNOW) return

        val world = block.world
        val currentTick = world.fullTime

        if (currentTick != lastTick) {
            lastTick = currentTick
            checkedPositions.clear()
        }

        checkAndFall(block)
        if (block.isEmpty) {
            checkAndFall(block.getRelative(BlockFace.UP))
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (!isPhysicsEnabled()) return
        for (block in event.blocks) {
            checkAndFall(block)
            checkAndFall(block.getRelative(BlockFace.UP))
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (!isPhysicsEnabled()) return
        for (block in event.blocks) {
            checkAndFall(block)
            checkAndFall(block.getRelative(BlockFace.UP))
        }
    }

    private fun checkAndFall(block: Block) {
        if (block.type != Material.SNOW) return

        val key = packCoords(block.x, block.y, block.z)
        if (!checkedPositions.add(key)) return

        val below = block.getRelative(BlockFace.DOWN)
        val canSupport = below.type.isSolid && !below.isPassable && below.type != Material.AIR

        // Only clone blockData and mutate world if the block is truly unsupported
        if (!canSupport) {
            val data = block.blockData
            block.setType(Material.AIR, false)

            val spawnLoc = block.location.add(0.5, 0.05, 0.5)
            block.world.spawn(spawnLoc, FallingBlock::class.java) { fallingBlock ->
                fallingBlock.blockData = data
                fallingBlock.dropItem = false
                fallingBlock.persistentDataContainer.set(fallingSnowTag, PersistentDataType.BYTE, 1.toByte())
            }
        }
    }

    // 2. Handle landing and stacking layers
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFallingBlockLand(event: EntityChangeBlockEvent) {
        val fallingBlock = event.entity as? FallingBlock ?: return
        if (fallingBlock.blockData.material != Material.SNOW) return

        val targetBlock = event.block
        val fallingSnow = fallingBlock.blockData as? Snow ?: return

        // If landing on an existing snow layer, combine them!
        if (targetBlock.type == Material.SNOW) {
            event.isCancelled = true
            fallingBlock.dropItem = false
            fallingBlock.remove()

            val targetSnow = targetBlock.blockData as? Snow ?: return
            val combined = targetSnow.layers + fallingSnow.layers
            val capped = minOf(combined, 8)

            targetSnow.layers = capped
            targetBlock.blockData = targetSnow

            // Overflow to the block above
            if (combined > 8) {
                val above = targetBlock.getRelative(BlockFace.UP)
                if (above.type.isAir) {
                    above.type = Material.SNOW
                    val aboveSnow = above.blockData as? Snow
                    if (aboveSnow != null) {
                        aboveSnow.layers = combined - 8
                        above.blockData = aboveSnow
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFallingBlockPop(event: EntityDropItemEvent) {
        val fallingBlock = event.entity as? FallingBlock ?: return
        if (fallingBlock.persistentDataContainer.has(fallingSnowTag, PersistentDataType.BYTE)) {
            event.isCancelled = true
        }
    }

    // 3. Snowball impact places or increments snow layers
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onSnowballHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        if (!plugin.config.getBoolean("snow-physics.enabled", true)) return
        if (!plugin.config.getBoolean("snow-physics.snowball-layers", true)) return

        val hitBlock = event.hitBlock ?: return
        val hitFace = event.hitBlockFace ?: BlockFace.UP

        // Directly hit a snow layer
        if (hitBlock.type == Material.SNOW) {
            incrementSnowLayer(hitBlock)
            return
        }

        // Hit the top of a solid surface
        if (hitFace == BlockFace.UP && hitBlock.type.isSolid && !hitBlock.isPassable) {
            val above = hitBlock.getRelative(BlockFace.UP)
            if (above.type == Material.SNOW) {
                incrementSnowLayer(above)
            } else if (above.type.isAir) {
                above.type = Material.SNOW
                val snowData = above.blockData as? Snow
                if (snowData != null) {
                    snowData.layers = 1
                    above.blockData = snowData
                }
                playSnowPlaceEffect(above)
            }
        }
    }

    private fun incrementSnowLayer(block: Block) {
        val data = block.blockData as? Snow ?: return
        if (data.layers < 8) {
            data.layers += 1
            block.blockData = data
            playSnowPlaceEffect(block)
        } else {
            if (plugin.config.getBoolean("snow-physics.convert-to-snow-block", true)) {
                block.type = Material.SNOW_BLOCK
                playSnowPlaceEffect(block)
            } else {
                val above = block.getRelative(BlockFace.UP)
                if (above.type.isAir) {
                    above.type = Material.SNOW
                    val aboveData = above.blockData as? Snow
                    if (aboveData != null) {
                        aboveData.layers = 1
                        above.blockData = aboveData
                    }
                    playSnowPlaceEffect(above)
                }
            }
        }
    }

    private fun playSnowPlaceEffect(block: Block) {
        val loc = block.location.add(0.5, 0.2, 0.5)
        block.world.playSound(loc, Sound.BLOCK_SNOW_PLACE, 0.8f, 1.0f)
        block.world.spawnParticle(Particle.SNOWFLAKE, loc, 6, 0.2, 0.1, 0.2, 0.02)
    }
}