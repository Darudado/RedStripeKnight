package data.hullmods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import data.scripts.Moci_RS_CollisionStateManager;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;
import data.scripts.util.Moci_TextLoader;
import data.scripts.util.Moci_AIRestoreUtil;

/**
 * Moci_RepairBayScript - 修理舱核心系统
 * 
 * 实现AdvanceableListener接口，持续监听和处理修理过程
 * 为航母舰船提供完整的修理舱管理功能
 * 
 * 核心功能：
 * - 多修理舱管理：支持一艘舰船拥有多个修理舱
 * - 动态修理时间计算：基于损伤程度智能计算修理时间
 * - 渐进式修理过程：使用FaderUtil实现平滑的修理进度
 * - 完整状态恢复：CR、血量、弹药、装甲全面修复
 * - 队列系统：WeightedRandomPicker随机分配空闲修理舱
 * 
 * 修理时间算法：
 * - 基础时间：2秒
 * - CR修复：0.2 * (目标CR - 当前CR) * 100 秒
 * - 弹药补给：每个空弹药武器 +4秒
 * - 血量修复：0.1 * (1 - 当前血量) * 100 秒
 * 
 * 技术特点：
 * - 基于武器标签"Moci_RepairBay"自动识别修理舱
 * - 修理期间完全控制被修理舰船的状态
 * - 支持玩家舰船的实时进度显示
 * - 异常情况自动清理和状态恢复
 */
public class Moci_RS_RepairBayScript implements AdvanceableListener {
    private static final boolean ENABLE_DEBUG_LOGS = false;
    private static final String TEXT_ID = "Moci_RepairBayScript";
    private static final String LOG_PREFIX = "[MOCI_REPAIR] ";

    private List<Moci_RepairBay> repairBays = new ArrayList<>();               // 修理舱武器列表
    private Map<Moci_RepairBay,Moci_RepairBayStatus> repairBaysStatus = new HashMap<>();  // 修理舱状态映射
    private final ShipAPI ship;  // 拥有修理舱的母舰

    // 修理时间常量配置
    public static final float _CR_REPAIR_TIME = 0.2f;      // CR修复时间系数
    public static final float _WEAPON_AMMO_FEED = 4f;      // 武器弹药补给时间
    public static final float _HULL_REPAIR_TIME = 0.1f;    // 船体修复时间系数
    public static final float _REPLACEMENT_LOSS_PER_SEC = 0.02f;//2%

    public static Map<WeaponAPI.WeaponSize,Float> _WEAPON_AMMO_FEED_TIME = new HashMap<>();
    static {
        _WEAPON_AMMO_FEED_TIME.put(WeaponAPI.WeaponSize.SMALL,2f);
        _WEAPON_AMMO_FEED_TIME.put(WeaponAPI.WeaponSize.MEDIUM,7.5f);
        _WEAPON_AMMO_FEED_TIME.put(WeaponAPI.WeaponSize.LARGE,15f);
    }

    public static class Moci_RepairBay{
        private WeaponAPI bay = null;
        private WeaponSlotAPI slot;
        private ShipAPI ship;

        public Moci_RepairBay(WeaponAPI weapon){
            this.bay = weapon;
            this.ship = weapon.getShip();
        }

        public Moci_RepairBay(WeaponSlotAPI slot,ShipAPI ship){
            this.slot = slot;
            this.ship = ship;
        }

        public Vector2f getLocation(){
            return (bay!=null)?bay.getLocation():slot.computePosition(ship);
        }

        public float getFacing(){
            return (bay!=null)?bay.getCurrAngle():slot.computeMidArcAngle(ship);
        }

        public WeaponAPI getBay() {
            return bay;
        }

        public WeaponSlotAPI getSlot() {
            return slot;
        }

        public ShipAPI getShip() {
            return ship;
        }

        public String getSlotId() {
            if (bay != null && bay.getSlot() != null) {
                return bay.getSlot().getId();
            }
            return slot != null ? slot.getId() : null;
        }
    }

