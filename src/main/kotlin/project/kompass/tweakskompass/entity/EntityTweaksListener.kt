package project.kompass.tweakskompass.entity

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftLivingEntity
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.*
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import project.kompass.tweakskompass.TweaksKompass

class EntityTweaksListener(private val plugin: TweaksKompass) : Listener {

    private var isIllusionerDarknessEnabled = true
    private var isXpDropsEnabled = true
    private var isPreventFriendlyFireEnabled = true
    private var isArmorStandsHaveArmsEnabled = true
    private var isGolemIgnoreZombieVillagersEnabled = true
    private var isNoDaylightBurningEnabled = true
    private var isNoOverworldZombificationEnabled = true
    private var isPreventCropTrampleEnabled = true
    private var isProtectTurtleEggsEnabled = true

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isIllusionerDarknessEnabled = cfg.getBoolean("entity-tweaks.illusioner-darkness", true)
        isXpDropsEnabled = cfg.getBoolean("entity-tweaks.environmental-xp-drops", true)
        isPreventFriendlyFireEnabled = cfg.getBoolean("entity-tweaks.prevent-illager-friendly-fire", true)
        isArmorStandsHaveArmsEnabled = cfg.getBoolean("entity-tweaks.armor-stands-have-arms", true)
        isGolemIgnoreZombieVillagersEnabled = cfg.getBoolean("entity-tweaks.iron-golem-ignore-zombie-villagers", true)
        isNoDaylightBurningEnabled = cfg.getBoolean("entity-tweaks.no-daylight-burning", true)
        isNoOverworldZombificationEnabled = cfg.getBoolean("entity-tweaks.no-overworld-zombification", true)
        isPreventCropTrampleEnabled = cfg.getBoolean("entity-tweaks.prevent-crop-trample", true)
        isProtectTurtleEggsEnabled = cfg.getBoolean("entity-tweaks.protect-turtle-eggs", true)
    }

    // 1. Illusioner Spell Nerf (3s Blindness -> 10s Darkness)
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onIllusionerSpellBlindness(event: EntityPotionEffectEvent) {
        if (!isIllusionerDarknessEnabled) return
        val player = event.entity as? Player ?: return

        if (event.modifiedType == PotionEffectType.BLINDNESS) {
            if (event.action == EntityPotionEffectEvent.Action.ADDED || event.action == EntityPotionEffectEvent.Action.CHANGED) {
                val newEffect = event.newEffect
                if (newEffect != null && newEffect.duration >= 380 && event.cause == EntityPotionEffectEvent.Cause.ATTACK) {
                    val nearbyIllusioner = player.getNearbyEntities(32.0, 32.0, 32.0).any { it is Illusioner }

                    if (nearbyIllusioner) {
                        event.isCancelled = true
                        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true))

                        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                            if (player.isOnline && !player.isDead) {
                                player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 200, 0, false, true, true))
                            }
                        }, 60L)
                    }
                }
            }
        }
    }

    // 2. Environmental Mob XP Drops
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onMobDeathXp(event: EntityDeathEvent) {
        if (!isXpDropsEnabled) return
        val entity = event.entity
        if (entity is Player || event.droppedExp > 0) return

        val damageEvent = entity.lastDamageCause ?: return
        val cause = damageEvent.cause

        val shouldDropXp = when (cause) {
            DamageCause.WITHER, DamageCause.HOT_FLOOR, DamageCause.CRAMMING,
            DamageCause.LAVA, DamageCause.FIRE_TICK, DamageCause.FALL -> true
            DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK -> {
                (damageEvent as? EntityDamageByEntityEvent)?.damager is IronGolem
            }
            DamageCause.CONTACT -> isPointedDripstone(damageEvent) || isStonecutter(damageEvent)
            DamageCause.FALLING_BLOCK -> isPointedDripstone(damageEvent)
            else -> false
        }

        if (shouldDropXp) {
            try {
                val nmsEntity = (entity as CraftLivingEntity).handle
                val nmsServerLevel = (entity.world as CraftWorld).handle
                event.droppedExp = nmsEntity.getExperienceReward(nmsServerLevel, null)
            } catch (ignored: Throwable) {
                event.droppedExp = when (entity) {
                    is Slime -> entity.size
                    else -> 5
                }
            }
        }
    }

    private fun isPointedDripstone(event: EntityDamageEvent): Boolean {
        return if (event is EntityDamageByBlockEvent) {
            event.damager?.type == Material.POINTED_DRIPSTONE
        } else if (event is EntityDamageByEntityEvent) {
            val damager = event.damager
            damager is FallingBlock && damager.blockData.material == Material.POINTED_DRIPSTONE
        } else false
    }

    private fun isStonecutter(event: EntityDamageEvent): Boolean {
        return event is EntityDamageByBlockEvent && event.damager?.type == Material.STONECUTTER
    }

    // 3. Prevent Friendly Fire Between Illagers & Skeletons
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onFriendlyFireDamage(event: EntityDamageByEntityEvent) {
        if (!isPreventFriendlyFireEnabled) return
        var damager = event.damager
        val damaged = event.entity

        if (damager is Projectile) {
            val shooter = damager.shooter
            if (shooter is Entity) damager = shooter
        }

        if (damager is AbstractSkeleton && damaged is AbstractSkeleton) {
            event.isCancelled = true
            return
        }

        if (isRaiderOrWitch(damager) && isRaiderOrWitch(damaged)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onFriendlyTarget(event: EntityTargetEvent) {
        if (!isPreventFriendlyFireEnabled) return
        val target = event.target ?: return
        val entity = event.entity

        if (entity is AbstractSkeleton && target is AbstractSkeleton) {
            event.isCancelled = true
            return
        }

        if (isRaiderOrWitch(entity) && isRaiderOrWitch(target)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onFriendlyProjectileHit(event: ProjectileHitEvent) {
        if (!isPreventFriendlyFireEnabled) return
        val hitEntity = event.hitEntity ?: return
        val shooter = event.entity.shooter as? Entity ?: return

        if (shooter is AbstractSkeleton && hitEntity is AbstractSkeleton) {
            event.isCancelled = true
            return
        }

        if (isRaiderOrWitch(shooter) && isRaiderOrWitch(hitEntity)) {
            event.isCancelled = true
            return
        }
    }

    private fun isRaiderOrWitch(entity: Entity): Boolean = entity is Raider || entity is Witch

    // 4. Armor Stands Have Arms
    @EventHandler(priority = EventPriority.NORMAL)
    fun onArmorStandSpawn(event: EntitySpawnEvent) {
        if (!isArmorStandsHaveArmsEnabled) return
        (event.entity as? ArmorStand)?.setArms(true)
    }

    // 5. Iron Golems Protect Zombie Villagers
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onGolemTargetZombieVillager(event: EntityTargetLivingEntityEvent) {
        if (!isGolemIgnoreZombieVillagersEnabled) return
        if (event.entity is IronGolem && event.target is ZombieVillager) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onGolemDamageZombieVillager(event: EntityDamageByEntityEvent) {
        if (!isGolemIgnoreZombieVillagersEnabled) return
        if (event.damager is IronGolem && event.entity is ZombieVillager) {
            event.isCancelled = true
        }
    }

    // 6. Daylight Burning Prevention
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDaylightCombust(event: EntityCombustEvent) {
        if (!isNoDaylightBurningEnabled) return
        val entity = event.entity
        if (event !is EntityCombustByBlockEvent && event !is EntityCombustByEntityEvent) {
            if (entity is Zombie || entity is AbstractSkeleton || entity is Phantom) {
                event.isCancelled = true
            }
        }
    }

    // 7. Overworld Zombification Prevention
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onZombification(event: EntityTransformEvent) {
        if (!isNoOverworldZombificationEnabled) return
        if (event.transformReason == EntityTransformEvent.TransformReason.PIGLIN_ZOMBIFIED) {
            event.isCancelled = true
        }
    }

    // 8. Crop Trampling Prevention
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCropTrample(event: PlayerInteractEvent) {
        if (!isPreventCropTrampleEnabled) return
        if (event.action == Action.PHYSICAL && event.clickedBlock?.type == Material.FARMLAND) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityCropTrample(event: EntityInteractEvent) {
        if (!isPreventCropTrampleEnabled) return
        if (event.block.type == Material.FARMLAND) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onFarmlandBlockChange(event: EntityChangeBlockEvent) {
        if (!isPreventCropTrampleEnabled) return
        if (event.to == Material.DIRT && event.block.type == Material.FARMLAND) {
            event.isCancelled = true
        }
    }

    // 9. Turtle Egg Protection
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerEggStep(event: PlayerInteractEvent) {
        if (!isProtectTurtleEggsEnabled) return
        if (event.action == Action.PHYSICAL && event.clickedBlock?.type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityEggStep(event: EntityInteractEvent) {
        if (!isProtectTurtleEggsEnabled) return
        if (event.block.type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEggBlockChange(event: EntityChangeBlockEvent) {
        if (!isProtectTurtleEggsEnabled) return
        if (event.block.type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEggBreak(event: BlockBreakEvent) {
        if (!isProtectTurtleEggsEnabled) return
        if (event.player.gameMode == org.bukkit.GameMode.CREATIVE) return
        if (event.block.type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }

    // 10. Axolotl Tweaks: Breed with Tropical Fish & Ignore Passive Mobs
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onAxolotlBreedWithFishItem(event: PlayerInteractEntityEvent) {
        val axolotl = event.rightClicked as? Axolotl ?: return
        if (!axolotl.isAdult || !axolotl.canBreed() || axolotl.isLoveMode) return

        val player = event.player
        val hand = event.hand
        val item = player.inventory.getItem(hand)

        if (item.type == Material.TROPICAL_FISH) {
            event.isCancelled = true

            axolotl.setLoveModeTicks(600)
            axolotl.world.spawnParticle(Particle.HEART, axolotl.location.add(0.0, 0.5, 0.0), 5, 0.2, 0.2, 0.2, 0.0)
            axolotl.world.playSound(axolotl.location, Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f)

            if (player.gameMode != org.bukkit.GameMode.CREATIVE) {
                if (item.amount <= 1) player.inventory.setItem(hand, null) else item.amount -= 1
            }

            val adv = Bukkit.getAdvancement(NamespacedKey.minecraft("husbandry/breed_an_animal"))
            if (adv != null) {
                val progress = player.getAdvancementProgress(adv)
                if (!progress.isDone) {
                    progress.remainingCriteria.forEach { progress.awardCriteria(it) }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onAxolotlTarget(event: EntityTargetLivingEntityEvent) {
        val axolotl = event.entity as? Axolotl ?: return
        val target = event.target ?: return

        if (target is Fish || target is Squid || target is GlowSquid || target is Tadpole) {
            event.isCancelled = true
        }
    }
}