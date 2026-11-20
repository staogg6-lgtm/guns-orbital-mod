package com.staogg6.gunsmod;

import java.util.Optional;

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

/** A hitscan gun: fires instantly along your crosshair, uses one bullet per trigger pull. */
public class GunItem extends Item {
	private final float damage;
	private final int cooldownTicks;
	private final int pellets;
	private final double spread;
	private final double range;
	private final int burst;
	private final SoundEvent sound;
	private final float pitch;

	public GunItem(Properties props, float damage, int cooldownTicks, int pellets, double spread,
			double range, int burst, SoundEvent sound, float pitch) {
		super(props);
		this.damage = damage;
		this.cooldownTicks = cooldownTicks;
		this.pellets = pellets;
		this.spread = spread;
		this.range = range;
		this.burst = burst;
		this.sound = sound;
		this.pitch = pitch;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel server = (ServerLevel) level;

		if (!player.isCreative() && !takeBullet(player)) {
			player.sendOverlayMessage(Component.literal("Out of bullets"));
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
					SoundSource.PLAYERS, 0.8f, 1.6f);
			return InteractionResult.FAIL;
		}

		for (int i = 0; i < burst; i++) {
			if (i == 0) {
				fire(server, player);
			} else {
				Scheduler.later(i * 2, () -> {
					if (player.isAlive()) {
						fire(server, player);
					}
				});
			}
		}
		player.getCooldowns().addCooldown(gun, cooldownTicks);
		return InteractionResult.SUCCESS;
	}

	private static boolean takeBullet(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.BULLET)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private void fire(ServerLevel level, Player player) {
		Vec3 eye = player.getEyePosition();
		for (int p = 0; p < pellets; p++) {
			Vec3 dir = player.getLookAngle();
			if (spread > 0) {
				dir = dir.add(
						(level.getRandom().nextDouble() - 0.5) * 2 * spread,
						(level.getRandom().nextDouble() - 0.5) * 2 * spread,
						(level.getRandom().nextDouble() - 0.5) * 2 * spread).normalize();
			}
			Vec3 end = eye.add(dir.scale(range));
			BlockHitResult block = level.clip(
					new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			double maxDist = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
			Vec3 stop = eye.add(dir.scale(maxDist));

			Entity hit = null;
			double best = maxDist;
			AABB area = player.getBoundingBox().expandTowards(dir.scale(maxDist)).inflate(1.0);
			for (Entity e : level.getEntities(player, area, en -> en.isAlive() && en.isPickable() && !en.isSpectator())) {
				AABB box = e.getBoundingBox().inflate(0.15);
				Optional<Vec3> pos = box.clip(eye, stop);
				if (pos.isPresent()) {
					double d = eye.distanceTo(pos.get());
					if (d < best) {
						best = d;
						hit = e;
					}
				}
			}
			if (hit != null) {
				hit.hurtServer(level, level.damageSources().playerAttack(player), damage);
				stop = eye.add(dir.scale(best));
				level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 6, 0.1, 0.1, 0.1, 0.2);
			} else if (block.getType() != HitResult.Type.MISS) {
				level.sendParticles(ParticleTypes.POOF, stop.x, stop.y, stop.z, 3, 0.05, 0.05, 0.05, 0.02);
			}
			tracer(level, eye.add(dir.scale(1.2)).add(0, -0.15, 0), stop);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.9f, pitch);
	}

	private static void tracer(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		double len = delta.length();
		if (len < 0.1) {
			return;
		}
		Vec3 step = delta.scale(1.0 / len);
		for (double d = 0; d < len; d += 1.2) {
			Vec3 pos = from.add(step.scale(d));
			level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		}
	}
}
