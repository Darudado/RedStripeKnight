package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;

import data.hullmods.crusaders.CrusadersPlateRenderer;

/**
 * Programmed for 百里连山.
 * @author luoxb
 */
public class RSSettingsRegisteredPlugin extends BaseEveryFrameCombatPlugin{
	@Override
	public void init(CombatEngineAPI engine) {
		Global.getLogger(getClass()).info("mounted");
		engine.addLayeredRenderingPlugin(CrusadersPlateRenderer.getInstance());
	}
}
