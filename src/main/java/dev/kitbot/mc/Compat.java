package dev.kitbot.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Every Minecraft call whose name or signature has moved between 1.21.x releases lives here, written
 * against the 1.21.11 Mojang mappings. KitBrain and KitBotMod only use stable API (entity movement,
 * targeting, equipment, commands), so if the compiler complains about anything, it should be in this
 * file, and the fix is a one-liner. Notes on the usual suspects are next to each method.
 */
final class Compat {
    private Compat() {}

    // Effect holders. Older mappings call these MOVEMENT_SPEED / DAMAGE_BOOST / MOVEMENT_SLOWDOWN / DAMAGE_RESISTANCE.
    static final Holder<MobEffect> SPEED = MobEffects.SPEED;
    static final Holder<MobEffect> STRENGTH = MobEffects.STRENGTH;
    static final Holder<MobEffect> SLOWNESS = MobEffects.SLOWNESS;
    static final Holder<MobEffect> RESISTANCE = MobEffects.RESISTANCE;
    static final Holder<MobEffect> WEAVING = MobEffects.WEAVING;

    /** Amplifier of an active effect (0 = level I), or -1 when the entity does not have it. */
    static int amplifier(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    static void addEffect(LivingEntity entity, Holder<MobEffect> effect, int ticks, int amplifier) {
        entity.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    /** An item with one enchantment. Enchantments are data-driven, so they come from the registry. */
    static ItemStack enchanted(ServerLevel level, Item item, ResourceKey<Enchantment> enchantment, int enchantLevel) {
        HolderLookup.RegistryLookup<Enchantment> lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack stack = new ItemStack(item);
        stack.enchant(lookup.getOrThrow(enchantment), enchantLevel);
        return stack;
    }

    /** A drinkable potion of the given type. */
    static ItemStack potion(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    /** Put the stack in the player's inventory; whatever does not fit is dropped at their feet. */
    static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    /** Make the zombie body behave like a sparring partner rather than a zombie. */
    static void prepareMob(Zombie mob) {
        mob.setBaby(false);
        mob.setCanPickUpLoot(false);
        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal("KitBot"));
        mob.setCustomNameVisible(true);
        setBase(mob, Attributes.ATTACK_DAMAGE, 1.0); // like a player: the sword supplies the rest
        setBase(mob, Attributes.FOLLOW_RANGE, 64.0);
        setBase(mob, Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
        // no kit items on the floor when it dies
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            mob.setDropChance(slot, 0.0F); // Mob#setDropChance(EquipmentSlot, float)
        }
    }

    private static void setBase(Zombie mob, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    /**
     * Fire a power-V arrow. 'var' on purpose: the arrow classes moved packages in recent versions, and
     * ProjectileUtil.getMobArrow(shooter, ammo, power, weapon) is what skeletons use.
     */
    static void fireArrow(ServerLevel level, LivingEntity shooter, ItemStack bow, Vec3 from, double dx, double dy, double dz) {
        var arrow = ProjectileUtil.getMobArrow(shooter, new ItemStack(Items.ARROW), 1.0F, bow);
        arrow.setPos(from.x, from.y, from.z);
        arrow.shoot(dx, dy, dz, 3.0F, 0.0F); // full draw speed
        arrow.setBaseDamage(5.0); // 2.0 vanilla base + 3.0 from Power V, times speed 3 = 15 damage
        arrow.setCritArrow(true);
        level.addFreshEntity(arrow);
    }

    // ---- blocks ----
    static boolean isCobweb(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.COBWEB);
    }

    static boolean isAir(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }

    static void placeCobweb(Level level, BlockPos pos) {
        level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
    }

    static void breakBlock(Level level, BlockPos pos) {
        level.destroyBlock(pos, false);
    }

    static void removeBlock(Level level, BlockPos pos) {
        level.removeBlock(pos, false);
    }
}
