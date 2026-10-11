package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ElytraBombingListener(private val plugin: TweaksKompass) : Listener {

    private val cooldowns = ConcurrentHashMap<UUID, Long>()
    private val lastBombTick = ConcurrentHashMap<UUID, Int>()

    private var bombingEnabled = true
    private var cooldownMs: Long = 1000L
    private var fuseTicks = 80
    private var requireIgniter = true
    private var inheritInertia = true

    init {
        updateValues()
    }

    fun updateValues() {
        bombingEnabled = plugin.config.getBoolean("better-elytras.bombing.enabled", true)
        cooldownMs = (plugin.config.getDouble("better-elytras.bombing.cooldown-seconds", 1.0) * 1000).toLong()
        fuseTicks = plugin.config.getInt("better-elytras.bombing.fuse-ticks", 80)
        requireIgniter = plugin.config.getBoolean("better-elytras.bombing.require-igniter", true)
        inheritInertia = plugin.config.getBoolean("better-elytras.bombing.inherit-inertia", true)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        if (!player.isGliding) return
        if (!bombingEnabled) return

        val item = event.item ?: return
        val itemType = item.type

        if (requireIgniter) {
            val isIgniter = itemType == Material.FLINT_AND_STEEL || itemType == Material.FIRE_CHARGE
            if (!isIgniter) return
        } else {
            if (itemType != Material.TNT) return
        }

        if (event.hand == EquipmentSlot.OFF_HAND) {
            val mainType = player.inventory.itemInMainHand.type
            if (requireIgniter) {
                if (mainType == Material.FLINT_AND_STEEL || mainType == Material.FIRE_CHARGE) return
            } else {
                if (mainType == Material.TNT) return
            }
        }

        val uuid = player.uniqueId
        val currentTick = player.ticksLived

        if (lastBombTick[uuid] == currentTick) return

        val now = System.currentTimeMillis()
        val lastBomb = cooldowns[uuid] ?: 0L
        if (now - lastBomb < cooldownMs) return

        if (player.gameMode != GameMode.CREATIVE && !player.inventory.contains(Material.TNT)) {
            return
        }

        event.isCancelled = true
        lastBombTick[uuid] = currentTick

        if (player.gameMode != GameMode.CREATIVE) {
            player.inventory.removeItem(ItemStack(Material.TNT, 1))

            if (itemType == Material.FLINT_AND_STEEL) {
                player.damageItemStack(item, 1)
            } else if (itemType == Material.FIRE_CHARGE || itemType == Material.TNT) {
                item.subtract(1)
            }
        }

        val velocity = player.velocity
        val speedSq = velocity.lengthSquared()

        val spawnLoc: Location = if (speedSq > 0.01) {
            val direction = velocity.clone().normalize()
            player.location.subtract(direction.multiply(1.5)).add(0.0, -1.0, 0.0)
        } else {
            player.location.add(0.0, -1.0, 0.0)
        }

        val tnt = player.world.spawn(spawnLoc, TNTPrimed::class.java)
        tnt.source = player
        tnt.fuseTicks = fuseTicks

        if (inheritInertia) {
            tnt.velocity = player.velocity
        } else {
            tnt.velocity = Vector(0.0, -0.5, 0.0)
        }

        try {
            player.playSound(spawnLoc, "entity.tnt.primed", org.bukkit.SoundCategory.PLAYERS, 1.0f, 1.0f)
        } catch (t: Throwable) {
            player.playSound(spawnLoc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f)
        }
        player.world.spawnParticle(Particle.SMOKE, spawnLoc, 5, 0.1, 0.1, 0.1, 0.05)

        cooldowns[uuid] = now
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        cooldowns.remove(uuid)
        lastBombTick.remove(uuid)
    }
}