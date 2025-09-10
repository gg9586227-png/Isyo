package com.trimabilities;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TrimAbilityCommand implements CommandExecutor {

	private final AbilityManager abilityManager;

	public TrimAbilityCommand(AbilityManager abilityManager) {
		this.abilityManager = abilityManager;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("Players only.");
			return true;
		}
		Player player = (Player) sender;
		if (!player.hasPermission("trimabilities.use")) {
			player.sendMessage("No permission.");
			return true;
		}
		abilityManager.activateFor(player);
		return true;
	}
}