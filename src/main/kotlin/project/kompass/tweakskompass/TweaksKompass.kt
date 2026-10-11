package project.kompass.tweakskompass

import project.kompass.tweakskompass.arrow.ArrowPacketListener
import project.kompass.tweakskompass.arrow.ArrowTrackingManager
import project.kompass.tweakskompass.leash.*
import project.kompass.tweakskompass.listener.*
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TweaksKompass : JavaPlugin() {

    private lateinit var speedLimiter: ElytraSpeedLimiterListener
    private lateinit var speedometer: ElytraSpeedometerListener
    private lateinit var boostJumpHandler: ElytraBoostJumpListener
    private lateinit var bombingHandler: ElytraBombingListener

    // Arrow Systems
    lateinit var arrowTrackingManager: ArrowTrackingManager
        private set
    val customArrowVelocities = ConcurrentHashMap<UUID, Vector>()
    val cachedArrowMultipliers = HashMap<String, Double>()
    var defaultArrowMultiplier: Double = 1.0
        private set

    // Leash Systems
    lateinit var leashRegistry: LeashRegistry
        private set
    lateinit var leashManager: LeashManager
        private set

    override fun onEnable() {
        saveDefaultConfig()
        loadCachedArrowMultipliers()

        val pm = server.pluginManager

        // Elytra listeners
        speedLimiter = ElytraSpeedLimiterListener(this)
        speedometer = ElytraSpeedometerListener(this)
        boostJumpHandler = ElytraBoostJumpListener(this)
        bombingHandler = ElytraBombingListener(this)

        // Initialize Arrow Systems
        arrowTrackingManager = ArrowTrackingManager(this).apply { start() }
        ArrowPacketListener.register(this)

        // Initialize Leash Systems
        leashRegistry = LeashRegistry()
        leashManager = LeashManager(this)

        // Register Core Listeners
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

        // Register Elytras listeners
        pm.registerEvents(speedLimiter, this)
        pm.registerEvents(speedometer, this)
        pm.registerEvents(boostJumpHandler, this)
        pm.registerEvents(bombingHandler, this)

        // Register Arrow listener
        pm.registerEvents(ArrowSpeedListener(this), this)

        // Register Leash listeners
        pm.registerEvents(LeashInteractListener(this, leashRegistry), this)
        pm.registerEvents(LeashSnapListener(this), this)
        pm.registerEvents(LeashTeleportListener(this), this)
        pm.registerEvents(LeashPortalListener(this), this)
        pm.registerEvents(LeashElytraBoostListener(this), this)

        // Background Tasks
        val copperArmorListener = CopperArmorListener()
        pm.registerEvents(copperArmorListener, this)
        copperArmorListener.startArmorCheckTask(this)
        copperArmorListener.startParticleTask(this)

        val campfireListener = MagicalCampfireListener(this)
        pm.registerEvents(campfireListener, this)
        campfireListener.startCampfireTask()

        logger.info("TweaksKompass v1.5 enabled successfully.")
    }

    override fun onDisable() {
        arrowTrackingManager.stop()
        logger.info("TweaksKompass disabled.")
    }

    fun loadCachedArrowMultipliers() {
        cachedArrowMultipliers.clear()
        val section = config.getConfigurationSection("arrow-settings.multipliers")
        if (section != null) {
            for (key in section.getKeys(false)) {
                if (key.equals("DEFAULT", ignoreCase = true)) {
                    defaultArrowMultiplier = section.getDouble(key, 1.0)
                } else {
                    cachedArrowMultipliers[key.uppercase()] = section.getDouble(key, 1.0)
                }
            }
        } else {
            defaultArrowMultiplier = 1.0
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("tweakskompass", ignoreCase = true)) {
            if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
                if (!sender.isOp) {
                    sender.sendMessage("§cYou must be a server operator to execute this command.")
                    return true
                }
                reloadConfig()
                loadCachedArrowMultipliers()
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