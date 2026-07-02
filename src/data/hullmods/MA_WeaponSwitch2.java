package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MA_WeaponSwitch2 extends BaseHullMod {

    public static final Map<String, hullModComfig> HULL_MOD_CONFIGS = new HashMap<>();
    static {
        hullModComfig config = new hullModComfig("rs_RX78_GP03D_Left_BeanSword_Mode");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_Left_BeanSword_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("BIG_LEFTHAND", "rs_RX78_GP03D_Left_BeanSword");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_Left_Bean_Mode";
        config.sourceHullMod = "rs_RX78_GP03D_Left_BeanSword_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3000;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D_Left");

        config = new hullModComfig("rs_RX78_GP03D_Left_Bean_Mode");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_Left_Bean_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("BIG_LEFTHAND", "rs_RX78_GP03D_Left_Bean");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_Left_BeanSword_Mode";
        config.sourceHullMod = "rs_RX78_GP03D_Left_BeanSword_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3100;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D_Left");

        config = new hullModComfig("rs_RX78_GP03D_Right_BeanSword_Mode");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_Right_BeanSword_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("BIG_RIGHTHAND", "rs_RX78_GP03D_Right_BeanSword");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_Right_Bean_Mode";
        config.sourceHullMod = "rs_RX78_GP03D_Right_BeanSword_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3200;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D_Right");

        config = new hullModComfig("rs_RX78_GP03D_Right_Bean_Mode");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_Right_Bean_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("BIG_RIGHTHAND", "rs_RX78_GP03D_Right_Bean");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_Right_BeanSword_Mode";
        config.sourceHullMod = "rs_RX78_GP03D_Right_BeanSword_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3300;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D_Right");

        config = new hullModComfig("rs_RX78_GP03D_arm_Right_gun_rifle_Mode");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_arm_Right_gun_rifle_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("HANDRIGHT", "rs_RX78_GP03D_arm_Right_gun_rifle");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_arm_Right_Bazooka_Mode";
        config.sourceHullMod = "rs_RX78_GP03D_arm_Right_gun_rifle_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3400;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D_arm_Right");

        config = new hullModComfig("rs_RX78_GP03D_arm_Right_Bazooka_Mode");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_arm_Right_Bazooka_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("HANDRIGHT", "rs_RX78_GP03D_arm_Right_Bazooka");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_arm_Right_gun_rifle_Mode";
        config.sourceHullMod = "rs_RX78_GP03D_arm_Right_gun_rifle_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3500;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D_arm_Right");


        config = new hullModComfig("rs_RX78_GP03D_arm_Left_gun_rifle_Mode");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_arm_Left_gun_rifle_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("HANDLEFT", "rs_RX78_GP03D_arm_Left_gun_rifle");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_arm_Left_Bazooka_Mode";
        config.sourceHullMod = "rs_RX78_GP03D_arm_Left_gun_rifle_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3600;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D_arm_Left");

        config = new hullModComfig("rs_RX78_GP03D_arm_Left_Bazooka_Mode");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_arm_Left_Bazooka_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("HANDLEFT", "rs_RX78_GP03D_arm_Left_Bazooka");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_arm_Left_gun_rifle_Mode";
        config.sourceHullMod = "rs_RX78_GP03D_arm_Left_gun_rifle_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3700;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D_arm_Left");

        config = new hullModComfig("rs_RX78_GP03D_MIRV1_Left1_Mode1");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_MIRV1_Left1_Mode1", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D_Leftmodule");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("Missile_1", "rs_RX78_GP03D_MIRV1_Left1");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_MIRV2_Left1_Mode1";
        config.sourceHullMod = "rs_RX78_GP03D_MIRV1_Left1_Mode1";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3800;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D");

        config = new hullModComfig("rs_RX78_GP03D_MIRV2_Left1_Mode1");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_MIRV2_Left1_Mode1", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D_Leftmodule");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("Missile_1", "rs_RX78_GP03D_MIRV2_Left1");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_MIRV1_Left1_Mode1";
        config.sourceHullMod = "rs_RX78_GP03D_MIRV1_Left1_Mode1";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 3900;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D");

        config = new hullModComfig("rs_RX78_GP03D_MIRV1_Left2_Mode2");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_MIRV1_Left2_Mode2", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D_Leftmodule");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("Missile_2", "rs_RX78_GP03D_MIRV1_Left2");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_MIRV2_Left2_Mode2";
        config.sourceHullMod = "rs_RX78_GP03D_MIRV1_Left2_Mode2";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4000;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D");

        config = new hullModComfig("rs_RX78_GP03D_MIRV2_Left2_Mode2");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_MIRV2_Left2_Mode2", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D_Leftmodule");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("Missile_2", "rs_RX78_GP03D_MIRV2_Left2");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_MIRV1_Left2_Mode2";
        config.sourceHullMod = "rs_RX78_GP03D_MIRV1_Left2_Mode2";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4100;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D");

        config = new hullModComfig("rs_RX78_GP03D_MIRV1_Right1_Mode1");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_MIRV1_Right1_Mode1", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D_Rightmodule");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("Missile_1", "rs_RX78_GP03D_MIRV1_Right1");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_MIRV2_Right1_Mode1";
        config.sourceHullMod = "rs_RX78_GP03D_MIRV1_Right1_Mode1";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4200;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D");

        config = new hullModComfig("rs_RX78_GP03D_MIRV2_Right1_Mode1");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_MIRV2_Right1_Mode1", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D_Rightmodule");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("Missile_1", "rs_RX78_GP03D_MIRV2_Right1");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_MIRV1_Right1_Mode1";
        config.sourceHullMod = "rs_RX78_GP03D_MIRV1_Right1_Mode1";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4300;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D");

        config = new hullModComfig("rs_RX78_GP03D_MIRV1_Right2_Mode2");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_MIRV1_Right2_Mode2", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D_Rightmodule");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("Missile_2", "rs_RX78_GP03D_MIRV1_Right2");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_MIRV2_Right2_Mode2";
        config.sourceHullMod = "rs_RX78_GP03D_MIRV1_Right2_Mode2";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4400;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D");

        config = new hullModComfig("rs_RX78_GP03D_MIRV2_Right2_Mode2");
        HULL_MOD_CONFIGS.put("rs_RX78_GP03D_MIRV2_Right2_Mode2", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_RX78_GP03_D_Rightmodule");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("Missile_2", "rs_RX78_GP03D_MIRV2_Right2");
        // 轮替设置
        config.nextHullMod = "rs_RX78_GP03D_MIRV1_Right2_Mode2";
        config.sourceHullMod = "rs_RX78_GP03D_MIRV1_Right2_Mode2";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4500;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_RX78_GP03D");

        config = new hullModComfig("rs_chesed_Mode");
        HULL_MOD_CONFIGS.put("rs_chesed_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_cherubicoblatus");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("WS0001", "rs_chesed");
        // 轮替设置
        config.nextHullMod = "rs_bracha_Mode";
        config.sourceHullMod = "rs_chesed_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4600;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_cherubicoblatus");

        config = new hullModComfig("rs_bracha_Mode");
        HULL_MOD_CONFIGS.put("rs_bracha_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_cherubicoblatus");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("WS0001", "rs_bracha");
        // 轮替设置
        config.nextHullMod = "rs_chesed_Mode";
        config.sourceHullMod = "rs_chesed_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4700;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_cherubicoblatus");

        config = new hullModComfig("rs_constantia_Maingun1_Mode");
        HULL_MOD_CONFIGS.put("rs_constantia_Maingun1_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_constantia");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("WS0001", "rs_constantia_Maingun1");
        // 轮替设置
        config.nextHullMod = "rs_constantia_Maingun2_Mode";
        config.sourceHullMod = "rs_constantia_Maingun1_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4800;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_constantia");

        config = new hullModComfig("rs_constantia_Maingun2_Mode");
        HULL_MOD_CONFIGS.put("rs_constantia_Maingun2_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_constantia");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("WS0001", "rs_constantia_Maingun2");
        // 轮替设置
        config.nextHullMod = "rs_constantia_Maingun1_Mode";
        config.sourceHullMod = "rs_constantia_Maingun1_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 4900;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_constantia");

        config = new hullModComfig("rs_euthystopos_b_Mode1");
        HULL_MOD_CONFIGS.put("rs_euthystopos_b_Mode1", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_nazaret");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("MAINGUN_1", "rs_euthystopos_b");
        // 轮替设置
        config.nextHullMod = "rs_euthystopos_e_Mode1";
        config.sourceHullMod = "rs_euthystopos_b_Mode1";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5000;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_nazaret_MainGun1");

        config = new hullModComfig("rs_euthystopos_e_Mode1");
        HULL_MOD_CONFIGS.put("rs_euthystopos_e_Mode1", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_nazaret");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("MAINGUN_1", "rs_euthystopos_e");
        // 轮替设置
        config.nextHullMod = "rs_euthystopos_b_Mode1";
        config.sourceHullMod = "rs_euthystopos_b_Mode1";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5100;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_nazaret_MainGun1");

        config = new hullModComfig("rs_euthystopos_b_Mode2");
        HULL_MOD_CONFIGS.put("rs_euthystopos_b_Mode2", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_nazaret");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("MAINGUN_2", "rs_euthystopos_b");
        // 轮替设置
        config.nextHullMod = "rs_euthystopos_e_Mode2";
        config.sourceHullMod = "rs_euthystopos_b_Mode2";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5200;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_nazaret_MainGun2");

        config = new hullModComfig("rs_euthystopos_e_Mode2");
        HULL_MOD_CONFIGS.put("rs_euthystopos_e_Mode2", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_nazaret");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("MAINGUN_2", "rs_euthystopos_e");
        // 轮替设置
        config.nextHullMod = "rs_euthystopos_b_Mode2";
        config.sourceHullMod = "rs_euthystopos_b_Mode2";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5300;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_nazaret_MainGun2");

        config = new hullModComfig("rs_Fiver_lanuch_assault_Mode");
        HULL_MOD_CONFIGS.put("rs_Fiver_lanuch_assault_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_Tr_5_fiver");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("LEFTWINGC_L_SOURCE", "rs_Fiver_lanuch_left_assault");
        config.replaceSlots.put("RIGHTWINGC_R_SOURCE", "rs_Fiver_lanuch_right_assault");
        // 轮替设置
        config.nextHullMod = "rs_Fiver_lanuch_defense_Mode";
        config.sourceHullMod = "rs_Fiver_lanuch_assault_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5400;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_Fiver_lanuch");

        config = new hullModComfig("rs_Fiver_lanuch_defense_Mode");
        HULL_MOD_CONFIGS.put("rs_Fiver_lanuch_defense_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_Tr_5_fiver");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("LEFTWINGC_L_SOURCE", "rs_Fiver_lanuch_left_defense");
        config.replaceSlots.put("RIGHTWINGC_R_SOURCE", "rs_Fiver_lanuch_right_defense");
        // 轮替设置
        config.nextHullMod = "rs_Fiver_lanuch_firesupport_Mode";
        config.sourceHullMod = "rs_Fiver_lanuch_assault_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5500;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_Fiver_lanuch");

        config = new hullModComfig("rs_Fiver_lanuch_firesupport_Mode");
        HULL_MOD_CONFIGS.put("rs_Fiver_lanuch_firesupport_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_Tr_5_fiver");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("LEFTWINGC_L_SOURCE", "rs_Fiver_lanuch_left_firesupport");
        config.replaceSlots.put("RIGHTWINGC_R_SOURCE", "rs_Fiver_lanuch_right_firesupport");
        // 轮替设置
        config.nextHullMod = "rs_Fiver_lanuch_assault_Mode";
        config.sourceHullMod = "rs_Fiver_lanuch_assault_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5600;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_Fiver_lanuch");

        config = new hullModComfig("rs_dsintegratorbean_L_Mode");
        HULL_MOD_CONFIGS.put("rs_dsintegratorbean_L_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_fidelitas");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("MAINGUN", "rs_dsintegratorbean_L");
        // 轮替设置
        config.nextHullMod = "rs_euthystopos_b_built_Mode";
        config.sourceHullMod = "rs_dsintegratorbean_L_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5700;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_fidelitas_gun");

        config = new hullModComfig("rs_euthystopos_b_built_Mode");
        HULL_MOD_CONFIGS.put("rs_euthystopos_b_built_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_fidelitas");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("MAINGUN", "rs_euthystopos_b_built");
        // 轮替设置
        config.nextHullMod = "rs_dsintegratorbean_L_Mode";
        config.sourceHullMod = "rs_dsintegratorbean_L_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5800;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_fidelitas_gun");

        config = new hullModComfig("rs_Inle_lanuch_assault_Mode");
        HULL_MOD_CONFIGS.put("rs_Inle_lanuch_assault_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_Tr_6_Inle");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("LEFTWINGC_L_SOURCE", "rs_Fiver_lanuch_left_assault");
        config.replaceSlots.put("RIGHTWINGC_R_SOURCE", "rs_Fiver_lanuch_right_assault");
        // 轮替设置
        config.nextHullMod = "rs_Inle_lanuch_defense_Mode";
        config.sourceHullMod = "rs_Inle_lanuch_assault_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 5900;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_Inle_lanuch");

        config = new hullModComfig("rs_Inle_lanuch_defense_Mode");
        HULL_MOD_CONFIGS.put("rs_Inle_lanuch_defense_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_Tr_6_Inle");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("LEFTWINGC_L_SOURCE", "rs_Fiver_lanuch_left_defense");
        config.replaceSlots.put("RIGHTWINGC_R_SOURCE", "rs_Fiver_lanuch_right_defense");
        // 轮替设置
        config.nextHullMod = "rs_Inle_lanuch_firesupport_Mode";
        config.sourceHullMod = "rs_Inle_lanuch_assault_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 6000;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_Inle_lanuch");

        config = new hullModComfig("rs_Inle_lanuch_firesupport_Mode");
        HULL_MOD_CONFIGS.put("rs_Inle_lanuch_firesupport_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_Tr_6_Inle");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("LEFTWINGC_L_SOURCE", "rs_Fiver_lanuch_left_firesupport");
        config.replaceSlots.put("RIGHTWINGC_R_SOURCE", "rs_Fiver_lanuch_right_firesupport");
        // 轮替设置
        config.nextHullMod = "rs_Inle_lanuch_assault_Mode";
        config.sourceHullMod = "rs_Inle_lanuch_assault_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 6100;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_Inle_lanuch");

        config = new hullModComfig("rs_AMS_06_beambazooka_brust_Mode");
        HULL_MOD_CONFIGS.put("rs_AMS_06_beambazooka_brust_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_Tr_4_rozet");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("ARM_R_FOLLOW", "rs_AMS_06_beambazooka_brust");
        // 轮替设置
        config.nextHullMod = "rs_AMS_06_beambazooka_charge_Mode";
        config.sourceHullMod = "rs_AMS_06_beambazooka_brust_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 6200;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_AMS_06_beambazooka");

        config = new hullModComfig("rs_AMS_06_beambazooka_charge_Mode");
        HULL_MOD_CONFIGS.put("rs_AMS_06_beambazooka_charge_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("rs_Tr_4_rozet");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("ARM_R_FOLLOW", "rs_AMS_06_beambazooka_charge");
        // 轮替设置
        config.nextHullMod = "rs_AMS_06_beambazooka_brust_Mode";
        config.sourceHullMod = "rs_AMS_06_beambazooka_brust_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 6300;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("rs_AMS_06_beambazooka");

        config = new hullModComfig("vow_VMS_12_arm_right_antiship_Mode");
        HULL_MOD_CONFIGS.put("vow_VMS_12_arm_right_antiship_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("vow_VMS_12");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("ARM_R_SOURCE", "vow_VMS_12_arm_right_antiship");
        // 轮替设置
        config.nextHullMod = "vow_VMS_12_arm_right_magena_Mode";
        config.sourceHullMod = "vow_VMS_12_arm_right_antiship_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 6400;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("vow_VMS_12_arm_right");

        config = new hullModComfig("vow_VMS_12_arm_right_magena_Mode");
        HULL_MOD_CONFIGS.put("vow_VMS_12_arm_right_magena_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("vow_VMS_12");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("ARM_R_SOURCE", "vow_VMS_12_arm_right_magena");
        // 轮替设置
        config.nextHullMod = "vow_VMS_12_arm_right_machinegun_Mode";
        config.sourceHullMod = "vow_VMS_12_arm_right_antiship_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 6500;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("vow_VMS_12_arm_right");

        config = new hullModComfig("vow_VMS_12_arm_right_machinegun_Mode");
        HULL_MOD_CONFIGS.put("vow_VMS_12_arm_right_machinegun_Mode", config);
        // 限制只能安装在MSN-04-2舰船上
        config.hulls.add("vow_VMS_12");
        // 武器槽配置：米加粒子枪模式（仅管理手臂武器）
        config.replaceSlots.put("ARM_R_SOURCE", "vow_VMS_12_arm_right_machinegun");
        // 轮替设置
        config.nextHullMod = "vow_VMS_12_arm_right_antiship_Mode";
        config.sourceHullMod = "vow_VMS_12_arm_right_antiship_Mode";
        // 显示控制：MSN-04-2手臂武器组，显示顺序3000-3099
        config.displaySortOrder = 6600;
        config.displayCategoryIndex = 2; // 舰船系统类
        // 武器清理：管理手臂武器相关的武器ID
        config.managedWeaponPrefixes.add("vow_VMS_12_arm_right");

    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (HULL_MOD_CONFIGS.containsKey(spec.getId())) {
            HULL_MOD_CONFIGS.get(spec.getId()).applyEffectsAfterShipCreation(ship, id);
        }
    }

    @Override
    public int getDisplaySortOrder() {
        if (HULL_MOD_CONFIGS.containsKey(spec.getId())) {
            return HULL_MOD_CONFIGS.get(spec.getId()).displaySortOrder;
        }
        return super.getDisplaySortOrder();
    }

    @Override
    public int getDisplayCategoryIndex() {
        if (HULL_MOD_CONFIGS.containsKey(spec.getId())) {
            return HULL_MOD_CONFIGS.get(spec.getId()).displayCategoryIndex;
        }
        return super.getDisplayCategoryIndex();
    }

    public static class hullModComfig {
        private final Set<String> conflictHullMods = new HashSet<>();
        private final Set<String> hulls = new HashSet<>();
        private String nextHullMod = null;
        private String sourceHullMod = null;
        private final Map<String, String> replaceSlots = new HashMap<>();
        private final String modId;

        // 新增：显示控制属性
        private int displaySortOrder = 2000; // 默认显示顺序
        private int displayCategoryIndex = 2; // 默认舰船系统分类

        // 新增：武器清理管理
        private final Set<String> managedWeaponPrefixes = new HashSet<>(); // 管理的武器ID前缀

        private hullModComfig(String modId) {
            this.modId = modId;
        }

        public String getNextHullmod() {
            return nextHullMod;
        }

        public String getSourceHullMod() {
            return sourceHullMod;
        }

        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            applySlotReplace(ship);
            // 新增：应用武器清理逻辑
            cleanupManagedWeapons(ship);
        }

        public void applySlotReplace(ShipAPI ship) {
            ShipVariantAPI variant = ship.getVariant();
            for (String slotID : replaceSlots.keySet()) {
                if (variant.getSlot(slotID) == null) {
                    continue;
                }
                variant.clearSlot(slotID);
                String weaponId = replaceSlots.get(slotID);
                if (weaponId != null && !weaponId.isEmpty() && Global.getSettings().getWeaponSpec(weaponId) != null) {
                    variant.addWeapon(slotID, weaponId);
                }
            }
        }

        /**
         * TpcWeaponSwap的仓库管理
         */
        private void cleanupManagedWeapons(ShipAPI ship) {
            if (ship.getOriginalOwner() < 0) {  // 仅处理玩家舰船
                // 确保玩家舰队货物存在
                if (Global.getSector() != null
                        && Global.getSector().getPlayerFleet() != null
                        && Global.getSector().getPlayerFleet().getCargo() != null) {

                    // 遍历所有货物
                    for (CargoStackAPI stack : Global.getSector().getPlayerFleet().getCargo().getStacksCopy()) {
                        if (stack.isWeaponStack()) {
                            String weaponId = stack.getWeaponSpecIfWeapon().getWeaponId();
                            // 检查是否是当前配置管理的武器类型
                            for (String prefix : managedWeaponPrefixes) {
                                if (weaponId.startsWith(prefix)) {
                                    // 移除被轮替的武器（防止重复）
                                    Global.getSector().getPlayerFleet().getCargo().removeStack(stack);
                                    Global.getLogger(this.getClass()).info(
                                            "武器切换系统：清理玩家仓库中的轮替武器 " + weaponId +
                                                    " (配置: " + modId + ")");
                                    break; // 找到匹配的前缀后跳出内层循环
                                }
                            }
                        }
                    }
                }
            }
        }

        public boolean isApplicableToShip(ShipVariantAPI variant) {
            String hullID = variant.getHullSpec().getBaseHullId();
            if (!hulls.isEmpty() && !hulls.contains(hullID)) {
                return false;
            }
            for (String mod : variant.getHullMods()) {
                if (conflictHullMods.contains(mod)) {
                    return false;
                }
            }
            return true;
        }
    }

}