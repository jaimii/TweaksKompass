package project.kompass.tweakskompass.listener

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Art
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Painting
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import java.util.concurrent.ThreadLocalRandom

class PaintingTweaksListener : Listener {

    companion object {
        val ELEMENTAL_2X2: List<Art> = listOf(Art.EARTH, Art.WIND, Art.FIRE, Art.WATER)

        private val ARTS_BY_DIMENSION: Map<Pair<Int, Int>, List<Art>> by lazy {
            val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.PAINTING_VARIANT)
            registry.groupBy { Pair(it.blockWidth, it.blockHeight) }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    fun onPaintingPlace(event: EntityPlaceEvent) {
        val painting = event.entity as? Painting ?: return

        if (painting.art.blockWidth == 2 && painting.art.blockHeight == 2) {
            val random = ThreadLocalRandom.current()
            if (random.nextDouble() < 0.40) {
                painting.setArt(ELEMENTAL_2X2[random.nextInt(ELEMENTAL_2X2.size)], true)
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    fun onPaintingCycle(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (!player.isSneaking) return

        val item = player.inventory.getItem(event.hand)
        if (item.type != Material.PAINTING) return

        val painting = event.rightClicked as? Painting ?: return
        event.isCancelled = true

        val width = painting.art.blockWidth
        val height = painting.art.blockHeight

        val eligibleArts = ARTS_BY_DIMENSION[Pair(width, height)] ?: return
        if (eligibleArts.size <= 1) return

        val currentIndex = eligibleArts.indexOf(painting.art)
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % eligibleArts.size

        painting.setArt(eligibleArts[nextIndex], true)
        painting.world.playSound(painting.location, Sound.ENTITY_PAINTING_PLACE, 1.0f, 1.2f)
    }
}