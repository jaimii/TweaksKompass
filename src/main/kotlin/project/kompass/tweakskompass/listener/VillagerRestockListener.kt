package project.kompass.tweakskompass.listener

import io.papermc.paper.event.player.PlayerTradeEvent
import org.bukkit.entity.AbstractVillager
import org.bukkit.entity.WanderingTrader
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.VillagerAcquireTradeEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import project.kompass.tweakskompass.TweaksKompass

class VillagerRestockListener(private val plugin: TweaksKompass) : Listener {

    // Pre-cached config flags eliminate YAML lookups during player trading
    private var isEnabled = true
    private var midTradeRestock = true
    private var openGuiRestock = true
    private var includeWanderingTraders = true
    private var clearPriceInflation = false

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        isEnabled = plugin.config.getBoolean("villager-restock.enabled", true)
        midTradeRestock = plugin.config.getBoolean("villager-restock.mid-trade-restock", true)
        openGuiRestock = plugin.config.getBoolean("villager-restock.open-gui-restock", true)
        includeWanderingTraders = plugin.config.getBoolean("villager-restock.include-wandering-traders", true)
        clearPriceInflation = plugin.config.getBoolean("villager-restock.clear-price-inflation", false)
    }

    private fun shouldProcess(merchant: AbstractVillager): Boolean {
        return if (merchant is WanderingTrader) includeWanderingTraders else true
    }

    private fun restockRecipes(merchant: AbstractVillager) {
        val recipes = merchant.recipes
        var changed = false

        for (recipe in recipes) {
            if (recipe.uses > 0) {
                recipe.uses = 0
                changed = true
            }
            if (clearPriceInflation && recipe.demand > 0) {
                recipe.demand = 0
                changed = true
            }
        }

        // Only commit updates to the entity if a modification actually occurred
        if (changed) {
            merchant.recipes = recipes
        }
    }

    // 1. Instant restock when opening the trade menu
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onVillagerInteract(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!isEnabled || !openGuiRestock) return

        val villager = event.rightClicked as? AbstractVillager ?: return
        if (!shouldProcess(villager)) return

        restockRecipes(villager)
    }

    // 2. Instant restock while actively trading (prevents lockouts during bulk trades)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTrade(event: PlayerTradeEvent) {
        if (!isEnabled || !midTradeRestock) return

        event.trade.uses = 0

        if (clearPriceInflation) {
            event.trade.demand = 0
        }

        val villager = event.villager
        if (villager is AbstractVillager && shouldProcess(villager)) {
            restockRecipes(villager)
        }
    }

    // 3. Ensure newly unlocked trades on villager level-up start with 0 uses
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onAcquireTrade(event: VillagerAcquireTradeEvent) {
        if (!isEnabled) return

        val recipe = event.recipe
        recipe.uses = 0
        event.recipe = recipe
    }
}