package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.Tuning;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.Random;

public class CrusadersBaseManager extends BaseEventManager {

    // 配置参数
    public static final float BASE_CHECK_INTERVAL_DAYS = 30.0F;        // 检查间隔
    public static final float BASE_SPAWN_PROBABILITY = 0.5F;           // 基础生成概率
    public static final float PLAYER_VISIT_COOLDOWN_DAYS = 45.0F;      // 玩家访问冷却
    public static final float FAR_SYSTEM_DISTANCE = 36000.0F;          // 远距离判定

    // 等级天数阈值
    public static final float TIER_1_THRESHOLD_DAYS = 180.0F;
    public static final float TIER_2_THRESHOLD_DAYS = 360.0F;
    public static final float TIER_3_THRESHOLD_DAYS = 540.0F;
    public static final float DESTROYED_BASE_DAYS_BONUS = 90.0F;       // 每摧毁一个增加的天数

    // 冷却月数
    public static final int MIN_TIMEOUT_MONTHS = 2;
    public static final int MAX_TIMEOUT_MONTHS = 4;
    public static final int CHECKS_PER_MONTH = 3;

    // 主题权重
    public static final float THEME_MISC_SKIP_WEIGHT = 1.0F;
    public static final float THEME_MISC_WEIGHT = 3.0F;
    public static final float THEME_REMNANT_WEIGHT = 3.0F;
    public static final float THEME_RUINS_WEIGHT = 5.0F;

    // 内存键
    public static final String KEY = "$core_CrusadersBaseManager";
    public static final String RECENTLY_USED_FOR_BASE = "$core_recentlyUsedForBase";

    protected Random random = new Random();
    protected int numSpawnChecksToSkip = 0;
    protected int numDestroyed = 0;
    protected long start = 0L;
    protected float extraDays = 0.0F;

    public static CrusadersBaseManager getInstance() {
        Object test = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        if (test instanceof CrusadersBaseManager) {
            return (CrusadersBaseManager) test;
        }
        return null;
    }

    public CrusadersBaseManager() {
        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
        this.start = Global.getSector().getClock().getTimestamp();
    }

    @Override
    protected float getBaseInterval() {
        return BASE_CHECK_INTERVAL_DAYS;
    }

    public float getExtraDays() {
        return this.extraDays;
    }

    public void setExtraDays(float extraDays) {
        this.extraDays = extraDays;
    }

    public float getDaysSinceStart() {
        float days = Global.getSector().getClock().getElapsedDaysSince(this.start) + this.extraDays;
        if (Misc.isFastStartExplorer()) {
            days += Tuning.FAST_START_EXTRA_DAYS - 30.0F;
        } else if (Misc.isFastStart()) {
            days += Tuning.FAST_START_EXTRA_DAYS + 60.0F;
        }
        return days;
    }

    @Override
    protected EveryFrameScript createEvent() {
        // 如果正处于跳过周期，则不生成
        if (this.numSpawnChecksToSkip > 0) {
            this.numSpawnChecksToSkip--;
            return null;
        }

        // 概率检查
        if (this.random.nextFloat() > BASE_SPAWN_PROBABILITY) {
            return null;
        }

        // 选择合适星系
        StarSystemAPI system = pickSystemForCrusadersBase();
        if (system == null) {
            return null;
        }

        // 根据游戏时间与摧毁数量选择等级
        CrusadersBaseGen.ColonyTier tier = pickTier();

        // 使用工厂类创建殖民地实例
        CrusadersBaseGen colony = CrusadersBaseConstruction.createColony(system, tier, random);
        if (colony != null && colony.isValid()) {
            markRecentlyUsedForBase(system);
            return colony;   // CrusadersBaseGen 实现了 EveryFrameScript
        }
        return null;
    }

