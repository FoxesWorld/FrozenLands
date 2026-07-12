local M = { id = "engine.shaders" }

function M.on_event(event)
  return event
end

M.events = {
  pipeline_initialized = "engine.shaders.pipeline.initialized",
  pipeline_enabled_changed = "engine.shaders.pipeline.enabled.changed",
  effect_enabled_changed = "engine.shaders.effect.enabled.changed",
  settings_changed = "engine.shaders.settings.changed",
  solar_shadows_rebuilt = "engine.shaders.solarShadows.rebuilt"
}

return M
