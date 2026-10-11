package project.kompass.tweakskompass.leash

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LeashManager(private val plugin: TweaksKompass) {
    private val programmaticUnleashes: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val activeLeashes: MutableMap<UUID, MutableSet<UUID>> = ConcurrentHashMap()

    fun trackLeash(playerUuid: UUID, entityUuid: UUID) {
        val leashes = activeLeashes.computeIfAbsent(playerUuid) { ConcurrentHashMap.newKeySet() }
        leashes.add(entityUuid)
    }

    fun untrackLeash(playerUuid: UUID, entityUuid: UUID) {
        val leashes = activeLeashes[playerUuid]
        if (leashes != null) {
            leashes.remove(entityUuid)
            if (leashes.isEmpty()) {
                activeLeashes.remove(playerUuid)
            }
        }
    }

    fun hasActiveLeashes(playerUuid: UUID): Boolean {
        return activeLeashes.containsKey(playerUuid)
    }

    fun registerProgrammaticUnleash(entityId: UUID) {
        programmaticUnleashes.add(entityId)
        plugin.server.scheduler.runTask(plugin, Runnable { programmaticUnleashes.remove(entityId) })
    }

    fun isProgrammaticUnleash(entityId: UUID): Boolean {
        return programmaticUnleashes.contains(entityId)
    }

    fun teleportLeashed(holder: LivingEntity) {
        getLeashedEntities(holder).forEach { leashed -> teleportLeashed(holder, leashed) }
    }

    fun teleportLeashed(holder: LivingEntity, leashed: LivingEntity) {
        val rawPassengers = leashed.passengers
        val passengers = if (rawPassengers.isEmpty()) emptyList() else ArrayList(rawPassengers)

        val chunk: Chunk = leashed.location.chunk
        chunk.addPluginChunkTicket(plugin)
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
        val worlds = plugin.config.getStringList("lead-any-mob.worlds")
        if (worlds.isEmpty()) return true
        return worlds.contains("ALL") || worlds.contains(world.name)
    }

    private fun getLeashedEntities(holder: Entity): List<LivingEntity> {
        var leashedEntities: MutableList<LivingEntity>? = null

        for (nearby in holder.getNearbyEntities(15.0, 15.0, 15.0)) {
            if (nearby is LivingEntity && nearby.isLeashed) {
                val leashHolder = nearby.leashHolder
                if (leashHolder != null && leashHolder.uniqueId == holder.uniqueId) {
                    if (leashedEntities == null) {
                        leashedEntities = ArrayList(2)
                    }
                    leashedEntities.add(nearby)
                }
            }
        }
        return leashedEntities ?: emptyList()
    }
}