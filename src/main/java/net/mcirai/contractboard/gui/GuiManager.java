package net.mcirai.contractboard.gui;

import net.mcirai.contractboard.economy.EconomyService;
import net.mcirai.contractboard.model.Request;
import net.mcirai.contractboard.model.RequestStatus;
import net.mcirai.contractboard.storage.RatingRepository;
import net.mcirai.contractboard.storage.RequestRepository;
import net.mcirai.contractboard.util.ItemBuilder;
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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GuiManager {

    private static final int PAGE_SIZE = 45;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN);

    private final RequestRepository requestRepository;
    private final RatingRepository ratingRepository;
    private final EconomyService economyService;
    private final MessageUtil messages;
    private final FileConfiguration config;
    private final Logger logger;

    public GuiManager(RequestRepository requestRepository, RatingRepository ratingRepository,
                       EconomyService economyService, MessageUtil messages, FileConfiguration config,
                       Logger logger) {
        this.requestRepository = requestRepository;
        this.ratingRepository = ratingRepository;
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
            inventory.setItem(slot, buildRequestItem(request));
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

    private ItemStack buildRequestItem(Request request) {
        List<String> lore = new ArrayList<>();
        lore.add("§7依頼者: §f" + request.getRequesterName());
        lore.add("§7報酬: §e" + economyService.format(request.getReward()));
        lore.add("§7期限: §f" + dateFormat.format(new Date(request.getExpiresAt())));
        lore.add("");
        lore.add(trim(request.getDescription(), 30));
        lore.add("");
        lore.add("§eクリックで詳細を見る");
        return new ItemBuilder(Material.PAPER)
                .name("§f" + request.getTitle())
                .lore(lore)
                .build();
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

        double avg = 0;
        int count = 0;
        try {
            avg = ratingRepository.averageStars(request.getRequesterId());
            count = ratingRepository.countByRated(request.getRequesterId());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "評価情報の取得に失敗しました", e);
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7依頼者: §f" + request.getRequesterName());
        lore.add("§7報酬: §e" + economyService.format(request.getReward()));
        lore.add("§7期限: §f" + dateFormat.format(new Date(request.getExpiresAt())));
        lore.add("§7状態: §f" + statusLabel(request.getStatus()));
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
        if (request.getStatus() == RequestStatus.OPEN && !isOwn) {
            inventory.setItem(RequestDetailHolder.SLOT_ACCEPT, new ItemBuilder(Material.LIME_WOOL)
                    .name("§aこの依頼を受注する")
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
                    case ACCEPTED, DELIVERED -> 1;
                    case OPEN -> 2;
                    default -> 0;
                };
                inventory.setItem(slot, buildMyRequestItem(request, action));
                holder.getSlotActions().put(slot, new int[]{request.getId(), action});
                slot++;

                boolean forceRevertEligible = request.getStatus() == RequestStatus.ACCEPTED
                        && request.getAcceptedAt() > 0
                        && now - request.getAcceptedAt() >= autoApproveMillis;
                if (forceRevertEligible && slot < 44) {
                    inventory.setItem(slot, buildMyRequestItem(request, 5));
                    holder.getSlotActions().put(slot, new int[]{request.getId(), 5});
                    slot++;
                }
            }
        }
        if (!accepted.isEmpty()) {
            inventory.setItem(slot++, new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                    .name("§b―― 自分が受注した依頼 ――").build());
            for (Request request : accepted) {
                if (slot >= 44) break;
                if (request.getStatus() == RequestStatus.ACCEPTED) {
                    inventory.setItem(slot, buildMyRequestItem(request, 4));
                    holder.getSlotActions().put(slot, new int[]{request.getId(), 4});
                    slot++;
                    if (slot < 44) {
                        inventory.setItem(slot, buildMyRequestItem(request, 3));
                        holder.getSlotActions().put(slot, new int[]{request.getId(), 3});
                        slot++;
                    }
                } else {
                    inventory.setItem(slot, buildMyRequestItem(request, 0));
                    holder.getSlotActions().put(slot, new int[]{request.getId(), 0});
                    slot++;
                }
            }
        }

        inventory.setItem(MyRequestsHolder.SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name("§cメニューに戻る").build());

        player.openInventory(inventory);
    }

    private ItemStack buildMyRequestItem(Request request, int action) {
        List<String> lore = new ArrayList<>();
        lore.add("§7報酬: §e" + economyService.format(request.getReward()));
        lore.add("§7状態: §f" + statusLabel(request.getStatus()));
        if (request.getWorkerName() != null) {
            lore.add("§7受注者: §f" + request.getWorkerName());
        }
        lore.add("");
        Material material = Material.PAPER;
        switch (action) {
            case 1 -> {
                material = Material.LIME_DYE;
                lore.add("§aクリックで完了承認");
            }
            case 2 -> {
                material = Material.RED_DYE;
                lore.add("§cクリックで取り下げ");
            }
            case 3 -> {
                material = Material.YELLOW_DYE;
                lore.add("§eクリックで受注を取り消す(ギブアップ)");
            }
            case 4 -> {
                material = Material.LIME_DYE;
                lore.add("§aクリックで納品完了を報告");
            }
            case 5 -> {
                material = Material.RED_DYE;
                lore.add("§c放置されています");
                lore.add("§cクリックで強制的に募集中へ差し戻す");
            }
            default -> lore.add("§7(操作なし)");
        }
        return new ItemBuilder(material)
                .name("§f" + request.getTitle())
                .lore(lore)
                .build();
    }

    public void openRating(Player player, int requestId) {
        RatingHolder holder = new RatingHolder(requestId);
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.get("gui.rate-title"));
        holder.setInventory(inventory);

        for (int i = 0; i < RatingHolder.STAR_SLOTS.length; i++) {
            int stars = i + 1;
            List<String> lore = new ArrayList<>();
            lore.add("§7クリックでこの評価を送る");
            inventory.setItem(RatingHolder.STAR_SLOTS[i], new ItemBuilder(Material.YELLOW_DYE)
                    .name("§e" + "★".repeat(stars) + "☆".repeat(5 - stars))
                    .lore(lore)
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
