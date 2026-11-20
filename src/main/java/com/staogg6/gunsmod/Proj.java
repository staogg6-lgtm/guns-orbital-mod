package com.staogg6.gunsmod;

import java.util.Optional;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Tick-stepped projectiles (rockets, grenades, mini nukes). No entity is spawned, so no client code is needed. */
public final class Proj {
	public enum Kind {
		ROCKET, GRENADE, NUKE
	}

	/** gravity per tick, accel multiplier per tick, max speed, fuse in ticks (-1 = none), bounce, blast. */
	public record Spec(Kind kind, double gravity, double accel, double maxSpeed, int fuse, boolean bounce,
			float power, float damage, double radius) {
	}

	private Proj() {
	}

	public static void launch(ServerLevel level, Player owner, Vec3 pos, Vec3 vel, Spec spec) {
		step(level, owner, pos, vel, spec, 0);
	}

	private static void step(ServerLevel l, Player owner, Vec3 pos, Vec3 vel, Spec s, int age) {
		if (age > 400 || pos.y < l.getMinY() - 20) {
			return;
		}
		Vec3 next = pos.add(vel);
		double len = vel.length();
		BlockHitResult bh = l.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		boolean blockHit = bh.getType() != HitResult.Type.MISS;
		double maxD = blockHit ? bh.getLocation().distanceTo(pos) : len;
		Vec3 dir = len > 1e-6 ? vel.scale(1.0 / len) : new Vec3(0, -1, 0);
		Vec3 segEnd = pos.add(dir.scale(maxD));

		// entity hit (not the owner during the first few ticks)
		Entity hitE = null;
		double best = maxD;
		AABB area = new AABB(pos, segEnd).inflate(1.0);
		for (Entity e : l.getEntities((Entity) null, area,
				en -> en.isAlive() && en.isPickable() && !en.isSpectator() && (en != owner))) {
			Optional<Vec3> c = e.getBoundingBox().inflate(0.25).clip(pos, segEnd);
			if (c.isPresent()) {
				double d = pos.distanceTo(c.get());
				if (d < best) {
					best = d;
					hitE = e;
				}
			}
		}
		if (hitE != null && s.kind() != Kind.GRENADE) {
			blow(l, owner, pos.add(dir.scale(best)), s);
			return;
		}

		Vec3 npos = next;
		Vec3 nvel = vel;
		if (blockHit) {
			if (s.bounce()) {
				Vec3 n = new Vec3(bh.getDirection().getStepX(), bh.getDirection().getStepY(), bh.getDirection().getStepZ());
				nvel = vel.subtract(n.scale(2 * vel.dot(n))).scale(0.45);
				npos = bh.getLocation().add(n.scale(0.06));
				Fx.sound(l, npos, SoundEvents.NOTE_BLOCK_BIT.value(), 0.6f, 0.6f);
			} else {
				blow(l, owner, bh.getLocation(), s);
				return;
			}
		}

		if (s.fuse() >= 0 && age >= s.fuse()) {
			blow(l, owner, npos, s);
			return;
		}

		// trail
		switch (s.kind()) {
			case ROCKET -> {
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, npos.x, npos.y, npos.z, 3, 0.05, 0.05, 0.05, 0.02);
				Fx.p(l, ParticleTypes.END_ROD, npos.x, npos.y, npos.z, 1, 0, 0, 0, 0);
				Fx.p(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, npos.x, npos.y, npos.z, 1, 0.05, 0.05, 0.05, 0.01);
				Fx.p(l, ParticleTypes.FLAME, npos.x, npos.y, npos.z, 2, 0.03, 0.03, 0.03, 0.02);
			}
			case GRENADE -> {
				Fx.p(l, ParticleTypes.ELECTRIC_SPARK, npos.x, npos.y, npos.z, 2, 0.05, 0.05, 0.05, 0.05);
				if (age % 6 == 0) {
					Fx.p(l, ParticleTypes.END_ROD, npos.x, npos.y + 0.15, npos.z, 1, 0, 0, 0, 0);
				}
			}
			case NUKE -> {
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, npos.x, npos.y, npos.z, 4, 0.15, 0.15, 0.15, 0.03);
				Fx.p(l, ParticleTypes.END_ROD, npos.x, npos.y, npos.z, 2, 0.1, 0.1, 0.1, 0.02);
				if (age % 8 == 0) {
					Fx.sound(l, npos, SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f, 0.5f + age / 120f);
				}
			}
		}

		// accelerate (rockets) then gravity
		if (s.accel() != 1.0) {
			double sp = nvel.length();
			if (sp < s.maxSpeed()) {
				nvel = nvel.scale(s.accel());
			}
		}
		nvel = nvel.add(0, -s.gravity(), 0);
		final Vec3 fp = npos;
		final Vec3 fv = nvel;
		Scheduler.later(1, () -> step(l, owner, fp, fv, s, age + 1));
	}

	private static void blow(ServerLevel l, Player owner, Vec3 at, Spec s) {
		if (s.kind() == Kind.NUKE) {
			nuke(l, owner, at, s);
		} else {
			Fx.explosion(l, owner, at, s.power(), s.damage(), s.radius(), true);
		}
	}

	/** Big staged blast: core explosion, then an expanding shockwave of smaller explosions and a rising cloud. */
	private static void nuke(ServerLevel l, Player owner, Vec3 at, Spec s) {
		Fx.sound(l, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 12f, 0.5f);
		Fx.p(l, ParticleTypes.SONIC_BOOM, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		Fx.explosion(l, owner, at.add(0, 1, 0), s.power(), s.damage(), s.radius(), true);
		final int rings = 4;
		for (int r = 1; r <= rings; r++) {
			final int k = r;
			Scheduler.later(4 * k, () -> {
				double rad = s.radius() * k / rings;
				int n = 4 + 3 * k;
				for (int i = 0; i < n; i++) {
					double a = (i + k * 0.5) * Math.PI * 2 / n;
					double x = at.x + Math.cos(a) * rad;
					double z = at.z + Math.sin(a) * rad;
					Fx.explosion(l, owner, new Vec3(x, at.y + 1, z), s.power() * 0.45f, s.damage() * 0.25f, 4.0, true);
				}
				Fx.ring(l, ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, rad, 70, 0.05);
				Fx.ring(l, ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.3, at.z, rad, 70, 0.1);
				Fx.sound(l, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 6f, 0.6f);
			});
		}
		// rising stem and mushroom cap
		for (int t = 0; t < 30; t++) {
			final int k = t;
			Scheduler.later(2 + k, () -> {
				double y = at.y + k * 1.4;
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, at.x, y, at.z, 10, 1.2, 0.4, 1.2, 0.02);
				Fx.p(l, ParticleTypes.LARGE_SMOKE, at.x, y, at.z, 6, 1.4, 0.4, 1.4, 0.01);
				if (k > 18) {
					Fx.ring(l, ParticleTypes.SOUL_FIRE_FLAME, at.x, y, at.z, (k - 18) * 1.3, 36, 0.02);
					Fx.ring(l, ParticleTypes.END_ROD, at.x, y, at.z, (k - 18) * 1.1, 24, 0.01);
				}
			});
		}
	}
}
