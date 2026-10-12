package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Sittable
import org.bukkit.entity.Tameable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.scheduler.BukkitRunnable

class PetTeleportListener(private val plugin: TweaksKompass) : Listener {

    fun startDistanceCheckTask() {
        val interval = plugin.config.getInt("pet-teleportation.distance-teleport.check-interval", 60).toLong().coerceAtLeast(20L)

        object : BukkitRunnable() {
            override fun run() {
                if (!plugin.config.getBoolean("pet-teleportation.enabled", true)) return
                if (!plugin.config.getBoolean("pet-teleportation.distance-teleport.enabled", true)) return

                val maxDistance = plugin.config.getDouble("pet-teleportation.distance-teleport.max-distance", 24.0)
                val maxDistanceSq = maxDistance * maxDistance
                // Dynamically bound the search box to just beyond the max-distance threshold
                val searchRadius = (maxDistance + 12.0).coerceIn(24.0, 40.0)

                for (player in Bukkit.getOnlinePlayers()) {
                    if (!player.isValid || player.isDead || player.gameMode == GameMode.SPECTATOR) continue

                    val pLoc = player.location
                    val world = player.world

                    // TPS OPTIMIZATION: Paper chunk-slice predicate filters out items, mobs, and armor stands
                    // before allocating the collection on the heap
                    val nearbyPets = world.getNearbyEntities(pLoc, searchRadius, searchRadius, searchRadius) { entity ->
                        entity is Tameable && entity.isTamed
                    }

                    for (nearby in nearbyPets) {
                        if (isEligiblePet(nearby, player)) {
                            if (nearby.location.distanceSquared(pLoc) >= maxDistanceSq) {
                                teleportPetSafely(nearby, pLoc)
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, interval, interval)
    }

    // 1. Teleport following pets when the owner teleports
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!plugin.config.getBoolean("pet-teleportation.enabled", true)) return
        if (!plugin.config.getBoolean("pet-teleportation.teleport-on-player-teleport", true)) return

        val player = event.player
        val from = event.from
        val to = event.to ?: return

        // Chunk ticket preservation if configured
        val keepChunkSeconds = plugin.config.getInt("pet-teleportation.keep-chunk-loaded-seconds", 0)
        if (keepChunkSeconds > 0) {
            val fromChunk = from.chunk
            if (!fromChunk.isForceLoaded) {
                fromChunk.addPluginChunkTicket(plugin)
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    fromChunk.removePluginChunkTicket(plugin)
                }, keepChunkSeconds * 20L)
            }
        }

        val crossWorld = plugin.config.getBoolean("pet-teleportation.cross-world", true)
        if (!crossWorld && from.world != to.world) return

        val fromWorld = from.world ?: return

        // TPS OPTIMIZATION: Predicate filter skips non-tameable entities at the engine level
        val petsToTeleport = fromWorld.getNearbyEntities(from, 36.0, 36.0, 36.0) { entity ->
            entity is Tameable && entity.isTamed && isEligiblePet(entity, player)
        }

        if (petsToTeleport.isEmpty()) return

        // Teleport on the next tick so the player entity is fully established at the destination
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline || player.isDead) return@Runnable
            for (pet in petsToTeleport) {
                if (pet.isValid && !pet.isDead) {
                    teleportPetSafely(pet, to)
                }
            }
        })
    }

    // 2. Teleport pets when their current chunk is about to unload
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        if (!plugin.config.getBoolean("pet-teleportation.enabled", true)) return
        if (!plugin.config.getBoolean("pet-teleportation.teleport-on-chunk-unload", true)) return

        val chunk = event.chunk
        val crossWorld = plugin.config.getBoolean("pet-teleportation.cross-world", true)

        for (entity in chunk.entities) {
            if (entity !is Tameable || !entity.isTamed) continue
            if (entity is Sittable && entity.isSitting) continue

            val owner = entity.owner as? Player ?: continue
            if (!owner.isOnline || owner.isDead || owner.gameMode == GameMode.SPECTATOR) continue

            if (!crossWorld && entity.world != owner.world) continue

            teleportPetSafely(entity, owner.location)
        }
    }

    private fun isEligiblePet(entity: Entity, owner: Player): Boolean {
        if (entity !is Tameable || !entity.isTamed) return false
        if (entity.owner?.uniqueId != owner.uniqueId) return false
        if (entity is Sittable && entity.isSitting) return false
        if (entity.isDead || !entity.isValid) return false
        return true
    }

    private fun teleportPetSafely(pet: Entity, destination: Location) {
        val targetLoc = findSafeLocation(destination) ?: destination
        pet.teleport(targetLoc)
        pet.fallDistance = 0f
    }

    private fun findSafeLocation(center: Location): Location? {
        val world = center.world ?: return null
        val baseBlock = center.block

        val offsets = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0),
            intArrayOf(0, 1), intArrayOf(0, -1),
            intArrayOf(1, 1), intArrayOf(-1, -1)
        )

        for (offset in offsets) {
            val candidate = baseBlock.getRelative(offset[0], 0, offset[1])
            val below = candidate.getRelative(BlockFace.DOWN)
            val above = candidate.getRelative(BlockFace.UP)

            if (below.type.isSolid && !candidate.type.isSolid && !above.type.isSolid) {
                return candidate.location.add(0.5, 0.0, 0.5)
            }
        }

        return center
    }
}