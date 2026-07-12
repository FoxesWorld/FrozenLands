-- FrozenLands Lua API: engine.shaders
-- Java is the host executor. This file is the Lua-side module facade.

local M = {
  id = "engine.shaders",
  commands = {
    "status",
    "setEnabled",
    "effects",
    "effect.get",
    "effect.set",
    "shadowSettings",
    "shadowSettings.set",
    "solarShadows.status",
    "solarShadows.set",
    "solarShadows.preset",
    "solarShadows.benchmark.start",
    "solarShadows.benchmark.status",
    "solarShadows.benchmark.cancel"
  }
}

function M.call(command, args)
  args = args or {}
  return java.callModule(M.id, command, args)
end

function M.enable_effect(id)
  return M.call("effect.set", { id = id, enabled = true })
end

function M.disable_effect(id)
  return M.call("effect.set", { id = id, enabled = false })
end

M.solar_shadows = {}

function M.solar_shadows.status()
  return M.call("solarShadows.status", {})
end

function M.solar_shadows.set(args)
  return M.call("solarShadows.set", args or {})
end

function M.solar_shadows.set_samples(samples)
  return M.solar_shadows.set({ pathSamples = samples })
end

function M.solar_shadows.set_angular_radius(degrees)
  return M.solar_shadows.set({ sunAngularRadiusDegrees = degrees })
end

function M.solar_shadows.set_mode(mode)
  return M.solar_shadows.set({ mode = mode })
end

function M.solar_shadows.use_pcss_single_csm()
  return M.solar_shadows.set({
    mode = "pcss-single-csm",
    hardwareCompare = false
  })
end

function M.solar_shadows.use_path_sampled_hard(samples)
  return M.solar_shadows.set({
    mode = "path-sampled-hard",
    pathSamples = samples or 4,
    hardwareCompare = false
  })
end

function M.solar_shadows.use_hybrid(samples)
  return M.solar_shadows.set({
    mode = "hybrid",
    pathSamples = samples or 4,
    hardwareCompare = false
  })
end

function M.solar_shadows.enable_pcss()
  return M.solar_shadows.use_pcss_single_csm()
end

function M.solar_shadows.disable_pcss()
  return M.solar_shadows.use_path_sampled_hard(4)
end

function M.solar_shadows.configure_pcss(args)
  args = args or {}
  args.mode = args.mode or "pcss-single-csm"
  args.hardwareCompare = false
  return M.solar_shadows.set(args)
end

function M.solar_shadows.set_pcss_quality(blocker_samples, filter_samples)
  return M.solar_shadows.configure_pcss({
    pcssBlockerSamples = blocker_samples,
    pcssFilterSamples = filter_samples
  })
end

function M.solar_shadows.set_pcss_radius(min_radius, max_radius)
  return M.solar_shadows.configure_pcss({
    pcssMinFilterRadius = min_radius,
    pcssMaxFilterRadius = max_radius
  })
end

function M.solar_shadows.set_pcss_angular_radius(degrees)
  return M.solar_shadows.configure_pcss({
    pcssAngularRadiusDegrees = degrees
  })
end

function M.solar_shadows.apply_preset(preset)
  return M.call("solarShadows.preset", { preset = preset })
end

M.solar_shadows.benchmark = {}

function M.solar_shadows.benchmark.start(frames, warmup_frames)
  return M.call("solarShadows.benchmark.start", {
    frames = frames or 600,
    warmupFrames = warmup_frames or 60
  })
end

function M.solar_shadows.benchmark.status()
  return M.call("solarShadows.benchmark.status", {})
end

function M.solar_shadows.benchmark.cancel()
  return M.call("solarShadows.benchmark.cancel", {})
end

-- Semantic alias: this pipeline follows SkyControl active sun/moon source.
M.primary_shadows = M.solar_shadows

return M
