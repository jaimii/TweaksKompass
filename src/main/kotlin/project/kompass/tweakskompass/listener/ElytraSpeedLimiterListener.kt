package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

class ElytraSpeedLimiterListener(private val plugin: TweaksKompass) : Listener {

    private var maxSpeedPerTick: Double = 0.0
    private var maxSpeedPerTickSquared: Double = 0.0

    init {
        updateValues()
    }

    fun updateValues() {
        val maxSpeedBps = plugin.config.getDouble("better-elytras.max-speed", 75.0)
        maxSpeedPerTick = maxSpeedBps / 20.0
        maxSpeedPerTickSquared = maxSpeedPerTick * maxSpeedPerTick
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!event.hasChangedPosition()) return

        val player = event.player
        if (!player.isGliding) return

        val velocity = player.velocity
        val speedSquared = velocity.lengthSquared()

        if (speedSquared > maxSpeedPerTickSquared) {
            val cappedVelocity = velocity.normalize().multiply(maxSpeedPerTick)
            player.velocity = cappedVelocity
        }
    }
}