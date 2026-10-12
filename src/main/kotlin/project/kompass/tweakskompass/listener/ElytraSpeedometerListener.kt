package project.kompass.tweakskompass.listener

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import project.kompass.tweakskompass.TweaksKompass
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

class ElytraSpeedometerListener(private val plugin: TweaksKompass) : Listener {

    private val miniMessage = MiniMessage.miniMessage()
    private val activeSpeedometerPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val lastActionBarTicks = ConcurrentHashMap<UUID, Int>()

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
        val cfg = plugin.config
        formatString = cfg.getString(
            "better-elytras.speedometer-format",
            "<green>◀</green> <{color}>Speed: {speed} m/s</{color}> <green>▶</green>"
        ) ?: "<green>◀</green> <{color}>Speed: {speed} m/s</{color}> <green>▶</green>"

        lowColor = cfg.getString("better-elytras.speedometer-colors.low", "green") ?: "green"
        lowThreshold = cfg.getDouble("better-elytras.speedometer-colors.low-threshold", 40.0)
        moderateColor = cfg.getString("better-elytras.speedometer-colors.moderate", "gold") ?: "gold"
        moderateThreshold = cfg.getDouble("better-elytras.speedometer-colors.moderate-threshold", 60.0)
        highColor = cfg.getString("better-elytras.speedometer-colors.high", "red") ?: "red"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
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

        // Monotonic server tick throttle (immune to /time set)
        val currentTick = Bukkit.getCurrentTick()
        val lastTick = lastActionBarTicks[uuid] ?: 0
        if (currentTick - lastTick < 3) return
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