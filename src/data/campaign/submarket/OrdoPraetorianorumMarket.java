package data.campaign.submarket;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.*;

public class OrdoPraetorianorumMarket extends BaseSubmarketPlugin {

    /**
     * 获取关税率
     * @return 1f 表示0%的关税（具体含义需参考游戏实现）
     */
    @Override
    public float getTariff() {
        return 1f;
    }

    /**
     * 在玩家交互前更新货物
     * 根据市场规模动态调整货物数量，只包含拥有特定tag的商品
     */
    @Override
    public void updateCargoPrePlayerInteraction() {
        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            sinceSWUpdate = 0f;
            pruneWeapons(0f);

            // 货物数量参数 - 基于市场规模动态调整
            int weapons = 10 + Math.max(5, market.getSize()) * 4;
            int fighterNum = 3 + market.getSize();
            int hullmods = 1 + (int) Math.ceil(market.getSize() / 3f);
            int shipNum = 5 + market.getSize() * 2;

            // 添加商品到市场（只添加带有特定标签的商品）
            addFightersWithTag(fighterNum, "rs_praetorianorum_bp");
            addWeaponsWithTag(weapons, "rs_praetorianorum_bp");
            addHullMods(hullmods, hullmods + 1);

            // 清理封存舰船
            getCargo().getMothballedShips().clear();

            // 添加带有特定标签的舰船
            addShipsWithTag(shipNum, "rs_praetorianorum_bp");
        }

