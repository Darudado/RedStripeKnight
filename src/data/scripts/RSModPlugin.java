package data.scripts;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.FactionHostilityManager;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.loading.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import data.hullmods.crusaders.CrusadersCore;
import data.scripts.campaign.*;
import data.scripts.campaign.ids.ModIDs;
import data.scripts.world.systems.Anathema;
import data.scripts.world.systems.Eremia;
import data.scripts.world.systems.RegnumDei;


import data.scripts.world.systems.Palingenesis;
import lunalib.lunaSettings.LunaSettings;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;
import org.json.JSONObject;
import org.magiclib.util.MagicSettings;


import java.awt.*;
import java.util.*;
import java.util.List;

import static data.scripts.world.RSNormalGenerate.initFactionRelation;
import static data.scripts.world.RSNormalGenerate.relationAdj;

public class RSModPlugin extends BaseModPlugin {
	public static final String GiaoMissileWeapon = "giao_dummy_m";
	public static boolean particleEngineEnabled = false;
	private final Map<String, MakeMissilePlugin> customMissiles = new HashMap<>();
	private static final Set<String> replaceExplosionWithParticles = new HashSet<>();
	protected static boolean isNewGame = false;
	public static final boolean HAVE_LUNALIB = Global.getSettings().getModManager().isModEnabled("lunalib");
	public static final boolean HAVE_VERSION_CHECKER = Global.getSettings().getModManager().isModEnabled("lw_version_checker");

	public static final boolean HAVE_INDEVO = Global.getSettings().getModManager().isModEnabled("IndEvo");

	public static java.util.List<String> DERECHO_RESIST = new ArrayList<>();
	public static List<String> DERECHO_IMMUNE = new ArrayList<>();

	private static Boolean boxUtilAvailable = null;

	public static Boolean INDEVO_MINES = null;
	public static Boolean INDEVO_ARTY = null;


	public RSModPlugin() {

	}

	public static boolean hasGraphicsLib;

	public void syncRSMODScripts() {
		this.addScriptsIfNeeded();
	}

	public void loadLunaSettings() {
		Boolean value;
		value = LunaSettings.getBoolean("IndEvo", "IndEvo_Enable_minefields");
		INDEVO_MINES = value != null ? value : true;
		value = LunaSettings.getBoolean("IndEvo", "IndEvo_Enable_Artillery");
		INDEVO_ARTY = value != null ? value : true;
	}



	private interface MakeMissilePlugin {
		MissileAIPlugin make(MissileAPI missile);
	}

	public static boolean isBoxUtilAvailable() {
		if (boxUtilAvailable == null) {
			boxUtilAvailable = Global.getSettings().getModManager().isModEnabled("BoxUtil");
		}
		return boxUtilAvailable;
	}

	private CrusadersRaid reprisalScript;

	public void afterGameSave(){
		Global.getSector().addTransientScript(reprisalScript = new CrusadersRaid());
	}


	@Override
	public void onApplicationLoad() {
		hasGraphicsLib = Global.getSettings().getModManager().isModEnabled("shaderLib");

		if (hasGraphicsLib) {
			ShaderLib.init();
			TextureData.readTextureDataCSV("data/config/RS_texture_data.csv");
		}
		particleEngineEnabled = Global.getSettings().getModManager().isModEnabled("particleengine");
		if (particleEngineEnabled) {
			String versionString = Global.getSettings().getModManager().getModSpec("particleengine").getVersion();
			String[] version = versionString.split("\\.");
			if (Integer.parseInt(version[0]) < 1 && Integer.parseInt(version[1]) < 5) {
				throw new RuntimeException("Particle Engine is enabled but out of date. Get the latest version here: https://fractalsoftworks.com/forum/index.php?topic=26453.0.");
			}
		}
		// Populate custom missile AI
		customMissiles.clear();

		// Remove default explosions if replacing with particles
		if (particleEngineEnabled) {
			for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
				Object o = spec.getProjectileSpec();
				if (o instanceof ProjectileSpecAPI pSpec) {
                    if (replaceExplosionWithParticles.contains(pSpec.getId())) {
						pSpec.setHitGlowRadius(0f);
					}
				}
				if (o instanceof MissileSpecAPI mSpec) {
                    if (replaceExplosionWithParticles.contains(mSpec.getHullSpec().getHullId())) {
						mSpec.setUseHitGlowWhenDealingDamage(false);
						DamagingExplosionSpec eSpec = mSpec.getExplosionSpec();
						if (eSpec != null) {
							eSpec.setUseDetailedExplosion(false);
							eSpec.setExplosionColor(new Color(0, 0, 0, 0));
							eSpec.setParticleCount(0);
						}
					}
				}
			}

			//RSContactBounty.CREATORS.add(new RSContactBountyManager());

		}

