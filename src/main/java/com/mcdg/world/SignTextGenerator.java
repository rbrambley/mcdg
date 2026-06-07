package com.mcdg.world;

import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Places and configures sign text for course tee signs and leaderboards.
 */
final class SignTextGenerator {
    private SignTextGenerator() {}

    static void placeLeaderboardSign(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            String courseName,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Place sign at the front edge of the deck (v=-2) where players walk in from the tee.
        // The sign post sits on the deck surface so the readable face is at eye level.
        BlockPos signGround = PlacementUtils.orientedOffset(hubSurface, side, back, 3, -2, 0);
        CoursePlacementService.clearHeadroom(world, signGround, 1, 3, originalBlocks, protectedPositions);
        BlockPos signPos = signGround.up(1);
        BlockState signState = Blocks.OAK_SIGN.getDefaultState();
        PlacementUtils.setTrackedBlock(world, signPos, signState, originalBlocks);

        if (world.getBlockEntity(signPos) instanceof SignBlockEntity signBlockEntity) {
            SignText front = signBlockEntity.getFrontText();
            String nameLine = courseName.length() > 13 ? courseName.substring(0, 13) : courseName;
            SignText updated = front
                    .withMessage(0, Text.literal(nameLine))
                    .withMessage(1, Text.literal("Leaderboard"))
                    .withMessage(2, Text.literal("Right-click"))
                    .withMessage(3, Text.literal("to view scores"));
            signBlockEntity.setText(updated, true);
            signBlockEntity.setText(updated, false);
            signBlockEntity.markDirty();
        }
    }

    static void placeTeeHoleSign(
            ServerWorld world,
            BlockPos signGround,
            int faceDirX,
            int faceDirZ,
            int holeNumber,
            int par,
            int distanceFeet,
            boolean signatureHole,
            String hazardNote,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        CoursePlacementService.clearHeadroom(world, signGround, 0, 3, originalBlocks, null);
        BlockPos signPos = signGround.up(1);
        BlockState signState = Blocks.OAK_SIGN
                .getDefaultState()
            .with(Properties.ROTATION, standingSignRotationForCardinal(faceDirX, faceDirZ));
        PlacementUtils.setTrackedBlock(world, signPos, signState, originalBlocks);

        if (world.getBlockEntity(signPos) instanceof SignBlockEntity signBlockEntity) {
            SignText front = signBlockEntity.getFrontText();
            String holeLine = signatureHole ? ("SIG H" + holeNumber) : ("Hole " + holeNumber);
            SignText updated = front
                    .withMessage(0, Text.literal(holeLine))
                    .withMessage(1, Text.literal("Par " + par))
                    .withMessage(2, Text.literal(distanceFeet + " ft"))
                    .withMessage(3, Text.literal(hazardNote));
            signBlockEntity.setText(updated, true);
            signBlockEntity.setText(updated, false);
            signBlockEntity.markDirty();
        }
    }

    static int standingSignRotationForCardinal(int dirX, int dirZ) {
        if (dirX == 0 && dirZ == 0) {
            return 0;
        }

        if (dirX == 0 && dirZ > 0) {
            return 0; // south
        }
        if (dirX < 0 && dirZ == 0) {
            return 4; // west
        }
        if (dirX == 0 && dirZ < 0) {
            return 8; // north
        }
        if (dirX > 0 && dirZ == 0) {
            return 12; // east
        }

        // Fallback for unexpected non-cardinal vectors.
        return 0;
    }

}
