package com.alpaca.turbogolem;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

public class TurboGolem extends JavaPlugin implements Listener {

    private EntityType copperGolemType;
    private Material copperChestMaterial;

    private final Set<LivingEntity> trackedGolems = new HashSet<>();
    private final Set<UUID> superGolems = new HashSet<>();
    private final Map<UUID, Double> superGolemRadius = new HashMap<>();  // per-golem custom radius

    // Config values
    private int scanIntervalTicks;
    private int superScanIntervalTicks;  // faster for super golems
    private double searchRadiusH;
    private double searchRadiusV;
    private double superRadius;          // radius for super golems
    private int batchSize;
    private int maxChests;
    private boolean showParticles;
    private boolean showMessages;
    private boolean allowEmptyChests;
    private boolean superAllowEmptyChests;

    private boolean isCopperChest(Block block) {
        // Copper chests have oxidation variants: exposed/weathered/oxidized + waxed
        String key = block.getType().getKey().toString();
        return key.contains("copper_chest");
    }

    @Override
    public void onEnable() {
        // Resolve copper golem EntityType
        try {
            copperGolemType = EntityType.valueOf("COPPER_GOLEM");
        } catch (IllegalArgumentException e) {
            try {
                copperGolemType = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft("copper_golem"));
            } catch (Exception ex) {
                getLogger().severe("Could not find Copper Golem entity type! Is this server 1.21.9+?");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        // Resolve copper chest Material
        copperChestMaterial = Material.matchMaterial("COPPER_CHEST");
        if (copperChestMaterial == null) {
            copperChestMaterial = Registry.MATERIAL.get(NamespacedKey.minecraft("copper_chest"));
        }
        if (copperChestMaterial == null) {
            getLogger().severe("Could not find Copper Chest material! Is this server 1.21.9+?");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        startGolemScanner();

        Objects.requireNonNull(getCommand("turbogolem")).setExecutor(new TurboCommand());
        Objects.requireNonNull(getCommand("supergolem")).setExecutor(new SuperGolemCommand());

        getLogger().info("TurboGolem enabled! Copper golem type: " + copperGolemType +
                ", Copper chest: " + copperChestMaterial);
        getLogger().info("Scan interval: " + scanIntervalTicks + " ticks, Radius: " +
                searchRadiusH + "H x " + searchRadiusV + "V, Batch: " + batchSize);
        getLogger().info("Super scan: " + superScanIntervalTicks + " ticks (" +
                (superScanIntervalTicks / 20.0) + "s), Radius: " + superRadius +
                ", Allow empty: " + superAllowEmptyChests);
    }

    @Override
    public void onDisable() {
        getLogger().info("TurboGolem disabled. Golems return to normal speed.");
    }

    private void loadConfig() {
        FileConfiguration c = getConfig();
        c.addDefault("scan-interval-ticks", 40);         // 2 seconds
        c.addDefault("search-radius-horizontal", 32.0);   // blocks
        c.addDefault("search-radius-vertical", 8.0);
        c.addDefault("batch-size", 16);                   // items per trip
        c.addDefault("max-chests", 10);                   // max copper chests to check
        c.addDefault("show-particles", true);
        c.addDefault("show-messages", false);
        c.addDefault("allow-empty-chests", true);
        c.addDefault("super-scan-interval-ticks", 10);   // 0.5s for super golems
        c.addDefault("super-radius", 8.0);               // super golem range
        c.addDefault("super-allow-empty-chests", false);  // super golems: strict matching only
        c.options().copyDefaults(true);
        saveConfig();

        scanIntervalTicks = c.getInt("scan-interval-ticks");
        superScanIntervalTicks = c.getInt("super-scan-interval-ticks");
        searchRadiusH = c.getDouble("search-radius-horizontal");
        searchRadiusV = c.getDouble("search-radius-vertical");
        superRadius = c.getDouble("super-radius");
        batchSize = c.getInt("batch-size");
        maxChests = c.getInt("max-chests");
        showParticles = c.getBoolean("show-particles");
        showMessages = c.getBoolean("show-messages");
        allowEmptyChests = c.getBoolean("allow-empty-chests");
        superAllowEmptyChests = c.getBoolean("super-allow-empty-chests");
    }

    @EventHandler
    public void onGolemSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == copperGolemType) {
            trackedGolems.add(event.getEntity());
            if (showMessages) {
                getLogger().fine("Copper golem spawned at " +
                        event.getLocation().getBlockX() + "," +
                        event.getLocation().getBlockY() + "," +
                        event.getLocation().getBlockZ());
            }
        }
    }

