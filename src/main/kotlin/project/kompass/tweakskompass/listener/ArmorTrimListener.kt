package project.kompass.tweakskompass.listener

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.entity.Piglin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.trim.TrimMaterial
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import project.kompass.tweakskompass.TweaksKompass
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ArmorTrimListener(private val plugin: TweaksKompass) : Listener {

    private sealed interface TrimState {
        data object None : TrimState
        data object Mixed : TrimState
        data class Matching(val pattern: String) : TrimState
    }

    // High-performance cache: Maps player UUID -> Pre-computed trim state
    private val playerTrimStates = ConcurrentHashMap<UUID, TrimState>()
    private val appliedEffects = ConcurrentHashMap<UUID, MutableSet<PotionEffectType>>()
    private val patternEffects = HashMap<String, List<Pair<PotionEffectType, Int>>>()
    private val patternParticles = HashMap<String, Particle>()

    private var isEnabled = true
    private var isGoldTrimPacifyEnabled = true
    private var isEffectsEnabled = true
    private var refreshDurationTicks = 100
    private var isMixedAbsorptionEnabled = true
    private var mixedAbsorptionAmplifier = 1
    private var isParticlesEnabled = true
    private var particleCount = 3

    init {
        setupParticleMappings()
        reloadConfig()
    }

    private fun setupParticleMappings() {
        patternParticles["SILENCE"] = Particle.SCULK_SOUL
        patternParticles["FLOW"] = Particle.SMALL_GUST
        patternParticles["TIDE"] = Particle.BUBBLE_POP
        patternParticles["BOLT"] = Particle.ELECTRIC_SPARK
        patternParticles["SPIRE"] = Particle.ENCHANT
        patternParticles["RIB"] = Particle.SOUL_FIRE_FLAME
        patternParticles["VEX"] = Particle.NOTE
        patternParticles["WILD"] = Particle.INFESTED
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("armor-trims.enabled", true)
        isGoldTrimPacifyEnabled = cfg.getBoolean("armor-trims.gold-trim-piglin-pacify", true)
        isEffectsEnabled = cfg.getBoolean("armor-trims.effects.enabled", true)
        refreshDurationTicks = cfg.getInt("armor-trims.effects.refresh-seconds", 5) * 20
        isMixedAbsorptionEnabled = cfg.getBoolean("armor-trims.effects.mixed-trims-absorption", true)
        mixedAbsorptionAmplifier = cfg.getInt("armor-trims.effects.mixed-absorption-amplifier", 1)
        isParticlesEnabled = cfg.getBoolean("armor-trims.particles.enabled", true)
        particleCount = cfg.getInt("armor-trims.particles.count", 3)

        patternEffects.clear()
        val section = cfg.getConfigurationSection("armor-trims.effects.patterns") ?: return

        for (patternKey in section.getKeys(false)) {
            val list = section.getStringList(patternKey)
            val parsedList = mutableListOf<Pair<PotionEffectType, Int>>()

            for (entry in list) {
                val parts = entry.split(",")
                if (parts.size == 2) {
                    val effectName = parts[0].trim().lowercase()
                    val key = org.bukkit.NamespacedKey.fromString(effectName) ?: org.bukkit.NamespacedKey.minecraft(effectName)
                    val effectType = org.bukkit.Registry.POTION_EFFECT_TYPE.get(key) ?: continue
                    val amplifier = parts[1].trim().toIntOrNull() ?: 0
                    parsedList.add(Pair(effectType, amplifier))
                }
            }

            if (parsedList.isNotEmpty()) {
                patternEffects[patternKey.uppercase()] = parsedList
            }
        }

        // Re-evaluate online players on reload
        for (player in Bukkit.getOnlinePlayers()) {
            recalculateTrimState(player)
        }
    }

    fun startTask() {
        object : BukkitRunnable() {
            override fun run() {
                if (!isEnabled) return

                // ZERO-ALLOCATION TICK LOOP: Simply reads pre-cached states
                for (player in Bukkit.getOnlinePlayers()) {
                    if (!player.isValid || player.isDead || player.gameMode == GameMode.SPECTATOR) {
                        clearEffects(player)
                        continue
                    }

                    when (val state = playerTrimStates[player.uniqueId] ?: TrimState.None) {
                        is TrimState.None -> clearEffects(player)
                        is TrimState.Mixed -> applyMixedTrimEffects(player)
                        is TrimState.Matching -> {
                            applyMatchingTrimEffects(player, state.pattern)
                            spawnTrimParticles(player, state.pattern)
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L)
    }

    // Only evaluates ItemMeta when armor is physically changed!
    private fun recalculateTrimState(player: Player) {
        val armor = player.inventory.armorContents

        if (armor.size < 4) {
            playerTrimStates[player.uniqueId] = TrimState.None
            return
        }

        var firstPattern: String? = null
        var allMatch = true

        for (i in 0 until 4) {
            val piece = armor[i]
            if (piece == null || piece.type.isAir || !piece.hasItemMeta()) {
                playerTrimStates[player.uniqueId] = TrimState.None
                return
            }

            val pattern = getTrimPatternName(piece) ?: run {
                playerTrimStates[player.uniqueId] = TrimState.None
                return
            }

            if (i == 0) {
                firstPattern = pattern
            } else if (pattern != firstPattern) {
                allMatch = false
            }
        }

        val pattern = firstPattern ?: run {
            playerTrimStates[player.uniqueId] = TrimState.None
            return
        }

        playerTrimStates[player.uniqueId] = if (allMatch) {
            TrimState.Matching(pattern)
        } else {
            TrimState.Mixed
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onArmorChange(event: PlayerArmorChangeEvent) {
        if (!isEnabled) return
        recalculateTrimState(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        recalculateTrimState(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (event.player.isOnline) {
                recalculateTrimState(event.player)
            }
        })
    }

    private fun applyMatchingTrimEffects(player: Player, patternName: String) {
        if (!isEffectsEnabled) return
        val effects = patternEffects[patternName] ?: run {
            clearEffects(player)
            return
        }

        val activeSet = appliedEffects.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }

        activeSet.removeIf { type ->
            if (effects.none { it.first == type }) {
                player.removePotionEffect(type)
                true
            } else false
        }

        for ((type, amplifier) in effects) {
            val current = player.getPotionEffect(type)
            if (current == null || current.duration <= 40) {
                player.addPotionEffect(PotionEffect(type, refreshDurationTicks, amplifier, true, false, true))
            }
            activeSet.add(type)
        }
    }

    private fun applyMixedTrimEffects(player: Player) {
        if (!isEffectsEnabled || !isMixedAbsorptionEnabled) {
            clearEffects(player)
            return
        }

        val activeSet = appliedEffects.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }

        activeSet.removeIf { type ->
            if (type != PotionEffectType.ABSORPTION) {
                player.removePotionEffect(type)
                true
            } else false
        }

        val current = player.getPotionEffect(PotionEffectType.ABSORPTION)
        if (current == null || current.duration <= 40) {
            player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, refreshDurationTicks, mixedAbsorptionAmplifier, true, false, true))
        }
        activeSet.add(PotionEffectType.ABSORPTION)
    }

    private fun spawnTrimParticles(player: Player, patternName: String) {
        if (!isParticlesEnabled) return
        val particle = patternParticles[patternName] ?: return
        val loc = player.location.add(0.0, 1.0, 0.0)
        player.world.spawnParticle(particle, loc, particleCount, 0.3, 0.5, 0.3, 0.01)
    }

    private fun clearEffects(player: Player) {
        val set = appliedEffects.remove(player.uniqueId) ?: return
        for (type in set) {
            player.removePotionEffect(type)
        }
    }

    private fun getTrimPatternName(item: ItemStack): String? {
        val meta = item.itemMeta as? ArmorMeta ?: return null
        val trim = meta.trim ?: return null

        return RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.TRIM_PATTERN)
            .getKey(trim.pattern)
            ?.key
            ?.uppercase()
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPiglinTarget(event: EntityTargetLivingEntityEvent) {
        if (!isGoldTrimPacifyEnabled) return
        if (event.entity !is Piglin) return
        val player = event.target as? Player ?: return

        val reason = event.reason
        if (reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY ||
            reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY) {
            return
        }

        if (hasGoldTrimArmor(player)) {
            event.isCancelled = true
        }
    }

    private fun hasGoldTrimArmor(player: Player): Boolean {
        for (item in player.inventory.armorContents) {
            if (item == null || item.type.isAir || !item.hasItemMeta()) continue
            val meta = item.itemMeta as? ArmorMeta ?: continue
            val trim = meta.trim ?: continue
            if (trim.material == TrimMaterial.GOLD) return true
        }
        return false
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        playerTrimStates.remove(uuid)
        clearEffects(event.player)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        appliedEffects.remove(event.player.uniqueId)
    }
}