    /**
     * 修理舱状态管理类
     * 负责单个修理舱的状态跟踪和修理过程控制
     */
    public static class Moci_RepairBayStatus{
        protected boolean isOccupied = false;              // 是否被占用
        protected FaderUtil repairSequence = new FaderUtil(0f,10f);  // 修理进度控制器
        protected ShipAPI rs = null;                       // 正在修理的舰船
        protected float baseHP = 1,baseCR = 1;            // 修理开始时的基础数值
        protected Moci_RepairBay bay;
        protected ShipAPI takeOff = null;
        /**
         * 构造函数
         * @param bay 修理舱武器槽
         */
        public Moci_RepairBayStatus(WeaponAPI bay){
            this.bay = new Moci_RepairBay(bay);
        }

        public Moci_RepairBayStatus(WeaponSlotAPI slot,ShipAPI source){
            this.bay = new Moci_RepairBay(slot,source);
        }

        public Moci_RepairBayStatus(Moci_RepairBay bay){
            this.bay = bay;
        }

        /**
         * 检查修理舱是否被占用
         */
        public boolean isOccupied(){
            return isOccupied;
        }

        /**
         * 设置修理舱占用状态
         */
        public void setOccupied(boolean occupied) {
            isOccupied = occupied;
        }

        /**
         * 获取修理进度控制器
         */
        public FaderUtil getRepairSequence() {
            return repairSequence;
        }

        /**
         * 开始修理流程
         * 初始化修理参数，设置被修理舰船的状态
         * 
         * @param rs 要修理的舰船
         */
        public void startRepair(ShipAPI rs){
            this.rs = rs;
            setOccupied(true);  // 标记修理舱为占用状态
            RS_Moci_MobileSuitRepairTracker.getOrCreate(rs).beginRepair(bay.getShip(), bay);
            Moci_SMALandingSequence.recordLandingMemory(rs, bay);
            rs.setCustomData("Moci_LaunchAnimStarted", null);
            logRepairEvent(rs, "startRepair() starts. carrier=" + (bay.getShip() != null ? bay.getShip().getName() : "null")
                    + ", slot=" + bay.getSlotId()
                    + ", isLanding=" + rs.isLanding()
                    + ", isFinishedLanding=" + rs.isFinishedLanding());
            
            // 计算动态修理时间
            float repairTime = computeRepairTime(rs);
            
            // === 修理速度加成检查 ===
            // 检查修理舱所属母舰是否装备了以下船插之一：
            // - Moci_ms_repair_bay (MS专用整备库)
            // - HugeOpenPort (大型开放式机库)
            // - OpenPort (开放式机库)
            // 拥有其中之一即可获得修理速度翻倍效果（不叠加）
            if (bay.getShip() != null && bay.getShip().getVariant() != null) {
                if (bay.getShip().getVariant().getHullMods().contains("Moci_ms_repair_bay") ||
                    bay.getShip().getVariant().getHullMods().contains("HugeOpenPort") ||
                    bay.getShip().getVariant().getHullMods().contains("OpenPort")) {
                    repairTime *= 0.5f;  // 修理速度翻倍（时间减半）
                }
            }
            
            repairSequence = new FaderUtil(0f,repairTime);
            repairSequence.fadeIn();  // 开始修理进度
            
            // 记录修理开始时的基础数值
            baseCR = rs.getCurrentCR();
            baseHP = rs.getHullLevel();
            
            // 设置被修理舰船的状态
            rs.setCollisionClass(CollisionClass.NONE);  // 禁用碰撞
            rs.setPhased(true);  // 设为相位状态
        }

