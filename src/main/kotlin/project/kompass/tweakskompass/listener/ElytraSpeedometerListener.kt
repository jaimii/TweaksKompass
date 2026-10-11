package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

class ElytraSpeedometerListener(private val plugin: TweaksKompass) : Listener {

    private val miniMessage = MiniMessage.miniMessage()
    private val activeSpeedometerPlayers = ConcurrentHashMap.newKeySet<UUID>()
    // Throttling map: tracks the last tick an actionbar was dispatched per player
    private val lastActionBarTicks = ConcurrentHashMap<UUID, Long>()

    private var formatString: String = ""
    private var lowColor: String = ""
    private var lowThreshold: Double = 40.0
    private var moderateColor: String = ""
    private var moderateThreshold: Double = 60.0
    private var highColor: String = ""

    init {
        updateValues()
    }

    fun updateValues() {
        formatString = plugin.config.getString(
            "better-elytras.speedometer-format",
            "<green>◀</green> <{color}>Speed: {speed} m/s</{color}> <green>▶</green>"
        ) ?: "<green>◀</green> <{color}>Speed: {speed} m/s</{color}> <green>▶</green>"

        lowColor = plugin.config.getString("better-elytras.speedometer-colors.low", "green") ?: "green"
        lowThreshold = plugin.config.getDouble("better-elytras.speedometer-colors.low-threshold", 40.0)
        moderateColor = plugin.config.getString("better-elytras.speedometer-colors.moderate", "gold") ?: "gold"
        moderateThreshold = plugin.config.getDouble("better-elytras.speedometer-colors.moderate-threshold", 60.0)
        highColor = plugin.config.getString("better-elytras.speedometer-colors.high", "red") ?: "red"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        // Fast-exit: Ignore pitch/yaw head rotations
        if (!event.hasChangedPosition()) return

        val player = event.player
        val uuid = player.uniqueId

        if (!player.isGliding) {
            if (activeSpeedometerPlayers.remove(uuid)) {
                lastActionBarTicks.remove(uuid)
                player.sendActionBar(Component.empty())
            }
            return
        }

        activeSpeedometerPlayers.add(uuid)

        // TPS OPTIMIZATION: Throttle action bar updates to once every 3 ticks (~6.6 Hz)
        val currentTick = player.world.fullTime
        val lastTick = lastActionBarTicks[uuid] ?: 0L
        if (currentTick - lastTick < 3L) return
        lastActionBarTicks[uuid] = currentTick

        val velocity = player.velocity
        val speedSquared = velocity.lengthSquared()
        val speedMps = if (speedSquared > 0.0001) sqrt(speedSquared) * 20.0 else 0.0

        val activeColor = when {
            speedMps < lowThreshold -> lowColor
            speedMps < moderateThreshold -> moderateColor
            else -> highColor
        }

        val speedStr = "%.1f".format(java.util.Locale.US, speedMps)
        val formattedMessage = formatString
            .replace("{color}", activeColor)
            .replace("{speed}", speedStr)

        player.sendActionBar(miniMessage.deserialize(formattedMessage))
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        activeSpeedometerPlayers.remove(uuid)
        lastActionBarTicks.remove(uuid)
    }
}