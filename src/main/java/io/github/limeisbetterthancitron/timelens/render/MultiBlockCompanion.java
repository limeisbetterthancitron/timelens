package io.github.limeisbetterthancitron.timelens.render;

import io.github.limeisbetterthancitron.timelens.util.BlockPosition;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;

import java.util.Optional;

/**
 * The second block of a two-block structure.
 *
 * <p>History backends record a door, bed or tall plant as a single entry at its base block, so a
 * reconstruction that replays only what was recorded produces half a door hanging in the air.
 * Deriving the missing half from the recorded one is what makes those structures render
 * correctly — and clearing the leftover half is what stops the reverse artefact when a structure
 * is being taken out of the view.
 *
 * @param position where the other half belongs
 * @param data     the block state that other half should show
 */
record MultiBlockCompanion(BlockPosition position, BlockData data) {

    /**
     * @return the other half of {@code data}, or empty if it is an ordinary single block
     */
    static Optional<MultiBlockCompanion> of(BlockPosition position, BlockData data) {
        if (data instanceof Bed bed) {
            return Optional.of(bedCompanion(position, bed));
        }
        if (isVerticalPair(data)) {
            return Optional.of(verticalCompanion(position, (Bisected) data));
        }
        return Optional.empty();
    }

    /**
     * Stairs and trapdoors also carry a {@code half}, but they occupy one block each, so
     * deriving a partner for them would invent a block that never existed.
     */
    private static boolean isVerticalPair(BlockData data) {
        return data instanceof Bisected && !(data instanceof Stairs) && !(data instanceof TrapDoor);
    }

    private static MultiBlockCompanion verticalCompanion(BlockPosition position, Bisected bisected) {
        Bisected other = (Bisected) bisected.clone();
        if (bisected.getHalf() == Bisected.Half.BOTTOM) {
            other.setHalf(Bisected.Half.TOP);
            return new MultiBlockCompanion(offset(position, 0, 1, 0), other);
        }
        other.setHalf(Bisected.Half.BOTTOM);
        return new MultiBlockCompanion(offset(position, 0, -1, 0), other);
    }

    /**
     * A bed's facing points from its foot towards its head, so the partner sits one block along
     * that direction, or one block back from it when this block is already the head.
     */
    private static MultiBlockCompanion bedCompanion(BlockPosition position, Bed bed) {
        Bed other = (Bed) bed.clone();
        BlockFace towardsPartner = bed.getPart() == Bed.Part.FOOT
                ? bed.getFacing()
                : bed.getFacing().getOppositeFace();
        other.setPart(bed.getPart() == Bed.Part.FOOT ? Bed.Part.HEAD : Bed.Part.FOOT);

        return new MultiBlockCompanion(
                offset(position, towardsPartner.getModX(), towardsPartner.getModY(), towardsPartner.getModZ()),
                other);
    }

    private static BlockPosition offset(BlockPosition position, int x, int y, int z) {
        return new BlockPosition(position.x() + x, position.y() + y, position.z() + z);
    }
}