        /**
         * 强制完成修理
         * 恢复被修理舰船的正常状态，清理修理舱
         */
        public void forceFinishRepair(){
            isOccupied = false;  // 释放修理舱
            
            // 记录当前自动驾驶状态以便后续恢复
            final ShipAIPlugin originalAI = rs.getShipAI();
            ShipAIConfig restoreConfig = originalAI != null ? originalAI.getConfig() : null;
            RS_Moci_MobileSuitRepairTracker tracker = RS_Moci_MobileSuitRepairTracker.getOrCreate(rs);
            tracker.beginTakeoff(bay.getShip(), bay);
            logRepairEvent(rs, "forceFinishRepair() is executed. carrier="
                    + (bay.getShip() != null ? bay.getShip().getName() : "null")
                    + ", slot=" + bay.getSlotId());
            
            // 恢复舰船控制
            this.rs.setControlsLocked(false);     // 解锁控制
            this.rs.setDefenseDisabled(false);    // 启用防御
            this.rs.setShipSystemDisabled(false); // 启用舰船系统
            
            // 清除玩家的目标选择（如果是玩家舰船）
            if(this.rs.getShipTarget()!=null&& rs.getShipTarget().getOwner()==rs.getOwner()&&rs.equals(Global.getCombatEngine().getPlayerShip())){
                rs.setShipTarget(null);
            }
            
            // 恢复物理状态
            rs.setPhased(false);  // 取消相位状态
            
            // ✅ 关键修复：使用碰撞管理器移除着陆AI的碰撞修改器
            // 这样会自动恢复到默认碰撞状态（SHIP或FIGHTER，取决于舰船类型）
            // 而不是直接设置CollisionClass.FIGHTER（会绕过管理器）
            Moci_RS_CollisionStateManager collisionManager = Moci_RS_CollisionStateManager.getInstance();
            collisionManager.removeCollisionModifier(rs, "Moci_LandingAI");

                
                // 不再启用巡航推进，改为直接设置不开火延迟
                if (originalAI != null) {
                    originalAI.setDoNotFireDelay(1.5f);  // 设置1.5秒不开火延迟
                }
                
                // 设置起飞动画 - 只有在没有Moci_LimitedLanding标签的武器时才播放起飞动画
                // 有Moci_LimitedLanding标签的武器，机甲应该直接弹出，不需要播放动画
                boolean shouldPlayAnimation = Moci_SMALandingSequence.bayShouldHidden(bay);
                
                if (Global.getSettings().isDevMode()) {
                        Global.getLogger(this.getClass()).info(rs.getName() + "Takeoff check - animation should play:" + shouldPlayAnimation + 
                            "- Has weapons:" + (bay.getBay() != null ? "Yes" : "No") + 
                            "- Weapon ID:" + (bay.getBay() != null ? bay.getBay().getSpec().getWeaponId() : "None") +
                        "- Weapon tags:" + (bay.getBay() != null && bay.getBay().getSpec() != null ? 
                            (bay.getBay().getSpec().hasTag("Moci_LimitedLanding") ? "Has Moci_LimitedLanding tag" : "No Moci_LimitedLanding tag") : "no label"));
                }

                if(shouldPlayAnimation){
                    rs.setInvalidTransferCommandTarget(false);
                    rs.setAnimatedLaunch();
                    rs.setCustomData("Moci_LaunchAnimStarted", true);
                    Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, rs.getLocation(), new Vector2f());
                    rs.addListener(new Moci_AnimationLaunch(rs));
                    for (ShipAPI module : rs.getChildModulesCopy()) {
                        if (module == null) continue;
                        module.setAnimatedLaunch();
                        if (module.getVariant() != null && module.getVariant().hasHullMod("Moci_module_collision_controller")) {
                            RS_Moci_ModuleCollisionSync.endRepairingHiding(module);
                        }
                    }
                    logRepairEvent(rs, "forceFinishRepair() has explicitly written native takeoff animation markers.");
                }
                    // 只有在应该播放动画的情况下才设置起飞动画
                    // 有武器的情况下，直接给一个额外的推力，模拟弹出效果
                    float pushForce = 150f; // 增加推力
                    Vector2f pushDirection = new Vector2f(
                        (float)Math.cos(Math.toRadians(bay.getFacing())),
                        (float)Math.sin(Math.toRadians(bay.getFacing()))
                    );
                    rs.getVelocity().set(pushDirection.x * pushForce, pushDirection.y * pushForce);
                    
