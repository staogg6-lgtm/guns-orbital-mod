package com.staogg6.gunsmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click aims at the block you are looking at. After a countdown with warning effects,
 * a beam drops from the sky and a chain of explosions hits the area.
 * NOTE: this destroys blocks. Use it away from anything you want to keep.
 */
public class OrbitalItem extends Item {
	private final int radius;
	private final int delayTicks;
	private final int cooldownTicks;
	private final float power;
	private final int blasts;
	private final float extraDamage;

	public OrbitalItem(Properties props, int radius, int delayTicks, int cooldownTicks, float power, int blasts,
			float extraDamage) {
		super(props);
		this.radius = radius;
		this.delayTicks = delayTicks;
		this.cooldownTicks = cooldownTicks;
		this.power = power;
		this.blasts = blasts;
		this.extraDamage = extraDamage;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel server = (ServerLevel) level;

		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(300));
		BlockHitResult hit = server.clip(
				new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			player.sendOverlayMessage(Component.literal("No target in range. Aim at the ground."));
			return InteractionResult.FAIL;
		}

		Vec3 target = hit.getLocation();
		player.sendOverlayMessage(Component.literal("Orbital strike inbound..."));
		server.playSound(null, target.x, target.y, target.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f,
				0.7f);
		countdown(server, player, target, delayTicks);
		player.getCooldowns().addCooldown(stack, cooldownTicks);
		return InteractionResult.SUCCESS;
	}

	private void countdown(ServerLevel level, Player owner, Vec3 target, int remaining) {
		if (remaining <= 0) {
			strike(level, owner, target);
			return;
		}
		// warning ring on the ground, tightening as the strike gets closer
		double r = Math.max(1.0, radius * (remaining / (double) delayTicks));
		for (int i = 0; i < 36; i++) {
			double a = i * Math.PI * 2 / 36;
			level.sendParticles(ParticleTypes.FLAME, target.x + Math.cos(a) * r, target.y + 0.1,
					target.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.END_ROD, target.x, target.y + 1.0, target.z, 2, 0.1, 1.0, 0.1, 0.0);
		if (remaining % 20 == 0) {
			level.playSound(null, target.x, target.y, target.z, SoundEvents.EXPERIENCE_ORB_PICKUP,
					SoundSource.PLAYERS, 2.0f, 0.6f + 0.8f * (1f - remaining / (float) delayTicks));
		}
		Scheduler.later(2, () -> countdown(level, owner, target, remaining - 2));
	}

	private void strike(ServerLevel level, Player owner, Vec3 target) {
		// the beam from the sky
		for (double y = 0; y < 140; y += 1.0) {
			level.sendParticles(ParticleTypes.END_ROD, target.x, target.y + y, target.z, 4, 0.5, 0.0, 0.5, 0.0);
			level.sendParticles(ParticleTypes.FLAME, target.x, target.y + y, target.z, 1, 0.3, 0.0, 0.3, 0.01);
		}
		level.playSound(null, target.x, target.y, target.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
				10.0f, 0.6f);

		for (int i = 0; i < blasts; i++) {
			final int n = i;
			Scheduler.later(1 + i * 2, () -> {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				double d = n == 0 ? 0 : level.getRandom().nextDouble() * radius;
				double x = target.x + Math.cos(a) * d;
				double z = target.z + Math.sin(a) * d;
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, target.y + 1, z, 1, 0, 0, 0, 0);
				level.explode(null, x, target.y + 1, z, power, true, Level.ExplosionInteraction.TNT);
			});
		}

		// extra damage to everything inside the radius, stronger near the centre
		AABB area = new AABB(target.x - radius, target.y - radius, target.z - radius, target.x + radius,
				target.y + radius, target.z + radius);
		for (Entity e : level.getEntities(owner, area, en -> en.isAlive())) {
			double dist = e.position().distanceTo(target);
			if (dist <= radius) {
				float dmg = (float) (extraDamage * (1.0 - dist / (radius + 1.0)));
				e.hurtServer(level, level.damageSources().explosion(null, owner), Math.max(2f, dmg));
			}
		}
	}
}
