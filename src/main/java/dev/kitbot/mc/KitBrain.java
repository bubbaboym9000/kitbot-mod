package dev.kitbot.mc;

import dev.kitbot.core.Ballistics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * The fight logic for one KitBot. The body is a vanilla zombie, so walking, pathfinding and sword swings
 * are vanilla; while the brain wants the zombie to do something else (draw a bow, eat, keep away) it
 * clears the zombie's target so the vanilla attack goal stays out of the way.
 *
 * The kit: netherite armor with Protection III, a Sharpness IV sword, a Power V bow, a totem in the
 * offhand, 128 golden apples, 2 enchanted golden apples, 64 cobwebs, and the potions (speed, strength and
 * weaving are applied at the start, 2 strong turtle masters are saved for emergencies).
 */
final class KitBrain {
    private enum Mode { MELEE, EAT, DRAW }

    // thresholds in health points (20 = full), same numbers as the Node bot
    private static final float GAP_AT = 11, EGAP_AT = 7, TURTLE_AT = 9, CALM_HEAL_AT = 15, NO_TOTEM_BONUS = 3;
    private static final double BOW_MIN = 12, BOW_MAX = 40;
    private static final int DRAW_TICKS = 22; // a full draw is 20 ticks; the extra two cover latency

    final Zombie mob;
    final UUID ownerId;
    boolean finished;

    private int gapples = 128, egapples = 2, turtles = 2, cobwebs = 64, arrows = 64;
    private boolean opened;
    private ItemStack sword;
    private ItemStack bow;
    private Mode mode = Mode.MELEE;
    private int modeTicks;
    private long age;
    private long lastEat = -1000, lastEgap = -1000, lastTurtle = -1000, lastWeb = -1000, lastBow = -1000;
    private Vec3 prevOwnerPos;
    private Vec3 ownerVel = Vec3.ZERO;
    private final List<PlacedWeb> placedWebs = new ArrayList<>();

    private record PlacedWeb(BlockPos pos, long expires) {}

    KitBrain(Zombie mob, UUID ownerId) {
        this.mob = mob;
        this.ownerId = ownerId;
    }

    void equipKit(ServerLevel level) {
        sword = Compat.enchanted(level, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 4);
        bow = Compat.enchanted(level, Items.BOW, Enchantments.POWER, 5);
        mob.setItemSlot(EquipmentSlot.MAINHAND, sword);
        mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        mob.setItemSlot(EquipmentSlot.HEAD, Compat.enchanted(level, Items.NETHERITE_HELMET, Enchantments.PROTECTION, 3));
        mob.setItemSlot(EquipmentSlot.CHEST, Compat.enchanted(level, Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 3));
        mob.setItemSlot(EquipmentSlot.LEGS, Compat.enchanted(level, Items.NETHERITE_LEGGINGS, Enchantments.PROTECTION, 3));
        mob.setItemSlot(EquipmentSlot.FEET, Compat.enchanted(level, Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 3));
        Compat.prepareMob(mob);
    }

    /** Remove the bot and any cobwebs it left behind. */
    void dismiss() {
        finished = true;
        clearWebs(true);
        if (mob.isAlive()) mob.discard();
    }

    void tick(MinecraftServer server) {
        age++;
        if (!mob.isAlive() || mob.isRemoved()) {
            if (!finished) {
                finished = true;
                clearWebs(true);
                ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
                if (owner != null && !mob.isAlive()) owner.sendSystemMessage(Component.literal("KitBot went down."));
            }
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        clearWebs(false);

        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            standDown();
            return;
        }
        trackOwner(owner);

        if (!opened) {
            opened = true;
            Compat.addEffect(mob, Compat.SPEED, 3600, 0);
            Compat.addEffect(mob, Compat.STRENGTH, 3600, 0);
            Compat.addEffect(mob, Compat.WEAVING, 3600, 0); // only matters on death: it leaves cobwebs behind
        }

        double dist = mob.distanceTo(owner);
        switch (mode) {
            case EAT -> tickEat(owner);
            case DRAW -> tickDraw(level, owner, dist);
            case MELEE -> tickMelee(level, owner, dist);
        }
    }

    // ---- modes ----

    private void tickMelee(ServerLevel level, ServerPlayer owner, double dist) {
        float hp = mob.getHealth();
        float bonus = hasTotem() ? 0 : NO_TOTEM_BONUS; // heal earlier once the totem is gone
        boolean ownerTurtled = Compat.amplifier(owner, Compat.SLOWNESS) >= 4 && Compat.amplifier(owner, Compat.RESISTANCE) >= 3;

        // stuck in a cobweb with the enemy out of reach: cut free
        if (dist > 3.2) breakOwnWeb(level);

        // healing
        if (age - lastEat >= 40) {
            if (dist < 6 && hp <= TURTLE_AT + bonus && turtles > 0 && age - lastTurtle > 440
                && Compat.amplifier(mob, Compat.RESISTANCE) < 3) {
                drinkTurtleMaster();
            }
            if (hp <= EGAP_AT + bonus && egapples > 0 && age - lastEgap > 300) {
                eat(owner, true);
                return;
            }
            if ((hp <= GAP_AT + bonus || (dist > 9 && hp <= CALM_HEAL_AT)) && gapples > 0) {
                eat(owner, false);
                return;
            }
        }

        tryWeb(level, owner, dist, ownerTurtled);

        // bow from range (closer in if the enemy is turtled and cannot close the gap)
        double bowMin = ownerTurtled ? 5 : BOW_MIN;
        if (arrows > 0 && age - lastBow > 30 && dist >= bowMin && dist <= BOW_MAX && mob.hasLineOfSight(owner)) {
            startDraw();
            return;
        }

        if (ownerTurtled) {
            keepAway(owner, dist); // slowness VI + resistance IV: no point trading blows, just wait it out
            return;
        }

        ensureSword();
        mob.setTarget(owner); // vanilla pathfinding + sword swings
    }