                    if (Global.getSettings().isDevMode()) {
                        Global.getLogger(this.getClass()).info(rs.getName() + "Use pop-up takeoff" + 
                            (rs.getCustomData().get("Moci_LaunchAnimStarted") != null ? "(The animation has already started)" : ""));
                    }
            
            // 清除所有自定义数据，确保不会影响后续操作
            // 注意：只清除与着陆相关的数据，保留起飞动画标记和自动驾驶恢复标记
            cleanupCustomData(rs);
            tracker.markTakingOff();
            Moci_AIRestoreUtil.restoreDefaultAI(rs, restoreConfig);
            tracker.markCompleted();
            logRepairEvent(rs, "forceFinishRepair() Completed, restored AI and marked takeoff complete.");
            
            // 清除引用
            this.rs = null;
        }
        
        /**
         * 清理所有与着陆/起飞相关的自定义数据
         * 确保在多次进入自动驾驶时不会有残留数据影响逻辑
         * 
         * @param ship 要清理数据的舰船
         */
        private void cleanupCustomData(ShipAPI ship) {
            // 不清除起飞动画标记和自动驾驶恢复标记
            // ship.setCustomData("Moci_LaunchAnimStarted", null);
            
            // 清除着陆AI活跃标记
            ship.setCustomData("Moci_LandingAIActive", false);
            // 清除位置记录和卡住计时器
            ship.setCustomData("Moci_LastLandingPosition", null);
            ship.setCustomData("Moci_StuckTimer", null);
            
            // 确保巡航加速关闭
            ship.turnOffTravelDrive();
            
            if (Global.getSettings().isDevMode()) {
                Global.getLogger(this.getClass()).info(ship.getName() + "Clean custom data related to landing, retain takeoff animation and autopilot recovery flags");
            }
        }

