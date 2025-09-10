package com.trimabilities;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.Locale;
import java.util.Optional;

public final class TrimUtils {
	private TrimUtils() {}

	public static Optional<TrimPattern> getUniformTrim(Player player) {
		ItemStack[] armor = player.getInventory().getArmorContents();
		TrimPattern found = null;
		for (ItemStack piece : armor) {
			if (piece == null) return Optional.empty();
			ItemMeta meta = piece.getItemMeta();
			if (!(meta instanceof ArmorMeta)) return Optional.empty();
			ArmorMeta armorMeta = (ArmorMeta) meta;
			ArmorTrim trim = armorMeta.getTrim();
			if (trim == null) return Optional.empty();
			TrimPattern pattern = trim.getPattern();
			if (found == null) {
				found = pattern;
			} else if (!found.equals(pattern)) {
				return Optional.empty();
			}
		}
		return Optional.ofNullable(found);
	}

	public static String keyOf(TrimPattern pattern) {
		return pattern.getKey().getKey().toLowerCase(Locale.ROOT);
	}
}