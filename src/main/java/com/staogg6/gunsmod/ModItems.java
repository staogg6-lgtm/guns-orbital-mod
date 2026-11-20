package com.staogg6.gunsmod;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
	// ammo
	public static final Item BULLET = register("bullet", 64, Item::new);

	// guns: damage is in half-hearts (2 = one heart)
	public static final Item PISTOL = register("pistol", 1,
			p -> new GunItem(p, 7f, 8, 1, 0.0, 80, 1, SoundEvents.CROSSBOW_SHOOT, 1.6f));
	public static final Item RIFLE = register("rifle", 1,
			p -> new GunItem(p, 5f, 14, 1, 0.01, 100, 3, SoundEvents.CROSSBOW_SHOOT, 1.9f));
	public static final Item SHOTGUN = register("shotgun", 1,
			p -> new GunItem(p, 3.5f, 26, 8, 0.07, 28, 1, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7f));
	public static final Item SNIPER = register("sniper_rifle", 1,
			p -> new GunItem(p, 26f, 50, 1, 0.0, 250, 1, SoundEvents.FIREWORK_ROCKET_BLAST, 0.5f));

	// orbital strikes: radius, delay(ticks), cooldown(ticks), power, blasts, extra damage
	public static final Item ORBITAL_DESIGNATOR = register("orbital_designator", 1,
			p -> new OrbitalItem(p, 7, 60, 20 * 30, 5f, 7, 40f));
	public static final Item ORBITAL_CANNON = register("orbital_cannon", 1,
			p -> new OrbitalItem(p, 16, 100, 20 * 90, 8f, 22, 80f));

	private ModItems() {
	}

	private static <T extends Item> T register(String name, int stack, Function<Item.Properties, T> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, GunsMod.id(name));
		T item = factory.apply(new Item.Properties().setId(key).stacksTo(stack));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(PISTOL);
			output.accept(RIFLE);
			output.accept(SHOTGUN);
			output.accept(SNIPER);
			output.accept(BULLET);
			output.accept(ORBITAL_DESIGNATOR);
			output.accept(ORBITAL_CANNON);
		});
	}
}