        /**
         * 修理状态推进方法
         * 每帧调用，处理修理进度和舰船状态更新
         */
        public void advance(float amount){
            if(isOccupied){
                repairSequence.advance(amount);  // 推进修理进度
                
                // 根据修理进度动态更新舰船状态
                // 血量线性恢复：从基础血量恢复到满血量
                rs.setHitpoints(Math.min(rs.getMaxHitpoints(), rs.getMaxHitpoints()*(baseHP+((repairSequence.getBrightness()*(1f-baseHP))))));
                // CR线性恢复：从基础CR恢复到部署CR
                rs.setCurrentCR(Math.min(rs.getCRAtDeployment(),baseCR+(repairSequence.getBrightness()*(rs.getCRAtDeployment()-baseCR))));

                if(bay.getBay()==null){
                    for(FighterLaunchBayAPI b:bay.getShip().getLaunchBaysCopy()){
                        if(b.getWeaponSlot().equals(bay.getSlot())){
                            b.setCurrRate(Math.max(0.3f,b.getCurrRate()-_REPLACEMENT_LOSS_PER_SEC*amount));
                        }
                    }
                }
                
                // 位置和朝向与修理舱保持同步
                
                rs.getLocation().set(bay.getLocation());

                if(Moci_SMALandingSequence.bayShouldHidden(bay)&&rs.getExtraAlphaMult2()>0) {

                }else {
                    rs.setFacing(bay.getFacing());
                }

                if(takeOff!=null){
                    if(!takeOff.hasListenerOfClass(Moci_AnimationLaunch.class)){
                        takeOff.addListener(new Moci_AnimationLaunch(takeOff));
                    }else{
                        takeOff = null;
                    }
                }
                
                
                if(repairSequence.isFadedIn()){
                    logRepairEvent(rs, "The repairSequence is completed and ready to be finished.");
                    // === 修理完成，进行最终处理 ===
                    
                    // 恢复主舰体所有武器弹药
                    for(WeaponAPI weapon:rs.getAllWeapons()){
                        if(weapon.isDecorative()) continue;  // 跳过装饰性武器
                        if(weapon.usesAmmo()&&weapon.getAmmo()<weapon.getMaxAmmo()){
                            weapon.setAmmo(weapon.getMaxAmmo());  // 填满弹药
                        }
                    }
                    
                    // 恢复子模块武器弹药（如果有模块化舰船）
                    if(rs.isShipWithModules()&&!rs.getChildModulesCopy().isEmpty()){
                        for(ShipAPI module:rs.getChildModulesCopy()){
                            for(WeaponAPI weapon:module.getAllWeapons()){
                                if(weapon.isDecorative()) continue;
                                if(weapon.usesAmmo()&&weapon.getAmmo()<weapon.getMaxAmmo()){
                                    weapon.setAmmo(weapon.getMaxAmmo());
                                }
                            }
                        }
                    }
                    
                    // 恢复峰值时间（Peak Performance Time）
                    // 如果有效峰值时间小于已部署时间，则增加峰值时间以补偿
                    if (rs.getMutableStats().getPeakCRDuration().computeEffective(0f) < rs.getTimeDeployedForCRReduction()) {
                        rs.getMutableStats().getPeakCRDuration().modifyFlat(rs.getId(), rs.getTimeDeployedForCRReduction());
                    }
                    
                    repairArmor();       // 修复装甲
                    forceFinishRepair(); // 完成修理流程
                }else{
                    // === 修理进行中，锁定舰船状态 ===
                    this.rs.setControlsLocked(true);      // 锁定控制
                    this.rs.setDefenseDisabled(true);     // 禁用防御
                    this.rs.setShipSystemDisabled(true);  // 禁用舰船系统
                }
                
                // 为玩家舰船显示修理进度
                if(this.rs!=null&&this.rs.equals(Global.getCombatEngine().getPlayerShip())) {
                    Global.getCombatEngine().maintainStatusForPlayerShip("Moci_Repair", Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck"),
                            Moci_TextLoader.getText(TEXT_ID, "status.title"),
                            Moci_TextLoader.getTextWithReplacements(TEXT_ID, "status.repairing",
                                    Moci_TextLoader.mapOf("seconds", String.format("%.1f",(1f-repairSequence.getBrightness())*repairSequence.getDurationIn()))), true);
                }
            }
        }

