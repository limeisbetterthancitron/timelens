package harness;

import io.github.limeisbetterthancitron.timelens.history.CoreProtectHistoryProvider;
import io.github.limeisbetterthancitron.timelens.history.HistoryEvent;
import io.github.limeisbetterthancitron.timelens.history.HistoryProvider;
import io.github.limeisbetterthancitron.timelens.history.HistoryQuery;
import io.github.limeisbetterthancitron.timelens.reconstruction.HistoricalReconstructor;
import io.github.limeisbetterthancitron.timelens.reconstruction.HistoricalSnapshot;
import io.github.limeisbetterthancitron.timelens.render.HistoricalRenderer;
import io.github.limeisbetterthancitron.timelens.util.DurationUnit;
import io.github.limeisbetterthancitron.timelens.util.HistoryDuration;
import io.github.limeisbetterthancitron.timelens.util.HistoryTarget;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class TimeLensHarness extends JavaPlugin {

    private static final int CX = -172;
    private static final int CY = 67;
    private static final int CZ = -200;
    private static final int[] RADII = {16, 48, 96};

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskLater(this, this::begin, 100L);
    }

    private void begin() {
        World world = getServer().getWorlds().get(0);
        Location centre = new Location(world, CX, CY, CZ);
        HistoryTarget target = new HistoryTarget.Relative(new HistoryDuration(30L, DurationUnit.DAYS));
        long now = System.currentTimeMillis();

        List<HistoryQuery> queries = new ArrayList<>();
        for (int radius : RADII) {
            queries.add(HistoryQuery.around(centre, radius, radius, target, now, 50_000));
        }

        // A real viewer has their surroundings loaded; loading them here first keeps the
        // measurement about TimeLens rather than about chunk generation.
        int maxRadius = RADII[RADII.length - 1];
        long chunkStart = System.nanoTime();
        int loaded = 0;
        for (int cx = (CX - maxRadius) >> 4; cx <= (CX + maxRadius) >> 4; cx++) {
            for (int cz = (CZ - maxRadius) >> 4; cz <= (CZ + maxRadius) >> 4; cz++) {
                // A ticket is required: with nobody online Paper drops unticketed chunks
                // immediately, and TimeLens skips positions in unloaded chunks.
                world.addPluginChunkTicket(cx, cz, this);
                loaded++;
            }
        }
        log(String.format("HARNESS pre-loaded %d chunks in %.0fms", loaded,
                (System.nanoTime() - chunkStart) / 1e6));

        getServer().getScheduler().runTaskAsynchronously(this, () -> lookupThenMeasure(world, queries));
    }

    private void lookupThenMeasure(World world, List<HistoryQuery> queries) {
        CoreProtectAPI api =
                ((CoreProtect) getServer().getPluginManager().getPlugin("CoreProtect")).getAPI();
        HistoryProvider provider = new CoreProtectHistoryProvider(api, getLogger());
        HistoricalReconstructor reconstructor = new HistoricalReconstructor();
        HistoricalRenderer renderer = new HistoricalRenderer(getLogger());

        List<HistoricalSnapshot> snapshots = new ArrayList<>();
        for (HistoryQuery query : queries) {
            try {
                List<HistoryEvent> events = provider.lookup(query);
                snapshots.add(reconstructor.reconstruct(query.targetTimestampMillis(), events));
            } catch (Exception exception) {
                log("HARNESS lookup r=" + query.horizontalRadius() + " FAILED " + exception);
                snapshots.add(null);
            }
        }
        // prepare() reads live world state, so it has to go back to the server thread, which is
        // exactly where its cost would land during a real /timelens.
        getServer().getScheduler().runTask(this, () -> measure(world, renderer, queries, snapshots));
    }

    /**
     * A benchmark that exercises nothing must fail, not pass quietly.
     *
     * <p>Two runs during development reported clean results while measuring nothing: with nobody
     * online Paper unloads chunks immediately, TimeLens correctly skipped every position as
     * unloaded, and the harness reported success. A green result meaning "no code ran" looks like
     * evidence and is worse than a red one.
     *
     * @return true when the scenario really did exercise the render path
     */
    private boolean assertDidWork(int radius, int events, int positions, int toSend) {
        boolean ok = true;
        if (events <= 0) {
            fail(radius, "lookup returned no events");
            ok = false;
        }
        if (positions <= 0) {
            fail(radius, "reconstruction produced no positions");
            ok = false;
        }
        if (toSend <= 0) {
            fail(radius, "no blocks would be sent - the past matches the present here, or every "
                    + "position sits in an unloaded chunk. Hold plugin chunk tickets and pick an "
                    + "area whose oldest recorded change is a removal.");
            ok = false;
        }
        return ok;
    }

    private void fail(int radius, String reason) {
        getLogger().severe("HARNESS r=" + radius + " INVALID BENCHMARK: " + reason);
    }

    private void measure(World world,
                         HistoricalRenderer renderer,
                         List<HistoryQuery> queries,
                         List<HistoricalSnapshot> snapshots) {
        log("=== HARNESS RENDER COST (main thread) ===");
        for (int i = 0; i < queries.size(); i++) {
            HistoricalSnapshot snapshot = snapshots.get(i);
            if (snapshot == null) {
                continue;
            }
            int radius = queries.get(i).horizontalRadius();

            int inLoadedChunk = 0;
            int differing = 0;
            for (var entry : snapshot.states().entrySet()) {
                var pos = entry.getKey();
                if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                    continue;
                }
                inLoadedChunk++;
                String live = world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData().getAsString();
                if (!live.equals(entry.getValue().blockData())) {
                    differing++;
                }
            }
            log(String.format("HARNESS r=%-3d diagnostics: inLoadedChunk=%d differingFromLiveWorld=%d",
                    radius, inLoadedChunk, differing));

            // Warm the JIT so the reported figure is steady-state, not first-call cost.
            renderer.prepare(world, snapshot);

            int runs = 40;
            long[] samples = new long[runs];
            HistoricalRenderer.PreparedView prepared = null;
            for (int run = 0; run < runs; run++) {
                long t0 = System.nanoTime();
                prepared = renderer.prepare(world, snapshot);
                samples[run] = System.nanoTime() - t0;
            }
            if (!assertDidWork(radius, snapshot.size(), snapshot.size(), prepared.changes().size())) {
                continue;
            }
            java.util.Arrays.sort(samples);
            double best = samples[0] / 1e6;
            double median = samples[runs / 2] / 1e6;
            double p95 = samples[(int) (runs * 0.95)] / 1e6;
            double worst = samples[runs - 1] / 1e6;
            log(String.format(
                    "HARNESS r=%-3d toSend=%-5d best=%5.2f median=%5.2f p95=%5.2f worst=%5.2f ms  (tick=50ms, n=%d)",
                    radius, prepared.changes().size(), best, median, p95, worst, runs));
        }
        log("=== HARNESS DONE ===");
    }

    private void log(String message) {
        getLogger().info(message);
    }
}
