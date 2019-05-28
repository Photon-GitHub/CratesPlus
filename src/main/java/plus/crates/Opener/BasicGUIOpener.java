package plus.crates.Opener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import plus.crates.Crates.Crate;
import plus.crates.Crates.Winning;
import plus.crates.CratesPlus;
import plus.crates.Utils.LegacyMaterial;

import java.io.IOException;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class BasicGUIOpener extends Opener implements Listener {
    private CratesPlus cratesPlus;
    private HashMap<UUID, Integer> tasks = new HashMap<>();
    private HashMap<UUID, Inventory> guis = new HashMap<>();
    private int length = 5;
    private String rollingText = "Rolling...";
    private String winnerText = "Winner!";
    private boolean sound = true;

    public BasicGUIOpener(CratesPlus cratesPlus) {
        super(cratesPlus, "BasicGUI");
        this.cratesPlus = cratesPlus;
    }

    @Override
    public void doSetup() {
        FileConfiguration config = getOpenerConfig();
        if (!config.isSet("Length")) {
            config.set("Length", cratesPlus.getConfigHandler().getCrateGUITime());
            try {
                config.save(getOpenerConfigFile());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        if (!config.isSet("Rolling Text")) {
            config.set("Rolling Text", "Rolling...");
            try {
                config.save(getOpenerConfigFile());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        if (!config.isSet("Winner Text")) {
            config.set("Winner Text", "Winner!");
            try {
                config.save(getOpenerConfigFile());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        if (!config.isSet("Sound")) {
            config.set("Sound", true);
            try {
                config.save(getOpenerConfigFile());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        length = config.getInt("Length", cratesPlus.getConfigHandler().getCrateGUITime());
        rollingText = config.getString("Rolling Text", "Rolling...");
        winnerText = config.getString("Winner Text", "Winner!");
        sound = config.getBoolean("Sound", true);
        cratesPlus.getServer().getPluginManager().registerEvents(this, cratesPlus);
    }

    @Override
    public void doOpen(final Player player, final Crate crate, Location blockLocation) {
        final Inventory winGUI;
        final Integer[] timer = {0};
        final Integer[] currentItem = new Integer[1];

        Random random = new Random();
        int max = crate.getWinnings().size() - 1;
        int min = 0;
        currentItem[0] = random.nextInt((max - min) + 1) + min;
        winGUI = Bukkit.createInventory(null, 45, crate.getColor() + crate.getName() + " Win");
        guis.put(player.getUniqueId(), winGUI);
        player.openInventory(winGUI);
        final int maxTimeTicks = length * 10;
        tasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskTimerAsynchronously(cratesPlus, () -> {
            if (!player.isOnline()) {
                finish(player);
                //TODO Want to re-explore what we should do here, this happens if the player logs off mid-opening.
                Bukkit.getScheduler().runTask(cratesPlus, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key " + player.getName() + " " + crate.getName() + " 1"));
                Bukkit.getScheduler().cancelTask(tasks.get(player.getUniqueId()));
                return;
            }
            Integer i = 0;
            while (i < 45) {
                if (i == 22) {
                    i++;
                    if (crate.getWinnings().size() == currentItem[0])
                        currentItem[0] = 0;
                    final Winning winning;
                    if (timer[0] == maxTimeTicks) {
                        winning = crate.handleWin(player);
                    } else {
                        winning = crate.getWinnings().get(currentItem[0]);
                    }

                    final ItemStack currentItemStack = winning.getPreviewItemStack();
                    winGUI.setItem(22, currentItemStack);

                    currentItem[0]++;
                    continue;
                }
                ItemStack itemStack = new ItemStack(LegacyMaterial.STAINED_GLASS_PANE.getMaterial(), 1, (short) cratesPlus.getCrateHandler().randInt(0, 15));
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (timer[0] == maxTimeTicks) {
                    itemMeta.setDisplayName(ChatColor.RESET + winnerText);
                } else {
                    if (sound) {
                        Sound sound;
                        try {
                            sound = Sound.valueOf("NOTE_PIANO");
                        } catch (Exception e) {
                            try {
                                sound = Sound.valueOf("BLOCK_NOTE_HARP");
                            } catch (Exception ee) {
                                return; // This should never happen!
                            }
                        }
                        final Sound finalSound = sound;
                        Bukkit.getScheduler().runTask(cratesPlus, () -> {
                            if (player.getOpenInventory().getTitle() != null && player.getOpenInventory().getTitle().contains(" Win"))
                                player.playSound(player.getLocation(), finalSound, (float) 0.2, 2);
                        });
                    }
                    itemMeta.setDisplayName(ChatColor.RESET + rollingText);
                }
                itemStack.setItemMeta(itemMeta);
                winGUI.setItem(i, itemStack);
                i++;
            }
            if (timer[0] == maxTimeTicks) {
                finish(player);
                Bukkit.getScheduler().cancelTask(tasks.get(player.getUniqueId()));
                return;
            }
            timer[0]++;
        }, 0L, 2L).getTaskId());
    }

    @Override
    public void doReopen(Player player, Crate crate, Location location) {
        player.openInventory(guis.get(player.getUniqueId()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() != null && event.getView().getTitle().contains(" Win") && !event.getView().getTitle().contains("Edit ")) {
            if (event.getInventory().getType() != null && event.getInventory().getType() == InventoryType.CHEST && event.getSlot() != 22 || (event.getCurrentItem() != null)) {
                event.setCancelled(true);
                event.getWhoClicked().closeInventory();
            }
        }
    }

    public boolean doesSupport(Crate crate) {
        return true;
    }

}
