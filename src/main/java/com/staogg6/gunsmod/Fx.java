package com.staogg6.gunsmod;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared visual / sound / damage helpers. Everything uses vanilla particles, so no client code is needed. */
public final class Fx {
	private Fx() {
	}

	/** Particle that is visible from far away and never culled. */
	public static void p(ServerLevel l, ParticleOptions t, double x, double y, double z, int n, double dx, double dy,
			double dz, double speed) {
		l.sendParticles(t, true, true, x, y, z, n, dx, dy, dz, speed);
	}

	public static void sound(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
		l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	/** A glowing blue energy beam between two points. */
	public static void blueBeam(ServerLevel l, Vec3 from, Vec3 to, double step, boolean thick) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 0.1) {
			return;
		}
		Vec3 dir = d.scale(1.0 / len);
		int i = 0;
		for (double t = 0; t < len; t += step, i++) {
			Vec3 pos = from.add(dir.scale(t));
			p(l, ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			p(l, ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			if (thick || i % 2 == 0) {
				p(l, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 1, 0.12, 0.12, 0.12, 0.05);
			}
		}
	}

	/** A spinning helix around the line, used for the railgun. */
	public static void helix(ServerLevel l, Vec3 from, Vec3 to, double radius, double turns) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 0.5) {
			return;
		}
		Vec3 dir = d.scale(1.0 / len);
		Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = dir.cross(up).normalize();
		Vec3 b = dir.cross(a).normalize();
		for (double t = 0; t < len; t += 0.35) {
			double ang = t * turns;
			Vec3 pos = from.add(dir.scale(t)).add(a.scale(Math.cos(ang) * radius)).add(b.scale(Math.sin(ang) * radius));
			p(l, ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		}
	}

	/** Flat ring on the ground (or any horizontal plane). */
	public static void ring(ServerLevel l, ParticleOptions t, double cx, double y, double cz, double r, int n,
			double speed) {
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			p(l, t, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 1, 0, 0, 0, speed);
		}
	}

	/** Damage that ignores the post-hit invulnerability window, so fast guns really hit hard. */
	public static void hurt(ServerLevel l, Entity target, net.minecraft.world.damagesource.DamageSource src, float amount) {
		target.setInvulnerableTime(0);
		target.hurtServer(l, src, amount);
	}

	/** Blue-tinted explosion with a shockwave ring, extra damage in a radius and (optionally) block damage. */
	public static void explosion(ServerLevel l, Player owner, Vec3 pos, float power, float damage, double radius,
			boolean breakBlocks) {
		if (breakBlocks && power > 0) {
			l.explode(null, pos.x, pos.y, pos.z, power, false, Level.ExplosionInteraction.TNT);
		} else if (power > 0) {
			l.explode(null, pos.x, pos.y, pos.z, power, false, Level.ExplosionInteraction.NONE);
		}
		p(l, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		p(l, ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 50, 0.3, 0.3, 0.3, 0.35);
		p(l, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 70, 0.3, 0.3, 0.3, 0.9);
		p(l, ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 25, 0.2, 0.2, 0.2, 0.5);
		p(l, ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.5, pos.z, 12, 0.5, 0.4, 0.5, 0.05);
		for (int i = 1; i <= 3; i++) {
			final int k = i;
			Scheduler.later(k * 2, () -> ring(l, ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.2, pos.z,
					radius * k / 3.0, (int) (14 + radius * 3), 0.02));
		}
		if (damage > 0) {
			AABB area = new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius,
					pos.z + radius);
			for (Entity e : l.getEntities((Entity) null, area, en -> en.isAlive() && en != owner)) {
				double dist = e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(pos);
				if (dist <= radius) {
					float dmg = (float) (damage * (1.0 - dist / (radius + 1.0)));
					hurt(l, e, l.damageSources().explosion(null, owner), Math.max(2f, dmg));
				}
			}
		}
	}
}
