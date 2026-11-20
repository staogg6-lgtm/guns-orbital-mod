package com.staogg6.gunsmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
 * Right-click locks onto the block you are looking at. A blue targeting grid appears, a satellite beam
 * streaks down from orbit, hits with a huge column of energy and sends out a chain of shockwaves.
 * NOTE: this destroys blocks. Use it away from anything you want to keep.
 */
public class OrbitalItem extends Item {
	private static final double SKY = 220;

	private final int radius;
	private final int lockTicks;
	private final int cooldownTicks;
	private final float power;
	private final int rings;
	private final float extraDamage;
	private final double beamRadius;

	public OrbitalItem(Properties props, int radius, int lockTicks, int cooldownTicks, float power, int rings,
			float extraDamage, double beamRadius) {
		super(props);
		this.radius = radius;
		this.lockTicks = lockTicks;
		this.cooldownTicks = cooldownTicks;
		this.power = power;
		this.rings = rings;
		this.extraDamage = extraDamage;
		this.beamRadius = beamRadius;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel server = (ServerLevel) level;

		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(400));
		BlockHitResult hit = server.clip(
				new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			player.sendOverlayMessage(Component.literal("No target in range. Aim at the ground."));
			return InteractionResult.FAIL;
		}

		Vec3 target = hit.getLocation();
		Fx.sound(server, target, SoundEvents.BEACON_ACTIVATE, 4.0f, 0.6f);
		Fx.sound(server, target, SoundEvents.CONDUIT_ACTIVATE, 4.0f, 0.5f);
		countdown(server, player, target, lockTicks, 0);
		player.getCooldowns().addCooldown(stack, cooldownTicks);
		return InteractionResult.SUCCESS;
	}

	private void countdown(ServerLevel l, Player owner, Vec3 t, int remaining, int tick) {
		if (remaining <= 0) {
			strike(l, owner, t);
			return;
		}
		double prog = 1.0 - remaining / (double) lockTicks;
		double y = t.y + 0.15;

		// contracting targeting ring + inner ring
		double r = Math.max(1.5, radius * (1.0 - prog * 0.75));
		Fx.ring(l, ParticleTypes.SOUL_FIRE_FLAME, t.x, y, t.z, r, 40, 0);
		Fx.ring(l, ParticleTypes.ELECTRIC_SPARK, t.x, y, t.z, radius * 0.5 * (1.0 - prog * 0.5), 20, 0);
		// rotating crosshair spokes
		double rot = tick * 0.12;
		for (int s = 0; s < 4; s++) {
			double a = rot + s * Math.PI / 2;
			for (double d = 0.5; d < r; d += 0.8) {
				Fx.p(l, ParticleTypes.END_ROD, t.x + Math.cos(a) * d, y, t.z + Math.sin(a) * d, 1, 0, 0, 0, 0);
			}
		}
		// thin pulsing lock-on pillar that grows taller
		double h = 6 + prog * 70;
		for (double d = 0; d < h; d += 1.5) {
			Fx.p(l, ParticleTypes.END_ROD, t.x, t.y + d, t.z, 1, 0.05, 0, 0.05, 0);
		}
		// energy gathers upward in a helix
		for (int i = 0; i < 3; i++) {
			double a = tick * 0.4 + i * Math.PI * 2 / 3;
			double hh = (tick * 0.9 + i * 6) % 40;
			Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, t.x + Math.cos(a) * 1.2, t.y + hh, t.z + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
		}
		// satellite streak coming down during the final second
		if (remaining <= 20) {
			double fall = remaining / 20.0;
			double hy = t.y + SKY * fall;
			for (double d = 0; d < 40; d += 1.5) {
				Fx.p(l, ParticleTypes.END_ROD, t.x, hy + d, t.z, 1, 0.1, 0, 0.1, 0);
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, t.x, hy + d, t.z, 1, 0.3, 0, 0.3, 0);
			}
		}
		if (remaining % 10 == 0) {
			float pitch = 0.6f + 1.4f * (float) prog;
			Fx.sound(l, t, SoundEvents.BEACON_POWER_SELECT, 3.0f, pitch);
			Fx.sound(l, t, SoundEvents.EXPERIENCE_ORB_PICKUP, 2.0f, pitch);
			if (remaining % 20 == 0) {
				owner.sendOverlayMessage(Component.literal(
						"ORBITAL LOCK  -  impact in " + String.format("%.1f", remaining / 20.0) + "s"));
			}
		}
		if (remaining == 20) {
			Fx.sound(l, t, SoundEvents.RESPAWN_ANCHOR_CHARGE, 5.0f, 0.5f);
			Fx.sound(l, t, SoundEvents.ENDER_DRAGON_GROWL, 4.0f, 1.8f);
		}
		Scheduler.later(2, () -> countdown(l, owner, t, remaining - 2, tick + 2));
	}

	private void strike(ServerLevel l, Player owner, Vec3 t) {
		owner.sendOverlayMessage(Component.literal("DIRECT HIT"));
		Fx.sound(l, t, SoundEvents.WARDEN_SONIC_BOOM, 12f, 0.5f);
		Fx.sound(l, t, SoundEvents.LIGHTNING_BOLT_THUNDER, 12f, 0.5f);
		Fx.sound(l, t, SoundEvents.END_PORTAL_SPAWN, 8f, 0.7f);
		Fx.p(l, ParticleTypes.SONIC_BOOM, t.x, t.y + 1, t.z, 1, 0, 0, 0, 0);

		// the beam slams down (a slab of layers per tick) and then burns for a while
		final int slabs = 5;
		final int perSlab = (int) (SKY / slabs);
		for (int s = 0; s < slabs; s++) {
			final int k = s;
			Scheduler.later(1 + s, () -> {
				double top = SKY - k * perSlab;
				for (double y = top; y > top - perSlab; y -= 1.0) {
					beamLayer(l, t, y, 1.0);
				}
			});
		}
		for (int b = 1; b <= 18; b++) {
			Scheduler.later(slabs + b * 2, () -> {
				for (double y = SKY; y > 0; y -= 3.0) {
					beamLayer(l, t, y, 0.7);
				}
			});
		}

		// impact: core blast, then shockwave rings of explosions
		Scheduler.later(slabs + 1, () -> {
			Fx.explosion(l, owner, new Vec3(t.x, t.y + 1, t.z), power * 1.6f, 0, 1, true);
			Fx.ring(l, ParticleTypes.END_ROD, t.x, t.y + 0.3, t.z, beamRadius + 1, 40, 0.5);
		});
		for (int i = 1; i <= rings; i++) {
			final int k = i;
			Scheduler.later(slabs + 2 + k * 3, () -> {
				double rad = radius * k / (double) rings;
				int n = 5 + 3 * k;
				for (int j = 0; j < n; j++) {
					double a = (j + (k % 2) * 0.5) * Math.PI * 2 / n;
					Fx.explosion(l, owner, new Vec3(t.x + Math.cos(a) * rad, t.y + 1, t.z + Math.sin(a) * rad),
							power, 0, 1, true);
				}
				Fx.ring(l, ParticleTypes.SOUL_FIRE_FLAME, t.x, t.y + 0.4, t.z, rad, 80, 0.15);
				Fx.ring(l, ParticleTypes.END_ROD, t.x, t.y + 0.6, t.z, rad, 60, 0.05);
				Fx.ring(l, ParticleTypes.ELECTRIC_SPARK, t.x, t.y + 0.8, t.z, rad, 60, 0.4);
			});
		}

		// damage + launch everything in the zone
		Scheduler.later(slabs + 2, () -> {
			AABB area = new AABB(t.x - radius, t.y - radius, t.z - radius, t.x + radius, t.y + radius, t.z + radius);
			for (Entity e : l.getEntities((Entity) null, area, en -> en.isAlive() && en != owner)) {
				double dist = e.position().distanceTo(t);
				if (dist <= radius + 1) {
					float dmg = (float) (extraDamage * (1.0 - dist / (radius + 2.0)));
					Fx.hurt(l, e, l.damageSources().explosion(null, owner), Math.max(4f, dmg));
					e.igniteForSeconds(4f);
					Vec3 away = e.position().subtract(t).normalize();
					e.push(away.x * 1.2, 0.9, away.z * 1.2);
					e.syncVelocity = true;
				}
			}
		});

		// aftermath: rising blue embers and smoke
		for (int a = 0; a < 25; a++) {
			final int k = a;
			Scheduler.later(slabs + 6 + k * 2, () -> {
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, t.x, t.y + 1, t.z, 18, radius * 0.4, 1.0, radius * 0.4, 0.06);
				Fx.p(l, ParticleTypes.LARGE_SMOKE, t.x, t.y + 1.5, t.z, 8, radius * 0.4, 1.0, radius * 0.4, 0.04);
				Fx.p(l, ParticleTypes.ELECTRIC_SPARK, t.x, t.y + 2, t.z, 12, radius * 0.5, 1.5, radius * 0.5, 0.3);
			});
		}
	}

	private void beamLayer(ServerLevel l, Vec3 t, double y, double density) {
		int c = (int) Math.max(1, Math.round(6 * beamRadius * density));
		Fx.p(l, ParticleTypes.END_ROD, t.x, t.y + y, t.z, Math.max(1, c / 2), beamRadius * 0.25, 0, beamRadius * 0.25, 0);
		Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, t.x, t.y + y, t.z, c, beamRadius * 0.7, 0, beamRadius * 0.7, 0.01);
		Fx.p(l, ParticleTypes.ELECTRIC_SPARK, t.x, t.y + y, t.z, 1, beamRadius, 0, beamRadius, 0.1);
	}
}
