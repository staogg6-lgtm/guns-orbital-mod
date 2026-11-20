package com.staogg6.gunsmod;

import java.util.function.Supplier;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Fires a tick-stepped projectile (rocket or grenade) that explodes. Also used for throwable grenades / nukes. */
public class LauncherItem extends Item {
	private final Proj.Spec spec;
	private final double speed;
	private final int cooldown;
	private final Supplier<Item> ammo; // null = consumes itself (throwable)
	private final SoundEvent sound;
	private final float pitch;
	private final double recoil;

	public LauncherItem(Properties props, Proj.Spec spec, double speed, int cooldown, Supplier<Item> ammo,
			SoundEvent sound, float pitch, double recoil) {
		super(props);
		this.spec = spec;
		this.speed = speed;
		this.cooldown = cooldown;
		this.ammo = ammo;
		this.sound = sound;
		this.pitch = pitch;
		this.recoil = recoil;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		ItemStack cdStack = held.copy();
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel server = (ServerLevel) level;
		if (!player.isCreative()) {
			if (ammo == null) {
				held.shrink(1);
			} else if (!GunItem.takeAmmo(player, ammo.get())) {
				player.sendOverlayMessage(Component.literal("Out of ammo"));
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
						SoundSource.PLAYERS, 0.8f, 1.2f);
				return InteractionResult.FAIL;
			}
		}
		Vec3 look = player.getLookAngle();
		Vec3 start = player.getEyePosition().add(look.scale(0.9)).add(0, -0.15, 0);
		Vec3 vel = look.scale(speed);
		if (ammo == null) {
			vel = vel.add(0, 0.12, 0).add(player.getDeltaMovement().scale(0.5));
		}
		Proj.launch(server, player, start, vel, spec);

		// muzzle effects
		Fx.p(server, ParticleTypes.SOUL_FIRE_FLAME, start.x, start.y, start.z, 8, 0.1, 0.1, 0.1, 0.12);
		Fx.p(server, ParticleTypes.CAMPFIRE_COSY_SMOKE, start.x, start.y, start.z, 3, 0.1, 0.1, 0.1, 0.04);
		Fx.p(server, ParticleTypes.ELECTRIC_SPARK, start.x, start.y, start.z, 8, 0.15, 0.15, 0.15, 0.4);
		server.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.2f, pitch);
		if (recoil > 0) {
			Vec3 back = look.scale(-recoil);
			player.push(back.x, back.y * 0.3 + 0.05, back.z);
			player.hurtMarked = true;
		}
		if (!player.isCreative() || cooldown > 0) {
			player.getCooldowns().addCooldown(cdStack, cooldown);
		}
		return InteractionResult.SUCCESS;
	}
}
