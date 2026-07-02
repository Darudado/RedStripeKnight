package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.*;

public class RSCommand extends BaseCommandPlugin {

    public static final Set<String> ships = new HashSet<>();

    static {
        ships.add("mem_bloody_wolf");
        // 可继续添加其他机甲 hull ID
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            return false;
        }

        String k = params.get(0).getString(memoryMap);

        if ("showBuyHullMemberPanel".equals(k)) {
            if (Global.getSector().getMemoryWithoutUpdate().contains("$RS_ProductScript")) {
                dialog.getOptionPanel().setOptionText("Check order status", "RS_person1_product_ops");
            } else {
                dialog.getOptionPanel().setOptionText("Produce mecha", "RS_person1_product_ops");
            }
            return false;
        }

        if ("newProduct".equals(k)) {
            if (Global.getSector().getMemoryWithoutUpdate().contains("$RS_ProductScript")) {
                RSProductScript script = (RSProductScript) Global.getSector().getMemoryWithoutUpdate().get("$RS_ProductScript");
                script.get(dialog.getTextPanel());
            } else {
                buyHull(dialog);
            }
            return false;
        }

        return false;
    }

    /**
     * 打开自定义机甲购买面板
     */
    public static void buyHull(InteractionDialogAPI dialog) {
        dialog.hideVisualPanel();
        float h = Global.getSettings().getScreenHeight() * 0.6f;
        float w = 500.0f;
        RS_BuyMechPanel panel = new RS_BuyMechPanel(dialog);
        dialog.getVisualPanel().showCustomPanel(w, h, panel);
    }

    /**
     * 自定义机甲购买面板，实现 CustomUIPanelPlugin 接口
     */
    public static class RS_BuyMechPanel implements CustomUIPanelPlugin {

        private final InteractionDialogAPI dialog;
        private CustomPanelAPI panel;
        private float panelWidth, panelHeight;
        private final Map<String, Integer> selected = new HashMap<>();
        private final Map<String, Float> prices = new HashMap<>();   // 单价为浮点数

        public RS_BuyMechPanel(InteractionDialogAPI dialog) {
            this.dialog = dialog;
            for (String hullId : ships) {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec != null) {
                    selected.put(hullId, 0);
                    prices.put(hullId, spec.getBaseValue());
                }
            }
        }

        public void init(CustomPanelAPI panel, float width, float height) {
            this.panel = panel;
            this.panelWidth = width;
            this.panelHeight = height;
            createUI();
        }

        @Override
        public void render(float alphaMult) { }

        @Override
        public void positionChanged(PositionAPI position) {

        }

        @Override
        public void renderBelow(float alphaMult) { }

        @Override
        public void advance(float amount) { }

        @Override
        public void processInput(List<InputEventAPI> events) {

        }

        @Override
        public void buttonPressed(Object buttonId) {
            String id = (String) buttonId;
            if ("confirm".equals(id)) {
                Map<String, Integer> product = new HashMap<>();
                float totalCost = 0f;
                for (Map.Entry<String, Integer> entry : selected.entrySet()) {
                    if (entry.getValue() > 0) {
                        product.put(entry.getKey(), entry.getValue());
                        totalCost += entry.getValue() * prices.get(entry.getKey());
                    }
                }
                if (product.isEmpty()) {
                    dialog.getTextPanel().addPara("No mechs selected.");
                } else {
                    long timestamp = Global.getSector().getClock().getTimestamp();
                    new RSProductScript(timestamp, product);
                    dialog.getTextPanel().addPara("The order has been submitted and is expected to be completed in 30 days.");
                    dialog.getTextPanel().addPara("Estimated cost:" + Misc.getDGSCredits(totalCost) + "Star coins (not actually deducted)");
                }
                dialog.showVisualPanel();   // 恢复默认视觉面板
                return;
            }

            if (id.startsWith("add_")) {
                String hullId = id.substring(4);
                selected.put(hullId, selected.getOrDefault(hullId, 0) + 1);
            } else if (id.startsWith("sub_")) {
                String hullId = id.substring(4);
                int cur = selected.getOrDefault(hullId, 0);
                if (cur > 0) selected.put(hullId, cur - 1);
            }
            createUI();
        }

        public void createUI(float width, float height) {
            this.panelWidth = width;
            this.panelHeight = height;
            createUI();
        }

        private void createUI() {
            if (panel == null) return;
            TooltipMakerAPI ui = panel.createUIElement(panelWidth, panelHeight, true);

            float pad = 3f;
            ui.addSectionHeading("Mecha production order", Alignment.MID, pad);

            for (String hullId : ships) {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec == null) continue;
                int qty = selected.getOrDefault(hullId, 0);
                float price = prices.get(hullId);

                // 图标与名称
                String iconName = spec.getSpriteName();
                if (iconName == null || iconName.isEmpty()) {
                    iconName = "graphics/icons/ships/generic_fighter.png"; // 默认图标
                }
                ui.beginImageWithText(iconName, 32f);
                ui.addPara(spec.getHullName() + " (" + spec.getDesignation() + ")", pad);
                ui.addPara("unit price:" + Misc.getDGSCredits(price) + "star coins", pad);
                ui.addImageWithText(0f);

                // 数量与加减按钮放在同一行（使用简单布局）
                ui.addPara("quantity:" + qty, pad);
                ui.addButton("sub_" + hullId, " - ", 30f, 20f, pad);
                // 使用 TooltipMakerAPI 的 addSpacer 或 addPara 来分隔，然后添加 add 按钮
                // 这里直接在减号按钮后添加加号按钮
                ui.addButton("add_" + hullId, " + ", 30f, 20f, pad);
                ui.addSpacer(5f);
            }

            ui.addButton("confirm", "Submit order", 200f, 25f, pad);
            panel.addUIElement(ui).inTL(5f, 5f);
        }

    }

    // ---------- 订单生产脚本 ----------
    public static class RSProductScript implements EveryFrameScript {
        private final long timestamp;
        private final Map<String, Integer> product;
        private boolean done = false;

        public RSProductScript(long timestamp, Map<String, Integer> product) {
            this.timestamp = timestamp;
            this.product = product;
            Global.getSector().getMemoryWithoutUpdate().set("$RS_ProductScript", this);
            Global.getSector().addScript(this);
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        private int getDays() {
            return Global.getSettings().isDevMode() ? 1 : 30;
        }

        @Override
        public void advance(float amount) {
            if (Global.getSector().getClock().getElapsedDaysSince(timestamp) > (float) getDays()
                    && Global.getSector().getMemoryWithoutUpdate().get("$RS_ProductScript") != this) {
                done = true;
            }
        }

        public void get(TextPanelAPI text) {
            if (done) {
                Global.getSector().getMemoryWithoutUpdate().unset("$RS_ProductScript");
                return;
            }
            if (Global.getSector().getClock().getElapsedDaysSince(timestamp) > (float) getDays()) {
                done = true;
                Global.getSector().getMemoryWithoutUpdate().unset("$RS_ProductScript");

                WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
                for (Map.Entry<String, Integer> entry : product.entrySet()) {
                    String id = entry.getKey();
                    int quantity = entry.getValue();

                    List<String> variants = Global.getSettings().getHullIdToVariantListMap().get(id);
                    String variant = id + "_Hull";
                    picker.clear();
                    if (variants != null && !variants.isEmpty()) {
                        picker.addAll(variants);
                    }
                    FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
                    for (int i = 0; i < quantity; i++) {
                        if (!picker.isEmpty()) {
                            variant = picker.pick();
                        }
                        AddRemoveCommodity.addFleetMemberGainText(member, text);
                        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(variant);
                    }
                }
            } else {
                int remainingDays = (int) (getDays() - Global.getSector().getClock().getElapsedDaysSince(timestamp));
                text.addPara("There are still orders" + remainingDays + "The genius will be finished producing, please wait patiently.");
            }
        }
    }
}