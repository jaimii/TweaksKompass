package project.kompass.tweakskompass.listener

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import project.kompass.tweakskompass.TweaksKompass
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class UpwardKnockbackListener(private val plugin: TweaksKompass) : Listener {

    // Short-lived same-tick handoff from Damage event to Knockback event
    private val pendingKnockback = ConcurrentHashMap<UUID, Vector>()

    private var isEnabled = true
    private var horizontalMultiplier = 0.15
    private var verticalBoost = 0.55
    private var sprintMultiplier = 1.5
    private var critMultiplier = 1.35
    private var kbEnchantMultiplier = 0.5
    private var applyBonusesToVertical = true
    private var activeOnShield = false

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("upward-knockback.enabled", true)
        horizontalMultiplier = cfg.getDouble("upward-knockback.horizontal-multiplier", 0.15)
        verticalBoost = cfg.getDouble("upward-knockback.vertical-boost", 0.55)
        sprintMultiplier = cfg.getDouble("upward-knockback.sprint-multiplier", 1.5)
        critMultiplier = cfg.getDouble("upward-knockback.crit-multiplier", 1.35)
        kbEnchantMultiplier = cfg.getDouble("upward-knockback.kb-enchant-multiplier", 0.5)
        applyBonusesToVertical = cfg.getBoolean("upward-knockback.apply-bonuses-to-vertical", true)
        activeOnShield = cfg.getBoolean("upward-knockback.active-on-shield", false)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (!isEnabled) return
        if (event.cause == EntityDamageEvent.DamageCause.THORNS) return

        val victim = event.entity as? LivingEntity ?: return
        val attacker = event.damager

        // Ignore Wind Charges so WindChargeListener handles wind-jumping natively
        if (attacker is AbstractWindCharge) return

        val kbVector = calculateKnockback(victim, attacker, event.isCritical)
        pendingKnockback[victim.uniqueId] = kbVector
        victim.velocity = kbVector
    }

    // Intercepts vanilla's post-damage knockback so vanilla does not clamp Y-velocity to 0.4
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityKnockback(event: EntityKnockbackByEntityEvent) {
        if (!isEnabled) return
        val customKb = pendingKnockback.remove(event.entity.uniqueId) ?: return

        event.isCancelled = true
        event.entity.velocity = customKb
    }

    private fun calculateKnockback(victim: LivingEntity, attacker: Entity, isEventCritical: Boolean): Vector {
        var dirX: Double
        var dirZ: Double
        var knockbackLevel = 0

        // 1. Direction & Enchantment Calculation
        if (attacker is Projectile) {
            val prjVel = attacker.velocity
            val hLenSq = prjVel.x * prjVel.x + prjVel.z * prjVel.z

            if (hLenSq >= 0.0001) {
                val invLen = 1.0 / sqrt(hLenSq)
                dirX = prjVel.x * invLen
                dirZ = prjVel.z * invLen
            } else {
                val dx = victim.x - attacker.x
                val dz = victim.z - attacker.z
                val dLenSq = dx * dx + dz * dz
                if (dLenSq >= 0.0001) {
                    val invLen = 1.0 / sqrt(dLenSq)
                    dirX = dx * invLen
                    dirZ = dz * invLen
                } else {
                    dirX = 0.0
                    dirZ = 0.0
                }
            }

            if (attacker is AbstractArrow) {
                val weapon = attacker.weapon
                if (weapon != null && !weapon.type.isAir) {
                    knockbackLevel += weapon.getEnchantmentLevel(Enchantment.PUNCH)
                    knockbackLevel += weapon.getEnchantmentLevel(Enchantment.KNOCKBACK)
                }
            }
        } else {
            // Direct trig from primitive entity.yaw (0 Location allocations)
            val angle = Math.toRadians(attacker.yaw.toDouble())
            dirX = -sin(angle)
            dirZ = cos(angle)
        }

        var extraHkb = 1.0
        var extraVkb = 1.0

        // 2. Attacker Sprint, Critical, and Melee Weapon Checks
        if (attacker is Player) {
            if (attacker.isSprinting) {
                extraHkb = sprintMultiplier
            }
            if (isEventCritical || isCriticalFallback(attacker)) {
                extraVkb = critMultiplier
            }
            val weapon = attacker.inventory.itemInMainHand
            if (!weapon.type.isAir) {
                knockbackLevel += weapon.getEnchantmentLevel(Enchantment.KNOCKBACK)
                knockbackLevel += weapon.getEnchantmentLevel(Enchantment.PUNCH)
            }
        } else if (attacker is LivingEntity) {
            val weapon = attacker.equipment?.itemInMainHand
            if (weapon != null && !weapon.type.isAir) {
                knockbackLevel += weapon.getEnchantmentLevel(Enchantment.KNOCKBACK)
                knockbackLevel += weapon.getEnchantmentLevel(Enchantment.PUNCH)
            }
        }

        // 3. Accurate 2D Horizontal Shield Block Check
        if (victim is Player && victim.isBlocking && !activeOnShield) {
            val victimAngle = Math.toRadians(victim.yaw.toDouble())
            val lookX = -sin(victimAngle)
            val lookZ = cos(victimAngle)
            val toAttackerX = attacker.x - victim.x
            val toAttackerZ = attacker.z - victim.z

            if (lookX * toAttackerX + lookZ * toAttackerZ >= 0.0) {
                return Vector(0.0, 0.0, 0.0)
            }
        }

        val enchantBonus = knockbackLevel * kbEnchantMultiplier
        extraHkb += enchantBonus

        if (applyBonusesToVertical) {
            if (attacker is Player && attacker.isSprinting) {
                extraVkb *= sprintMultiplier
            }
            extraVkb += enchantBonus
        }

        return Vector(
            dirX * horizontalMultiplier * extraHkb,
            verticalBoost * extraVkb,
            dirZ * horizontalMultiplier * extraHkb
        )
    }

    private fun isCriticalFallback(player: Player): Boolean {
        return player.fallDistance > 0.0f &&
                !(player as Entity).isOnGround &&
                !player.isInsideVehicle &&
                !player.isClimbing &&
                !player.isInWater &&
                !player.hasPotionEffect(PotionEffectType.BLINDNESS)
    }
}