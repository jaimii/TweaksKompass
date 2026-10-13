package project.kompass.tweakskompass.listener

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.MushroomCow
import org.bukkit.entity.Sheep
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerShearEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import java.util.Random

class LootingShearsListener : Listener {

    private val random = Random()

    // 1. Allow Looting I-III onto Shears in an Anvil
    @EventHandler
    fun onAnvilPrepare(event: PrepareAnvilEvent) {
        val first = event.inventory.getItem(0) ?: return
        val second = event.inventory.getItem(1) ?: return

        if (first.type != Material.SHEARS) return

        val lootingLevel = if (second.type == Material.ENCHANTED_BOOK) {
            (second.itemMeta as? EnchantmentStorageMeta)?.getStoredEnchantLevel(Enchantment.LOOTING) ?: 0
        } else {
            second.getEnchantmentLevel(Enchantment.LOOTING)
        }

        if (lootingLevel > 0) {
            val result = first.clone()
            result.addUnsafeEnchantment(Enchantment.LOOTING, lootingLevel)

            val rename = event.view.renameText
            if (!rename.isNullOrEmpty()) {
                val meta = result.itemMeta
                meta?.displayName(net.kyori.adventure.text.Component.text(rename))
                result.itemMeta = meta
            }

            event.result = result
            event.view.repairCost = lootingLevel * 2
        }
    }

    // 2. Extra wool/mushroom drops when shearing with Looting Shears
    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerShear(event: PlayerShearEntityEvent) {
        val tool = event.item
        val looting = tool.getEnchantmentLevel(Enchantment.LOOTING)
        if (looting <= 0) return

        val entity = event.entity
        val bonus = random.nextInt(looting + 1)
        if (bonus <= 0) return

        if (entity is Sheep) {
            val color = entity.color ?: org.bukkit.DyeColor.WHITE
            val woolMat = Material.getMaterial("${color.name}_WOOL") ?: Material.WHITE_WOOL
            entity.world.dropItemNaturally(entity.location, ItemStack(woolMat, bonus))
        } else if (entity is MushroomCow) {
            val shroom = if (entity.variant == MushroomCow.Variant.BROWN) Material.BROWN_MUSHROOM else Material.RED_MUSHROOM
            entity.world.dropItemNaturally(entity.location, ItemStack(shroom, bonus))
        }
    }
}