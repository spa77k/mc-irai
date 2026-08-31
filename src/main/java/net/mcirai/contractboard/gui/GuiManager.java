package net.mcirai.contractboard.gui;

import net.mcirai.contractboard.RequestService;
import net.mcirai.contractboard.economy.EconomyService;
import net.mcirai.contractboard.model.Request;
import net.mcirai.contractboard.model.RequestStatus;
import net.mcirai.contractboard.model.VaultItem;
import net.mcirai.contractboard.storage.RatingRepository;
import net.mcirai.contractboard.storage.RequestRepository;
import net.mcirai.contractboard.util.ItemBuilder;
import net.mcirai.contractboard.util.ItemSerialization;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GuiManager {

    private static final int PAGE_SIZE = 45;

    // 自分の依頼画面のボタン種別
    public static final int ACTION_NONE = 0;
    public static final int ACTION_APPROVE = 1;
    public static final int ACTION_WITHDRAW = 2;
    public static final int ACTION_GIVE_UP = 3;
    public static final int ACTION_DELIVER = 4;
    public static final int ACTION_FORCE_REVERT = 5;
    public static final int ACTION_OPEN_BOX = 6;
    public static final int ACTION_REVISION = 7;
    public static final int ACTION_VIEW_BOX = 8;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN);

    private final RequestRepository requestRepository;
    private final RatingRepository ratingRepository;
    private final RequestService requestService;
    private final EconomyService economyService;
    private final MessageUtil messages;
    private final FileConfiguration config;
    private final Logger logger;

    public GuiManager(RequestRepository requestRepository, RatingRepository ratingRepository,
                       RequestService requestService, EconomyService economyService, MessageUtil messages,
                       FileConfiguration config, Logger logger) {
        this.requestRepository = requestRepository;
        this.ratingRepository = ratingRepository;
        this.requestService = requestService;
        this.economyService = economyService;
        this.messages = messages;
        this.config = config;
        this.logger = logger;
    }

    public void openMainMenu(Player player) {
        MainMenuHolder holder = new MainMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.get("gui.main-title"));
        holder.setInventory(inventory);

        inventory.setItem(MainMenuHolder.SLOT_LIST, new ItemBuilder(Material.WRITTEN_BOOK)
                .name("§a依頼を探す")
                .lore("§7募集中の依頼一覧を見る")
                .build());
        inventory.setItem(MainMenuHolder.SLOT_CREATE, new ItemBuilder(Material.FEATHER)
                .name("§a依頼を作成する")
                .lore("§7新しい依頼を出す")
                .build());
        inventory.setItem(MainMenuHolder.SLOT_MY, new ItemBuilder(Material.NAME_TAG)
                .name("§a自分の依頼")
                .lore("§7出した依頼・受けた依頼を見る")
                .build());
        int vaultCount = requestService.findVaultItems(player.getUniqueId()).size();
        inventory.setItem(MainMenuHolder.SLOT_VAULT, new ItemBuilder(Material.CHEST)
                .name("§a保管庫")
                .lore("§7受け取り待ちのアイテム: §f" + vaultCount + "件",
                        "§7クリックで開く")
                .build());

        player.openInventory(inventory);
    }

    public void openRequestList(Player player, int page) {
        List<Request> open;
        try {
            open = requestRepository.findByStatus(RequestStatus.OPEN);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼一覧の取得に失敗しました", e);
            open = new ArrayList<>();
        }

        int totalPages = Math.max(1, (int) Math.ceil(open.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        RequestListHolder holder = new RequestListHolder();
        holder.setPage(page);
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("gui.list-title"));
        holder.setInventory(inventory);

        int from = page * PAGE_SIZE;
        int to = Math.min(open.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Request request = open.get(i);
            int slot = i - from;
            inventory.setItem(slot, buildRequestItem(request, player));
            holder.getSlotToRequestId().put(slot, request.getId());
        }

        if (open.isEmpty()) {
            player.sendMessage(messages.get("prefix") + messages.get("list.empty"));
        }

        if (page > 0) {
            inventory.setItem(RequestListHolder.SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name("§a前のページ").build());
        }
        inventory.setItem(RequestListHolder.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name("§cメニューに戻る").build());
        if (page < totalPages - 1) {
            inventory.setItem(RequestListHolder.SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name("§a次のページ").build());
        }

        player.openInventory(inventory);
    }

    private ItemStack buildRequestItem(Request request, Player viewer) {
        boolean eligible = meetsMinStars(request, viewer);
        List<String> lore = new ArrayList<>();
        lore.add("§7依頼者: §f" + request.getRequesterName());
        lore.add("§7報酬: §e" + economyService.format(request.getReward()));
        lore.add("§7期限: §f" + dateFormat.format(new Date(request.getExpiresAt())));
        if (request.isItemDelivery()) {
            lore.add("§b納品ボックスあり(アイテム納品が必要)");
        }
        if (request.getMinStars() > 0) {
            lore.add((eligible ? "§7" : "§c") + "受注条件: ★" + request.getMinStars() + "以上");
        }
        lore.add("");
        lore.add(trim(request.getDescription(), 30));
        lore.add("");
        lore.add("§eクリックで詳細を見る");
        return new ItemBuilder(Material.PAPER)
                .name((eligible ? "§f" : "§8") + request.getTitle())
                .lore(lore)
                .build();
    }

    private boolean meetsMinStars(Request request, Player viewer) {
        if (request.getMinStars() <= 0) {
            return true;
        }
        try {
            if (ratingRepository.countByRated(viewer.getUniqueId()) == 0) {
                return true;
            }
            return ratingRepository.averageStars(viewer.getUniqueId()) >= request.getMinStars();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "評価情報の取得に失敗しました", e);
            return true;
        }
    }

    public void openRequestDetail(Player player, int requestId, boolean fromList) {
        Request request;
        try {
            request = requestRepository.findById(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼詳細の取得に失敗しました", e);
            request = null;
        }
        if (request == null) {
            player.closeInventory();
            return;
        }

        RequestDetailHolder holder = new RequestDetailHolder(requestId, fromList);
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.get("gui.detail-title"));
        holder.setInventory(inventory);

        boolean eligible = meetsMinStars(request, player);

        List<String> lore = new ArrayList<>();
        lore.add("§7依頼者: §f" + request.getRequesterName());
        lore.add("§7報酬: §e" + economyService.format(request.getReward()));
        lore.add("§7期限: §f" + dateFormat.format(new Date(request.getExpiresAt())));
        lore.add("§7状態: §f" + statusLabel(request.getStatus()));
        if (request.isItemDelivery()) {
            lore.add("§b納品方法: §f納品ボックスへアイテムを入れる");
        } else {
            lore.add("§7納品方法: §f報告のみ(アイテム納品なし)");
        }
        if (request.getMinStars() > 0) {
            lore.add((eligible ? "§7" : "§c") + "受注条件: ★" + request.getMinStars() + "以上");
        }
        if (request.getWorkerName() != null) {
            lore.add("§7受注者: §f" + request.getWorkerName());
        }
        lore.add("");
        lore.add("§f" + request.getDescription());

        inventory.setItem(13, new ItemBuilder(Material.WRITTEN_BOOK)
                .name("§f" + request.getTitle())
                .lore(lore)
                .build());

        boolean isOwn = request.getRequesterId().equals(player.getUniqueId());
        if (request.getStatus() == RequestStatus.OPEN && !isOwn && eligible) {
            inventory.setItem(RequestDetailHolder.SLOT_ACCEPT, new ItemBuilder(Material.LIME_WOOL)
                    .name("§aこの依頼を受注する")
                    .build());
        } else if (request.getStatus() == RequestStatus.OPEN && !isOwn) {
            inventory.setItem(RequestDetailHolder.SLOT_ACCEPT, new ItemBuilder(Material.RED_WOOL)
                    .name("§c受注条件を満たしていません(★" + request.getMinStars() + "以上必要)")
                    .build());
        }

        // 依頼者・受注者は進行中の納品ボックスをここからも覗ける(取り出しは不可)
        boolean inProgress = request.getStatus() == RequestStatus.ACCEPTED
                || request.getStatus() == RequestStatus.DELIVERED;
        boolean isParty = isOwn || player.getUniqueId().equals(request.getWorkerId());
        if (request.isItemDelivery() && inProgress && isParty) {
            inventory.setItem(RequestDetailHolder.SLOT_BOX, new ItemBuilder(Material.CHEST)
                    .name("§b納品ボックスを開く")
                    .lore("§7中身: §f" + requestService.loadBoxContents(requestId).size() + "スロット")
                    .build());
        }

        inventory.setItem(RequestDetailHolder.SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("§7戻る")
                .build());

        player.openInventory(inventory);
    }

    public void openMyRequests(Player player) {
        UUID uuid = player.getUniqueId();
        List<Request> mine;
        List<Request> accepted;
        try {
            mine = requestRepository.findByRequester(uuid);
            accepted = requestRepository.findByWorker(uuid);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "自分の依頼一覧の取得に失敗しました", e);
            mine = new ArrayList<>();
            accepted = new ArrayList<>();
        }

        MyRequestsHolder holder = new MyRequestsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("gui.my-requests-title"));
        holder.setInventory(inventory);

        long autoApproveMillis = config.getLong("request.auto-approve-hours", 72) * 3_600_000L;
        long now = System.currentTimeMillis();

        int slot = 0;
        if (!mine.isEmpty()) {
            inventory.setItem(slot++, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                    .name("§6―― 自分が出した依頼 ――").build());
            for (Request request : mine) {
                if (slot >= 44) break;
                int action = switch (request.getStatus()) {
                    case ACCEPTED, DELIVERED -> ACTION_APPROVE;
                    case OPEN -> ACTION_WITHDRAW;
                    default -> ACTION_NONE;
                };
                slot = addAction(inventory, holder, slot, request, action);

                boolean inProgress = request.getStatus() == RequestStatus.ACCEPTED
                        || request.getStatus() == RequestStatus.DELIVERED;
                if (request.isItemDelivery() && inProgress) {
                    slot = addAction(inventory, holder, slot, request, ACTION_VIEW_BOX);
                }
                if (request.getStatus() == RequestStatus.DELIVERED) {
                    slot = addAction(inventory, holder, slot, request, ACTION_REVISION);
                }
                boolean forceRevertEligible = request.getStatus() == RequestStatus.ACCEPTED
                        && request.getAcceptedAt() > 0
                        && now - request.getAcceptedAt() >= autoApproveMillis;
                if (forceRevertEligible) {
                    slot = addAction(inventory, holder, slot, request, ACTION_FORCE_REVERT);
                }
            }
        }
        if (!accepted.isEmpty()) {
            inventory.setItem(slot++, new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                    .name("§b―― 自分が受注した依頼 ――").build());
            for (Request request : accepted) {
                if (slot >= 44) break;
                if (request.getStatus() == RequestStatus.ACCEPTED) {
                    if (request.isItemDelivery()) {
                        slot = addAction(inventory, holder, slot, request, ACTION_OPEN_BOX);
                    }
                    slot = addAction(inventory, holder, slot, request, ACTION_DELIVER);
                    slot = addAction(inventory, holder, slot, request, ACTION_GIVE_UP);
                } else if (request.getStatus() == RequestStatus.DELIVERED && request.isItemDelivery()) {
                    slot = addAction(inventory, holder, slot, request, ACTION_VIEW_BOX);
                } else {
                    slot = addAction(inventory, holder, slot, request, ACTION_NONE);
                }
            }
        }

        inventory.setItem(MyRequestsHolder.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name("§cメニューに戻る").build());

        player.openInventory(inventory);
    }

    private int addAction(Inventory inventory, MyRequestsHolder holder, int slot, Request request, int action) {
        if (slot >= 44) {
            return slot;
        }
        inventory.setItem(slot, buildMyRequestItem(request, action));
        holder.getSlotActions().put(slot, new int[]{request.getId(), action});
        return slot + 1;
    }

    private ItemStack buildMyRequestItem(Request request, int action) {
        List<String> lore = new ArrayList<>();
        lore.add("§7報酬: §e" + economyService.format(request.getReward()));
        lore.add("§7状態: §f" + statusLabel(request.getStatus()));
        if (request.isItemDelivery()) {
            lore.add("§b納品ボックスあり");
        }
        if (request.getWorkerName() != null) {
            lore.add("§7受注者: §f" + request.getWorkerName());
        }
        lore.add("");
        Material material = Material.PAPER;
        switch (action) {
            case ACTION_APPROVE -> {
                material = Material.LIME_DYE;
                lore.add("§aクリックで完了承認(星評価が必須です)");
                if (request.isItemDelivery()) {
                    lore.add("§7承認すると納品物が保管庫に入ります");
                }
            }
            case ACTION_WITHDRAW -> {
                material = Material.RED_DYE;
                lore.add("§cクリックで取り下げ");
            }
            case ACTION_GIVE_UP -> {
                material = Material.YELLOW_DYE;
                lore.add("§eクリックで受注を取り消す(ギブアップ)");
                if (request.isItemDelivery()) {
                    lore.add("§7ボックスの中身は自分の保管庫へ戻ります");
                }
            }
            case ACTION_DELIVER -> {
                material = Material.LIME_DYE;
                lore.add("§aクリックで納品完了を報告");
                if (request.isItemDelivery()) {
                    lore.add("§7報告するとボックスは固定され、出し入れできなくなります");
                }
            }
            case ACTION_FORCE_REVERT -> {
                material = Material.RED_DYE;
                lore.add("§c放置されています");
                lore.add("§cクリックで強制的に募集中へ差し戻す");
            }
            case ACTION_OPEN_BOX -> {
                material = Material.CHEST;
                lore.add("§bクリックで納品ボックスを開く");
                lore.add("§7チェストと同じように出し入れできます");
            }
            case ACTION_REVISION -> {
                material = Material.ORANGE_DYE;
                lore.add("§6クリックでやり直しを依頼(差し戻し)");
                lore.add("§7受注中に戻り、自動承認の待ち時間もやり直しになります");
            }
            case ACTION_VIEW_BOX -> {
                material = Material.CHEST;
                lore.add("§bクリックで納品ボックスの中身を確認");
                lore.add("§7閲覧のみで、取り出しはできません");
            }
            default -> lore.add("§7(操作なし)");
        }
        return new ItemBuilder(material)
                .name("§f" + request.getTitle())
                .lore(lore)
                .build();
    }

    /**
     * 納品ボックスを開く。editable が true のときだけアイテムの出し入れができる。
     * 装飾アイテムを一切置かないため、誤って飾りを持ち出される事故が起きない。
     */
    public void openDeliveryBox(Player player, int requestId, boolean editable) {
        DeliveryBoxHolder holder = new DeliveryBoxHolder(requestId, editable);
        String title = messages.get(editable ? "gui.box-title" : "gui.box-view-title");
        Inventory inventory = Bukkit.createInventory(holder, RequestService.BOX_SIZE, title);
        holder.setInventory(inventory);

        for (Map.Entry<Integer, ItemStack> entry : requestService.loadBoxContents(requestId).entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < RequestService.BOX_SIZE) {
                inventory.setItem(entry.getKey(), entry.getValue());
            }
        }

        player.openInventory(inventory);
        messages.send(player, editable ? "box.opened-editable" : "box.opened-readonly");
    }

    public void openVault(Player player, int page) {
        List<VaultItem> items = requestService.findVaultItems(player.getUniqueId());
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        VaultHolder holder = new VaultHolder();
        holder.setPage(page);
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("gui.vault-title"));
        holder.setInventory(inventory);

        int retentionDays = config.getInt("box.vault-retention-days", 30);
        int from = page * PAGE_SIZE;
        int to = Math.min(items.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            VaultItem item = items.get(i);
            int slot = i - from;
            List<String> lore = new ArrayList<>();
            lore.add("§7理由: §f" + item.getReason());
            lore.add("§7預かり日時: §f" + dateFormat.format(new Date(item.getCreatedAt())));
            if (retentionDays > 0) {
                lore.add("§7保管期限: §f"
                        + dateFormat.format(new Date(item.getCreatedAt() + retentionDays * 86_400_000L)));
            }
            lore.add("");
            lore.add("§eクリックで受け取る");
            inventory.setItem(slot, new ItemBuilder(vaultDisplay(item)).lore(lore).build());
            holder.getSlotToVaultId().put(slot, item.getId());
        }

        if (items.isEmpty()) {
            player.sendMessage(messages.get("prefix") + messages.get("vault.empty"));
        }
        if (page > 0) {
            inventory.setItem(VaultHolder.SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name("§a前のページ").build());
        }
        inventory.setItem(VaultHolder.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name("§cメニューに戻る").build());
        if (page < totalPages - 1) {
            inventory.setItem(VaultHolder.SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name("§a次のページ").build());
        }

        player.openInventory(inventory);
    }

    private ItemStack vaultDisplay(VaultItem item) {
        Optional<ItemStack> deserialized = ItemSerialization.deserialize(item.getItemData(), logger,
                "vault_items#" + item.getId());
        return deserialized.orElseGet(() -> new ItemBuilder(Material.BARRIER)
                .name("§c(データを復元できませんでした)").build());
    }

    public void openRating(Player player, int requestId) {
        RatingHolder holder = new RatingHolder(requestId);
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.get("gui.rate-title"));
        holder.setInventory(inventory);

        for (int i = 0; i < RatingHolder.STAR_SLOTS.length; i++) {
            int stars = i + 1;
            inventory.setItem(RatingHolder.STAR_SLOTS[i], new ItemBuilder(Material.YELLOW_DYE)
                    .name("§e" + "★".repeat(stars) + "☆".repeat(5 - stars))
                    .lore("§7クリックでこの評価を送る")
                    .build());
        }

        player.openInventory(inventory);
    }

    private String statusLabel(RequestStatus status) {
        return switch (status) {
            case OPEN -> "§a募集中";
            case ACCEPTED -> "§e受注中";
            case DELIVERED -> "§6納品報告済み(承認待ち)";
            case COMPLETED -> "§b完了";
            case EXPIRED -> "§7期限切れ";
            case WITHDRAWN -> "§7取り下げ済み";
        };
    }

    private String trim(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return "§7" + text;
        }
        return "§7" + text.substring(0, maxLength) + "...";
    }
}
