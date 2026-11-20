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
	public static final Item PLASMA_CELL = register("plasma_cell", 64, Item::new);
	public static final Item ROCKET = register("rocket", 64, Item::new);

	// grenade is both a throwable and ammo for the grenade launcher
	public static final Item GRENADE = register("grenade", 16,
			p -> new LauncherItem(p,
					new Proj.Spec(Proj.Kind.GRENADE, 0.05, 1.0, 99, 45, true, 3.4f, 34f, 6.5),
					1.15, 8, null, SoundEvents.SNOWBALL_THROW, 0.7f, 0));

	// guns: damage in half-hearts (2 = one heart), cooldown in ticks (20 = 1 second)
	public static final Item PISTOL = register("pistol", 1, p -> new GunItem(p, new GunItem.Gun(
			12f, 3, 1, 0.0, 90, 1, 1, SoundEvents.CROSSBOW_SHOOT, 1.7f, () -> BULLET, GunItem.Style.BULLET, false, 0)));
	public static final Item RIFLE = register("rifle", 1, p -> new GunItem(p, new GunItem.Gun(
			8f, 5, 1, 0.008, 110, 3, 1, SoundEvents.CROSSBOW_SHOOT, 1.9f, () -> BULLET, GunItem.Style.BULLET, false, 0)));
	public static final Item SHOTGUN = register("shotgun", 1, p -> new GunItem(p, new GunItem.Gun(
			6f, 12, 10, 0.075, 34, 1, 1, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7f, () -> BULLET, GunItem.Style.BULLET, false, 0.3)));
	public static final Item SNIPER = register("sniper_rifle", 1, p -> new GunItem(p, new GunItem.Gun(
			45f, 20, 1, 0.0, 300, 1, 1, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.6f, () -> BULLET, GunItem.Style.BULLET, false, 0.35)));
	public static final Item MINIGUN = register("minigun", 1, p -> new GunItem(p, new GunItem.Gun(
			5f, 4, 1, 0.03, 90, 5, 1, SoundEvents.CROSSBOW_SHOOT, 2.0f, () -> BULLET, GunItem.Style.BULLET, false, 0)));
	public static final Item PLASMA_RIFLE = register("plasma_rifle", 1, p -> new GunItem(p, new GunItem.Gun(
			11f, 4, 1, 0.004, 130, 2, 2, SoundEvents.BLAZE_SHOOT, 1.9f, () -> PLASMA_CELL, GunItem.Style.PLASMA, false, 0)));
	public static final Item RAILGUN = register("railgun", 1, p -> new GunItem(p, new GunItem.Gun(
			70f, 28, 1, 0.0, 300, 1, 1, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.6f, () -> PLASMA_CELL, GunItem.Style.RAIL, true, 0.5)));

	// launchers
	public static final Item ROCKET_LAUNCHER = register("rocket_launcher", 1, p -> new LauncherItem(p,
			new Proj.Spec(Proj.Kind.ROCKET, 0.0, 1.08, 3.0, -1, false, 4.8f, 55f, 8.0),
			0.8, 18, () -> ROCKET, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7f, 0.4));
	public static final Item GRENADE_LAUNCHER = register("grenade_launcher", 1, p -> new LauncherItem(p,
			new Proj.Spec(Proj.Kind.GRENADE, 0.045, 1.0, 99, 40, true, 3.4f, 34f, 6.5),
			1.7, 10, () -> GRENADE, SoundEvents.WIND_CHARGE_THROW, 0.8f, 0.2));
	public static final Item MINI_NUKE = register("mini_nuke", 4, p -> new LauncherItem(p,
			new Proj.Spec(Proj.Kind.NUKE, 0.04, 1.0, 99, 70, true, 11f, 100f, 18.0),
			0.9, 100, null, SoundEvents.SNOWBALL_THROW, 0.4f, 0));

	// orbital strikes: radius, lock-on ticks, cooldown ticks, blast power, shockwave rings, extra damage, beam radius
	public static final Item ORBITAL_DESIGNATOR = register("orbital_designator", 1,
			p -> new OrbitalItem(p, 9, 70, 20 * 8, 5f, 3, 70f, 1.6));
	public static final Item ORBITAL_CANNON = register("orbital_cannon", 1,
			p -> new OrbitalItem(p, 22, 120, 20 * 20, 6f, 6, 140f, 3.5));

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
			output.accept(MINIGUN);
			output.accept(PLASMA_RIFLE);
			output.accept(RAILGUN);
			output.accept(ROCKET_LAUNCHER);
			output.accept(GRENADE_LAUNCHER);
			output.accept(BULLET);
			output.accept(PLASMA_CELL);
			output.accept(ROCKET);
			output.accept(GRENADE);
			output.accept(MINI_NUKE);
			output.accept(ORBITAL_DESIGNATOR);
			output.accept(ORBITAL_CANNON);
		});
	}
}
