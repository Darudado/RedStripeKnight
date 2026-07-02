package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.hullmods.MA_WeaponSwitch2.hullModComfig;
import java.util.*;
import static data.hullmods.MA_WeaponSwitch2.HULL_MOD_CONFIGS;

/**
 * 轮换插件管理器插件，内置在飞船中，可以直接设置隐藏不让玩家看见
 * 该插件的作用是飞船创建的时候检测所有预设的轮替插件，将第一个插件装到飞船上
 */
public class MA_WeaponSwitch extends BaseHullMod {

    private static final Map<ShipVariantAPI, Set<String>> modRecorder = new HashMap<>();
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        ShipVariantAPI variant = stats.getVariant();
        if (variant != null) {
            //比对新旧插件差异，被移除的插件丢进该set
            Set<String> removedHullMod = new HashSet<>();
            if (modRecorder.containsKey(variant)) {
                Global.getLogger(this.getClass()).info("检查被移除的插件");
                for (String modId : modRecorder.get(variant)) {
                    if (!variant.hasHullMod(modId)) {
                        removedHullMod.add(modId);
                        Global.getLogger(this.getClass()).info("被移除的插件："+modId);
                    }
                }

            } else {
                Global.getLogger(this.getClass()).info("首次安装");
                Set<String> sourceHullMod = new HashSet<>();
                for (MA_WeaponSwitch2.hullModComfig config : HULL_MOD_CONFIGS.values()) {
                    if (!sourceHullMod.contains(config.getSourceHullMod())) {
                        if (config.getSourceHullMod() != null && config.isApplicableToShip(variant)) {
                            sourceHullMod.add(config.getSourceHullMod());
                            Global.getLogger(this.getClass()).info("SourceMod："+config.getSourceHullMod());
                        }
                    }
                }
                for (String modId : variant.getHullMods()) {
                    if (HULL_MOD_CONFIGS.containsKey(modId)) {
                        hullModComfig config = HULL_MOD_CONFIGS.get(modId);
                        sourceHullMod.remove(config.getSourceHullMod());
                        Global.getLogger(this.getClass()).info("已安装的："+modId +" 移除"+sourceHullMod);
                    }
                }
                if (!sourceHullMod.isEmpty()) {
                    for (String modId : sourceHullMod) {
                        Global.getLogger(this.getClass()).info("安装插件："+modId);
                        variant.addMod(modId);
                    }
                }
            }
            if (!removedHullMod.isEmpty()) {
                Global.getLogger(this.getClass()).info("遍历被移除mod的下一个mod：");
                for (String modId : removedHullMod) {
                    String addId = null;
                    String sourceId = null;
                    int i = 0;
                    while (addId == null && HULL_MOD_CONFIGS.containsKey(modId)) {
                        i++;
                        hullModComfig config = HULL_MOD_CONFIGS.get(modId);
                        addId = config.getNextHullmod();
                        if (sourceId == null) {
                            sourceId = config.getSourceHullMod();
                            Global.getLogger(this.getClass()).info("sourceid："+sourceId);
                        }
                        if (!config.isApplicableToShip(variant)) {
                            addId = null;
                            modId=config.getNextHullmod();
                        }
                        if (i > 999) {
                            Global.getLogger(this.getClass()).info("尝试寻找继承插件id的时候陷入死循环，跳出，所有插件都不适用于当前舰船 插件id：" + modId);
                            break;
                        }
                    }
                    if (addId != null && !addId.isEmpty()) {
                        variant.addMod(addId);
                        Global.getLogger(this.getClass()).info("安装插件："+addId);
                    } else if (sourceId != null && !sourceId.isEmpty()) {
                        variant.addMod(sourceId);
                        Global.getLogger(this.getClass()).info("安装插件："+sourceId);
                    }
                }
            }
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        //当飞船生成后，保存一次装配至记录器，用于检索是否存在插件更换
        ShipVariantAPI variant = ship.getVariant();
        modRecorder.put(variant, new HashSet<>(variant.getHullMods()));
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (isInRefit()) {
            return;
        }
        //清理所有纪录
        modRecorder.clear();
    }

    /**
     * 当处于装配界面时返回true
     */
    public static boolean isInRefit() {
        if (Global.getSector().getCampaignUI().getCurrentCoreTab() == CoreUITabId.REFIT) {
            return true;
        }
        if (Global.getCurrentState() == GameState.COMBAT) {
            return false; // not in combat
        }
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) {
            return false; // why
        }
        if (engine.isSimulation()) {
            return false; // not in sim
        }
        return engine.getCombatUI() == null; // not ui
    }

}
