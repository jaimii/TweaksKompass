package project.kompass.tweakskompass.leash

import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.player.PlayerTeleportEvent
import project.kompass.tweakskompass.TweaksKompass
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LeashManager(private val plugin: TweaksKompass) {
    private val programmaticUnleashes: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val activeLeashes: MutableMap<UUID, MutableSet<UUID>> = ConcurrentHashMap()

    fun trackLeash(playerUuid: UUID, entityUuid: UUID) {
        activeLeashes.computeIfAbsent(playerUuid) { ConcurrentHashMap.newKeySet() }.add(entityUuid)
    }

    fun untrackLeash(playerUuid: UUID, entityUuid: UUID) {
        activeLeashes[playerUuid]?.let {
            it.remove(entityUuid)
            if (it.isEmpty()) activeLeashes.remove(playerUuid)
        }
    }

    fun hasActiveLeashes(playerUuid: UUID): Boolean = activeLeashes.containsKey(playerUuid)

    fun registerProgrammaticUnleash(entityId: UUID) {
        programmaticUnleashes.add(entityId)
        plugin.server.scheduler.runTask(plugin, Runnable { programmaticUnleashes.remove(entityId) })
    }

    fun isProgrammaticUnleash(entityId: UUID): Boolean = programmaticUnleashes.contains(entityId)

    fun teleportLeashed(holder: LivingEntity, fromLocation: Location) {
        val fromWorld = fromLocation.world ?: return
        val nearbyLeashed = fromWorld.getNearbyEntities(fromLocation, 16.0, 16.0, 16.0)
            .filterIsInstance<LivingEntity>()
            .filter { it.isLeashed && it.leashHolder?.uniqueId == holder.uniqueId }

        for (leashed in nearbyLeashed) {
            teleportLeashed(holder, leashed)
        }
    }

    fun teleportLeashed(holder: LivingEntity, leashed: LivingEntity) {
        val rawPassengers = leashed.passengers
        val passengers = if (rawPassengers.isEmpty()) emptyList() else ArrayList(rawPassengers)

        val chunk: Chunk = leashed.location.chunk
        chunk.addPluginChunkTicket(plugin)

        registerProgrammaticUnleash(leashed.uniqueId)
        leashed.setLeashHolder(null)

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            leashed.eject()
            leashed.teleport(holder)
            leashed.setLeashHolder(holder)

            if (passengers.isNotEmpty()) {
                for (passenger in passengers) {
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        passenger.teleport(leashed, PlayerTeleportEvent.TeleportCause.UNKNOWN)
                        leashed.addPassenger(passenger)
                    }, 1L)
                }
            }

            chunk.removePluginChunkTicket(plugin)
        }, 3L)
    }

    fun teleportLeashedInstant(holder: LivingEntity, leashed: LivingEntity) {
        val rawPassengers = leashed.passengers
        val passengers = if (rawPassengers.isEmpty()) emptyList() else ArrayList(rawPassengers)

        leashed.eject()
        leashed.teleport(holder.location.add(0.0, 1.0, 0.0))

        if (passengers.isNotEmpty()) {
            for (passenger in passengers) {
                passenger.teleport(leashed, PlayerTeleportEvent.TeleportCause.UNKNOWN)
                leashed.addPassenger(passenger)
            }
        }
    }

    fun isWorldValid(world: World): Boolean {
        if (!plugin.config.getBoolean("lead-any-mob.enabled", true)) return false
        val worlds = plugin.config.getStringList("lead-any-mob.worlds")
        if (worlds.isEmpty()) return true
        return worlds.contains("ALL") || worlds.contains(world.name)
    }
}