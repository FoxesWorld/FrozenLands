-- FrozenLands HtmlDom local bridge.
-- DOM click only: gameplay/runtime work is routed by Java ModuleEventBus and Lua event handlers.

function onUiEvent(topic, elementId)
  return true
end

function afterUiEvent(topic, elementId)
  return true
end

function onClick(action, elementId)
  return true
end

function afterClick(action, elementId)
  return true
end