    private void startGolemScanner() {
        // Regular golem scanner
        new BukkitRunnable() {
            @Override
            public void run() {
                try { scanAndBoost(); }
                catch (Exception e) { getLogger().warning("Error in golem scan: " + e.getMessage()); }
            }
        }.runTaskTimer(this, 20L, scanIntervalTicks);

        // Super golem fast scanner (0.25s)
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (UUID uuid : new HashSet<>(superGolems)) {
                        Entity e = Bukkit.getEntity(uuid);
                        if (e instanceof LivingEntity le && le.isValid() && !le.isDead()) {
                            double r = superGolemRadius.getOrDefault(uuid, superRadius);
                            boostSuperGolem(le, r);
                        } else {
                            superGolems.remove(uuid);
                            superGolemRadius.remove(uuid);
                        }
                    }
                } catch (Exception ex) {
                    getLogger().warning("Error in super golem scan: " + ex.getMessage());
                }
            }
        }.runTaskTimer(this, 5L, superScanIntervalTicks);
    }

    private void scanAndBoost() {
        int processed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.getType() != copperGolemType) continue;
                if (!entity.isValid() || entity.isDead()) continue;

                trackedGolems.add(entity);
                
                // Super golems handled by fast scanner — skip here
                if (superGolems.contains(entity.getUniqueId())) continue;
                
                int moved = boostGolem(entity);
                if (moved > 0) processed += moved;
            }
        }

        trackedGolems.removeIf(g -> !g.isValid() || g.isDead());
        superGolems.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            if (e == null || !e.isValid()) {
                superGolemRadius.remove(uuid);
                return true;
            }
            return false;
        });

        if (showMessages && processed > 0) {
            getLogger().fine("TurboGolem processed " + processed + " item(s) this cycle");
        }
    }

    private int boostGolem(LivingEntity golem) {
        Location loc = golem.getLocation();
        World world = loc.getWorld();
        int totalMoved = 0;

        // Find copper chests in range
        List<Chest> copperChests = new ArrayList<>();
        BoundingBox searchBox = new BoundingBox(
                loc.getX() - searchRadiusH, loc.getY() - searchRadiusV, loc.getZ() - searchRadiusH,
                loc.getX() + searchRadiusH, loc.getY() + searchRadiusV, loc.getZ() + searchRadiusH
        );

        for (int x = (int) searchBox.getMinX(); x <= searchBox.getMaxX(); x++) {
            for (int y = (int) searchBox.getMinY(); y <= searchBox.getMaxY(); y++) {
                for (int z = (int) searchBox.getMinZ(); z <= searchBox.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isCopperChest(block)) {
                        if (block.getState() instanceof Chest chest) {
                            copperChests.add(chest);
                            if (copperChests.size() >= maxChests) break;
                        }
                    }
                }
                if (copperChests.size() >= maxChests) break;
            }
            if (copperChests.size() >= maxChests) break;
        }

        // For each copper chest, try to sort items to matching regular chests
        for (Chest copperChest : copperChests) {
            Inventory source = copperChest.getBlockInventory();
            if (source.isEmpty()) continue;

            // Get first non-empty item type
            ItemStack[] contents = source.getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (item == null || item.getType().isAir()) continue;

                Material targetType = item.getType();
                List<Container> targets = findTargetContainers(world, loc, targetType, copperChest.getLocation());

                if (targets.isEmpty()) continue;

                // Transfer items to target containers (hoppers, chests, barrels, etc.)
                int remaining = item.getAmount();
                int moved = 0;

                for (Container target : targets) {
                    if (remaining <= 0) break;

                    Inventory dest = target.getInventory();
                    HashMap<Integer, ItemStack> overflow = dest.addItem(
                            new ItemStack(targetType, Math.min(remaining, batchSize))
                    );

                    int added = Math.min(remaining, batchSize);
                    if (!overflow.isEmpty()) {
                        added -= overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                    }

                    if (added > 0) {
                        remaining -= added;
                        moved += added;
                        item.setAmount(item.getAmount() - added);
                        if (item.getAmount() <= 0) {
                            source.clear(slot);
                        } else {
                            source.setItem(slot, item);
                        }

                        if (showParticles) {
                            Location particleLoc = copperChest.getLocation().clone()
                                    .add(0.5, 0.8, 0.5);
                            world.spawnParticle(Particle.HAPPY_VILLAGER,
                                    particleLoc, 3, 0.3, 0.3, 0.3, 0.1);
                        }
                    }
                }

                totalMoved += moved;
                if (copperChest.getBlockInventory().isEmpty()) break;
            }
        }

        return totalMoved;
    }

    // ─── Super Golem ─────────────────────────────────────────

    private int boostSuperGolem(LivingEntity golem, double radius) {
        Location loc = golem.getLocation();
        World world = loc.getWorld();
        int totalMoved = 0;
        final Set<Location> touchedDestinations = new HashSet<>();

        // Only process the copper chest directly beneath the golem
        Block below = golem.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (!isCopperChest(below)) return 0;
        if (!(below.getState() instanceof Chest copperChest)) return 0;

        Inventory source = copperChest.getBlockInventory();
        if (source.isEmpty()) return 0;

        ItemStack[] contents = source.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;

            Material targetType = item.getType();
            List<Container> targets = findTargetContainers(
                world, loc, targetType,
                radius, radius,
                copperChest.getLocation(),
                superAllowEmptyChests
            );

            if (targets.isEmpty()) continue;

            int remaining = item.getAmount();
            int moved = 0;

            for (Container target : targets) {
                if (remaining <= 0) break;
                Inventory dest = target.getInventory();
                HashMap<Integer, ItemStack> overflow = dest.addItem(
                    new ItemStack(targetType, Math.min(remaining, batchSize))
                );
                int added = Math.min(remaining, batchSize);
                if (!overflow.isEmpty()) {
                    added -= overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                }
                if (added > 0) {
                    remaining -= added;
                    moved += added;
                    item.setAmount(item.getAmount() - added);
                    if (item.getAmount() <= 0) source.clear(slot);
                    else source.setItem(slot, item);

                    if (showParticles) {
                        // Source particle: spark at copper chest
                        world.spawnParticle(Particle.ELECTRIC_SPARK,
                            copperChest.getLocation().clone().add(0.5, 1.0, 0.5),
                            3, 0.3, 0.3, 0.3, 0.05);
                        // Destination: collect location for sustained particle effect
                        Location destLoc = target.getLocation();
                        if (destLoc != null) {
                            touchedDestinations.add(destLoc.clone().add(0.5, 0.8, 0.5));
                            // For double chests, also add the other half
                            if (target instanceof Chest chest && chest.getInventory().getSize() > 27) {
                                Block destBlock = destLoc.getBlock();
                                for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                                    Block neighbor = destBlock.getRelative(face);
                                    if (neighbor.getType() == destBlock.getType()) {
                                        touchedDestinations.add(neighbor.getLocation().clone().add(0.5, 0.8, 0.5));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            totalMoved += moved;
            if (copperChest.getBlockInventory().isEmpty()) break;
        }

        // Draw particle lines from copper chest to each destination
        if (showParticles && !touchedDestinations.isEmpty()) {
            Location srcCenter = copperChest.getLocation().clone().add(0.5, 0.5, 0.5);
            for (Location dest : touchedDestinations) {
                double dist = srcCenter.distance(dest);
                if (dist < 0.5) continue;
                Vector dir = dest.toVector().subtract(srcCenter.toVector()).normalize();
                for (double d = 0; d < dist; d += 0.4) {
                    world.spawnParticle(Particle.END_ROD,
                            srcCenter.clone().add(dir.clone().multiply(d)),
                            1, 0, 0, 0, 0);
                }
            }
        }

        // Sustained particle effect at destinations (10 seconds)
        if (showParticles && !touchedDestinations.isEmpty()) {
            for (Location dest : touchedDestinations) {
                new BukkitRunnable() {
                    int tick = 0;
                    @Override
                    public void run() {
                        if (tick >= 200) { cancel(); return; }
                        world.spawnParticle(Particle.HAPPY_VILLAGER, dest,
                                5, 0.5, 0.5, 0.5, 0.5);
                        tick += 5;
                    }
                }.runTaskTimer(TurboGolem.this, 0L, 5L);
            }
        }

        return totalMoved;
    }

    private List<Container> findTargetContainers(World world, Location center, Material type,
                                                  double radiusH, double radiusV, Location exclude,
                                                  boolean allowEmpty) {
        // Two-pass approach: first find containers with matching items (best for sorters),
        // then fall back to empty containers if allow-empty-chests is enabled
        List<Container> matching = new ArrayList<>();
        List<Container> empty = new ArrayList<>();
        BoundingBox box = new BoundingBox(
                center.getX() - radiusH, center.getY() - radiusV, center.getZ() - radiusH,
                center.getX() + radiusH, center.getY() + radiusV, center.getZ() + radiusH
        );

        for (int x = (int) box.getMinX(); x <= box.getMaxX(); x++) {
            for (int y = (int) box.getMinY(); y <= box.getMaxY(); y++) {
                for (int z = (int) box.getMinZ(); z <= box.getMaxZ(); z++) {
                    Location checkLoc = new Location(world, x, y, z);
                    if (checkLoc.equals(exclude)) continue;

                    Block block = world.getBlockAt(x, y, z);
                    Material mat = block.getType();

                    // Exclude copper chests
                    if (isCopperChest(block)) continue;

                    // Support: chest, barrel, shulker, hopper, dropper, dispenser
                    if (!isStorageBlock(mat)) continue;

                    if (block.getState() instanceof Container container) {
                        Inventory inv = container.getInventory();
                        if (hasMatchingItem(inv, type)) {
                            matching.add(container);
                        } else if (allowEmpty && isInventoryEmpty(inv)) {
                            empty.add(container);
                        }
                    }
                }
            }
        }

        // Return matching first, then empty — this prioritizes sorters
        List<Container> result = new ArrayList<>(matching);
        result.addAll(empty);
        return result;
    }

    // Convenience: use default config radius
    private List<Container> findTargetContainers(World world, Location center, Material type, Location exclude) {
        return findTargetContainers(world, center, type, searchRadiusH, searchRadiusV, exclude, allowEmptyChests);
    }

    private boolean isStorageBlock(Material mat) {
        return mat == Material.CHEST || mat == Material.TRAPPED_CHEST
                || mat == Material.BARREL || mat.name().contains("SHULKER_BOX")
                || mat == Material.HOPPER || mat == Material.DROPPER
                || mat == Material.DISPENSER;
    }

    private boolean hasMatchingItem(Inventory inv, Material type) {
        for (ItemStack item : inv.getContents()) {
            // Match same item type with any amount — even full stacks indicate "this chest takes this type"
            if (item != null && item.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) return false;
        }
        return true;
    }

    // ─── Commands ──────────────────────────────────────────────────

    private class TurboCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!sender.hasPermission("turbogolem.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "TurboGolem v1.0.0");
                sender.sendMessage(ChatColor.GRAY + "/turbogolem reload - Reload config");
                sender.sendMessage(ChatColor.GRAY + "/turbogolem info   - Show stats");
                sender.sendMessage(ChatColor.GRAY + "/turbogolem scan   - Force scan now");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    reloadConfig();
                    loadConfig();
                    sender.sendMessage(ChatColor.GREEN + "TurboGolem config reloaded!");
                }
                case "info" -> {
                    sender.sendMessage(ChatColor.GOLD + "=== TurboGolem Stats ===");
                    sender.sendMessage(ChatColor.GRAY + "Tracked golems: " + ChatColor.WHITE + trackedGolems.size());
                    sender.sendMessage(ChatColor.GRAY + "Super golems: " + ChatColor.WHITE + superGolems.size());
                    sender.sendMessage(ChatColor.GRAY + "Scan interval: " + ChatColor.WHITE +
                            scanIntervalTicks + " ticks (" + (scanIntervalTicks / 20.0) + "s)");
                    sender.sendMessage(ChatColor.GRAY + "Super scan: " + ChatColor.WHITE +
                            superScanIntervalTicks + " ticks (" + (superScanIntervalTicks / 20.0) + "s)");
                    sender.sendMessage(ChatColor.GRAY + "Range: " + ChatColor.WHITE +
                            searchRadiusH + "H x " + searchRadiusV + "V");
                    sender.sendMessage(ChatColor.GRAY + "Super radius: " + ChatColor.WHITE + superRadius);
                    sender.sendMessage(ChatColor.GRAY + "Batch size: " + ChatColor.WHITE + batchSize);
                    sender.sendMessage(ChatColor.GRAY + "Particles: " + ChatColor.WHITE + showParticles);
                    sender.sendMessage(ChatColor.GRAY + "Allow empty chests: " + ChatColor.WHITE + allowEmptyChests);
                }
                case "scan" -> {
                    int moved = 0;
                    for (World w : Bukkit.getWorlds()) {
                        for (LivingEntity e : w.getLivingEntities()) {
                            if (e.getType() == copperGolemType && e.isValid() && !e.isDead()) {
                                moved += boostGolem(e);
                            }
                        }
                    }
                    sender.sendMessage(ChatColor.GREEN + "Forced scan complete. Moved " +
                            moved + " items.");
                }
                case "killgolems" -> {
                    int count = 0;
                    for (UUID uuid : new HashSet<>(superGolems)) {
                        Entity e = Bukkit.getEntity(uuid);
                        if (e != null && e.isValid()) {
                            e.remove();
                            count++;
                        }
                        superGolems.remove(uuid);
                        superGolemRadius.remove(uuid);
                    }
                    sender.sendMessage(ChatColor.GREEN + "Removed " + count + " super golem(s).");
                }
                default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use: reload, info, scan, killgolems");
            }
            return true;
        }
    }

    // ─── Super Golem Command ───────────────────────────────────

    private class SuperGolemCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can spawn super golems.");
                return true;
            }
            if (!sender.hasPermission("turbogolem.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            Entity p = (Entity) sender;

            // Parse optional radius
            double r = superRadius;
            if (args.length >= 1) {
                try { r = Double.parseDouble(args[0]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid radius: " + args[0]);
                    return true;
                }
                if (r < 1 || r > 128) {
                    sender.sendMessage(ChatColor.RED + "Radius must be 1-128");
                    return true;
                }
            }

            Location loc = p.getLocation();
            World world = loc.getWorld();

            // Check if there's a copper chest beneath (or at feet level for negative Y)
            Block feetBlock = p.getLocation().getBlock();
            Block below = feetBlock.getRelative(BlockFace.DOWN);
            if (!isCopperChest(below) && !isCopperChest(feetBlock)) {
                sender.sendMessage(ChatColor.RED + "You must stand on or near a copper chest!");
                return true;
            }

            // Determine chest location for spawning
            Block chestBlock = isCopperChest(below) ? below : feetBlock;

            // Spawn the golem centered on the chest, one block above
            Location spawnLoc = chestBlock.getLocation().add(0.5, 1.0, 0.5);
            spawnLoc.setYaw(0);
            Entity entity = world.spawnEntity(spawnLoc, copperGolemType, CreatureSpawnEvent.SpawnReason.COMMAND);
            if (!(entity instanceof LivingEntity golem)) {
                sender.sendMessage(ChatColor.RED + "Failed to spawn copper golem!");
                return true;
            }

            // Make it stationary
            golem.setGravity(false);
            golem.setInvulnerable(true);  // immune to damage/knockback — use /turbogolem killgolems to remove
            golem.setSilent(true);
            golem.setCollidable(false);
            try {
                ((Mob) golem).setAware(false);
            } catch (Exception ignored) {}

            superGolems.add(golem.getUniqueId());
            superGolemRadius.put(golem.getUniqueId(), r);
            trackedGolems.add(golem);

            sender.sendMessage(ChatColor.GREEN + "Super golem spawned! Radius: " + r +
                    " blocks. Items will be sorted instantly from the copper chest beneath you.");
            sender.sendMessage(ChatColor.GRAY + "Destroy the golem or copper chest to stop.");

            return true;
        }
    }
}
