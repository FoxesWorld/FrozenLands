local M = { id = "frozenlands.ui" }

M.topics = {
  prefix = "ui.*",
  menuPrefix = "ui.menu.*",
  newGame = "ui.menu.newGame",
  continueGame = "ui.menu.continue",
  refreshSaves = "ui.menu.refreshSaves",
  loadSlot = "ui.menu.loadSlot",
  save = "ui.menu.save",
  resume = "ui.menu.resume",
  mainMenu = "ui.menu.mainMenu",
  exitToDesktop = "ui.menu.exitToDesktop",
  mounted = "ui.html.mounted",
  screenChanged = "ui.html.screen.changed"
}

return M
