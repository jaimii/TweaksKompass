package project.kompass.tweakskompass.boat

import org.bukkit.entity.Boat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LaunchTracker {
    private val activeUntil = ConcurrentHashMap<UUID, Long>()

    fun isActive(boat: Boat): Boolean {
        val until = activeUntil[boat.uniqueId] ?: return false
        if (boat.world.gameTime < until) {
            return true
        }
        activeUntil.remove(boat.uniqueId)
        return false
    }

    fun mark(boat: Boat, ticks: Long) {
        activeUntil[boat.uniqueId] = boat.world.gameTime + ticks
    }

    fun clear(boat: Boat) {
        activeUntil.remove(boat.uniqueId)
    }
}