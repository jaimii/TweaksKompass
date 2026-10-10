package project.kompass.tweakskompass

import project.kompass.tweakskompass.listener.*
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class TweaksKompass : JavaPlugin() {

    private lateinit var speedLimiter: ElytraSpeedLimiterListener
    private lateinit var speedometer: ElytraSpeedometerListener
    private lateinit var boostJumpHandler: ElytraBoostJumpListener
    private lateinit var bombingHandler: ElytraBombingListener

    override fun onEnable() {
        saveDefaultConfig()

        val pm = server.pluginManager

        // Instantiate Elytra listeners
        speedLimiter = ElytraSpeedLimiterListener(this)
        speedometer = ElytraSpeedometerListener(this)
        boostJumpHandler = ElytraBoostJumpListener(this)
        bombingHandler = ElytraBombingListener(this)

        // Register All Listeners
        pm.registerEvents(TridentAttributeListener(), this)
        pm.registerEvents(TridentRiptideListener(), this)
        pm.registerEvents(TridentChannelingListener(this), this)
        pm.registerEvents(TridentDamageListener(), this)
        pm.registerEvents(RangedLootingListener(), this)
        pm.registerEvents(TridentAnvilListener(), this)
        pm.registerEvents(SpearListener(), this)
        pm.registerEvents(ArmorDurabilityListener(this), this)
        pm.registerEvents(PotionSoupStackListener(), this)
        pm.registerEvents(BundleCapacityListener(this), this)
        pm.registerEvents(AlwaysEatListener(this), this)
        pm.registerEvents(EnchantablePetArmorListener(), this)
        pm.registerEvents(WindChargeListener(this), this)
        pm.registerEvents(TurtleScuteBrushListener(this), this)

        // Register BetterElytras listeners
        pm.registerEvents(speedLimiter, this)
        pm.registerEvents(speedometer, this)
        pm.registerEvents(boostJumpHandler, this)
        pm.registerEvents(bombingHandler, this)

        // Background Tasks
        val copperArmorListener = CopperArmorListener()
        pm.registerEvents(copperArmorListener, this)
        copperArmorListener.startArmorCheckTask(this)
        copperArmorListener.startParticleTask(this)

        val campfireListener = MagicalCampfireListener(this)
        pm.registerEvents(campfireListener, this)
        campfireListener.startCampfireTask()

        logger.info("TweaksKompass has been successfully enabled.")
    }

    override fun onDisable() {
        logger.info("TweaksKompass has been successfully disabled.")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("tweakskompass", ignoreCase = true)) {
            if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
                if (!sender.hasPermission("tweakskompass.admin")) {
                    sender.sendMessage("§cYou do not have permission to execute this command.")
                    return true
                }
                reloadConfig()
                speedLimiter.updateValues()
                speedometer.updateValues()
                boostJumpHandler.updateValues()
                bombingHandler.updateValues()
                sender.sendMessage("§a[TweaksKompass] Configuration reloaded successfully.")
                return true
            }
        }
        return false
    }
}