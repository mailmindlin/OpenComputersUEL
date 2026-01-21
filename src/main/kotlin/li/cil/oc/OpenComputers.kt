package li.cil.oc

import li.cil.oc.common.IMC
import li.cil.oc.common.Proxy
import li.cil.oc.server.command.CommandHandler
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLInterModComms.IMCEvent
import net.minecraftforge.fml.common.event.*
import net.minecraftforge.fml.common.network.FMLEventChannel
import li.cil.oc.util.ThreadPoolFactory
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(modid = OpenComputers.ID, name = OpenComputers.Name,
  version = OpenComputers.Version,
  modLanguage = "kotlin", useMetadata = true /*@MCVERSIONDEP@*/)
object OpenComputers {
  const val ID = "opencomputers"

  const val Name = "OpenComputers"

  const val McVersion = "1.12.2-forge"

  const val Version = "@VERSION@"

  var logger: Logger? = null
  val log: Logger
    get() = logger ?: LogManager.getLogger(Name)


  @SidedProxy(clientSide = "li.cil.oc.client.Proxy", serverSide = "li.cil.oc.server.Proxy")
  lateinit var proxy: Proxy

  lateinit var channel: FMLEventChannel

  @EventHandler
  fun preInit(e: FMLPreInitializationEvent) {
    logger = e.modLog
    proxy.preInit(e)
    OpenComputers.log.info("Done with pre init phase.")
  }

  @EventHandler
  fun init(e: FMLInitializationEvent) {
    proxy.init(e)
    OpenComputers.log.info("Done with init phase.")
  }

  @EventHandler
  fun postInit(e: FMLPostInitializationEvent) {
    proxy.postInit(e)
    OpenComputers.log.info("Done with post init phase.")
  }

  @EventHandler
  fun serverStart(e: FMLServerStartingEvent) {
    CommandHandler.register(e)
    ThreadPoolFactory.safePools.forEach { it.newThreadPool() }

    if (Settings.get.internetAccessConfigured()) {
      if (Settings.get.internetFilteringRulesInvalid()) {
        OpenComputers.log.warn("####################################################")
        OpenComputers.log.warn("#                                                  #")
        OpenComputers.log.warn("#  Could not parse Internet Card filtering rules!  #")
        OpenComputers.log.warn("#  Review the server log and adjust the filtering  #")
        OpenComputers.log.warn("#  list to ensure it is appropriately configured.  #")
        OpenComputers.log.warn("#   (config/OpenComputers.cfg => filteringRules)   #")
        OpenComputers.log.warn("# Internet access has been automatically disabled. #")
        OpenComputers.log.warn("#                                                  #")
        OpenComputers.log.warn("####################################################")
      } else if (!Settings.get.internetFilteringRulesObserved && e.server.isDedicatedServer) {
        OpenComputers.log.warn("####################################################")
        OpenComputers.log.warn("#                                                  #")
        OpenComputers.log.warn("#    It appears that you're running a dedicated    #")
        OpenComputers.log.warn("#  server with OpenComputers installed! Make sure  #")
        OpenComputers.log.warn("#  to review the Internet Card address filtering   #")
        OpenComputers.log.warn("#  list to ensure it is appropriately configured.  #")
        OpenComputers.log.warn("#   (config/OpenComputers.cfg => filteringRules)   #")
        OpenComputers.log.warn("#                                                  #")
        OpenComputers.log.warn("####################################################")
      } else {
        OpenComputers.log.info("Successfully applied ${Settings.get.internetFilteringRules.size} Internet Card filtering rules.")
      }
    }
  }

  @EventHandler
  fun serverStop(e: FMLServerStoppedEvent) {
    ThreadPoolFactory.safePools.forEach { it.waitForCompletion() }
  }

  @EventHandler
  fun imc(e: IMCEvent) = IMC.handleEvent(e)
}
