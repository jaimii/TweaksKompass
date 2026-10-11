package project.kompass.tweakskompass.arrow

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import org.bukkit.Bukkit
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import project.kompass.tweakskompass.TweaksKompass

object ArrowPacketListener {

    private var isRegistered = false

    fun register(plugin: TweaksKompass) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin.logger.info("[ArrowModule] ProtocolLib not detected. Running on native Paper velocity physics.")
            return
        }

        try {
            val protocolManager = ProtocolLibrary.getProtocolManager()
            protocolManager.addPacketListener(object : PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.ENTITY_VELOCITY
            ) {
                override fun onPacketSending(event: PacketEvent) {
                    val packet = event.packet
                    val entity = packet.getEntityModifier(event.player.world).readSafely(0)

                    if (entity is AbstractArrow) {
                        val correctVelocity = plugin.customArrowVelocities[entity.uniqueId] ?: return

                        if (packet.vectors.size() > 0) {
                            packet.vectors.write(0, correctVelocity)
                        } else if (packet.integers.size() >= 4) {
                            packet.integers.write(1, (correctVelocity.x * 8000.0).toInt())
                            packet.integers.write(2, (correctVelocity.y * 8000.0).toInt())
                            packet.integers.write(3, (correctVelocity.z * 8000.0).toInt())
                        }
                    }
                }
            })
            isRegistered = true
            plugin.logger.info("[ArrowModule] ProtocolLib packet adapter registered successfully.")
        } catch (t: Throwable) {
            plugin.logger.warning("[ArrowModule] Failed to register ProtocolLib packet listener: ${t.message}")
        }
    }

    fun sendSyncPacket(player: Player, entity: Entity, velocity: Vector) {
        if (!isRegistered) return
        try {
            val protocolManager = ProtocolLibrary.getProtocolManager()
            val packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_VELOCITY)
            packet.integers.write(0, entity.entityId)

            if (packet.vectors.size() > 0) {
                packet.vectors.write(0, velocity)
            } else if (packet.integers.size() >= 4) {
                packet.integers.write(1, (velocity.x * 8000.0).toInt())
                packet.integers.write(2, (velocity.y * 8000.0).toInt())
                packet.integers.write(3, (velocity.z * 8000.0).toInt())
            }
            protocolManager.sendServerPacket(player, packet)
        } catch (ignored: Throwable) {}
    }
}