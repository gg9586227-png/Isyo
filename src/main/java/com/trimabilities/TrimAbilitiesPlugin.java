package com.trimabilities;

import org.bukkit.plugin.java.JavaPlugin;

public final class TrimAbilitiesPlugin extends JavaPlugin {

	private AbilityManager abilityManager;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		this.abilityManager = new AbilityManager(this);

		getServer().getPluginManager().registerEvents(new AbilityListeners(this, abilityManager), this);
		if (getCommand("trimability") != null) {
			getCommand("trimability").setExecutor(new TrimAbilityCommand(abilityManager));
		}
		getLogger().info("TrimAbilities enabled.");
	}

	@Override
	public void onDisable() {
		if (abilityManager != null) {
			abilityManager.shutdown();
		}
		getLogger().info("TrimAbilities disabled.");
	}
}