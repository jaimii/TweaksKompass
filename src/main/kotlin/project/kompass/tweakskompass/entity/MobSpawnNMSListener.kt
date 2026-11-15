package project.kompass.tweakskompass.entity

import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal
import net.minecraft.world.entity.ai.goal.FleeSunGoal
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RangedAttackGoal
import net.minecraft.world.entity.ai.goal.RestrictSunGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.WrappedGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.animal.golem.IronGolem as NmsIronGolem
import net.minecraft.world.entity.monster.Giant as NmsGiant
import net.minecraft.world.entity.monster.Witch as NmsWitch
import net.minecraft.world.entity.monster.piglin.Piglin as NmsPiglin
import net.minecraft.world.entity.monster.piglin.PiglinBrute as NmsPiglinBrute
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.monster.zombie.Zombie as NmsZombie
import net.minecraft.world.entity.player.Player as NmsPlayer
import net.minecraft.world.entity.raid.Raider as NmsRaider
import org.bukkit.craftbukkit.entity.*
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import project.kompass.tweakskompass.TweaksKompass

class MobSpawnNMSListener(private val plugin: TweaksKompass) : Listener {

    private fun unwrapGoal(goal: Goal): Goal {
        return if (goal is WrappedGoal) goal.goal else goal
    }

    @EventHandler
    fun onMobSpawn(event: EntitySpawnEvent) {
        if (!plugin.config.getBoolean("entity-tweaks.custom-mob-ai", true)) return
        val entity = event.entity

        // 1. Raiders (Illagers)
        if (entity is Raider) {
            val nms = (entity as CraftRaider).handle
            nms.goalSelector.addGoal(1, AvoidEntityGoal(nms, NmsGiant::class.java, 24.0f, 1.0, 1.2))
            nms.goalSelector.addGoal(1, AvoidEntityGoal(nms, Warden::class.java, 16.0f, 1.0, 1.2))

            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsZombie::class.java, true))
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsPiglin::class.java, true))
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsPiglinBrute::class.java, true))
        }

        // 2. Witches
        else if (entity is Witch) {
            val nms = (entity as CraftWitch).handle
            nms.goalSelector.removeAllGoals { true }
            nms.targetSelector.removeAllGoals { true }

            nms.goalSelector.addGoal(0, FloatGoal(nms))
            nms.goalSelector.addGoal(2, RangedAttackGoal(nms, 1.0, 15, 12.0f))
            nms.goalSelector.addGoal(3, WaterAvoidingRandomStrollGoal(nms, 1.0))

            nms.targetSelector.addGoal(1, HurtByTargetGoal(nms))
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsPlayer::class.java, true))
            nms.targetSelector.addGoal(3, NearestAttackableTargetGoal(nms, NmsZombie::class.java, true))
            nms.targetSelector.addGoal(4, NearestAttackableTargetGoal(nms, NmsPiglin::class.java, true))
        }

        // 3. Zombies
        else if (entity is Zombie) {
            val nms = (entity as CraftZombie).handle
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsRaider::class.java, true))
            nms.targetSelector.addGoal(3, NearestAttackableTargetGoal(nms, NmsWitch::class.java, true))
        }

        // 4. Skeletons, Strays, Bogged, Parched (Sun-avoidance purge)
        else if (entity is AbstractSkeleton) {
            val nms = (entity as CraftAbstractSkeleton).handle
            nms.goalSelector.removeAllGoals { goal ->
                val inner = unwrapGoal(goal)
                inner is FleeSunGoal || inner is RestrictSunGoal
            }
        }

        // 5. Giants
        else if (entity is Giant) {
            val nms = (entity as CraftGiant).handle
            nms.goalSelector.addGoal(0, MeleeAttackGoal(nms, 1.0, true))
            nms.targetSelector.addGoal(1, NearestAttackableTargetGoal(nms, NmsPlayer::class.java, true))
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsRaider::class.java, true))
            nms.targetSelector.addGoal(3, NearestAttackableTargetGoal(nms, NmsWitch::class.java, true))
            nms.targetSelector.addGoal(4, NearestAttackableTargetGoal(nms, NmsPiglin::class.java, true))

            nms.getAttribute(Attributes.FOLLOW_RANGE)?.baseValue = 48.0
        }

        // 6. Piglins & Piglin Brutes (Smart-cast avoids redundant casting warning)
        else if (entity is PiglinAbstract) {
            entity.isImmuneToZombification = true
        }

        // 7. Hoglins
        else if (entity is Hoglin) {
            entity.isImmuneToZombification = true
        }

        // 8. Iron Golems (Purge vanilla target goals so GolemAttackTask controls targeting)
        else if (entity is IronGolem) {
            val nms = (entity as CraftIronGolem).handle
            nms.targetSelector.removeAllGoals { goal ->
                unwrapGoal(goal) is NearestAttackableTargetGoal<*>
            }
        }

        // 9. Creepers (Flee from Iron Golems)
        else if (entity is Creeper) {
            val nms = (entity as CraftCreeper).handle
            nms.goalSelector.addGoal(1, AvoidEntityGoal(nms, NmsIronGolem::class.java, 16.0f, 1.0, 1.2))
        }
    }
}