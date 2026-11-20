package com.staogg6.gunsmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class GunsMod implements ModInitializer {
	public static final String MOD_ID = "gunsmod";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Scheduler.init();
		ModItems.register();
	}
}
