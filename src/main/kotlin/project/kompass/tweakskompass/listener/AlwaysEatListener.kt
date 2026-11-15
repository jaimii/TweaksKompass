package project.kompass.tweakskompass.listener

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.util.isFood

class AlwaysEatListener(private val plugin: TweaksKompass) :
    PacketListenerAbstract(PacketListenerPriority.NORMAL), Listener {

    private val isAlwaysEatEnabled: Boolean
        get() = plugin.config.getBoolean("food-and-storage.always-eat.enabled", true)

    fun registerPacketListener() {
        PacketEvents.getAPI().eventManager.registerListener(this)
    }

    // 1. Client-Side Ability Spoof: Sets 'isInGodMode = true' on outgoing ability packets
    // so the client's Player#canEat() always returns true at 20/20 hunger without altering item components.
    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType != PacketType.Play.Server.PLAYER_ABILITIES) return
        if (!isAlwaysEatEnabled) return

        val wrapper = WrapperPlayServerPlayerAbilities(event)
        if (!wrapper.isInGodMode) {
            wrapper.isInGodMode = true
            event.markForReEncode(true)
        }
    }

    // 2. Server-Side Handshake: Temporarily enables invulnerable flag during the UseItem interaction
    // so the server's FoodProperties#canConsume allows starting the eating animation at 20/20 hunger.
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!isAlwaysEatEnabled) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

        val item = event.item ?: return
        if (player.foodLevel >= 20 && item.isFood()) {
            val nmsPlayer = (player as CraftPlayer).handle
            if (!nmsPlayer.abilities.invulnerable) {
                nmsPlayer.abilities.invulnerable = true

                // Reset immediately on the same tick after UseItem processing finishes
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline && player.gameMode != GameMode.CREATIVE && player.gameMode != GameMode.SPECTATOR) {
                        nmsPlayer.abilities.invulnerable = false
                    }
                })
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!isAlwaysEatEnabled) return
        sendAlwaysEatAbilities(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (!isAlwaysEatEnabled) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (event.player.isOnline) {
                sendAlwaysEatAbilities(event.player)
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        if (!isAlwaysEatEnabled) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (event.player.isOnline) {
                sendAlwaysEatAbilities(event.player)
            }
        })
    }

    private fun sendAlwaysEatAbilities(player: Player) {
        val wrapper = WrapperPlayServerPlayerAbilities(
            true, // isInGodMode (invulnerable flag)
            player.isFlying,
            player.allowFlight,
            player.gameMode == GameMode.CREATIVE,
            player.flySpeed / 2.0f,
            player.walkSpeed / 2.0f
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, wrapper)
    }
}