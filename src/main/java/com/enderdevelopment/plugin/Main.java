package com.enderdevelopment.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Main extends JavaPlugin implements Listener {
    private static final int[] CARD_SLOTS = {10, 11, 12, 13, 14};
    private static final int DEAL_SLOT = 22;
    private final Random random = new Random();
    private final Map<UUID, UUID> hunters = new HashMap<>();
    private final Map<UUID, Game> games = new HashMap<>();
    private final Map<UUID, BukkitTask> escapeTasks = new HashMap<>();
    private NamespacedKey deckKey;
    private NamespacedKey devilKey;

    @Override
    public void onEnable() {
        deckKey = new NamespacedKey(this, "poker_deck");
        devilKey = new NamespacedKey(this, "poker_devil");
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::huntEligiblePlayers, 100L, 200L);
    }

    @Override
    public void onDisable() {
        escapeTasks.values().forEach(BukkitTask::cancel);
        hunters.values().forEach(id -> {
            var entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveDeck(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        games.remove(id);
        BukkitTask task = escapeTasks.remove(id);
        if (task != null) task.cancel();
        UUID devil = hunters.remove(id);
        if (devil != null && Bukkit.getEntity(devil) != null) Bukkit.getEntity(devil).remove();
    }

    private void giveDeck(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDeck(item)) return;
        }
        ItemStack deck = new ItemStack(Material.PAPER);
        ItemMeta meta = deck.getItemMeta();
        meta.displayName(Component.text("Poker Deck", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("A worn deck. The house always watches.", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(deckKey, PersistentDataType.BYTE, (byte) 1);
        deck.setItemMeta(meta);
        player.getInventory().addItem(deck).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private boolean isDeck(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(deckKey, PersistentDataType.BYTE);
    }

    private void huntEligiblePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isValid() || player.isDead() || games.containsKey(player.getUniqueId())
                    || escapeTasks.containsKey(player.getUniqueId()) || hunters.containsKey(player.getUniqueId())) continue;
            if ((isNight(player.getWorld()) || isCave(player)) && random.nextDouble() < 0.35) spawnDevil(player);
        }
    }

    private boolean isNight(World world) {
        long time = world.getTime();
        return time >= 13000 && time <= 23000;
    }

    private boolean isCave(Player player) {
        Location location = player.getLocation();
        return location.getBlockY() < player.getWorld().getSeaLevel() - 5
            && location.getBlock().getLightFromSky() <= 4;
    }

    private void spawnDevil(Player player) {
        Location origin = player.getLocation();
        Location spawn = null;
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = random.nextInt(33) - 16;
            int dz = random.nextInt(33) - 16;
            if (dx * dx + dz * dz < 100) continue;
            int x = origin.getBlockX() + dx;
            int z = origin.getBlockZ() + dz;
            int y = origin.getBlockY();
            if (isNight(player.getWorld())) y = player.getWorld().getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(player.getWorld(), x + 0.5, y, z + 0.5);
            if (candidate.getBlock().isPassable() && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                    && candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                spawn = candidate;
                break;
            }
        }
        if (spawn == null) return;
        Zombie devil = player.getWorld().spawn(spawn, Zombie.class, zombie -> {
            zombie.customName(Component.text("Poker Devil", NamedTextColor.DARK_RED));
            zombie.setCustomNameVisible(true);
            zombie.setPersistent(true);
            zombie.setCanPickupItems(false);
            zombie.addScoreboardTag("poker_devil");
            zombie.getPersistentDataContainer().set(devilKey, PersistentDataType.STRING, player.getUniqueId().toString());
            zombie.setTarget(player);
            if (zombie.getAttribute(Attribute.MOVEMENT_SPEED) != null) zombie.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.32);
            var equipment = zombie.getEquipment();
            var inventory = player.getInventory();
            equipment.setHelmet(copy(inventory.getHelmet()));
            equipment.setChestplate(copy(inventory.getChestplate()));
            equipment.setLeggings(copy(inventory.getLeggings()));
            equipment.setBoots(copy(inventory.getBoots()));
            equipment.setItemInMainHand(copy(inventory.getItemInMainHand()));
            equipment.setItemInMainHandDropChance(0.0f);
            equipment.setHelmetDropChance(0.0f);
            equipment.setChestplateDropChance(0.0f);
            equipment.setLeggingsDropChance(0.0f);
            equipment.setBootsDropChance(0.0f);
        });
        hunters.put(player.getUniqueId(), devil.getUniqueId());
        player.sendMessage(Component.text("Something with your silhouette is hunting you.", NamedTextColor.DARK_RED));
    }

    private ItemStack copy(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie) || !(event.getTarget() instanceof Player player)) return;
        String owner = zombie.getPersistentDataContainer().get(devilKey, PersistentDataType.STRING);
        if (owner != null && owner.equals(player.getUniqueId().toString())) hunters.put(player.getUniqueId(), zombie.getUniqueId());
    }

    @EventHandler
    public void onDevilHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Zombie zombie) || !(event.getEntity() instanceof Player player)) return;
        String owner = zombie.getPersistentDataContainer().get(devilKey, PersistentDataType.STRING);
        if (owner == null || !owner.equals(player.getUniqueId().toString())) return;
        event.setCancelled(true);
        if (games.containsKey(player.getUniqueId()) || escapeTasks.containsKey(player.getUniqueId())) return;
        zombie.setTarget(null);
        openGame(player, zombie);
    }

    private void openGame(Player player, Zombie devil) {
        Game game = new Game(devil.getUniqueId(), drawHand());
        games.put(player.getUniqueId(), game);
        showGame(player, game);
        player.sendMessage(Component.text("The Poker Devil demands one hand. Choose what to hold, then draw.", NamedTextColor.RED));
    }

    private void showGame(Player player, Game game) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Poker Devil: Hold or Fold", NamedTextColor.DARK_RED));
        for (int i = 0; i < 5; i++) inventory.setItem(CARD_SLOTS[i], cardItem(game.hand.get(i), game.held[i]));
        ItemStack draw = new ItemStack(Material.REDSTONE);
        ItemMeta meta = draw.getItemMeta();
        meta.displayName(Component.text(game.drew ? "Reveal Hand" : "Draw Cards", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(game.drew ? "Compare hands" : "Click held cards to toggle", NamedTextColor.GRAY)));
        draw.setItemMeta(meta);
        inventory.setItem(DEAL_SLOT, draw);
        player.openInventory(inventory);
    }

    private ItemStack cardItem(Card card, boolean held) {
        boolean red = card.suit.equals("♥") || card.suit.equals("♦");
        ItemStack item = new ItemStack(red ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(card.label(), red ? NamedTextColor.RED : NamedTextColor.WHITE));
        meta.lore(List.of(Component.text(held ? "HELD" : "Click to hold", held ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Game game = games.get(player.getUniqueId());
        if (game == null || !event.getView().title().equals(Component.text("Poker Devil: Hold or Fold", NamedTextColor.DARK_RED))) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        for (int i = 0; i < CARD_SLOTS.length; i++) {
            if (slot == CARD_SLOTS[i] && !game.drew) {
                game.held[i] = !game.held[i];
                showGame(player, game);
                return;
            }
        }
        if (slot == DEAL_SLOT) {
            if (!game.drew) {
                List<Card> replacement = drawHand();
                for (int i = 0; i < 5; i++) if (!game.held[i]) game.hand.set(i, replacement.get(i));
                game.drew = true;
                showGame(player, game);
            } else finishGame(player, game);
        }
    }

    private void finishGame(Player player, Game game) {
        games.remove(player.getUniqueId());
        player.closeInventory();
        HandValue theirs = evaluate(game.hand);
        HandValue devilHand = evaluate(drawHand());
        if (theirs.compareTo(devilHand) > 0) {
            player.sendMessage(Component.text("You beat the Devil. Run. You have 10 seconds.", NamedTextColor.GREEN));
            startEscape(player, game.devilId);
        } else {
            player.sendMessage(Component.text("The Devil wins the hand. It lets you go... for now.", NamedTextColor.DARK_RED));
            removeDevil(player.getUniqueId());
            player.damage(4.0);
        }
    }

    private void startEscape(Player player, UUID devilId) {
        long deadline = System.currentTimeMillis() + 10_000L;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || player.isDead()) {
                endEscape(player.getUniqueId(), false);
                return;
            }
            var entity = Bukkit.getEntity(devilId);
            if (!(entity instanceof Zombie devil) || !devil.isValid()) {
                endEscape(player.getUniqueId(), true);
                return;
            }
            devil.setTarget(player);
            if (devil.getLocation().distanceSquared(player.getLocation()) <= 2.25) {
                endEscape(player.getUniqueId(), false);
                return;
            }
            if (System.currentTimeMillis() >= deadline) endEscape(player.getUniqueId(), true);
        }, 1L, 5L);
        escapeTasks.put(player.getUniqueId(), task);
    }

    private void endEscape(UUID playerId, boolean escaped) {
        BukkitTask task = escapeTasks.remove(playerId);
        if (task != null) task.cancel();
        Player player = Bukkit.getPlayer(playerId);
        removeDevil(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(Component.text(escaped ? "You escaped the Poker Devil." : "The Poker Devil caught you.", escaped ? NamedTextColor.GREEN : NamedTextColor.DARK_RED));
            if (!escaped && !player.isDead()) player.damage(4.0);
        }
    }

    private void removeDevil(UUID playerId) {
        UUID devilId = hunters.remove(playerId);
        if (devilId != null && Bukkit.getEntity(devilId) != null) Bukkit.getEntity(devilId).remove();
    }

    private List<Card> drawHand() {
        List<Card> deck = new ArrayList<>();
        String[] suits = {"♠", "♥", "♦", "♣"};
        for (String suit : suits) for (int rank = 2; rank <= 14; rank++) deck.add(new Card(rank, suit));
        Collections.shuffle(deck, random);
        return new ArrayList<>(deck.subList(0, 5));
    }

    private HandValue evaluate(List<Card> cards) {
        List<Integer> ranks = cards.stream().map(card -> card.rank).sorted(Comparator.reverseOrder()).toList();
        Map<Integer, Integer> counts = new HashMap<>();
        ranks.forEach(rank -> counts.merge(rank, 1, Integer::sum));
        List<Integer> groups = counts.keySet().stream().sorted((a, b) -> {
            int byCount = Integer.compare(counts.get(b), counts.get(a));
            return byCount != 0 ? byCount : Integer.compare(b, a);
        }).toList();
        boolean flush = cards.stream().allMatch(card -> card.suit.equals(cards.get(0).suit));
        List<Integer> unique = ranks.stream().distinct().sorted().toList();
        int straightHigh = 0;
        if (unique.size() == 5) {
            if (unique.get(4) - unique.get(0) == 4) straightHigh = unique.get(4);
            else if (unique.equals(List.of(2, 3, 4, 5, 14))) straightHigh = 5;
        }
        List<Integer> tie = new ArrayList<>();
        int category;
        if (flush && straightHigh > 0) { category = 8; tie.add(straightHigh); }
        else if (counts.containsValue(4)) { category = 7; tie.add(groups.get(0)); tie.add(groups.get(1)); }
        else if (counts.containsValue(3) && counts.containsValue(2)) { category = 6; tie.add(groups.get(0)); tie.add(groups.get(1)); }
        else if (flush) { category = 5; tie.addAll(ranks); }
        else if (straightHigh > 0) { category = 4; tie.add(straightHigh); }
        else if (counts.containsValue(3)) { category = 3; tie.addAll(groups); }
        else if (counts.values().stream().filter(value -> value == 2).count() == 2) { category = 2; tie.addAll(groups); }
        else if (counts.containsValue(2)) { category = 1; tie.addAll(groups); }
        else { category = 0; tie.addAll(ranks); }
        return new HandValue(category, tie);
    }

    private static final class Game {
        private final UUID devilId;
        private final List<Card> hand;
        private final boolean[] held = new boolean[5];
        private boolean drew;
        private Game(UUID devilId, List<Card> hand) { this.devilId = devilId; this.hand = hand; }
    }

    private record Card(int rank, String suit) {
        private String label() {
            String face = switch (rank) { case 14 -> "A"; case 13 -> "K"; case 12 -> "Q"; case 11 -> "J"; default -> Integer.toString(rank); };
            return face + suit;
        }
    }

    private record HandValue(int category, List<Integer> tieBreak) implements Comparable<HandValue> {
        @Override
        public int compareTo(HandValue other) {
            int comparison = Integer.compare(category, other.category);
            if (comparison != 0) return comparison;
            for (int i = 0; i < Math.min(tieBreak.size(), other.tieBreak.size()); i++) {
                comparison = Integer.compare(tieBreak.get(i), other.tieBreak.get(i));
                if (comparison != 0) return comparison;
            }
            return 0;
        }
    }
}
