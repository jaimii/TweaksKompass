package project.kompass.tweakskompass.invisibility

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment
import org.bukkit.entity.Player

class InvisibilityPacketListener(private val manager: InvisibilityManager) :
    PacketListenerAbstract(PacketListenerPriority.NORMAL) {

    fun register() {
        PacketEvents.getAPI().eventManager.registerListener(this)
    }

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType != PacketType.Play.Server.ENTITY_EQUIPMENT) return

        val packet = WrapperPlayServerEntityEquipment(event)
        val viewer = event.getPlayer<Player>()

        // Do not mask equipment for the player themselves (preserves F5 third-person view)
        if (viewer != null && viewer.entityId == packet.entityId) return

        if (manager.isTracking(packet.entityId)) {
            manager.maskPacket(packet)
            event.markForReEncode(true)
        }
    }
}