		ModSpecAPI ml = Global.getSettings().getModManager().getModSpec("MagicLib");
		int minor = Integer.parseInt(ml.getVersionInfo().getMinor());
		int major = Integer.parseInt(ml.getVersionInfo().getMajor());
		if (major < 1 || (major == 1 && minor < 4))
			throw new RuntimeException("requires MagicLib version 1.4.0 or newer.");

		try {
			Global.getSettings().getScriptClassLoader().loadClass("org.dark.shaders.util.ShaderLib");
			ShaderLib.init();
			//LightData.readLightDataCSV("data/config/modFiles/diableavionics_lights.csv");
			//TextureData.readTextureDataCSV("data/config/modFiles/diableavionics_maps.csv");
		} catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }

		//modSettings loading:
		DERECHO_RESIST = MagicSettings.getList("crusaders", "missile_resist_derecho");
		DERECHO_IMMUNE = MagicSettings.getList("crusaders", "missile_immune_derecho");
		
		CrusadersCore.initShader();
	}

	protected void addScriptsIfNeeded() {
		SectorAPI sector = Global.getSector();
		if (!sector.getGenericPlugins().hasPlugin(CrusadersfleetGenPlugin.class)) {
			sector.getGenericPlugins().addPlugin(new CrusadersfleetGenPlugin(), true);
		}
	}

	public void onNewGame() {
		if(HAVE_LUNALIB){
			loadLunaSettings();
		}

			new RegnumDei().generate(Global.getSector());
		    new Eremia().generate(Global.getSector());
		    new Anathema().generate(Global.getSector());
		    new Palingenesis().generate(Global.getSector());
			initFactionRelation(Global.getSector());
			relationAdj(Global.getSector());
			new CrusadersGenPlugin(Global.getSector());

		CrusadersBaseManager baseManager = new CrusadersBaseManager();
		Global.getSector().addScript(baseManager);   // 作为全局脚本运行，自动定期生成殖民地

	}

	protected void addTransientScriptsAndListeners() {
		SectorAPI sector = Global.getSector();
		GenericPluginManagerAPI plugins = sector.getGenericPlugins();
		if (!plugins.hasPlugin(CrusadersfleetGenPlugin.class)) {
			plugins.addPlugin(new CrusadersfleetGenPlugin(), true);
		}

	}

	@Override
	public void onGameLoad(boolean newGame) {
		FactionAPI crusaders = Global.getSector().getFaction(ModIDs.CR);
		//StarSystemAPI system = this.pickSystemForCrusadersBase();
		if (crusaders == null) {
			Global.getLogger(this.getClass()).error("CRUSADERS faction missing on load!");
		}
		isNewGame = newGame;
		this.syncRSMODScripts();

		if (!newGame) {
			Global.getSector().removeTransientScript(reprisalScript);

			Global.getSector().addTransientScript(reprisalScript = new CrusadersRaid());
			// --- 修改：确保管理器存在，不存在则创建并注册 ---
			if (CrusadersBaseManager.getInstance() == null) {
				CrusadersBaseManager baseManager = new CrusadersBaseManager();
				Global.getSector().addScript(baseManager);
			}
			// 删除原先错误的 new CrusadersBaseGen();
		}

		SectorAPI sector = Global.getSector();
		addTransientScriptsAndListeners();
		sector.removeScriptsOfClass(FactionHostilityManager.class);

	}


	public void addDumbfireMirv(String weaponSpec, String projSpec) {
		MissileSpecAPI missileSpec = (MissileSpecAPI) Global.getSettings().getWeaponSpec(weaponSpec).getProjectileSpec();
		JSONObject clusterBehaviorJSON = missileSpec.getBehaviorJSON();
		final int numShots = clusterBehaviorJSON.optInt("spawnCount", 0);
		final float timeToSplit = (float) clusterBehaviorJSON.optDouble("timeToSplit", 0);
		final float splitArc = (float) clusterBehaviorJSON.optDouble("arc", 0);
		final boolean evenSpread = clusterBehaviorJSON.optBoolean("evenSpread", false);
		final String spawnSpec = clusterBehaviorJSON.optString("spawnSpec", "");
		MakeMissilePlugin put = customMissiles.put(projSpec, missile -> new DumbfireTimedMirv(
                missile,
                spawnSpec,
                numShots,
                timeToSplit,
                splitArc,
                evenSpread
        ));
	}



	public static class DumbfireTimedMirv implements MissileAIPlugin {

		private static final int smokeCount = 5;
		private static final Color smokeColor = new Color(100, 100, 100, 200);
		private static final float speedVariance = 0.25f;
        private final MissileAPI missile;
        private float elapsed = 0f;


		public DumbfireTimedMirv(
				MissileAPI missile,
				String weaponName,
				int numShots,
				float timeToSplit,
				float splitArc,
				boolean evenSpread) {
			this.missile = missile;
            float initialFacing = missile.getFacing();
        }


		@Override
		public void advance(float amount) {

		}
	}

	public static void replaceSubmarket(MarketAPI market, String submarketId) {
		if (!market.hasSubmarket(submarketId)) return;

		CargoAPI current = market.getSubmarket(submarketId).getCargo();
		FleetDataAPI ships = current.getMothballedShips();
		boolean haveAccess = Misc.playerHasStorageAccess(market);

		market.removeSubmarket(submarketId);
		market.addSubmarket(submarketId);
		SubmarketAPI submarket = market.getSubmarket(submarketId);

		// migrate cargo
		CargoAPI newCargo = market.getSubmarket(submarketId).getCargo();
		newCargo.clear();
		newCargo.addAll(current);
		newCargo.sort();

		// move ships to new cargo
		newCargo.initMothballedShips(submarket.getFaction().getId());
		for (FleetMemberAPI ship : ships.getMembersListCopy()) {
			newCargo.getMothballedShips().addFleetMember(ship);
		}

		if (submarketId.equals(Submarkets.SUBMARKET_STORAGE)) {
			((StoragePlugin)submarket.getPlugin()).setPlayerPaidToUnlock(haveAccess);
		}
	}


	//public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {
		//switch (getModuleId(ship)) {
			//case "rs_RX78_GP03_D_Rightmodule":
				//return new PluginPick<ShipAIPlugin>(new ModuleWeaponControlerAi(ship), CampaignPlugin.PickPriority.HIGHEST);
			//case "rs_RX78_GP03_D_Leftmodule":
				//return new PluginPick<ShipAIPlugin>(new ModuleWeaponControlerAi(ship), CampaignPlugin.PickPriority.HIGHEST);
		//}
		//return null;
	//}


	public static String getModuleId(String hullId) {
		if (hullId.endsWith(Misc.D_HULL_SUFFIX)) {
			return hullId.substring(0, hullId.length() - Misc.D_HULL_SUFFIX.length());
		}
		return hullId;
	}

	public void onNewGameAfterEconomyLoad() {
		try {
			this.createRSKomet();
			this.createRSSchwalbe();
			this.createRSFrieda();
			this.createRSBrigitta();
		} catch (Throwable t) {
			this.throwJokeError(t);
		}

	}

	private void throwJokeError(Throwable t) {
		throw new RuntimeException("羽雨鱼猫最可爱啦！", t);
	}

	private void createRSKomet() {
		ImportantPeopleAPI Komet = Global.getSector().getImportantPeople();
		MarketAPI market = Global.getSector().getEconomy().getMarket("RS_planet2_market");
		if (market != null && Komet.getPerson("rs_Komet") == null) {
			if (market.getAdmin() != null && !market.getAdmin().isPlayer()) {
				PersonAPI oldAdmin = market.getAdmin();
				market.removePerson(oldAdmin);
				Komet.removePerson(oldAdmin);
				market.getCommDirectory().removePerson(oldAdmin);
			}

			PersonAPI Komet_sub = market.getFaction().createRandomPerson(FullName.Gender.FEMALE);
			Komet_sub.setFaction("red_stripe");
			Komet_sub.setId("rs_Komet");
			Komet_sub.setName(new FullName("Komet", "Messerschmitt", FullName.Gender.FEMALE));
			Komet_sub.setPortraitSprite("graphics/portraits/Komet.png");
			Komet_sub.setRankId(Ranks.POST_FLEET_COMMANDER);
			Komet_sub.setPostId(Ranks.POST_FLEET_COMMANDER);
			Komet_sub.setImportance(PersonImportance.VERY_HIGH);
			Komet_sub.addTag("military");
			//Komet_sub.addTag("trade");
			//starlight.addTag("science");
			Komet_sub.getMemoryWithoutUpdate().set(BaseMissionHub.NUM_BONUS_MISSIONS, 2);
			BaseMissionHub.set(Komet_sub, new BaseMissionHub(Komet_sub));
			Komet.addPerson(Komet_sub);
			Komet.getData(Komet_sub).getLocation().setMarket(market);
			Komet.checkOutPerson(Komet_sub, "permanent_staff");
			market.setAdmin(Komet_sub);
			market.getCommDirectory().addPerson(Komet_sub, 0);
			market.addPerson(Komet_sub);
			ContactIntel.addPotentialContact(Komet_sub, market, null);
		}

	}

	private void createRSSchwalbe() {
		ImportantPeopleAPI Schwalbe = Global.getSector().getImportantPeople();
		MarketAPI market = Global.getSector().getEconomy().getMarket("RS_planet1_market");
		if (market != null && Schwalbe.getPerson("rs_Schwalbe") == null) {
			if (market.getAdmin() != null && !market.getAdmin().isPlayer()) {
				PersonAPI oldAdmin = market.getAdmin();
				market.removePerson(oldAdmin);
				Schwalbe.removePerson(oldAdmin);
				market.getCommDirectory().removePerson(oldAdmin);
			}

			PersonAPI Schwalbe_sub = market.getFaction().createRandomPerson(FullName.Gender.FEMALE);
			Schwalbe_sub.setFaction("red_stripe");
			Schwalbe_sub.setId("rs_Schwalbe");
			Schwalbe_sub.setName(new FullName("Schwalbe", "Messerschmitt", FullName.Gender.FEMALE));
			Schwalbe_sub.setPortraitSprite("graphics/portraits/Schwalbe.png");
			Schwalbe_sub.setRankId(Ranks.POST_FLEET_COMMANDER);
			Schwalbe_sub.setPostId(Ranks.POST_FLEET_COMMANDER);
			Schwalbe_sub.setImportance(PersonImportance.VERY_HIGH);
			Schwalbe_sub.addTag("military");
			//Komet_sub.addTag("trade");
			//starlight.addTag("science");
			Schwalbe_sub.getMemoryWithoutUpdate().set(BaseMissionHub.NUM_BONUS_MISSIONS, 2);
			BaseMissionHub.set(Schwalbe_sub, new BaseMissionHub(Schwalbe_sub));
			Schwalbe.addPerson(Schwalbe_sub);
			Schwalbe.getData(Schwalbe_sub).getLocation().setMarket(market);
			Schwalbe.checkOutPerson(Schwalbe_sub, "permanent_staff");
			market.setAdmin(Schwalbe_sub);
			market.getCommDirectory().addPerson(Schwalbe_sub, 0);
			market.addPerson(Schwalbe_sub);
			ContactIntel.addPotentialContact(Schwalbe_sub, market, null);
		}

	}

	private void createRSFrieda() {
		ImportantPeopleAPI Frieda = Global.getSector().getImportantPeople();
		MarketAPI market = Global.getSector().getEconomy().getMarket("rs_arxcaelestis_battlestation_market");
		if (market != null && Frieda.getPerson("rs_Frieda") == null) {
			if (market.getAdmin() != null && !market.getAdmin().isPlayer()) {
				PersonAPI oldAdmin = market.getAdmin();
				market.removePerson(oldAdmin);
				Frieda.removePerson(oldAdmin);
				market.getCommDirectory().removePerson(oldAdmin);
			}

			PersonAPI Frieda_sub = market.getFaction().createRandomPerson(FullName.Gender.FEMALE);
			Frieda_sub.setFaction("red_stripe");
			Frieda_sub.setId("rs_Frieda");
			Frieda_sub.setName(new FullName("Frieda", "Horten", FullName.Gender.FEMALE));
			Frieda_sub.setPortraitSprite("graphics/portraits/Frieda.png");
			Frieda_sub.setRankId(Ranks.POST_FLEET_COMMANDER);
			Frieda_sub.setPostId(Ranks.POST_FLEET_COMMANDER);
			Frieda_sub.setImportance(PersonImportance.VERY_HIGH);
			Frieda_sub.addTag("military");
			//Komet_sub.addTag("trade");
			//starlight.addTag("science");
			Frieda_sub.getMemoryWithoutUpdate().set(BaseMissionHub.NUM_BONUS_MISSIONS, 2);
			BaseMissionHub.set(Frieda_sub, new BaseMissionHub(Frieda_sub));
			Frieda.addPerson(Frieda_sub);
			Frieda.getData(Frieda_sub).getLocation().setMarket(market);
			Frieda.checkOutPerson(Frieda_sub, "permanent_staff");
			market.setAdmin(Frieda_sub);
			market.getCommDirectory().addPerson(Frieda_sub, 0);
			market.addPerson(Frieda_sub);
			ContactIntel.addPotentialContact(Frieda_sub, market, null);
		}

	}

	private void createRSBrigitta() {
		ImportantPeopleAPI Brigitta = Global.getSector().getImportantPeople();
		MarketAPI market = Global.getSector().getEconomy().getMarket("RS_planet3_market");
		if (market != null && Brigitta.getPerson("rs_Brigitta") == null) {
			if (market.getAdmin() != null && !market.getAdmin().isPlayer()) {
				PersonAPI oldAdmin = market.getAdmin();
				market.removePerson(oldAdmin);
				Brigitta.removePerson(oldAdmin);
				market.getCommDirectory().removePerson(oldAdmin);
			}

			PersonAPI Brigitta_sub = market.getFaction().createRandomPerson(FullName.Gender.FEMALE);
			Brigitta_sub.setFaction("red_stripe");
			Brigitta_sub.setId("rs_Brigitta");
			Brigitta_sub.setName(new FullName("Brigitta", "Horten", FullName.Gender.FEMALE));
			Brigitta_sub.setPortraitSprite("graphics/portraits/Frieda.png");
			Brigitta_sub.setRankId(Ranks.POST_SCIENTIST);
			Brigitta_sub.setPostId(Ranks.POST_SCIENTIST);
			Brigitta_sub.setImportance(PersonImportance.VERY_HIGH);
			//Brigitta_sub.addTag("military");
			Brigitta_sub.addTag("trade");
			Brigitta_sub.addTag("science");
			Brigitta_sub.getMemoryWithoutUpdate().set(BaseMissionHub.NUM_BONUS_MISSIONS, 2);
			BaseMissionHub.set(Brigitta_sub, new BaseMissionHub(Brigitta_sub));
			Brigitta.addPerson(Brigitta_sub);
			Brigitta.getData(Brigitta_sub).getLocation().setMarket(market);
			Brigitta.checkOutPerson(Brigitta_sub, "permanent_staff");
			market.setAdmin(Brigitta_sub);
			market.getCommDirectory().addPerson(Brigitta_sub, 0);
			market.addPerson(Brigitta_sub);
			ContactIntel.addPotentialContact(Brigitta_sub, market, null);
		}

	}

	private static class RS_MaintenanceGantryManager implements EveryFrameScript {
		private IntervalUtil updateInterval = new IntervalUtil(1f, 1f); // 每1秒更新一次
		private int lastMaintenanceGantryCount = 0;

		public boolean isDone() {
			return false; // 永远不结束
		}

		public boolean runWhilePaused() {
			return true; // 暂停时运行
		}

		public void advance(float amount) {
			updateInterval.advance(amount);
			if (updateInterval.intervalElapsed()) {
				updateFleetEffects();
			}
		}

		private void updateFleetEffects() {
			// 检查基本条件
			if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) {
				return;
			}

			if (Global.getSector().getPlayerFleet().getFleetData() == null) {
				return;
			}

			// 计算舰队中有效的维护舱门船插数量
			int maintenanceGantryCount = 0;
			for (FleetMemberAPI fleetMember : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
				if (fleetMember.getVariant().hasHullMod("HugeOpenPort")
						&& !fleetMember.isMothballed()) {
					maintenanceGantryCount++;
				}
			}

			// 如果数量没有变化，不需要重新应用效果
			if (maintenanceGantryCount == lastMaintenanceGantryCount) {
				return;
			}

			lastMaintenanceGantryCount = maintenanceGantryCount;

			// 为所有舰队成员添加舰队效果船插
			boolean needSync = false;
			for (FleetMemberAPI fleetMember : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
				if (fleetMember.getVariant() != null
						&& !fleetMember.getVariant().hasHullMod("Moci_open_maintenance_gantry_fleet_effect")) {
					// 克隆变体并设置为改装来源，解除装配保护
					ShipVariantAPI v = fleetMember.getVariant().clone();
					v.setSource(VariantSource.REFIT);
					v.setHullVariantId(Misc.genUID());
					fleetMember.setVariant(v, false, false);

					// 添加舰队效果船插
					v.addPermaMod("Moci_open_maintenance_gantry_fleet_effect");
					needSync = true;
				}
			}

			// 如果有变化，强制同步舰队
			if (needSync) {
				Global.getSector().getPlayerFleet().forceSync();
			}
		}
	}
}

