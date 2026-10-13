package project.kompass.tweakskompass.listener

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.inventory.ItemStack
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumSet

class ToolBlockTweaksListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        private val PICKAXES: Set<Material> = EnumSet.of(
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
        )

        private val HOES: Set<Material> = EnumSet.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
        )

        private val AXES: Set<Material> = EnumSet.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
        )

        private val LIGHT_SOURCES: Set<Material> = EnumSet.of(
            Material.GLOWSTONE, Material.SEA_LANTERN, Material.REDSTONE_LAMP,
            Material.SHROOMLIGHT, Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT,
            Material.PEARLESCENT_FROGLIGHT, Material.LANTERN, Material.SOUL_LANTERN,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE
        )

        private val FROGLIGHTS: Set<Material> = EnumSet.of(
            Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT
        )

        private val SKULLS: Set<Material> = EnumSet.of(
            Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL, Material.ZOMBIE_HEAD,
            Material.PLAYER_HEAD, Material.CREEPER_HEAD, Material.PIGLIN_HEAD, Material.DRAGON_HEAD
        )
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBlockDamage(event: BlockDamageEvent) {
        val type = event.block.type
        val toolType = event.itemInHand.type

        // 1. Pickaxe on Glass, Light Sources & Cacti
        if (PICKAXES.contains(toolType)) {
            if (type == Material.GLASS || type == Material.TINTED_GLASS ||
                type.name.endsWith("_GLASS") || type.name.endsWith("_GLASS_PANE") ||
                LIGHT_SOURCES.contains(type) || type == Material.CACTUS
            ) {
                event.setInstaBreak(true)
                return
            }
        }

        // 2. Hoe on Cacti & Froglights
        if (HOES.contains(toolType)) {
            if (type == Material.CACTUS || FROGLIGHTS.contains(type)) {
                event.setInstaBreak(true)
                return
            }
        }

        // 3. Axe on Skulls & Heads
        if (AXES.contains(toolType) && SKULLS.contains(type)) {
            event.setInstaBreak(true)
            return
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onSilkTouchMine(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player
        val tool = player.inventory.itemInMainHand

        val hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH)

        // Budding Amethyst
        if (block.type == Material.BUDDING_AMETHYST && hasSilkTouch && PICKAXES.contains(tool.type)) {
            event.isDropItems = false
            block.world.dropItemNaturally(block.location, ItemStack(Material.BUDDING_AMETHYST))
            return
        }

        // Reinforced Deepslate: Netherite Pickaxe + Silk Touch ONLY
        if (block.type == Material.REINFORCED_DEEPSLATE) {
            if (tool.type == Material.NETHERITE_PICKAXE && hasSilkTouch) {
                event.isDropItems = false
                block.world.dropItemNaturally(block.location, ItemStack(Material.REINFORCED_DEEPSLATE))
            }
        }
    }
}