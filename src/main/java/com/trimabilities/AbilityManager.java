package com.trimabilities;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class AbilityManager {

	private final JavaPlugin plugin;
	private final Map<String, Integer> cooldownSeconds = new HashMap<>();
	private final Map<String, Integer> durationSeconds = new HashMap<>();
	private final Map<UUID, Map<String, Long>> playerCooldownUntilMs = new ConcurrentHashMap<>();

	private final Map<UUID, Long> lavaUntil = new ConcurrentHashMap<>();
	private final Map<UUID, Long> vexAuraUntil = new ConcurrentHashMap<>();
	private final Map<UUID, Long> cheatDeathUntil = new ConcurrentHashMap<>();
	private final Map<UUID, Long> flightUntil = new ConcurrentHashMap<>();

	private final Map<String, BiFunction<Player, Integer, Boolean>> abilityMap = new HashMap<>();
	private int cleanupTaskId = -1;

	public AbilityManager(JavaPlugin plugin) {
		this.plugin = plugin;
		loadConfig();
		registerAbilities();
		startCleanupTask();
	}

	public void shutdown() {
		if (cleanupTaskId != -1) Bukkit.getScheduler().cancelTask(cleanupTaskId);
		for (UUID id : new ArrayList<>(flightUntil.keySet())) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) {
				p.setAllowFlight(false);
				p.setFlying(false);
			}
		}
	}

	private void loadConfig() {
		for (String k : plugin.getConfig().getConfigurationSection("cooldowns").getKeys(false)) {
			cooldownSeconds.put(k, plugin.getConfig().getInt("cooldowns." + k, 45));
		}
		for (String k : plugin.getConfig().getConfigurationSection("durations").getKeys(false)) {
			durationSeconds.put(k, plugin.getConfig().getInt("durations." + k, 30));
		}
	}

	private void startCleanupTask() {
		cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
			long now = System.currentTimeMillis();
			cleanupMap(lavaUntil, now);
			cleanupMap(vexAuraUntil, now);
			cleanupMap(cheatDeathUntil, now);
			for (UUID id : new ArrayList<>(flightUntil.keySet())) {
				if (flightUntil.get(id) <= now) {
					Player p = Bukkit.getPlayer(id);
					if (p != null) {
						p.setAllowFlight(false);
						p.setFlying(false);
						p.sendMessage(ChatColor.YELLOW + "Your flight ended.");
					}
					flightUntil.remove(id);
				}
			}
		}, 20L, 20L);
	}

	private void cleanupMap(Map<UUID, Long> map, long now) {
		for (UUID id : new ArrayList<>(map.keySet())) {
			if (map.get(id) <= now) map.remove(id);
		}
	}

	public void activateFor(Player player) {
		Optional<TrimPattern> opt = TrimUtils.getUniformTrim(player);
		if (opt.isEmpty()) {
			player.sendMessage(color(plugin.getConfig().getString("messages.no_set", "&cYou need a full set with the same trim!")));
			return;
		}
		String key = TrimUtils.keyOf(opt.get());
		int duration = durationSeconds.getOrDefault(key, 30);
		int cooldown = cooldownSeconds.getOrDefault(key, 45);

		if (isOnCooldown(player.getUniqueId(), key)) {
			long remaining = (getCooldownUntil(player.getUniqueId(), key) - System.currentTimeMillis()) / 1000L;
			player.sendMessage(color(plugin.getConfig().getString("messages.on_cooldown", "&eAbility on cooldown for &6%secs%s &eseconds.")).replace("%secs%", String.valueOf(Math.max(1, remaining))));
			return;
		}

		BiFunction<Player, Integer, Boolean> ability = abilityMap.get(key);
		if (ability == null) {
			player.sendMessage(ChatColor.RED + "No ability for trim: " + key);
			return;
		}

		boolean ok = ability.apply(player, duration);
		if (ok) {
			setCooldown(player.getUniqueId(), key, cooldown);
			player.sendMessage(color(plugin.getConfig().getString("messages.activated", "&aActivated &2%name% &aability!")).replace("%name%", key));
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (!isOnCooldown(player.getUniqueId(), key)) return;
				long remain = getCooldownUntil(player.getUniqueId(), key) - System.currentTimeMillis();
				if (remain <= 0) {
					player.sendMessage(color(plugin.getConfig().getString("messages.ready", "&aYour &2%name% &aability is ready again.")).replace("%name%", key));
				}
			}, cooldown * 20L);
		}
	}

	private String color(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}

	private void setCooldown(UUID id, String key, int seconds) {
		playerCooldownUntilMs.computeIfAbsent(id, u -> new ConcurrentHashMap<>())
			.put(key, System.currentTimeMillis() + seconds * 1000L);
	}

	private boolean isOnCooldown(UUID id, String key) {
		return getCooldownUntil(id, key) > System.currentTimeMillis();
	}

	private long getCooldownUntil(UUID id, String key) {
		return playerCooldownUntilMs.getOrDefault(id, Collections.emptyMap()).getOrDefault(key, 0L);
	}

	private void registerAbilities() {
		abilityMap.put("sentry", (player, duration) -> {
			player.setAllowFlight(true);
			player.setFlying(true);
			flightUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 1000L);
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.3f);
			return true;
		});

		abilityMap.put("dune", (player, durationIgnored) -> {
			org.bukkit.util.Vector dir = player.getLocation().getDirection().normalize().multiply(2.4);
			player.setVelocity(dir.setY(0.1));
			player.getWorld().spawnParticle(Particle.FALLING_DUST, player.getLocation(), 50, 0.5, 0.1, 0.5, Material.SAND.createBlockData());
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_HUSK_STEP, 1f, 0.5f);
			return true;
		});

		abilityMap.put("coast", (player, duration) -> {
			addEffect(player, PotionEffectType.CONDUIT_POWER, duration, 0);
			addEffect(player, PotionEffectType.DOLPHINS_GRACE, duration, 0);
			addEffect(player, PotionEffectType.WATER_BREATHING, duration, 0);
			addEffect(player, PotionEffectType.SPEED, duration, 1);
			return true;
		});

		abilityMap.put("eye", (player, duration) -> {
			org.bukkit.Location target = player.getTargetBlockExact(50) != null ? player.getTargetBlockExact(50).getLocation().add(0.5, 1, 0.5) : null;
			if (target == null) target = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(8));
			player.teleport(target);
			addEffect(player, PotionEffectType.INVISIBILITY, Math.max(5, duration), 0);
			player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
			return true;
		});

		abilityMap.put("host", (player, duration) -> {
			for (int i = 0; i < 4; i++) {
				Wolf wolf = player.getWorld().spawn(player.getLocation(), Wolf.class);
				wolf.setOwner(player);
				wolf.setAdult();
				wolf.setCustomName(org.bukkit.ChatColor.GOLD + "Host Guardian");
				int ticks = duration * 20;
				org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, wolf::remove, ticks);
			}
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1f, 0.9f);
			return true;
		});

		abilityMap.put("raiser", (player, duration) -> {
			for (LivingEntity e : nearbyMobs(player, 10)) {
				e.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * Math.min(8, duration), 1, true, true, true));
			}
			player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 20 * Math.min(8, duration), 2, true, true, true));
			player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1f, 0.7f);
			return true;
		});

		abilityMap.put("rib", (player, duration) -> {
			long until = System.currentTimeMillis() + duration * 1000L;
			lavaUntil.put(player.getUniqueId(), until);
			int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> trySolidifyLavaUnder(player), 1L, 2L);
			Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getScheduler().cancelTask(task), duration * 20L);
			addEffect(player, PotionEffectType.FIRE_RESISTANCE, duration, 0);
			player.getWorld().playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1f, 1f);
			return true;
		});

		abilityMap.put("shaper", (player, duration) -> {
			addEffect(player, PotionEffectType.FAST_DIGGING, duration, 4);
			addEffect(player, PotionEffectType.NIGHT_VISION, duration, 0);
			player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
			return true;
		});

		abilityMap.put("silence", (player, duration) -> {
			addEffect(player, PotionEffectType.INVISIBILITY, duration, 0);
			addEffect(player, PotionEffectType.SPEED, duration, 2);
			player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.5f, 1.8f);
			return true;
		});

		abilityMap.put("snout", (player, duration) -> {
			addEffect(player, PotionEffectType.LUCK, duration, 0);
			addEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE, duration, 0);
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PIGLIN_CELEBRATE, 1f, 1.2f);
			return true;
		});

		abilityMap.put("spire", (player, durationIgnored) -> {
			for (LivingEntity e : nearbyMobs(player, 12)) {
				player.getWorld().strikeLightningEffect(e.getLocation());
				e.damage(8.0, player);
			}
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
			return true;
		});

		abilityMap.put("tide", (player, duration) -> {
			addEffect(player, PotionEffectType.DOLPHINS_GRACE, duration, 0);
			addEffect(player, PotionEffectType.WATER_BREATHING, duration, 0);
			if (player.getLocation().getBlock().isLiquid()) {
				Vector v = player.getLocation().getDirection().normalize().multiply(1.6);
				player.setVelocity(v.setY(0.2));
			}
			player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.2f);
			return true;
		});

		abilityMap.put("vex", (player, duration) -> {
			long until = System.currentTimeMillis() + duration * 1000L;
			vexAuraUntil.put(player.getUniqueId(), until);
			int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
				if (System.currentTimeMillis() > until) return;
				for (LivingEntity e : nearbyMobs(player, 6)) {
					player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 2);
					e.damage(2.5, player);
				}
			}, 0L, 10L);
			Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getScheduler().cancelTask(task), duration * 20L);
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 1.4f);
			return true;
		});

		abilityMap.put("ward", (player, duration) -> {
			cheatDeathUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 1000L);
			player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f);
			return true;
		});

		abilityMap.put("wayfinder", (player, durationIgnored) -> {
			Location start = player.getLocation();
			Location target = start.clone().add(start.getDirection().normalize().multiply(50));
			target.setY(Math.min(319, Math.max(-64, target.getY())));
			player.teleport(target);
			addEffect(player, PotionEffectType.NIGHT_VISION, 15, 0);
			player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
			return true;
		});

		abilityMap.put("wild", (player, duration) -> {
			addEffect(player, PotionEffectType.INCREASE_DAMAGE, duration, 2);
			addEffect(player, PotionEffectType.SPEED, duration, 1);
			addEffect(player, PotionEffectType.DAMAGE_RESISTANCE, duration, 1);
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PANDA_BITE, 1f, 0.8f);
			return true;
		});
	}

	private void addEffect(Player player, PotionEffectType type, int seconds, int amplifier) {
		player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, true, true, true));
	}

	private java.util.List<LivingEntity> nearbyMobs(Player player, double radius) {
		java.util.List<LivingEntity> out = new java.util.ArrayList<>();
		for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
			if (e instanceof LivingEntity && !(e instanceof Player)) out.add((LivingEntity) e);
		}
		return out;
	}

	private void trySolidifyLavaUnder(Player player) {
		Location under = player.getLocation().clone().subtract(0, 1, 0);
		if (under.getBlock().getType() == Material.LAVA) {
			under.getBlock().setType(Material.BASALT, false);
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (under.getBlock().getType() == Material.BASALT) {
					under.getBlock().setType(Material.LAVA, false);
				}
			}, 100L);
		}
	}

	public boolean hasLavaImmunity(Player player) {
		return lavaUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
	}

	public boolean hasVexAura(Player player) {
		return vexAuraUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
	}

	public boolean hasCheatDeath(Player player) {
		return cheatDeathUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
	}
}