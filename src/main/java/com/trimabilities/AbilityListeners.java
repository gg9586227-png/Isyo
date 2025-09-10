package com.trimabilities;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AbilityListeners implements Listener {

	private final JavaPlugin plugin;
	private final AbilityManager abilityManager;

	public AbilityListeners(JavaPlugin plugin, AbilityManager abilityManager) {
		this.plugin = plugin;
		this.abilityManager = abilityManager;
	}

	@EventHandler
	public void onLavaDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player)) return;
		Player player = (Player) event.getEntity();
		if ((event.getCause() == EntityDamageEvent.DamageCause.LAVA || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR)
			&& abilityManager.hasLavaImmunity(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onFatal(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player)) return;
		Player player = (Player) event.getEntity();
		if (!abilityManager.hasCheatDeath(player)) return;

		double finalDamage = event.getFinalDamage();
		double health = player.getHealth();
		if (finalDamage >= health) {
			event.setCancelled(true);
			double max = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
			player.setHealth(Math.min(max, Math.max(1.0, max * 0.5)));
			player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.0f);
			player.sendMessage(ChatColor.GOLD + "Fate spared you this time!");
		}
	}

	@EventHandler
	public void onMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (!abilityManager.hasLavaImmunity(player)) return;
	}

	@EventHandler
	public void onLifesteal(EntityDamageByEntityEvent event) {
		Entity damager = event.getDamager();
		if (!(damager instanceof Player)) return;
		Player player = (Player) damager;
		if (abilityManager.hasVexAura(player)) {
			double heal = Math.max(1.0, event.getFinalDamage() * 0.15);
			double max = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
			player.setHealth(Math.min(max, player.getHealth() + heal));
		}
	}
}