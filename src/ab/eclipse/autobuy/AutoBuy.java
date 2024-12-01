package ab.eclipse.autobuy;

import ab.eclipse.autobuy.manager.*;
import ab.eclipse.event.ReceiveMessageEvent;
import ab.eclipse.event.SendPacketEvent;
import ab.eclipse.event.TickEvent;
import com.google.common.eventbus.Subscribe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.play.client.CClickWindowPacket;
import net.minecraft.util.text.ITextComponent;

import java.util.List;
import java.util.stream.Collectors;

import static ab.eclipse.EclipseFuntime.mc;

public class AutoBuy {
    public static long update; // Таймер обновления
    private long clickUptime; // Таймер кликов
    private boolean click;
    public static boolean send;

    public static boolean enabled = false;
    private boolean packet, packetSend;
    public long idkTime;

    private HistoryItem lastItem;
    public static String autoPayCom = "";
    public static int balance;

    @Subscribe
    private void onPacketEvent(SendPacketEvent event) {
        if (idkTime > System.currentTimeMillis() && event.packet instanceof CClickWindowPacket clickWindowPacket && clickWindowPacket.id != 1337) {
            event.cancel();
        }

        if (event.packet instanceof CClickWindowPacket clickSlotC2SPacket) {
            if (packet && lastItem != null) {
                lastItem.stack = clickSlotC2SPacket.getClickedItem();
                packet = false;
                packetSend = true;
            }
        }
    }

    @Subscribe
    private void onReceiveMessageEvent(ReceiveMessageEvent event) {
        if (lastItem != null && packetSend) {
            if (event.message.equals("Не так быстро!")) {
                lastItem = null;
                packetSend = false;
                return;
            }
            lastItem.purchased = !event.message.contains("Этот предмет нельзя купить, т.к он больше не продается.");
            HistoryManager.add(lastItem);
            lastItem = null;
            packetSend = false;
        }
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (mc.currentScreen == null || !enabled) return;

        Screen screen = mc.currentScreen;

        if (mc.currentScreen instanceof ChestScreen chestScreen) {
            boolean ah = screen.getTitle().getString().contains("Аукцион");
            boolean accept = screen.getTitle().getString().contains("Покупка предмета");

            if (ah) handleAuction(chestScreen);
            if (accept) handleAcceptScreen(chestScreen);
        }
    }

    /**
     * Обработка экрана аукциона.
     */
    private void handleAuction(ChestScreen chestScreen) {
        for (int i = 0; i < chestScreen.getContainer().getInventory().size(); i++) {
            ItemStack stack = chestScreen.getContainer().getInventory().get(i);

            if (stack == null || stack.isEmpty()) continue;

            String name = stack.getDisplayName().getString();

            // Обновление аукциона
            if (stack.getItem().equals(Items.EMERALD) && name.contains("Обновить аукцион") && System.currentTimeMillis() > update && !packet && System.currentTimeMillis() > idkTime) {
                click(i, 0);  // Нажимаем на кнопку обновления аукциона
                update = System.currentTimeMillis() + 250L;  // Устанавливаем задержку 1 секунды между кликами
                break;
            }

            // Пропуск ненужных слотов
            if (name.equals("Активные товары на продаже") || name.equals("Завершенные товары") || name.equals("Следующая страница ▶")
                    || name.contains("помощь по аукциону") || name.equals("как продать товар?") || name.equals("сортировка")
                    || name.equals("Категории предметорв") || name.equals("◀ Предыдущая страница"))
                continue;

            List<ITextComponent> info = chestScreen.getTooltipFromItem(stack).stream().filter(text -> text.getString().contains("▍")).toList();
            List<ITextComponent> sumList = info.stream().filter(text -> text.getString().contains("¤")).collect(Collectors.toList());

            String author = "";
            try {
                author = info.stream().filter(text -> text.getString().contains("Продавец")).toList().get(0).getString().split(":")[1];
            } catch (Exception ignore) {
            }

            if (IgnoreManager.getIgnore().contains(author.replace(" ", ""))) continue;

            int[] sum = calcSum(sumList);

            if (sum != null) {
                int price = sum[0];
                if (price <= 0) return;

                for (AutoBuyItem item : AutoBuyManager.getItems()) {
                    if (item instanceof CustomAutoBuyItem) {
                        if (((CustomAutoBuyItem) item).tryBuy(stack, price) && System.currentTimeMillis() > idkTime) {
                            processPurchase(i, stack, price);
                        }
                    }
                    if (item instanceof DefaultAutoBuyItem) {
                        if (((DefaultAutoBuyItem) item).tryBuy(stack, price) && System.currentTimeMillis() > idkTime) {
                            processPurchase(i, stack, price);
                        }
                    }
                }
            }
        }
    }

    /**
     * Обработка экрана подтверждения покупки.
     * Автоматически кликает на 11-й слот при открытии экрана с названием "Покупка предмета"
     */
    private void handleAcceptScreen(ChestScreen chestScreen) {
        for (int i = 0; i < chestScreen.getContainer().getInventory().size(); i++) {
            ItemStack stack = chestScreen.getContainer().getInventory().get(i);

            // Проверяем, что это кнопка "Купить" в соответствующем слоте
            if (stack != null && stack.getItem().equals(Items.GREEN_STAINED_GLASS_PANE)
                    && stack.getDisplayName().getString().contains("Купить")) {
                System.out.println("Найдена кнопка 'Купить' в слоте: " + i);

                if (!click || System.currentTimeMillis() > clickUptime) {
                    click(i, 0); // Клик по кнопке
                    click = true;
                    clickUptime = System.currentTimeMillis() + 1L; // Задержка между кликами
                    break;
                }
            }

            // Автоматический клик по 11-му слоту
            if (stack != null && System.currentTimeMillis() > clickUptime) {
                click(11, 0); // Кликаем по 11-му слоту
                clickUptime = System.currentTimeMillis() + 1L; // Задержка между кликами
                break;
            }
        }
    }

    /**
     * Обработка покупки предмета.
     */
    private void processPurchase(int slot, ItemStack stack, int price) {
        click(slot, 1337);
        if (lastItem == null) {
            lastItem = new HistoryItem(price, stack);
        }
        packet = true;
        idkTime = System.currentTimeMillis() + 500L;
    }

    /**
     * Клик по указанному слоту.
     */
    private void click(int slot, int id) {
        try {
            mc.playerController.windowClick(
                    mc.player.openContainer.windowId,
                    slot,
                    0,
                    ClickType.PICKUP,
                    mc.player
            );
            System.out.println("Успешный клик по слоту: " + slot + ", ID: " + id);
        } catch (Exception e) {
            System.err.println("Ошибка при клике по слоту: " + slot + ". " + e.getMessage());
        }
    }

    /**
     * Расчет суммы.
     */
    private int[] calcSum(List<ITextComponent> s) {
        if (s == null || s.isEmpty()) return null;
        int i1 = -1;
        int i2 = -1;
        for (ITextComponent t : s) {
            String string = t.getString();
            StringBuilder stringBuilder = new StringBuilder();
            for (char c : string.toCharArray()) {
                try {
                    stringBuilder.append(Integer.parseInt(String.valueOf(c)));
                } catch (Exception ignore) {
                }
            }
            if (!stringBuilder.toString().isEmpty()) {
                if (i1 < 0) {
                    i1 = Integer.parseInt(stringBuilder.toString());
                } else {
                    i2 = Integer.parseInt(stringBuilder.toString());
                }
            }
        }
        return new int[]{i1, i2};
    }
}