    protected CrusadersBaseGen.ColonyTier pickTier() {
        float days = getDaysSinceStart();
        days += numDestroyed * DESTROYED_BASE_DAYS_BONUS;

        WeightedRandomPicker<CrusadersBaseGen.ColonyTier> picker = new WeightedRandomPicker<>(random);
        if (days < TIER_1_THRESHOLD_DAYS) {
            picker.add(CrusadersBaseGen.ColonyTier.TIER_1, 10f);
        } else if (days < TIER_2_THRESHOLD_DAYS) {
            picker.add(CrusadersBaseGen.ColonyTier.TIER_1, 5f);
            picker.add(CrusadersBaseGen.ColonyTier.TIER_2, 10f);
        } else if (days < TIER_3_THRESHOLD_DAYS) {
            picker.add(CrusadersBaseGen.ColonyTier.TIER_2, 5f);
            picker.add(CrusadersBaseGen.ColonyTier.TIER_3, 10f);
        } else {
            picker.add(CrusadersBaseGen.ColonyTier.TIER_2, 3f);
            picker.add(CrusadersBaseGen.ColonyTier.TIER_3, 10f);
        }
        return picker.pick();
    }

    public static void markRecentlyUsedForBase(StarSystemAPI system) {
        if (system != null && system.getCenter() != null) {
            system.getCenter().getMemoryWithoutUpdate().set(RECENTLY_USED_FOR_BASE, true, 180f);
        }
    }

    public StarSystemAPI pickSystemForCrusadersBase() {
        WeightedRandomPicker<StarSystemAPI> far = new WeightedRandomPicker<>(random);
        WeightedRandomPicker<StarSystemAPI> normal = new WeightedRandomPicker<>(random);

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasPulsar() || system.hasTag("theme_special") || system.hasTag("theme_hidden"))
                continue;

            float daysSinceVisit = Global.getSector().getClock().getElapsedDaysSince(system.getLastPlayerVisitTimestamp());
            if (daysSinceVisit < PLAYER_VISIT_COOLDOWN_DAYS) continue;
            if (system.getCenter().getMemoryWithoutUpdate().contains(RECENTLY_USED_FOR_BASE)) continue;

            float weight = calculateSystemWeight(system);
            if (weight <= 0f) continue;

            float usefulStuff = system.getCustomEntitiesWithTag("objective").size() +
                    system.getCustomEntitiesWithTag("stable_location").size();
            if (usefulStuff <= 0) continue;
            if (Misc.hasPulsar(system)) continue;
            if (!Misc.getMarketsInLocation(system).isEmpty()) continue;

            float dist = system.getLocation().length();
            if (dist > FAR_SYSTEM_DISTANCE) {
                far.add(system, weight * usefulStuff);
            } else {
                normal.add(system, weight * usefulStuff);
            }
        }

        if (normal.isEmpty()) normal.addAll(far);
        return normal.pick();
    }

    private static float calculateSystemWeight(StarSystemAPI system) {
        if (system.hasTag("theme_misc_skip")) return THEME_MISC_SKIP_WEIGHT;
        if (system.hasTag("theme_misc")) return THEME_MISC_WEIGHT;
        if (system.hasTag("theme_remnant_no_fleets") || system.hasTag("theme_remnant_destroyed"))
            return THEME_REMNANT_WEIGHT;
        if (system.hasTag("theme_ruins")) return THEME_RUINS_WEIGHT;
        if (system.hasTag("theme_core_unpopulated")) return 0f;
        return 0f;
    }

    public void incrDestroyed() {
        this.numDestroyed++;
        int monthsTimeout = MIN_TIMEOUT_MONTHS + random.nextInt(MAX_TIMEOUT_MONTHS - MIN_TIMEOUT_MONTHS + 1);
        this.numSpawnChecksToSkip = Math.max(this.numSpawnChecksToSkip, monthsTimeout * CHECKS_PER_MONTH);
    }

    @Override
    protected int getMinConcurrent() {
        return 0;
    }

    @Override
    protected int getMaxConcurrent() {
        return 4;   // 最多同时存在4个十字军殖民地
    }
}