    private void tickEat(ServerPlayer owner) {
        mob.setTarget(owner); // keep walking at them while eating
        modeTicks++;
        if ((!mob.isUsingItem() && modeTicks > 2) || modeTicks > 80) {
            mode = Mode.MELEE;
            ensureSword();
        }
    }

    private void tickDraw(ServerLevel level, ServerPlayer owner, double dist) {
        modeTicks++;
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        if (dist < 7 || !mob.hasLineOfSight(owner)) { // they closed in, or ducked behind something
            cancelDraw();
            return;
        }
        if (modeTicks >= DRAW_TICKS) fire(level, owner);
    }

    // ---- actions ----

    private void eat(ServerPlayer owner, boolean enchanted) {
        Item item = enchanted ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE;
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        mob.startUsingItem(InteractionHand.MAIN_HAND); // vanilla applies the apple's effects when it finishes
        if (enchanted) {
            egapples--;
            lastEgap = age;
        } else {
            gapples--;
        }
        lastEat = age;
        mode = Mode.EAT;
        modeTicks = 0;
        mob.setTarget(owner);
    }

    private void drinkTurtleMaster() {
        turtles--;
        lastTurtle = age;
        Compat.addEffect(mob, Compat.SLOWNESS, 400, 5); // strong turtle master: Slowness VI + Resistance IV
        Compat.addEffect(mob, Compat.RESISTANCE, 400, 3);
    }

    private void startDraw() {
        mode = Mode.DRAW;
        modeTicks = 0;
        mob.setItemSlot(EquipmentSlot.MAINHAND, bow);
        mob.startUsingItem(InteractionHand.MAIN_HAND); // shows the draw pose; releasing a bow only fires for players
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    private void fire(ServerLevel level, ServerPlayer owner) {
        Vec3 eye = mob.getEyePosition();
        Ballistics.Aim aim = Ballistics.aim(eye.x, eye.y, eye.z, owner.getX(), owner.getY(0.5), owner.getZ(), ownerVel.x, ownerVel.z);
        if (aim != null) {
            Compat.fireArrow(level, mob, bow, eye, aim.dirX(), aim.dirY(), aim.dirZ());
            arrows--;
        }
        mob.stopUsingItem();
        lastBow = age;
        mode = Mode.MELEE;
        ensureSword();
    }

    private void cancelDraw() {
        mob.stopUsingItem();
        lastBow = age - 10;
        mode = Mode.MELEE;
        ensureSword();
    }

    private void keepAway(ServerPlayer owner, double dist) {
        mob.setTarget(null);
        ensureSword();
        mob.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        if (dist < 8) {
            Vec3 away = mob.position().subtract(owner.position());
            away = new Vec3(away.x, 0, away.z).normalize().scale(6);
            mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.0);
        } else {
            mob.getNavigation().stop();
        }
    }

    private void tryWeb(ServerLevel level, ServerPlayer owner, double dist, boolean ownerTurtled) {
        if (cobwebs <= 0 || ownerTurtled || age - lastWeb < 100 || dist > 4.2 || dist < 1.0) return;
        BlockPos feet = owner.blockPosition();
        if (!Compat.isAir(level, feet) || Compat.isAir(level, feet.below())) return; // already webbed, or they are airborne
        Compat.placeCobweb(level, feet);
        placedWebs.add(new PlacedWeb(feet, age + 160));
        cobwebs--;
        lastWeb = age;
    }

    private void breakOwnWeb(ServerLevel level) {
        BlockPos here = mob.blockPosition();
        if (Compat.isCobweb(level, here)) {
            Compat.breakBlock(level, here);
        } else if (Compat.isCobweb(level, here.above())) {
            Compat.breakBlock(level, here.above());
        }
    }

    /** Take back the cobwebs we placed once they have done their job (or all of them when forced). */
    private void clearWebs(boolean all) {
        if (placedWebs.isEmpty()) return;
        ServerLevel level = (ServerLevel) mob.level();
        placedWebs.removeIf(web -> {
            if (!all && web.expires() > age) return false;
            if (Compat.isCobweb(level, web.pos())) Compat.removeBlock(level, web.pos());
            return true;
        });
    }

    // ---- helpers ----

    private boolean hasTotem() {
        return mob.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private void ensureSword() {
        if (!mob.getMainHandItem().is(Items.NETHERITE_SWORD)) mob.setItemSlot(EquipmentSlot.MAINHAND, sword);
    }

    private void standDown() {
        if (mode == Mode.DRAW) mob.stopUsingItem();
        mode = Mode.MELEE;
        mob.setTarget(null);
        mob.getNavigation().stop();
        ensureSword();
    }

    /** The server does not track a player's velocity, so estimate it from how far they moved this tick. */
    private void trackOwner(ServerPlayer owner) {
        Vec3 pos = owner.position();
        if (prevOwnerPos != null) ownerVel = ownerVel.scale(0.5).add(pos.subtract(prevOwnerPos).scale(0.5));
        prevOwnerPos = pos;
    }
}
