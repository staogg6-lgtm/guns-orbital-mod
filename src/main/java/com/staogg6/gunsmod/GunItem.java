package com.staogg6.gunsmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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

/** Hitscan gun with several visual styles. Fires instantly along the crosshair. */
public class GunItem extends Item {
	public enum Style {
		BULLET, PLASMA, RAIL
	}

	/** damage is in half-hearts. cooldown in ticks (20 = 1s). */
	public record Gun(float damage, int cooldown, int pellets, double spread, double range, int burst, int burstGap,
			SoundEvent sound, float pitch, Supplier<Item> ammo, Style style, boolean pierce, double recoil) {
	}

	private final Gun g;

	public GunItem(Properties props, Gun gun) {
		super(props);
		this.g = gun;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel server = (ServerLevel) level;

		if (!player.isCreative() && !takeAmmo(player, g.ammo().get())) {
			player.sendOverlayMessage(Component.literal("Out of ammo"));
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
					SoundSource.PLAYERS, 0.8f, 1.6f);
			return InteractionResult.FAIL;
		}

		for (int i = 0; i < g.burst(); i++) {
			if (i == 0) {
				fire(server, player);
			} else {
				Scheduler.later(i * g.burstGap(), () -> {
					if (player.isAlive()) {
						fire(server, player);
					}
				});
			}
		}
		if (g.recoil() > 0) {
			Vec3 back = player.getLookAngle().scale(-g.recoil());
			player.push(back.x, back.y * 0.3 + 0.05, back.z);
			player.syncVelocity = true;
		}
		player.getCooldowns().addCooldown(gun, g.cooldown());
		return InteractionResult.SUCCESS;
	}

	static boolean takeAmmo(Player player, Item ammo) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ammo)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private void fire(ServerLevel level, Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 muzzle = eye.add(player.getLookAngle().scale(1.1)).add(0, -0.18, 0);
		muzzleFlash(level, muzzle);
		for (int p = 0; p < g.pellets(); p++) {
			Vec3 dir = player.getLookAngle();
			if (g.spread() > 0) {
				dir = dir.add(
						(level.getRandom().nextDouble() - 0.5) * 2 * g.spread(),
						(level.getRandom().nextDouble() - 0.5) * 2 * g.spread(),
						(level.getRandom().nextDouble() - 0.5) * 2 * g.spread()).normalize();
			}
			Vec3 end = eye.add(dir.scale(g.range()));
			BlockHitResult block = level.clip(
					new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			double maxDist = block.getType() == HitResult.Type.MISS ? g.range() : block.getLocation().distanceTo(eye);
			Vec3 stop = eye.add(dir.scale(maxDist));

			AABB area = player.getBoundingBox().expandTowards(dir.scale(maxDist)).inflate(1.0);
			List<Entity> victims = new ArrayList<>();
			Entity first = null;
			double best = maxDist;
			for (Entity e : level.getEntities(player, area, en -> en.isAlive() && en.isPickable() && !en.isSpectator())) {
				Optional<Vec3> pos = e.getBoundingBox().inflate(0.15).clip(eye, stop);
				if (pos.isPresent()) {
					double d = eye.distanceTo(pos.get());
					if (g.pierce()) {
						victims.add(e);
					} else if (d < best) {
						best = d;
						first = e;
					}
				}
			}
			if (!g.pierce() && first != null) {
				victims.add(first);
				stop = eye.add(dir.scale(best));
			}
			for (Entity v : victims) {
				Fx.hurt(level, v, level.damageSources().playerAttack(player), g.damage());
				Vec3 c = v.position().add(0, v.getBbHeight() * 0.6, 0);
				impact(level, c, true);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6f, 1.5f);
			}
			if (victims.isEmpty() && block.getType() != HitResult.Type.MISS) {
				impact(level, stop, false);
			}
			tracer(level, muzzle, stop);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), g.sound(), SoundSource.PLAYERS, 1.0f, g.pitch());
		if (g.style() == Style.PLASMA) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
					SoundSource.PLAYERS, 0.35f, 2.0f);
		}
		if (g.style() == Style.RAIL) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
					SoundSource.PLAYERS, 0.8f, 1.6f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE,
					SoundSource.PLAYERS, 1.0f, 1.8f);
		}
	}

	private void muzzleFlash(ServerLevel l, Vec3 m) {
		switch (g.style()) {
			case BULLET -> {
				Fx.p(l, ParticleTypes.FLAME, m.x, m.y, m.z, 3, 0.03, 0.03, 0.03, 0.06);
				Fx.p(l, ParticleTypes.SMOKE, m.x, m.y, m.z, 2, 0.03, 0.03, 0.03, 0.02);
				Fx.p(l, ParticleTypes.CRIT, m.x, m.y, m.z, 2, 0.05, 0.05, 0.05, 0.2);
			}
			case PLASMA -> {
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, m.x, m.y, m.z, 5, 0.05, 0.05, 0.05, 0.08);
				Fx.p(l, ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 6, 0.1, 0.1, 0.1, 0.3);
				Fx.p(l, ParticleTypes.END_ROD, m.x, m.y, m.z, 1, 0, 0, 0, 0);
			}
			case RAIL -> {
				Fx.p(l, ParticleTypes.SONIC_BOOM, m.x, m.y, m.z, 1, 0, 0, 0, 0);
				Fx.p(l, ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 20, 0.2, 0.2, 0.2, 0.6);
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, m.x, m.y, m.z, 10, 0.1, 0.1, 0.1, 0.2);
			}
		}
	}

	private void impact(ServerLevel l, Vec3 at, boolean entity) {
		switch (g.style()) {
			case BULLET -> {
				Fx.p(l, entity ? ParticleTypes.CRIT : ParticleTypes.POOF, at.x, at.y, at.z, entity ? 8 : 4, 0.1, 0.1, 0.1, 0.2);
				Fx.p(l, ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.3);
			}
			case PLASMA -> {
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 12, 0.15, 0.15, 0.15, 0.12);
				Fx.p(l, ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.5);
				Fx.p(l, ParticleTypes.END_ROD, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.1);
			}
			case RAIL -> {
				Fx.p(l, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				Fx.p(l, ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 25, 0.25, 0.25, 0.25, 0.25);
				Fx.p(l, ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 35, 0.3, 0.3, 0.3, 0.8);
			}
		}
	}

	private void tracer(ServerLevel l, Vec3 from, Vec3 to) {
		switch (g.style()) {
			case BULLET -> {
				Vec3 d = to.subtract(from);
				double len = d.length();
				if (len < 0.1) {
					return;
				}
				Vec3 step = d.scale(1.0 / len);
				for (double t = 0; t < len; t += 1.0) {
					Vec3 pos = from.add(step.scale(t));
					Fx.p(l, ParticleTypes.CRIT, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
					if (((int) t) % 3 == 0) {
						Fx.p(l, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
					}
				}
			}
			case PLASMA -> Fx.blueBeam(l, from, to, 0.5, false);
			case RAIL -> {
				Fx.blueBeam(l, from, to, 0.3, true);
				Fx.helix(l, from, to, 0.45, 1.3);
			}
		}
	}
}
