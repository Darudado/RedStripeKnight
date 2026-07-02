package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicRenderPlugin;

import java.awt.Color;

public class DengineRDecoEffect implements EveryFrameWeaponEffectPlugin {

	public static final String ID = "DengineRDecoEffect";
	public static final float FRAME_TIME = 0.04f;

	private float alpha = 0f;
	private float timer = 0f;
	float timer1 =0f;

	@Override
	public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

		if (weapon.getAnimation().getFrameRate() > 0) {
			weapon.getAnimation().setFrameRate(0);
		}

		ShipAPI ship = weapon.getShip();
		AnimationAPI animation = weapon.getAnimation();
		if (ship.getEngineController().isTurningLeft() || ship.getEngineController().isStrafingLeft()) {
			if (animation.getFrame() < animation.getNumFrames() - 1) {
				timer += amount;
				if (timer >= FRAME_TIME) {
					timer -= FRAME_TIME;

					animation.setFrame(animation.getFrame() + 1);
				}
			} else {

				alpha = Math.min(alpha + amount, 1f);
				if(alpha > 1f){alpha =1f;}

				SpriteAPI goat_breathing_glow = Global.getSettings().getSprite( "dengine_glow");
				goat_breathing_glow.setColor(new Color(210, 37, 124,255));
				goat_breathing_glow.setAngle(weapon.getCurrAngle() - 90f);
				goat_breathing_glow.setAlphaMult(alpha);

				MagicRenderPlugin.addSingleframe(goat_breathing_glow, weapon.getLocation(), CombatEngineLayers.ABOVE_SHIPS_LAYER);


				Vector2f loc = weapon.getLocation();
				float angle = weapon.getCurrAngle();
				double radians = Math.toRadians(angle);
				if (timer1 < 0.08f) {
					timer1 += amount;
				} else {
					engine.addHitParticle(loc, new Vector2f((float)((5f*Math.sin(radians))), (float)(5f * Math.cos(radians + MathUtils.getRandomNumberInRange(-0.4f, 0.4f)))), 24f+MathUtils.getRandomNumberInRange(-12.4f, 12.4f), 0.62f, 0.1f, new Color(127, 41, 255, 255));
					engine.addHitParticle(loc, new Vector2f((float)((5f*Math.sin(radians))), (float)(5f * Math.cos(radians + MathUtils.getRandomNumberInRange(-0.4f, 0.4f)))), 11f, 0.82f, 0.05f, new Color(180, 246, 255, 255));
					timer1 = 0f;
				}
			}

		} else {
			if (animation.getFrame() > 0) {
				if (alpha > 0f) {
					alpha = Math.max(0f, alpha - amount);

					SpriteAPI goat_breathing_glow = Global.getSettings().getSprite( "goat_dengine_glow");
					goat_breathing_glow.setColor(new Color(210, 63, 37,255));
					goat_breathing_glow.setAngle(weapon.getCurrAngle() - 90f);
					goat_breathing_glow.setAlphaMult(alpha);


					MagicRenderPlugin.addSingleframe(goat_breathing_glow, weapon.getLocation(), CombatEngineLayers.ABOVE_SHIPS_LAYER);
				} else {
					timer += amount;
					if (timer >= FRAME_TIME) {
						timer -= FRAME_TIME;

						animation.setFrame(animation.getFrame() - 1);
					}
				}
			}
		}
	}
}