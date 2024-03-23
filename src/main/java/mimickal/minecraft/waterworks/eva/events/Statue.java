package mimickal.minecraft.waterworks.eva.events;

import mimickal.minecraft.waterworks.ModBlocks;
import mimickal.minecraft.waterworks.eva.EvaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Statue {

    /** Update statue cache when a {@link ModBlocks#STATUE} is placed. */
    @SubscribeEvent
    public static void recordStatuePlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().getServer() == null) return; // Lets us safely cast world to ServerLevel
        if (event.getState().getBlock() != ModBlocks.STATUE.get()) return;
        EvaData.get((ServerLevel) event.getWorld()).changeStatueCount(event.getPos(), 1);
    }

    /** Update statue cache when a {@link ModBlocks#STATUE} is broken. */
    @SubscribeEvent
    public static void recordStatueBroken(BlockEvent.BreakEvent event) {
        if (event.getWorld().getServer() == null) return; // Lets us safely cast world to ServerLevel
        if (event.getState().getBlock() != ModBlocks.STATUE.get()) return;
        EvaData.get((ServerLevel) event.getWorld()).changeStatueCount(event.getPos(), -1);
    }

    /** Update statue cache when a {@link ModBlocks#STATUE} is broken by a piston. */
    @SubscribeEvent
    public static void recordStatueBrokenByPiston(PistonEvent.Pre event) {
        if (event.getWorld().getServer() == null) return; // Lets us safely cast world to ServerLevel

        ServerLevel world = (ServerLevel) event.getWorld();
        BlockPos targetPos = event.getPos().relative(event.getDirection());
        if (world.getBlockState(targetPos).getBlock() != ModBlocks.STATUE.get()) return;

        EvaData.get(world).changeStatueCount(targetPos, -1);
    }
}
