package data.hullmods.VOW;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;

import java.util.*;
public class VOW_CastigatioSystem extends BaseHullMod {
    public static final String BASIC_HULL_ID = "vow_castigatio";

    // System configuration data structure
    private static class SystemConfig {
        final String hullId;
        final String selectorHullmod;
        final int nextIndex;
        final String description;

        SystemConfig(String hullId, String selectorHullmod, int nextIndex, String description) {
            this.hullId = hullId;
            this.selectorHullmod = selectorHullmod;
            this.nextIndex = nextIndex;
            this.description = description;
        }
    }

    // Centralized system configuration - creates cycle: Elite → EliteB → EliteC → EliteD → Elite
    private static final Map<Integer, SystemConfig> SYSTEM_CONFIGS = new HashMap<>();
    static {
        SYSTEM_CONFIGS.put(0, new SystemConfig("vow_castigatio", "vow_castigatioA_selector", 1, "A"));
        SYSTEM_CONFIGS.put(1, new SystemConfig("vow_castigatioB", "vow_castigatioB_selector", 0, "B"));
        //SYSTEM_CONFIGS.put(2, new SystemConfig("vow_disciplinaC", "vow_disciplinaC_selector", 2, "C"));
        //SYSTEM_CONFIGS.put(3, new SystemConfig("vow_piafidesD", "MSS_selector_WingheadD", 0, "D"));
    }

    // Reverse lookup map (built once, cached for O(1) performance)
    private static final Map<String, Integer> HULL_TO_INDEX = new HashMap<>();
    static {
        for (Map.Entry<Integer, SystemConfig> entry : SYSTEM_CONFIGS.entrySet()) {
            HULL_TO_INDEX.put(entry.getValue().hullId, entry.getKey());
        }
    }

    // Cache selector hullmods set for faster lookup (O(1) vs O(n) iteration)
    private static final Set<String> SYSTEM_SELECTOR_HULLMODS = new HashSet<>();
    static {
        for (SystemConfig config : SYSTEM_CONFIGS.values()) {
            SYSTEM_SELECTOR_HULLMODS.add(config.selectorHullmod);
        }
    }

    private static final String HULL_ID_PREFIX = "vow_";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        handleSystemSwapping(stats);
    }

    private static final List<String> SORTED_BASE_HULL_IDS = new ArrayList<>();
    static {
        SORTED_BASE_HULL_IDS.addAll(HULL_TO_INDEX.keySet());
        SORTED_BASE_HULL_IDS.sort((a, b) -> Integer.compare(b.length(), a.length()));
    }

    // 修改 handleSystemSwapping 中的查找部分
    private void handleSystemSwapping(MutableShipStatsAPI stats) {
        if (hasAnySystemSelectorHullmod(stats)) {
            return;
        }
        if (stats.getEntity() == null || !(stats.getEntity() instanceof ShipAPI ship)) {
            return;
        }
        if (ship.getHullSpec() == null) {
            return;
        }

        String currentHullId = ship.getHullSpec().getHullId();
        Integer currentIndex = HULL_TO_INDEX.get(currentHullId);

        // 精确匹配失败时，改用前缀最长匹配
        if (currentIndex == null) {
            for (String baseId : SORTED_BASE_HULL_IDS) {
                if (currentHullId.startsWith(baseId)) {
                    currentIndex = HULL_TO_INDEX.get(baseId);
                    break;
                }
            }
        }

        // 仍然匹配不到，但船体前缀符合，安全回退到基础变体（索引 0）
        if (currentIndex == null && currentHullId.startsWith(HULL_ID_PREFIX)) {
            currentIndex = 0;
        }

        if (currentIndex == null) {
            Global.getLogger(this.getClass()).warn("Hull ID not matched, no system config: " + currentHullId);
            return;
        }

        // Get the next system configuration in the cycle
        SystemConfig currentConfig = SYSTEM_CONFIGS.get(currentIndex);
        int nextIndex = currentConfig.nextIndex;
        SystemConfig nextConfig = SYSTEM_CONFIGS.get(nextIndex);

        if (nextConfig == null) {
            Global.getLogger(this.getClass()).error("Invalid next index configuration: " + nextIndex);
            return;
        }

        // Perform the system switch
        performSystemSwitch(stats, ship, nextConfig);
    }

    /**
     * Check if any system selector hullmod is present
     * Optimized version using Set.contains() for O(1) lookup instead of nested loops
     */
    private boolean hasAnySystemSelectorHullmod(MutableShipStatsAPI stats) {
        Collection<String> currentHullmods = stats.getVariant().getHullMods();
        for (String hullmod : currentHullmods) {
            if (SYSTEM_SELECTOR_HULLMODS.contains(hullmod)) {
                return true; // Found a system selector hullmod
            }
        }
        return false; // No system selector hullmods found
    }

    /**
     * Perform the actual system switch with proper error handling
     */
    private void performSystemSwitch(MutableShipStatsAPI stats, ShipAPI ship, SystemConfig nextConfig) {
        try {
            // Get the hull spec to switch to
            ShipHullSpecAPI newHullSpec = Global.getSettings().getHullSpec(nextConfig.hullId);

            if (newHullSpec == null) {
                Global.getLogger(this.getClass()).error("Hull spec not found: " + nextConfig.hullId);
                return;
            }

            // Set the hull spec of the ship's variant to the new one
            ship.getVariant().setHullSpecAPI(newHullSpec);

            // Set the hull spec of the mutable ship stats to the new one
            stats.getVariant().setHullSpecAPI(newHullSpec);

            // Add the proper selector hullmod to the variant
            stats.getVariant().addMod(nextConfig.selectorHullmod);

        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Error switching system to " + nextConfig.hullId, e);
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        SystemConfig systemConfig = SYSTEM_CONFIGS.get(index);
        return systemConfig != null ? systemConfig.description : null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getHullSpec().getHullId().startsWith(HULL_ID_PREFIX);
    }

    // Utility methods for external access if needed

    /**
     * Get all available hull IDs for this system
     */
    public static Set<String> getAvailableHullIds() {
        return new HashSet<>(HULL_TO_INDEX.keySet());
    }

    /**
     * Get system config by index
     */
    public static SystemConfig getSystemConfig(int index) {
        return SYSTEM_CONFIGS.get(index);
    }

    /**
     * Get total number of system configurations
     */
    public static int getSystemConfigCount() {
        return SYSTEM_CONFIGS.size();
    }

    /**
     * Check if a hullmod is a Winghead system selector
     */
    public static boolean isSystemSelectorHullmod(String hullmodId) {
        return SYSTEM_SELECTOR_HULLMODS.contains(hullmodId);
    }

    /**
     * Get the next hull ID in the cycle for a given hull ID
     */
    public static String getNextHullId(String currentHullId) {
        Integer currentIndex = HULL_TO_INDEX.get(currentHullId);
        if (currentIndex == null) return null;

        SystemConfig currentConfig = SYSTEM_CONFIGS.get(currentIndex);
        SystemConfig nextConfig = SYSTEM_CONFIGS.get(currentConfig.nextIndex);

        return nextConfig != null ? nextConfig.hullId : null;
    }
}