        getCargo().sort();
    }

    /**
     * 添加带有特定标签的战斗机
     */
    private void addFightersWithTag(int num, String requiredTag) {
        WeightedRandomPicker<FighterWingSpecAPI> picker = new WeightedRandomPicker<>(itemGenRandom);

        // 获取所有战斗机规格
        List<FighterWingSpecAPI> allFighters = Global.getSettings().getAllFighterWingSpecs();

        for (FighterWingSpecAPI spec : allFighters) {
            if (spec.hasTag(requiredTag)) {
                // 根据战斗机等级设置权重
                float weight = 10f - spec.getTier(); // 低级战斗机权重更高
                if (weight < 1f) weight = 1f;
                picker.add(spec, weight);
            }
        }

        if (picker.isEmpty()) {
            return;
        }

        for (int i = 0; i < num && !picker.isEmpty(); i++) {
            FighterWingSpecAPI spec = picker.pick();
            if (spec == null) continue;

            // 根据战斗机类型决定数量
            int count = switch (spec.getRole()) {
                case BOMBER -> 1 + itemGenRandom.nextInt(2);
                case FIGHTER, INTERCEPTOR -> 2 + itemGenRandom.nextInt(3);
                default -> 1 + itemGenRandom.nextInt(2);
            };

            cargo.addItems(CargoAPI.CargoItemType.FIGHTER_CHIP, spec.getId(), count);
        }
    }

    /**
     * 添加带有特定标签的武器
     */
    private void addWeaponsWithTag(int num, String requiredTag) {
        WeightedRandomPicker<WeaponSpecAPI> picker = new WeightedRandomPicker<>(itemGenRandom);

        // 获取所有武器规格
        List<WeaponSpecAPI> allWeapons = Global.getSettings().getAllWeaponSpecs();

        for (WeaponSpecAPI spec : allWeapons) {
            if (spec.hasTag(requiredTag)) {
                // 根据武器大小和等级设置权重
                float weight = switch (spec.getSize()) {
                    case SMALL -> 10f;
                    case MEDIUM -> 6f;
                    case LARGE -> 3f;
                };
                weight -= spec.getTier(); // 低级武器权重更高
                if (weight < 1f) weight = 1f;
                picker.add(spec, weight);
            }
        }

        if (picker.isEmpty()) {
            return;
        }

        for (int i = 0; i < num && !picker.isEmpty(); i++) {
            WeaponSpecAPI spec = picker.pick();
            if (spec == null) continue;

            // 根据武器大小决定数量
            int count = switch (spec.getSize()) {
                case LARGE -> 1;
                case MEDIUM -> 1 + itemGenRandom.nextInt(2);
                case SMALL -> 2 + itemGenRandom.nextInt(3);
            };

            cargo.addWeapons(spec.getWeaponId(), count);
        }
    }

    /**
     * 添加带有特定标签的舰船（修复版）
     */
    private void addShipsWithTag(int shipNum, String requiredTag) {
        // 正确的获取所有船体规格的方式
        List<ShipHullSpecAPI> allHullSpecs = Global.getSettings().getAllShipHullSpecs();

        // 过滤出带有特定标签的船体
        List<ShipHullSpecAPI> validHulls = new ArrayList<>();
        for (ShipHullSpecAPI hullSpec : allHullSpecs) {
            if (hullSpec != null && hullSpec.hasTag(requiredTag)) {
                if (!hullSpec.hasTag(Tags.NO_SELL)) {
                    validHulls.add(hullSpec);
                }
            }
        }

        if (validHulls.isEmpty()) {
            System.out.println("OrdoPraetorianorumMarket: No ships found with tag: " + requiredTag);
            return;
        }

        // 使用加权随机选择器
        WeightedRandomPicker<ShipHullSpecAPI> hullPicker = new WeightedRandomPicker<>(itemGenRandom);

        for (ShipHullSpecAPI hullSpec : validHulls) {
            // 根据舰船尺寸设置权重
            float weight = switch (hullSpec.getHullSize()) {
                case FIGHTER -> 20f;
                case FRIGATE -> 15f;
                case DESTROYER -> 10f;
                case CRUISER -> 5f;
                case CAPITAL_SHIP -> 2f;
                default -> 10f;
            };

            // 根据等级调整权重（低级船更常见）
            weight -= hullSpec.getRarity();
            if (weight < 1f) weight = 1f;

            hullPicker.add(hullSpec, weight);
        }

        if (hullPicker.isEmpty()) {
            return;
        }

        // 生成舰船
        for (int i = 0; i < shipNum; i++) {
            ShipHullSpecAPI hullSpec = hullPicker.pick();
            if (hullSpec == null) continue;

            // 获取船体ID
            String hullId = hullSpec.getHullId();

            // 使用船体ID创建空船体变体
            String variantId = hullId + "_Hull";

            // 计算质量
            float quality = Misc.getShipQuality(market, "red_stripe");

            // 添加舰船到货物
            try {
                addShip(variantId, true, quality);
            } catch (Exception e) {
                // 如果"_Hull"变体不存在，尝试其他变体
                System.out.println("Failed to add ship with variant: " + variantId + ", trying alternatives");

                // 获取派系对该船体的默认变体 - 修正后的方法
                FactionAPI faction = Global.getSector().getFaction("red_stripe");
                String factionVariant = getFactionVariantForHull(faction, hullId);

                if (factionVariant != null) {
                    try {
                        addShip(factionVariant, true, quality);
                    } catch (Exception e2) {
                        // 尝试使用船体ID本身作为变体ID
                        try {
                            addShip(hullId, true, quality);
                        } catch (Exception e3) {
                            System.out.println("Could not add ship: " + hullId);
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取派系对特定船体的变体
     * 这是对原代码中不存在的getVariantsForShip方法的替代实现
     */
    private String getFactionVariantForHull(FactionAPI faction, String hullId) {
        // 方法1：检查派系的变体重写
        Map<String, Float> variantOverrides = faction.getVariantOverrides();
        if (variantOverrides.containsKey(hullId)) {
            return hullId; // 或者返回变体重写中的特定变体
        }

        // 方法2：尝试获取派系的已知舰船列表中的变体
        // 在游戏中，派系的舰船通常是特定变体，我们可以尝试构建标准变体ID
        String[] possibleVariants = {
                hullId,  // 船体ID本身
                hullId + "_Standard",  // 标准变体
                hullId + "_variant",   // 变体
                hullId + "_Hull"   // 另一种变体格式
        };

        for (String variant : possibleVariants) {
            // 检查这个变体是否存在于游戏中
            try {
                // 尝试创建舰队成员来测试变体是否存在
                FleetMemberAPI testMember = Global.getFactory().createFleetMember(
                        FleetMemberType.SHIP, variant
                );
                // 如果能创建成功，返回这个变体
                return variant;
            } catch (Exception e) {
                // 变体不存在，继续尝试下一个
            }
        }

        return null;
    }


    /**
     * 检查货物在市场上是否非法交易
     * @param stack 货物堆
     * @param action 交易动作
     * @return 是否非法
     */
    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        RepLevel ordoLevel = Global.getSector().getFaction("red_stripe").getRelationshipLevel(player);

        if (action == TransferAction.PLAYER_SELL) return true; // 完全禁止玩家出售
        return action == TransferAction.PLAYER_BUY && !ordoLevel.isAtWorst(RepLevel.COOPERATIVE);
    }

    /**
     * 检查舰船在市场上是否非法交易
     * @param member 舰队成员
     * @param action 交易动作
     * @return 是否非法
     */
    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        RepLevel ordoLevel = Global.getSector().getFaction("red_stripe").getRelationshipLevel(player);

        if (action == TransferAction.PLAYER_SELL) return true; // 完全禁止玩家出售舰船
        return action == TransferAction.PLAYER_BUY && !ordoLevel.isAtWorst(RepLevel.COOPERATIVE);
    }

    /**
     * 获取货物非法交易的提示文本
     * @param stack 货物堆
     * @param action 交易动作
     * @return 提示文本
     */
    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        RepLevel ordoLevel = Global.getSector().getFaction("red_stripe").getRelationshipLevel(player);

        if (action == TransferAction.PLAYER_SELL) return "Items prohibited for sale";
        if (!ordoLevel.isAtWorst(RepLevel.COOPERATIVE)) return "The relationship needs to be above cooperation";
        return "Access conditions not met";
    }

    /**
     * 获取舰船非法交易的提示文本
     * @param member 舰队成员
     * @param action 交易动作
     * @return 提示文本
     */
    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        RepLevel ordoLevel = Global.getSector().getFaction("red_stripe").getRelationshipLevel(player);

        if (action == TransferAction.PLAYER_SELL) return "Ban on sale of ships";
        if (!ordoLevel.isAtWorst(RepLevel.COOPERATIVE)) return "The relationship needs to be above cooperation";
        return "Access conditions not met";
    }

    /**
     * 检查市场是否隐藏
     * @return 只有当市场由OrdoPraetorianorum派系控制时才显示
     */
    @Override
    public boolean isHidden() {
        return !submarket.getFaction().getId().equals("red_stripe");
    }
}