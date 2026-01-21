package li.cil.oc.server.machine

internal object ProgramLocations {
  private val architectureLocations = mutableMapOf<String, MutableMap<String, String>>()
  private val globalLocations = mutableMapOf<String, String>()

  fun addMapping(program: String, label: String) {
    globalLocations[program] = label
  }

  fun addMapping(program: String, label: String, vararg architectures: String) {
    if (architectures == null || architectures.isEmpty()) {
      globalLocations[program] = label
    }  else {
      for (architecture in architectures) {
        architectureLocations
          .getOrPut(architecture) { mutableMapOf() }
          .put(program, label)
      }
    }
  }

  fun getMappings(architecture: String) = architectureLocations.getOrElse(architecture, ::emptyMap) + globalLocations
}
