package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.entity.Turtle
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.util.Random

class TurtleScuteBrushListener(private val plugin: TweaksKompass) : Listener {

    private val random = Random()

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onTurtleBrush(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val turtle = event.rightClicked as? Turtle ?: return

        // Only adult turtles can be brushed for scutes
        if (!turtle.isAdult) return

        val player = event.player
        val item = player.inventory.itemInMainHand
        if (item.type != Material.BRUSH) return

        event.isCancelled = true

        val loc = turtle.location
        val world = turtle.world

        // 1. Drop 1 Turtle Scute
        world.dropItemNaturally(loc.clone().add(0.0, 0.2, 0.0), ItemStack(Material.TURTLE_SCUTE))

        // 2. Play sound effects
        world.playSound(loc, Sound.ITEM_BRUSH_BRUSHING_GENERIC, 1.0f, 1.0f)
        world.playSound(loc, Sound.ENTITY_TURTLE_AMBIENT_LAND, 1.0f, 1.2f)

        // 3. Spawn particle effects
        world.spawnParticle(
            Particle.ITEM,
            loc.clone().add(0.0, 0.4, 0.0),
            12,
            0.25, 0.25, 0.25,
            0.05,
            ItemStack(Material.TURTLE_SCUTE)
        )

        // 4. Consume brush durability (default 16 to match Armadillo brushing)
        val durabilityCost = plugin.config.getInt("turtle-brush-durability-cost", 16)
        damageBrush(player, item, durabilityCost)
    }

    private fun damageBrush(player: Player, item: ItemStack, amount: Int) {
        val meta = item.itemMeta as? Damageable ?: return

        // Calculate Unbreaking mitigation
        if (item.containsEnchantment(Enchantment.UNBREAKING)) {
            val level = item.getEnchantmentLevel(Enchantment.UNBREAKING)
            var actualDamage = 0
            for (i in 0 until amount) {
                val chance = 1.0 / (level + 1)
                if (random.nextDouble() >= chance) {
                    actualDamage++
                }
            }
            if (actualDamage == 0) return
            meta.damage = meta.damage + actualDamage
        } else {
            meta.damage = meta.damage + amount
        }

        if (meta.damage >= item.type.maxDurability) {
            // Brush broke
            player.world.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
            player.inventory.setItemInMainHand(null)
        } else {
            item.itemMeta = meta
            player.inventory.setItemInMainHand(item)
        }
    }
}