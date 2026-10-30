package dev.kitbot.mc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * KitBot: a server-side sparring partner for the Classes SMP kit.
 *
 *   /kitbot         spawn a bot with the kit six blocks in front of you; it fights you (and only you)
 *   /kitbot clear   remove your bots
 *   /kit            give yourself the kit (also /kitbot kit)
 *
 * Server-side only: it works in singleplayer (the integrated server) and on a dedicated server, and
 * players do not need the mod installed.
 */
public class KitBotMod implements ModInitializer {
    private static final int MAX_BOTS_PER_PLAYER = 3;
    private static final List<KitBrain> BOTS = new ArrayList<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("kitbot")
                .executes(ctx -> spawn(ctx.getSource()))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("kit").executes(ctx -> giveKit(ctx.getSource()))));
            dispatcher.register(Commands.literal("kit").executes(ctx -> giveKit(ctx.getSource())));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Iterator<KitBrain> it = BOTS.iterator(); it.hasNext();) {
                KitBrain brain = it.next();
                brain.tick(server);
                if (brain.finished) it.remove();
            }
        });

        // the integrated server restarts inside one JVM when you switch worlds
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BOTS.clear());
    }

    private static int spawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        long mine = BOTS.stream().filter(b -> b.ownerId.equals(owner.getUUID()) && !b.finished).count();
        if (mine >= MAX_BOTS_PER_PLAYER) {
            source.sendFailure(Component.literal("You already have " + MAX_BOTS_PER_PLAYER + " KitBots. Use /kitbot clear first."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Zombie mob = new Zombie(level);
        Vec3 look = owner.getLookAngle();
        mob.setPos(owner.getX() + look.x * 6, owner.getY(), owner.getZ() + look.z * 6);
        mob.setYRot(owner.getYRot() + 180.0F); // face the owner

        KitBrain brain = new KitBrain(mob, owner.getUUID());
        brain.equipKit(level);
        level.addFreshEntity(mob);
        BOTS.add(brain);

        source.sendSuccess(() -> Component.literal("KitBot spawned. It will fight you with the Classes SMP kit. /kitbot clear removes it."), false);
        return 1;
    }

    private static int giveKit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        KitGiver.give(player, source.getLevel());
        source.sendSuccess(() -> Component.literal("Classes SMP kit given. Anything you were wearing went into your inventory."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        int removed = 0;
        for (KitBrain brain : BOTS) {
            if (brain.ownerId.equals(owner.getUUID()) && !brain.finished) {
                brain.dismiss();
                removed++;
            }
        }
        final int count = removed;
        source.sendSuccess(() -> Component.literal("Removed " + count + " KitBot(s)."), false);
        return count;
    }
}
