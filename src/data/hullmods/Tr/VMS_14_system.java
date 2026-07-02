package data.hullmods.Tr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.campaign.CargoAPI;
import data.scripts.utils.RSUtil;

import java.util.*;

public class VMS_14_system extends BaseHullMod {
    public static final String BASIC_HULL_ID = "vow_VMS_14";
    public static final Map<String, String> HULL_IDS = new HashMap<>();

    static {
        HULL_IDS.put("vow_VMS_14_assault_selector", "vow_VMS_14_assault");
        HULL_IDS.put("vow_VMS_14_firesupport_selector", "vow_VMS_14_firesupport");
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 保留空实现或添加需要的逻辑
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        String hullId = ship.getVariant().getHullSpec().getHullId();
        boolean hasSelector = false;

        // 检查是否需要切换变种
        for (Map.Entry<String, String> entry : HULL_IDS.entrySet()) {
            String selectorMod = entry.getKey();
            if (ship.getVariant().hasHullMod(selectorMod)) {
                hasSelector = true;

                if (BASIC_HULL_ID.equals(hullId)) {
                    // 从基础型号切换到变种型号
                    handleVariantSwitch(ship, selectorMod, entry.getValue());
                    return;
                }
                break;
            }
        }

        if (!hasSelector && !BASIC_HULL_ID.equals(hullId)) {
            // 从变种型号切换回基础型号（选择器插件被移除）
            handleVariantSwitch(ship, null, BASIC_HULL_ID);
        }
    }

    /**
     * 处理舰船变种切换的逻辑，包括移除武器并放入仓库，同时保留所有S-mod。
     */
    private void handleVariantSwitch(ShipAPI ship, String selectorHullMod, String targetHullId) {
        // 保存当前变体的S-mod列表（必须在切换船体之前执行）
        ShipVariantAPI variant = ship.getVariant();
        List<String> sMods = new ArrayList<>(variant.getSMods());

        // 将当前舰船的非内置武器移除并存入仓库
        saveWeaponsToStorage(ship);

        // 切换到新的船体规格
        ShipHullSpecAPI hullSpec = Global.getSettings().getHullSpec(targetHullId);

        // 重建船体插件：保留S-mod，添加新船体内置、选择器插件和系统插件
        rebuildHullMods(ship, hullSpec, selectorHullMod, sMods);

        // 刷新UI
        refreshUIIfNeeded();
    }

    /**
     * 将当前舰船的所有非内置武器移除并存入仓库
     */
    private void saveWeaponsToStorage(ShipAPI ship) {
        if (!RSUtil.isInPlayerFleet(ship)) return;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo == null) return;

        ShipVariantAPI variant = ship.getVariant();
        List<String> nonBuiltInSlots = variant.getNonBuiltInWeaponSlots();

        for (String slotId : nonBuiltInSlots) {
            String weaponId = variant.getWeaponId(slotId);
            if (weaponId != null && !weaponId.isEmpty()) {
                variant.clearSlot(slotId);
                cargo.addWeapons(weaponId, 1);
            }
        }
    }

    /**
     * 重建船体插件系统：先切换船体，清除所有插件，再重新添加内置插件、S-mod、选择器插件和系统插件。
     */
    private void rebuildHullMods(ShipAPI ship, ShipHullSpecAPI newHullSpec, String selectorHullMod, List<String> sMods) {
        ShipVariantAPI variant = ship.getVariant();

        // 1. 设置新船体规格
        variant.setHullSpecAPI(newHullSpec);

        // 2. 清除所有武器和插件（武器已由 saveWeaponsToStorage 清空，此处主要清除插件）
        variant.clear();

        // 3. 添加新船体的内置插件
        Set<String> builtInMods = new HashSet<>(newHullSpec.getBuiltInMods());
        //for (String builtInMod : builtInMods) {
            //variant.addPermaMod(builtInMod);
        //}

        // 4. 添加之前保存的S-mod（排除已内置的插件，避免重复）
        for (String sMod : sMods) {
            if (!builtInMods.contains(sMod)) {
                variant.addPermaMod( sMod,true);
            }
        }

        // 5. 如果是从基础型号切换，添加选择器插件（作为普通插件）
        if (selectorHullMod != null) {
            variant.addMod(selectorHullMod);
        }

        // 6. 添加系统插件自身（确保 hullmod 效果持续生效）
        variant.addMod("vow_VMS_14_system");
    }

    /**
     * 如果需要则刷新UI
     */
    private void refreshUIIfNeeded() {
        if (Global.getCurrentState() == com.fs.starfarer.api.GameState.CAMPAIGN &&
                Global.getSector() != null &&
                !Global.getSector().isPaused()) {
            RSUtil.refreshRefitUI();
        }
    }
}