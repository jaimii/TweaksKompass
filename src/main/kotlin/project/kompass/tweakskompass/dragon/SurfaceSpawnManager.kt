package project.kompass.tweakskompass.dragon

import org.bukkit.Bukkit
import org.bukkit.Statistic
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import project.kompass.tweakskompass.TweaksKompass
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SurfaceSpawnManager(private val plugin: TweaksKompass) {

    private val playerdataFolder = File(plugin.dataFolder, "playerdata")

    // In-memory set: only contains players who have killed the dragon AND have suppression toggled on
    private val activeSuppression = ConcurrentHashMap.newKeySet<UUID>()

    init {
        if (!playerdataFolder.exists()) {
            playerdataFolder.mkdirs()
        }
    }

    // Nanosecond fast-exit: If nobody has suppression active, mob spawning is 100% untouched
    fun hasAnyActive(): Boolean = activeSuppression.isNotEmpty()

    fun isSuppressed(uuid: UUID): Boolean = activeSuppression.contains(uuid)

    fun loadPlayer(player: Player) {
        val file = File(playerdataFolder, "${player.uniqueId}.yml")
        val config = YamlConfiguration.loadConfiguration(file)

        val isSuppressionToggled = config.getBoolean("surface-spawn-suppression", true)
        val hasKilledDragon = player.getStatistic(Statistic.KILL_ENTITY, EntityType.ENDER_DRAGON) > 0 || player.isOp

        if (isSuppressionToggled && hasKilledDragon) {
            activeSuppression.add(player.uniqueId)
        } else {
            activeSuppression.remove(player.uniqueId)
        }

        savePlayerFile(player, isSuppressionToggled)
    }

    fun unloadPlayer(uuid: UUID) {
        activeSuppression.remove(uuid)
    }

    fun setSuppression(player: Player, enabled: Boolean) {
        val hasKilledDragon = player.getStatistic(Statistic.KILL_ENTITY, EntityType.ENDER_DRAGON) > 0 || player.isOp

        if (enabled && hasKilledDragon) {
            activeSuppression.add(player.uniqueId)
        } else {
            activeSuppression.remove(player.uniqueId)
        }

        savePlayerFile(player, enabled)
    }

    private fun savePlayerFile(player: Player, enabled: Boolean) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val file = File(playerdataFolder, "${player.uniqueId}.yml")
            val config = YamlConfiguration()
            config.set("name", player.name)
            config.set("surface-spawn-suppression", enabled)

            try {
                config.save(file)
            } catch (e: IOException) {
                plugin.logger.warning("Failed to save playerdata for ${player.name}: ${e.message}")
            }
        })
    }
}