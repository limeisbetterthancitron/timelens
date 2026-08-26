package io.github.limeisbetterthancitron.timelens;

import io.github.limeisbetterthancitron.timelens.command.TimeLensCommand;
import io.github.limeisbetterthancitron.timelens.config.TimeLensConfig;
import io.github.limeisbetterthancitron.timelens.history.CoreProtectHistoryProvider;
import io.github.limeisbetterthancitron.timelens.history.HistoryProvider;
import io.github.limeisbetterthancitron.timelens.listener.SessionListener;
import io.github.limeisbetterthancitron.timelens.message.Messages;
import io.github.limeisbetterthancitron.timelens.reconstruction.HistoricalReconstructor;
import io.github.limeisbetterthancitron.timelens.render.HistoricalRenderer;
import io.github.limeisbetterthancitron.timelens.session.TimelineSessionManager;
import io.github.limeisbetterthancitron.timelens.view.HistoryViewCoordinator;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point.
 *
 * <p>Only wiring lives here: load configuration, bind to CoreProtect, construct the services,
 * register the command and listeners. All behaviour belongs to the classes it assembles.
 */
public final class TimeLensPlugin extends JavaPlugin {

    private static final String COREPROTECT_PLUGIN_NAME = "CoreProtect";
    private static final String COMMAND_NAME = "timelens";

    /** The API level that first provided the block lookup shape TimeLens depends on. */
    private static final int REQUIRED_COREPROTECT_API = 12;

    private TimelineSessionManager sessions;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        TimeLensConfig config = TimeLensConfig.load(getConfig(), getLogger());
        Messages messages = new Messages(config.messagePrefix(), getLogger());

        CoreProtectAPI coreProtectApi = connectToCoreProtect();
        if (coreProtectApi == null) {
            disableSelf();
            return;
        }

        HistoryProvider provider = new CoreProtectHistoryProvider(coreProtectApi, getLogger());
        HistoricalRenderer renderer = new HistoricalRenderer(getLogger());
        sessions = new TimelineSessionManager(getServer(), renderer);

        HistoryViewCoordinator coordinator = new HistoryViewCoordinator(this, provider,
                new HistoricalReconstructor(), renderer, sessions, messages, getLogger());

        if (!registerCommand(new TimeLensCommand(config, coordinator, sessions, messages))) {
            disableSelf();
            return;
        }
        getServer().getPluginManager().registerEvents(new SessionListener(sessions, messages, config, getLogger()), this);

        getLogger().info("Connected to " + provider.backendDescription());
        getLogger().info("TimeLens enabled successfully");
    }

    @Override
    public void onDisable() {
        // Viewers must not be left looking at a past that will never be corrected.
        if (sessions != null) {
            sessions.stopAll();
            sessions = null;
        }
    }

    /**
     * Resolves CoreProtect's API, reporting exactly what is wrong when it cannot.
     *
     * <p>Every failure here is a configuration or version problem rather than a bug, so each one
     * is reported as a single actionable line instead of a stack trace.
     *
     * @return the API, or {@code null} if TimeLens cannot run against this installation
     */
    private CoreProtectAPI connectToCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin(COREPROTECT_PLUGIN_NAME);
        if (plugin == null) {
            getLogger().severe("CoreProtect is not installed. TimeLens needs CoreProtect 24.0 or newer "
                    + "to read world history.");
            return null;
        }
        // Checked before anything touches a CoreProtect type: a CoreProtect that failed to
        // enable has had its classes unloaded, so testing the type first would blow up with a
        // NoClassDefFoundError instead of explaining the real problem.
        if (!plugin.isEnabled()) {
            getLogger().severe("CoreProtect is installed but did not enable, so TimeLens has no "
                    + "history to read. Look further up this log for the CoreProtect error, fix "
                    + "that, then restart.");
            return null;
        }

        CoreProtectAPI api = resolveApi(plugin);
        if (api == null) {
            return null;
        }
        if (!api.isEnabled()) {
            getLogger().severe("CoreProtect's API is turned off. Set 'api-enabled: true' in "
                    + "plugins/CoreProtect/config.yml and restart.");
            return null;
        }

        int apiVersion = api.APIVersion();
        if (apiVersion < REQUIRED_COREPROTECT_API) {
            getLogger().severe("TimeLens needs CoreProtect API v" + REQUIRED_COREPROTECT_API
                    + " or newer but found v" + apiVersion + ". Update CoreProtect to 24.0 or newer.");
            return null;
        }
        return api;
    }

    /**
     * Narrows the plugin to CoreProtect and asks it for its API.
     *
     * <p>Isolated so the CoreProtect types are only touched once the plugin is known to be
     * enabled, and so a class-loading failure is reported as a readable line rather than
     * propagating as a startup crash.
     *
     * @return the API, or {@code null} after logging why it could not be obtained
     */
    private CoreProtectAPI resolveApi(Plugin plugin) {
        try {
            if (!(plugin instanceof CoreProtect coreProtect)) {
                getLogger().severe("Another plugin is registered under the name CoreProtect, so TimeLens "
                        + "cannot read world history.");
                return null;
            }
            CoreProtectAPI api = coreProtect.getAPI();
            if (api == null) {
                getLogger().severe("CoreProtect did not provide its API. Check the server log for "
                        + "CoreProtect errors, then restart.");
            }
            return api;
        } catch (LinkageError error) {
            getLogger().severe("CoreProtect's classes could not be loaded, so TimeLens cannot read "
                    + "world history. This usually means CoreProtect failed to start correctly: "
                    + error.getMessage());
            return null;
        }
    }

    private boolean registerCommand(TimeLensCommand executor) {
        PluginCommand command = getCommand(COMMAND_NAME);
        if (command == null) {
            getLogger().severe("The /" + COMMAND_NAME + " command is missing from plugin.yml; "
                    + "this jar is not built correctly.");
            return false;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        return true;
    }

    private void disableSelf() {
        getServer().getPluginManager().disablePlugin(this);
    }
}
