package net.mcirai.contractboard;

import net.mcirai.contractboard.command.IraiCommand;
import net.mcirai.contractboard.economy.EconomyService;
import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.listener.ChatInputListener;
import net.mcirai.contractboard.listener.GuiListener;
import net.mcirai.contractboard.listener.PlayerQuitListener;
import net.mcirai.contractboard.session.SessionManager;
import net.mcirai.contractboard.storage.Database;
import net.mcirai.contractboard.storage.RatingRepository;
import net.mcirai.contractboard.storage.RequestRepository;
import net.mcirai.contractboard.task.ExpirationTask;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class ContractBoardPlugin extends JavaPlugin {

    private Database database;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(this);
        try {
            database.connect();
        } catch (SQLException e) {
            getLogger().severe("データベースの初期化に失敗しました。プラグインを無効化します: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        RequestRepository requestRepository = new RequestRepository(database);
        RatingRepository ratingRepository = new RatingRepository(database);
        MessageUtil messages = new MessageUtil(getConfig());

        EconomyService economyService = new EconomyService();
        if (!economyService.setup(this)) {
            getLogger().warning("Vault経済プラグインが見つかりません。依頼の作成・支払いが機能しません。");
        }

        RequestService requestService = new RequestService(requestRepository, ratingRepository,
                economyService, messages, getConfig(), getLogger());
        GuiManager guiManager = new GuiManager(requestRepository, ratingRepository,
                economyService, messages, getConfig(), getLogger());
        SessionManager sessionManager = new SessionManager();

        IraiCommand iraiCommand = new IraiCommand(guiManager, sessionManager, messages, getConfig());
        getCommand("irai").setExecutor(iraiCommand);
        getCommand("irai").setTabCompleter(iraiCommand);

        getServer().getPluginManager().registerEvents(
                new GuiListener(guiManager, requestService, sessionManager, messages, getConfig()), this);
        getServer().getPluginManager().registerEvents(
                new ChatInputListener(this, getConfig(), sessionManager, requestService, messages), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(sessionManager), this);

        long intervalTicks = getConfig().getLong("expiration-check-interval-seconds", 60) * 20L;
        new ExpirationTask(requestService).runTaskTimer(this, intervalTicks, intervalTicks);

        getLogger().info("ContractBoardが有効化されました。");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }
}
