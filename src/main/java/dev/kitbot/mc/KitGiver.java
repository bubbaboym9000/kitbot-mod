package dev.kitbot.mc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Hands a player the Classes SMP kit: Protection III armor, Sharpness IV sword and axe, a Power V bow, a
 * totem, 128 golden apples, 2 enchanted golden apples, 64 cobwebs, 128 experience bottles and the potions
 * (speed, strength, 2 strong turtle masters, 3 weaving).
 */
final class KitGiver {
    private KitGiver() {}

    static void give(ServerPlayer player, ServerLevel level) {
        // armor and totem go straight onto the player; whatever was there moves to the inventory
        wear(player, EquipmentSlot.HEAD, Compat.enchanted(level, Items.NETHERITE_HELMET, Enchantments.PROTECTION, 3));
        wear(player, EquipmentSlot.CHEST, Compat.enchanted(level, Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 3));
        wear(player, EquipmentSlot.LEGS, Compat.enchanted(level, Items.NETHERITE_LEGGINGS, Enchantments.PROTECTION, 3));
        wear(player, EquipmentSlot.FEET, Compat.enchanted(level, Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 3));
        wear(player, EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        Compat.give(player, Compat.enchanted(level, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 4));
        Compat.give(player, Compat.enchanted(level, Items.NETHERITE_AXE, Enchantments.SHARPNESS, 4));
        Compat.give(player, Compat.enchanted(level, Items.BOW, Enchantments.POWER, 5));
        Compat.give(player, new ItemStack(Items.ARROW, 64));

        Compat.give(player, new ItemStack(Items.GOLDEN_APPLE, 64));
        Compat.give(player, new ItemStack(Items.GOLDEN_APPLE, 64));
        Compat.give(player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
        Compat.give(player, new ItemStack(Items.COBWEB, 64));
        Compat.give(player, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        Compat.give(player, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));

        Compat.give(player, Compat.potion(Potions.SWIFTNESS));
        Compat.give(player, Compat.potion(Potions.STRENGTH));
        for (int i = 0; i < 2; i++) Compat.give(player, Compat.potion(Potions.STRONG_TURTLE_MASTER));
        for (int i = 0; i < 3; i++) Compat.give(player, Compat.potion(Potions.WEAVING));
    }

    private static void wear(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        ItemStack old = player.getItemBySlot(slot);
        if (!old.isEmpty()) Compat.give(player, old.copy());
        player.setItemSlot(slot, stack);
    }
}
