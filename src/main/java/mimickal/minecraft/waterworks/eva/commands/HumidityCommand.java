package mimickal.minecraft.waterworks.eva.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import mimickal.minecraft.waterworks.eva.EvaData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Command for getting and setting a chunk's humidity. */
public class HumidityCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CMD_NAME = "humidity";
    private static final String ARG_X = "x";
    private static final String ARG_Z = "z";
    private static final String ARG_AMT = "amount";


    // Minecraft's command definition system is completely insane,
    // and the vanilla definitions are (somehow) even more insane.
    //
    // I've done my best to break this command into logical groupings,
    // but I'm just a programmer, not a miracle worker.

    /** Subcommand: {@code humidity get} */
    private static final LiteralArgumentBuilder<CommandSourceStack> SUBCMD_GET =
        Commands.literal("get")
            .executes(HumidityCommand::getHumidityCurrentChunk)
            .then(addOptionalCoordinateArgs(HumidityCommand::getHumidityAtPos));

    /** Subcommand: {@code humidity set} */
    private static final LiteralArgumentBuilder<CommandSourceStack> SUBCMD_SET =
        Commands.literal("set")
            .then(Commands.argument(ARG_AMT, IntegerArgumentType.integer())
                .executes(HumidityCommand::setHumidityCurrentChunk)
                .then(addOptionalCoordinateArgs(HumidityCommand::setHumidityAtPos))
            );

    /** Subcommand: {@code humidity reset [all] } */
    private static final LiteralArgumentBuilder<CommandSourceStack> SUBCMD_RESET =
        Commands.literal("reset")
            .executes(HumidityCommand::resetHumidityCurrentChunk)
            .then(Commands.literal("all")
                .executes(HumidityCommand::resetHumidityAll)
            )
            .then(addOptionalCoordinateArgs(HumidityCommand::resetHumidityAtPos));

    /** Top-level command: {@code humidity} */
    private static final LiteralArgumentBuilder<CommandSourceStack> CMD_HUMIDITY =
        Commands.literal(CMD_NAME)
            .requires(req -> req.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(SUBCMD_GET)
            .then(SUBCMD_SET)
            .then(SUBCMD_RESET);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LOGGER.debug("Registering command: {}", CMD_NAME);
        event.getDispatcher().register(CMD_HUMIDITY);
    }

    /**
     * Adds two optional coordinate arguments to a command, along with a function to invoke with those arguments.
     * <p>
     * For example: {@code humidity get [x] [z]}
     */
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> addOptionalCoordinateArgs(
        TriFunction<CommandContext<CommandSourceStack>, Integer, Integer, Integer> executor
    ) {
        return Commands.argument(ARG_X, IntegerArgumentType.integer())
            .then(Commands.argument(ARG_Z, IntegerArgumentType.integer())
                .executes(context -> {
                    int x = IntegerArgumentType.getInteger(context, ARG_X);
                    int z = IntegerArgumentType.getInteger(context, ARG_Z);
                    return executor.apply(context, x, z);
                })
            );
    }

    /** Prints the humidity at the chunk of the player who invoked this command. */
    private static int getHumidityCurrentChunk(CommandContext<CommandSourceStack> context) {
        return printHumidityCommon(context, getPlayerChunk(context));
    }

    /** Prints the humidity at the chunk given in the coordinates. */
    private static int getHumidityAtPos(CommandContext<CommandSourceStack> context, int x, int z) {
        return printHumidityCommon(context, new ChunkPos(x, z));
    }

    /** Common logic for printing humidity at a chunk. */
    private static int printHumidityCommon(CommandContext<CommandSourceStack> context, ChunkPos pos) {
        ServerLevel world = context.getSource().getLevel();
        int humidity = EvaData.get(world).getHumidity(pos);
        sendMsg(context, "Humidity at", pos, ":", humidity, "mB");
        return humidity;
    }

    /** Sets the humidity at the chunk of the player who invoked this command. */
    private static int setHumidityCurrentChunk(CommandContext<CommandSourceStack> context) {
        return setHumidityCommon(context, getPlayerChunk(context));
    }

    /** Sets the humidity at the chunk given in the coordinates. */
    private static int setHumidityAtPos(CommandContext<CommandSourceStack> context, int x, int z) {
        return setHumidityCommon(context, new ChunkPos(x, z));
    }

    /** Common logic for changing humidity at a chunk. */
    private static int setHumidityCommon(CommandContext<CommandSourceStack> context, ChunkPos pos) {
        ServerLevel world = context.getSource().getLevel();
        int newAmount = IntegerArgumentType.getInteger(context, ARG_AMT);
        int oldAmount = EvaData.get(world).getHumidity(pos);
        EvaData.get(world).setHumidity(pos, newAmount);

        sendMsg(context, "Setting humidity at", pos, ":", oldAmount, "mB", "->", newAmount, "mB");
        return newAmount;
    }

    /** Resets the humidity to default at the chunk of the player who invoked this command. */
    private static int resetHumidityCurrentChunk(CommandContext<CommandSourceStack> context) {
        return resetHumidityCommon(context, getPlayerChunk(context));
    }

    /** Resets the humidity to default at the chunk given in the coordinates. */
    private static int resetHumidityAtPos(CommandContext<CommandSourceStack> context, int x, int z) {
        return resetHumidityCommon(context, new ChunkPos(x, z));
    }

    /** Common logic for resetting humidity at a chunk. */
    private static int resetHumidityCommon(CommandContext<CommandSourceStack> context, ChunkPos pos) {
        ServerLevel world = context.getSource().getLevel();
        EvaData.get(world).resetHumidity(pos);
        int newAmount = EvaData.get(world).getHumidity(pos);

        sendMsg(context, "Resetting humidity at", pos, ":", newAmount, "mB");
        return 0; // Doesn't correspond to anything, but also doesn't matter...?
    }

    /** Resets <i>the entire</i> humidity map to default (actually it just deletes the map). */
    private static int resetHumidityAll(CommandContext<CommandSourceStack> context) {
        ServerLevel world = context.getSource().getLevel();
        EvaData.get(world).resetAllHumidity(true);
        sendMsg(context, "Resetting ALL humidity");
        return 0; // Still doesn't correspond to anything.
    }

    /** Returns the position of the chunk the player who invoked this command is in. */
    private static ChunkPos getPlayerChunk(CommandContext<CommandSourceStack> context) {
        BlockPos playerPosition = new BlockPos(context.getSource().getPosition());
        return new ChunkPos(playerPosition);
    }

    /** Prints a message to the screen in response to a command. */
    private static void sendMsg(CommandContext<CommandSourceStack> context, Object... parts) {
        String message = Arrays.stream(parts).map(Object::toString).collect(Collectors.joining(" "));
        context.getSource().sendSuccess(new TextComponent(message), false);
    }
}
