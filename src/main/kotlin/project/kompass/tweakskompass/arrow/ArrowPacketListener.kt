package project.kompass.tweakskompass.arrow

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import project.kompass.tweakskompass.TweaksKompass

object ArrowPacketListener : PacketListenerAbstract(PacketListenerPriority.HIGHEST) {

    private lateinit var plugin: TweaksKompass

    fun register(plugin: TweaksKompass) {
        this.plugin = plugin
        PacketEvents.getAPI().eventManager.registerListener(this)
    }

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType != PacketType.Play.Server.ENTITY_VELOCITY) return

        val packet = WrapperPlayServerEntityVelocity(event)

        // ULTRA-FAST O(1) LOOKUP:
        // Non-arrow velocity packets (players, mobs, minecarts) instantly return in 1 nanosecond.
        // Bypasses player.world.entities completely!
        val customVel = plugin.customArrowVelocitiesByEntityId[packet.entityId] ?: return

        packet.velocity = Vector3d(customVel.x, customVel.y, customVel.z)
        event.markForReEncode(true)
    }

    fun sendSyncPacket(player: Player, entity: Entity, velocity: Vector) {
        val packet = WrapperPlayServerEntityVelocity(
            entity.entityId,
            Vector3d(velocity.x, velocity.y, velocity.z)
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }
}