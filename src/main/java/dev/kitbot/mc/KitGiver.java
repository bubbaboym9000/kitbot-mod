package dev.kitbot.mc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Hands a player the Classes SMP kit, laid out slot for slot like the kit-editor screenshot in the Discord post.
 *
 * <pre>
 *  armor    diamond helmet / chestplate / leggings / boots, Protection III        offhand  shield
 *
 *  inv row 1 (9-17)   diamond pickaxe | speed | strength | turtle master II | 3x healing | 16 buckets | Power V bow
 *  inv row 2 (18-26)  64 XP | speed | strength | turtle master II | weaving | 2x healing | 64 arrows | (unreadable)
 *  inv row 3 (27-35)  64 XP | speed | strength | 64 golden apples | weaving | 3x healing | 64 wind charges
 *  hotbar (0-8)       Sharp IV sword | Sharp IV axe | 64 cobwebs | 64 golden apples | weaving | water bucket |
 *                     healing | water bucket | totem
 * </pre>
 *
 * All potions are splash potions. What the player was carrying is dropped at their feet, not deleted.
 */
final class KitGiver {
    private KitGiver() {}

    static void give(ServerPlayer player, ServerLevel level) {
        Inventory inv = player.getInventory();
        List<ItemStack> previous = new ArrayList<>();

        // take everything off the player first so each kit item lands in its own slot
        for (int slot = 0; slot < 36; slot++) {
            ItemStack old = inv.getItem(slot);
            if (!old.isEmpty()) previous.add(old.copy());
            inv.setItem(slot, ItemStack.EMPTY);
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            ItemStack old = player.getItemBySlot(slot);
            if (!old.isEmpty()) previous.add(old.copy());
        }

        player.setItemSlot(EquipmentSlot.HEAD, Compat.enchanted(level, Items.DIAMOND_HELMET, Enchantments.PROTECTION, 3));
        player.setItemSlot(EquipmentSlot.CHEST, Compat.enchanted(level, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 3));
        player.setItemSlot(EquipmentSlot.LEGS, Compat.enchanted(level, Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 3));
        player.setItemSlot(EquipmentSlot.FEET, Compat.enchanted(level, Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 3));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

        // hotbar
        inv.setItem(0, Compat.enchanted(level, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 4));
        inv.setItem(1, Compat.enchanted(level, Items.NETHERITE_AXE, Enchantments.SHARPNESS, 4));
        inv.setItem(2, new ItemStack(Items.COBWEB, 64));
        inv.setItem(3, new ItemStack(Items.GOLDEN_APPLE, 64));
        inv.setItem(4, Compat.splash(Potions.WEAVING));
        inv.setItem(5, new ItemStack(Items.WATER_BUCKET));
        inv.setItem(6, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(7, new ItemStack(Items.WATER_BUCKET));
        inv.setItem(8, new ItemStack(Items.TOTEM_OF_UNDYING));

        // inventory row 1
        inv.setItem(9, Compat.enchanted(level, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5));
        inv.setItem(10, Compat.splash(Potions.SWIFTNESS));
        inv.setItem(11, Compat.splash(Potions.STRENGTH));
        inv.setItem(12, Compat.splash(Potions.STRONG_TURTLE_MASTER));
        inv.setItem(13, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(14, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(15, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(16, new ItemStack(Items.BUCKET, 16));
        inv.setItem(17, Compat.enchanted(level, Items.BOW, Enchantments.POWER, 5));

        // inventory row 2
        inv.setItem(18, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        inv.setItem(19, Compat.splash(Potions.SWIFTNESS));
        inv.setItem(20, Compat.splash(Potions.STRENGTH));
        inv.setItem(21, Compat.splash(Potions.STRONG_TURTLE_MASTER));
        inv.setItem(22, Compat.splash(Potions.WEAVING));
        inv.setItem(23, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(24, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(25, new ItemStack(Items.ARROW, 64));
        // slot 26 holds a stack of 64 of something the screenshot is too blurry to identify; left empty

        // inventory row 3
        inv.setItem(27, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        inv.setItem(28, Compat.splash(Potions.SWIFTNESS));
        inv.setItem(29, Compat.splash(Potions.STRENGTH));
        inv.setItem(30, new ItemStack(Items.GOLDEN_APPLE, 64));
        inv.setItem(31, Compat.splash(Potions.WEAVING));
        inv.setItem(32, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(33, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(34, Compat.splash(Potions.STRONG_HEALING));
        inv.setItem(35, new ItemStack(Items.WIND_CHARGE, 64));

        for (ItemStack old : previous) Compat.dropAtFeet(player, old);
    }
}
