#!/usr/bin/env bash
# Lists method signatures of the Minecraft classes this mod uses.
OUT="$1"
: > "$OUT"
JARS=$(find "$HOME/.gradle" "$HOME/.m2" -name '*.jar' -size +5M 2>/dev/null | grep -i -E 'minecraft|loom' | grep -v -E 'sources|javadoc')
echo "JARS:" >> "$OUT"; echo "$JARS" >> "$OUT"
CP=$(echo "$JARS" | tr '\n' ':')
for c in \
  net.minecraft.resources.Identifier net.minecraft.resources.ResourceLocation net.minecraft.resources.ResourceKey \
  net.minecraft.world.item.Item 'net.minecraft.world.item.Item$Properties' \
  net.minecraft.world.entity.Entity net.minecraft.server.level.ServerLevel net.minecraft.world.level.Level \
  net.minecraft.world.entity.player.Player net.minecraft.world.item.ItemCooldowns \
  net.minecraft.world.damagesource.DamageSources net.minecraft.sounds.SoundEvents \
  net.minecraft.world.level.ClipContext net.minecraft.world.phys.AABB net.minecraft.core.particles.ParticleTypes \
  net.minecraft.world.InteractionResult net.minecraft.network.chat.Component net.minecraft.world.entity.player.Inventory; do
  echo "=== $c" >> "$OUT"
  javap -cp "$CP" -public "$c" >> "$OUT" 2>&1
done

# Fabric API classes (to find the creative tab events)
{
  echo "=== FABRIC API jars"
  FJARS=$(find "$HOME/.gradle" -name 'fabric-*.jar' 2>/dev/null | grep -v -E 'sources|javadoc')
  echo "$FJARS" | head -80
  for j in $FJARS; do
    unzip -l "$j" 2>/dev/null | awk '{print $4}' | grep -i -E 'creativetab|itemgroup|CreativeModeTab' | sed "s#^#$(basename $j): #"
  done
} >> "$OUT" 2>&1
