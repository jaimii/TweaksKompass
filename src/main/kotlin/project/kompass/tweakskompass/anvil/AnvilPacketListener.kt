package project.kompass.tweakskompass.anvil

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowProperty
import org.bukkit.GameMode
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AnvilPacketListener : PacketListenerAbstract(PacketListenerPriority.HIGHEST) {

    val preparing = ConcurrentHashMap<UUID, Int>()

    fun register() {
        PacketEvents.getAPI().eventManager.registerListener(this)
    }

    override fun onPacketSend(event: PacketSendEvent) {
        // FAST-EXIT: If no players are currently in an anvil over level 39, exit in 1 nanosecond
        if (preparing.isEmpty()) return

        val player = event.getPlayer<Player>() ?: return
        val uuid = player.uniqueId

        when (event.packetType) {
            PacketType.Play.Server.PLAYER_ABILITIES -> {
                if (preparing.containsKey(uuid)) {
                    val wrapper = WrapperPlayServerPlayerAbilities(event)
                    wrapper.isInCreativeMode = true
                    event.markForReEncode(true)
                }
            }
            PacketType.Play.Server.WINDOW_PROPERTY -> {
                val level = preparing[uuid] ?: return
                val wrapper = WrapperPlayServerWindowProperty(event)
                if (wrapper.id != 0) return
                wrapper.value = level
                event.markForReEncode(true)
            }
            PacketType.Play.Server.SET_SLOT -> {
                val level = preparing[uuid] ?: return
                val wrapper = WrapperPlayServerSetSlot(event)
                event.postTasks.add(Runnable {
                    val propertyPacket = WrapperPlayServerWindowProperty(wrapper.windowId, 0, level)
                    PacketEvents.getAPI().playerManager.sendPacket(player, propertyPacket)
                })
            }
            else -> {}
        }
    }

    fun sendSpoofedCreativeAbilities(player: Player) {
        val wrapper = WrapperPlayServerPlayerAbilities(
            player.isInvulnerable,
            player.isFlying,
            player.allowFlight,
            true,
            player.flySpeed / 2.0f,
            player.walkSpeed / 2.0f
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, wrapper)
    }

    fun restoreAbilities(player: Player) {
        val wrapper = WrapperPlayServerPlayerAbilities(
            player.isInvulnerable,
            player.isFlying,
            player.allowFlight,
            player.gameMode == GameMode.CREATIVE,
            player.flySpeed / 2.0f,
            player.walkSpeed / 2.0f
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, wrapper)
    }
}