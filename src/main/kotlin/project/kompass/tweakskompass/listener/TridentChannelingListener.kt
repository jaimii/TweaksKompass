package project.kompass.tweakskompass.listener

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.entity.*
import org.bukkit.persistence.PersistentDataType
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.util.TridentUtil
import java.time.Duration
import java.util.HashMap
import java.util.UUID

class TridentChannelingListener(private val plugin: TweaksKompass) : Listener {

    // Modern java.time.Duration replaces deprecated TimeUnit call
    private val protectedEntitiesCache: Cache<UUID, Boolean> = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .build()

    private val recentChannelingDeaths = HashMap<Location, Long>()

    @EventHandler
    fun onTridentHit(event: ProjectileHitEvent) {
        val trident = event.entity as? Trident ?: return
        val item = trident.itemStack

        if (item.containsEnchantment(Enchantment.CHANNELING)) {
            val hitEntity = event.hitEntity
            val hitBlock = event.hitBlock
            val targetLoc = hitEntity?.location?.clone() ?: hitBlock?.location?.clone() ?: trident.location.clone()
            val shooter = trident.shooter as? Player

            Bukkit.getScheduler().runTask(plugin, Runnable {
                val strike = trident.world.spawn(targetLoc, LightningStrike::class.java)
                strike.persistentDataContainer.set(TridentUtil.CHANNELING_LIGHTNING_KEY, PersistentDataType.BYTE, 1.toByte())
                shooter?.let { strike.causingPlayer = it }

                if (hitEntity is LivingEntity && hitEntity.isValid && !hitEntity.isDead) {
                    hitEntity.noDamageTicks = 0
                }
            })
        }
    }

    @EventHandler
    fun onMeleeHit(event: EntityDamageByEntityEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.THORNS) return

        val player = event.damager as? Player ?: return
        val item = player.inventory.itemInMainHand

        if (item.containsEnchantment(Enchantment.CHANNELING)) {
            val victim = event.entity
            val targetLoc = victim.location.clone()

            Bukkit.getScheduler().runTask(plugin, Runnable {
                val strike = victim.world.spawn(targetLoc, LightningStrike::class.java)
                strike.persistentDataContainer.set(TridentUtil.CHANNELING_LIGHTNING_KEY, PersistentDataType.BYTE, 1.toByte())
                strike.causingPlayer = player

                if (victim is LivingEntity && victim.isValid && !victim.isDead) {
                    victim.noDamageTicks = 0
                }
            })
        }
    }

    @Suppress("DEPRECATION")
    @EventHandler
    fun onLightningDamage(event: EntityDamageByEntityEvent) {
        val lightning = event.damager as? LightningStrike ?: return
        val entity = event.entity

        val isChanneling = lightning.persistentDataContainer.has(
            TridentUtil.CHANNELING_LIGHTNING_KEY,
            PersistentDataType.BYTE
        )

        if (entity is Item || entity is ExperienceOrb) {
            if (!isChanneling) {
                event.damage = 5.0
                return
            }

            if (protectedEntitiesCache.getIfPresent(entity.uniqueId) != null) {
                event.isCancelled = true
            } else {
                event.damage = 2.0
            }
            return
        }

        if (entity is Player && entity == lightning.causingPlayer) {
            event.isCancelled = true
            return
        }

        if (isChanneling) {
            event.damage = 2.0
        } else {
            event.damage = 5.0
        }

        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0.0)
        }
    }

    @EventHandler
    fun onMobDeath(event: EntityDeathEvent) {
        val mob = event.entity as? Mob ?: return
        var killedByChanneling = false

        val damageEvent = mob.lastDamageCause as? EntityDamageByEntityEvent
        if (damageEvent != null) {
            val damager = damageEvent.damager
            if (damager is Trident) {
                if (damager.itemStack.containsEnchantment(Enchantment.CHANNELING)) {
                    killedByChanneling = true
                }
            } else if (damager is Player) {
                val hand = damager.inventory.itemInMainHand
                if (hand.containsEnchantment(Enchantment.CHANNELING)) {
                    killedByChanneling = true
                }
            } else if (damager is LightningStrike) {
                if (damager.causingPlayer != null) {
                    killedByChanneling = true
                }
            }
        }

        if (killedByChanneling) {
            recentChannelingDeaths[mob.location] = mob.world.fullTime
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntitySpawn(event: EntitySpawnEvent) {
        if (recentChannelingDeaths.isEmpty()) return

        val entity = event.entity
        if (entity !is Item && entity !is ExperienceOrb) return

        val loc = entity.location
        val world = entity.world
        val currentTick = world.fullTime

        var isChannelingDrop = false
        val iterator = recentChannelingDeaths.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val deathLoc = entry.key
            val deathTick = entry.value

            if (currentTick - deathTick > 10L) {
                iterator.remove()
                continue
            }

            if (deathLoc.world == world && deathLoc.distanceSquared(loc) <= 4.0) {
                isChannelingDrop = true
            }
        }

        if (isChannelingDrop) {
            protectedEntitiesCache.put(entity.uniqueId, true)
            entity.fireTicks = 0
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity is Item || entity is ExperienceOrb) {
            if (protectedEntitiesCache.getIfPresent(entity.uniqueId) != null) {
                event.isCancelled = true
                entity.fireTicks = 0
            }
        }
    }

    @EventHandler
    fun onItemMerge(event: ItemMergeEvent) {
        val target = event.target
        val entity = event.entity

        if (protectedEntitiesCache.getIfPresent(entity.uniqueId) != null ||
            protectedEntitiesCache.getIfPresent(target.uniqueId) != null) {
            protectedEntitiesCache.put(target.uniqueId, true)
        }

        protectedEntitiesCache.invalidate(entity.uniqueId)
    }

    @EventHandler
    fun onItemPickup(event: EntityPickupItemEvent) {
        protectedEntitiesCache.invalidate(event.item.uniqueId)
    }

    @EventHandler
    fun onItemDespawn(event: ItemDespawnEvent) {
        protectedEntitiesCache.invalidate(event.entity.uniqueId)
    }

    @EventHandler
    fun onBlockIgnite(event: BlockIgniteEvent) {
        if (event.cause == BlockIgniteEvent.IgniteCause.LIGHTNING) {
            val igniter = event.ignitingEntity as? LightningStrike ?: return

            if (igniter.persistentDataContainer.has(TridentUtil.CHANNELING_LIGHTNING_KEY, PersistentDataType.BYTE)) {
                event.isCancelled = true
            }
        }
    }
}