        /**
         * 修复装甲
         * 将所有装甲格恢复到最大值
         */
        public void repairArmor(){
            ArmorGridAPI a = rs.getArmorGrid();
            if (a == null)
                return;
            float[][] cag = a.getGrid();
            if (cag.length == 0)
                return;
            // 遍历所有装甲格，恢复到最大装甲值
            for (int i = 0; i < cag.length; i++) {
                for (int j = 0; j < cag[i].length; j++) {
                    a.setArmorValue(i, j, a.getMaxArmorInCell());
                }
            }
        }
    }

    /**
     * 计算舰船的修理时间
     * 基于舰船的损伤情况动态计算所需的修理时间
     * 
     * @param ship 要修理的舰船
     * @return 计算出的修理时间（秒）
     */
    public static float computeRepairTime(ShipAPI ship){
        float time = 2f;  // 基础修理时间2秒
        
        // CR修复时间计算
        if(ship.getCurrentCR()<ship.getCRAtDeployment()){
            time += _CR_REPAIR_TIME*(MathUtils.clamp(ship.getCRAtDeployment()-ship.getCurrentCR()*100f,0,100));
        }
        
        // 主舰体武器弹药补给时间
        for(WeaponAPI weapon:ship.getAllWeapons()){
            if(weapon.isDecorative()) continue;
            if(weapon.usesAmmo()&&weapon.getAmmo()<weapon.getMaxAmmo()){
                time+=_WEAPON_AMMO_FEED_TIME.get(weapon.getSize()); // 每个需要补给的武器增加2/7.5/15秒
            }
        }
        
        // 子模块武器弹药补给时间
        if(ship.isShipWithModules()&&!ship.getChildModulesCopy().isEmpty()){
            for(ShipAPI module:ship.getChildModulesCopy()){
                for(WeaponAPI weapon:module.getAllWeapons()){
                    if(weapon.isDecorative()) continue;
                    if(weapon.usesAmmo()&&weapon.getAmmo()<weapon.getMaxAmmo()){
                        time+=_WEAPON_AMMO_FEED_TIME.get(weapon.getSize());
                    }
                }
            }
        }
        
        // 船体修复时间计算
        if(ship.getHullLevel()<1){
            time+=_HULL_REPAIR_TIME*(MathUtils.clamp((1f-ship.getHullLevel())*100,0,100));
        }
        
        return time;
    }

    /**
     * 构造函数
     * 初始化修理舱系统，扫描母舰的所有修理舱
     * 
     * @param ship 拥有修理舱的母舰
     */
    public Moci_RS_RepairBayScript(ShipAPI ship){
        this.ship = ship;
        
        // 扫描所有武器，找到修理舱
        for(WeaponAPI weapon:ship.getAllWeapons()){
            if(weapon.getSpec().hasTag("Moci_RepairBay")||weapon.getSpec().getWeaponId().equals("Moci_RepairBayBeacon")){
                Moci_RepairBay bay = new Moci_RepairBay(weapon);
                repairBays.add(bay);  // 添加到修理舱列表
                repairBaysStatus.put(bay,new Moci_RepairBayStatus(bay));  // 创建状态跟踪
            }
        }

        if(repairBays.isEmpty()){
            int numDecks = ship.getNumFighterBays();
            for(String slotId:ship.getVariant().getLaunchBaysSlotIds()){
                if(numDecks>0){
                    WeaponSlotAPI slot = ship.getVariant().getSlot(slotId);
                    if(slot!=null) {
                        Moci_RepairBay bay = new Moci_RepairBay(slot,ship);
                        repairBays.add(bay);
                        repairBaysStatus.put(bay,new Moci_RepairBayStatus(bay));
                        numDecks--;
                    }
                }
            }
        }
        
        // 将脚本实例保存到舰船自定义数据中
        ship.setCustomData("Moci_RepairBayScript",this);
    }

    /**
     * 主推进方法
     * 每帧调用，更新所有修理舱的状态
     */
    public void advance(float amount){
        boolean shouldForceFinishRepair = !ship.isAlive() || ship.isHulk();  // 母舰死亡时强制完成所有修理
        boolean shouldRemoveListener = shouldForceFinishRepair;
        // 更新所有修理舱状态
        for(Moci_RepairBay bay:repairBays){
            repairBaysStatus.get(bay).advance(amount);
            if(repairBaysStatus.get(bay).takeOff!=null) shouldRemoveListener = false;
            // 如果母舰死亡，强制完成修理以释放被修理的舰船
            if(shouldForceFinishRepair&&repairBaysStatus.get(bay).isOccupied()){
                if (repairBaysStatus.get(bay).rs != null) {
                    RS_Moci_MobileSuitRepairTracker.getOrCreate(repairBaysStatus.get(bay).rs).markEmergencyRelease();
                }
                repairBaysStatus.get(bay).forceFinishRepair();
            }

        }
        
        // 母舰死亡时清理脚本
        if(shouldRemoveListener){
            ship.removeListener(this);  // 移除监听器
            ship.setCustomData("Moci_RepairBayScript",null);  // 清除自定义数据
            return;
        }
    }

    /**
     * 检查是否有空闲的修理舱
     * 
     * @return 如果有空闲修理舱返回true
     */
    public boolean hasVacancy(){
        return getVacancy()!=null;
    }

    /**
     * 获取一个空闲的修理舱
     * 使用加权随机选择器从所有空闲修理舱中随机选择一个
     * 
     * @return 空闲的修理舱武器，如果没有空闲的则返回null
     */
    public Moci_RepairBay getVacancy(){
        return getVacancy(null);
    }

    public Moci_RepairBay getVacancy(String preferredSlotId){
        WeightedRandomPicker<Moci_RepairBay> baysToChoose = new WeightedRandomPicker<>();
        Moci_RepairBay preferredBay = null;
        
        // 收集所有空闲的修理舱
        for(Moci_RepairBay bay:repairBays){
            if(!repairBaysStatus.get(bay).isOccupied()){
                if (preferredSlotId != null && preferredSlotId.equals(bay.getSlotId())) {
                    preferredBay = bay;
                }
                baysToChoose.add(bay);
            }
        }
        
        if(baysToChoose.isEmpty()){
            return null;  // 没有空闲修理舱
        }else{
            if (preferredBay != null) {
                return preferredBay;
            }
            return baysToChoose.pick();  // 随机选择一个空闲修理舱
        }
    }

    /**
     * 获取指定舰船的修理舱脚本实例
     * 从舰船的自定义数据中提取修理舱脚本
     * 
     * @param ship 目标舰船
     * @return 修理舱脚本实例，如果没有则返回null
     */
    @Nullable
    public static Moci_RS_RepairBayScript getInstance(ShipAPI ship){
        Moci_RS_RepairBayScript script = null;
        if(ship.getCustomData()!=null&& ship.getCustomData().containsKey("Moci_RepairBayScript")&&ship.getCustomData().get("Moci_RepairBayScript") instanceof Moci_RS_RepairBayScript){
            script = (Moci_RS_RepairBayScript) ship.getCustomData().get("Moci_RepairBayScript");
        }
        return script;
    }

    /**
     * 获取指定修理舱的状态
     * 
     * @param bay 修理舱武器
     * @return 修理舱状态对象，如果不存在则返回null
     */
    public Moci_RepairBayStatus getBay(Moci_RepairBay bay){
        if(repairBaysStatus.containsKey(bay)){
            return repairBaysStatus.get(bay);
        }else{
            return null;
        }
    }



    public static class Moci_AnimationLaunch implements AdvanceableListener{

        private ShipAPI ship;
        private FaderUtil fader = new FaderUtil(0,1f);

        public Moci_AnimationLaunch(ShipAPI ship){
            this.ship = ship;
            fader.fadeIn();
        }

        @Override
        public void advance(float v) {
            fader.advance(v);
            for(ShipAPI module:ship.getChildModulesCopy()){
                module.setExtraAlphaMult2(fader.getBrightness());
            }
            ship.setExtraAlphaMult2(fader.getBrightness());
            if(fader.isFadedIn()){
                logAnimationEvent(ship, "[MOCI_ANIM] Takeoff animation fade-in completed.");
                ship.removeListener(this);
            }
        }
    }

    public static class Moci_AnimationLanding implements AdvanceableListener{

        private ShipAPI ship;
        private FaderUtil fader = new FaderUtil(1,1f);

        public Moci_AnimationLanding(ShipAPI ship){
            this.ship = ship;
            fader.fadeOut();
        }

        @Override
        public void advance(float v) {
            fader.advance(v);
            for(ShipAPI module:ship.getChildModulesCopy()){
                module.setExtraAlphaMult2(fader.getBrightness());
            }
            ship.setExtraAlphaMult2(fader.getBrightness());
            if(fader.isFadedOut()){
                logAnimationEvent(ship, "[MOCI_ANIM] Landing animation fadeout completed.");
                ship.removeListener(this);
            }
        }
    }

    private static void logRepairEvent(ShipAPI ship, String message) {
        if (!ENABLE_DEBUG_LOGS) {
            return;
        }
        if (ship == null) {
            return;
        }
        Global.getLogger(Moci_RS_RepairBayScript.class).info(LOG_PREFIX + ship.getName() + " - " + message);
    }

    private static void logAnimationEvent(ShipAPI ship, String message) {
        if (!ENABLE_DEBUG_LOGS) {
            return;
        }
        if (ship == null) {
            return;
        }
        Global.getLogger(Moci_RS_RepairBayScript.class).info(message + " ship=" + ship.getName());
    }
}
