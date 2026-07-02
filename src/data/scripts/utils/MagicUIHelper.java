package data.scripts.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.lazywizard.lazylib.ui.FontException;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

/**
 *
 * @author Dark.Revenant, Tartiflette, LazyWizard, Snrasha
 * <p>
 * This class is mostly taken from MagicUI. Thanks to the above, although we need to modify this to support ships that
 * have two systems.
 *
 */

public class MagicUIHelper {
    //Color of the HUD when the ship is alive or the hud
    public static final Color GREENCOLOR;
    //Color of the HUD when the ship is not alive.
    public static final Color BLUCOLOR;
    //Color of the HUD for the red color.
    public static final Color REDCOLOR;
    private static DrawableString TODRAW14;

    private static final Vector2f PERCENTBARVEC1 = new Vector2f(21f, 0f); // Just 21 pixel of width of difference.
    private static final Vector2f PERCENTBARVEC2 = new Vector2f(50f, 58f);

    private static final float UIscaling = Global.getSettings().getScreenScaleMult();

    static {
        GREENCOLOR = Global.getSettings().getColor("textFriendColor");
        BLUCOLOR = Global.getSettings().getColor("textNeutralColor");
        REDCOLOR = Global.getSettings().getColor("textEnemyColor");

        try {
            LazyFont fontdraw = LazyFont.loadFont("graphics/fonts/victor14.fnt");
            TODRAW14 = fontdraw.createText();
        } catch (FontException ex) {
            throw new RuntimeException(ex);
        }
    }

    ///////////////////////////////////
    //                               //
    //          STATUS BAR           //
    //                               //
    //////////////////////////////////

    /**
     * Draw a third status bar above the Flux and Hull ones on the User Interface.
     * With a text of the left and the number on the right.
     *
     * @param ship Player ship.
     *
     * @param fill Filling level of the bar. 0 to 1
     *
     * @param innerColor Color of the bar. If null, the vanilla green UI color will be used.
     *
     * @param borderColor Color of the border. If null, the vanilla green UI color will be used.
     *
     * @param secondfill Wider filling like the soft/hard-flux. 0 to 1.
     *
     * @param text The text written to the left, automatically cut if too large. Set to null to ignore
     *
     * @param number The number displayed on the right. Can go from 0 to 999 999. Set to <0 value to ignore
     */
    public static void drawInterfaceStatusBar(ShipAPI ship, float fill, Color innerColor, Color borderColor, float secondfill, String text, int number, Vector2f offset) {
        if (ship != Global.getCombatEngine().getPlayerShip()) {
            return;
        }

        if (Global.getCombatEngine().getCombatUI()==null || Global.getCombatEngine().getCombatUI().isShowingCommandUI() || !Global.getCombatEngine().isUIShowingHUD()) {
            return;
        }

        addInterfaceStatusBar(ship, fill, innerColor, borderColor, secondfill, offset);
        if (TODRAW14 != null) {
            if(text!=null && !text.isEmpty()){
                addInterfaceStatusText(ship, text, offset);
            }
            if(number>=0){
                addInterfaceStatusNumber(ship, number, offset);
            }
        }

    }

    // UTILS /////

    /**
     * Get the UI Element Offset for the Third bar. (Depends on the group
     * layout, or if the player has some wing)
     *
     * @param ship The player ship.
     * @param variant The variant of the ship.
     * @return The offset who depends on weapon and wing.
     */
    public static Vector2f getInterfaceOffsetFromStatusBars(ShipAPI ship, ShipVariantAPI variant) {
        return getInterfaceOffsetFromStatusBars(ship, variant, null);
    }

    /**
     * Get the UI Element Offset for the Third bar. (Depends on the group
     * layout, or if the player has some wing)
     *
     * @param ship The player ship.
     * @param variant The variant of the ship.
     * @param offset Offsets the resulting vector by this one. Accepts null for no offset.
     * @return The offset who depends on weapon and wing.
     */
    public static Vector2f getInterfaceOffsetFromStatusBars(ShipAPI ship, ShipVariantAPI variant, Vector2f offset) {
        Vector2f result = getUIElementOffset(ship, variant, PERCENTBARVEC1, PERCENTBARVEC2);
        if (offset != null) {
            return Vector2f.add(result, offset, null);
        }
        return result;
    }

    /**
     * Get the UI Element Offset.
     * (Depends on the weapon groups and wings present)
     *
     * @param ship The player ship.
     * @param variant The variant of the ship.
     * @param vec1 Vector2f used if there are no wing and less than 2 weapon groups.
     * @param vec2 Vector2f used if there are wings but less than 2 weapon groups.
     *
     * @return the offset.
     */
    private static Vector2f getUIElementOffset(ShipAPI ship, ShipVariantAPI variant, Vector2f vec1, Vector2f vec2) {
        int numEntries = 0;
        final List<WeaponGroupSpec> weaponGroups = variant.getWeaponGroups();
        final List<WeaponAPI> usableWeapons = ship.getUsableWeapons();
        for (WeaponGroupSpec group : weaponGroups) {
            final Set<String> uniqueWeapons = new HashSet<>(group.getSlots().size());
            for (String slot : group.getSlots()) {
                boolean isUsable = false;
                for (WeaponAPI weapon : usableWeapons) {
                    if (weapon.getSlot().getId().contentEquals(slot)) {
                        isUsable = true;
                        break;
                    }
                }
                if (!isUsable) {
                    continue;
                }
                String id = Global.getSettings().getWeaponSpec(variant.getWeaponId(slot)).getWeaponName();
                if (id != null) {
                    uniqueWeapons.add(id);
                }
            }
            numEntries += uniqueWeapons.size();
        }
        if (variant.getFittedWings().isEmpty()) {
            if (numEntries < 2) {
                return vec1;
            }
            return new Vector2f(30f + ((numEntries - 2) * 13f), 18f + ((numEntries - 2) * 26f));
        } else {
            if (numEntries < 2) {
                return vec2;
            }
            return new Vector2f(59f + ((numEntries - 2) * 13f), 76f + ((numEntries - 2) * 26f));
        }
    }


    /**
     * Draws a small UI bar above the flux bar. The HUD color change to blue
     * when the ship is not alive. Bug: When you left the battle, the hud
     * keep for qew second, no solution found. Bug: Also for other
     * normal drawBox, when paused, they switch brutally of "color".
     *
     * @param ship Ship concerned (the element will only be drawn if that ship
     * is the player ship)
     * @param fill Filling level
     * @param innerColor Color of the bar. If null, use the vanilla HUD color.
     * @param borderColor Color of the border. If null, use the vanilla HUD
     * color.
     * @param secondfill Like the hardflux of the fluxbar. 0 per default.
     * @param offset A Vector used to offset the location of the bar. Null for no offset is acceptable.
     */
    private static void addInterfaceStatusBar(ShipAPI ship, float fill, Color innerColor, Color borderColor, float secondfill, Vector2f offset) {

        final float boxWidth = 79 * UIscaling;
        final float boxHeight = 7 * UIscaling;
        final Vector2f element = getInterfaceOffsetFromStatusBars(ship, ship.getVariant(), offset);

        final Vector2f boxLoc = Vector2f.add(new Vector2f(224f, 120f), element, null);
        final Vector2f shadowLoc = Vector2f.add(new Vector2f(225f, 119f), element, null);
        if(UIscaling!=1){
            boxLoc.scale(UIscaling);
            shadowLoc.scale(UIscaling);
        }

        // Used to properly interpolate between colors
        float alpha = 1;
        if (Global.getCombatEngine().isUIShowingDialog()) {
            alpha = 0.28f;
        }

        Color innerCol = innerColor == null ? GREENCOLOR : innerColor;
        Color borderCol = borderColor == null ? GREENCOLOR : borderColor;
        if (!ship.isAlive()) {
            innerCol = BLUCOLOR;
            borderCol = BLUCOLOR;
        }
        float hardfill = secondfill < 0 ? 0 : secondfill;
        hardfill = hardfill > 1 ? 1 : hardfill;
        if (hardfill >= fill) {
            hardfill = fill;
        }
        int pixelHardfill = (int) (boxWidth * hardfill);
        pixelHardfill = pixelHardfill <= 3 ? -pixelHardfill : -3;

        int hfboxWidth = (int) (boxWidth * hardfill);
        int fboxWidth = (int) (boxWidth * fill);

        OpenGLBar(ship, alpha, borderCol, innerCol, fboxWidth, hfboxWidth, boxHeight, boxWidth, pixelHardfill, shadowLoc, boxLoc);
    }

    /**
     * Draw the text with the font victor14.
     * @param ship The player ship
     * @param text The text to write.
     * @param offset Vector2f to offset the text by. Accepts null for no offset.
     */
    private static void addInterfaceStatusText(ShipAPI ship, String text, Vector2f offset) {
        if (ship != Global.getCombatEngine().getPlayerShip()) {
            return;
        }
        if (Global.getCombatEngine().getCombatUI().isShowingCommandUI() || !Global.getCombatEngine().isUIShowingHUD()) {
            return;
        }
        Color borderCol = GREENCOLOR;
        if (!ship.isAlive()) {
            borderCol = BLUCOLOR;
        }
        float alpha = 1;
        if (Global.getCombatEngine().isUIShowingDialog()) {
            alpha = 0.28f;
        }
        Color shadowcolor = new Color(Color.BLACK.getRed() / 255f, Color.BLACK.getGreen() / 255f, Color.BLACK.getBlue() / 255f,
                1f - Global.getCombatEngine().getCombatUI().getCommandUIOpacity());
        Color color = new Color(borderCol.getRed() / 255f, borderCol.getGreen() / 255f, borderCol.getBlue() / 255f,
                alpha * (borderCol.getAlpha() / 255f)
                        * (1f - Global.getCombatEngine().getCombatUI().getCommandUIOpacity()));

        Vector2f uiPos = getInterfaceOffsetFromStatusBars(ship, ship.getVariant());
        if(offset != null) {
            uiPos = Vector2f.add(uiPos, offset, null);
        }

        final Vector2f boxLoc = Vector2f.add(new Vector2f(176f, 131f),
                uiPos, null);
        final Vector2f shadowLoc = Vector2f.add(new Vector2f(177f, 130f),
                uiPos, null);

        if(UIscaling!=1){
            boxLoc.scale(UIscaling);
            shadowLoc.scale(UIscaling);
            TODRAW14.setFontSize(14*UIscaling);
        }

        openGL11ForText();
        TODRAW14.setText(text);
        TODRAW14.setMaxWidth(46*UIscaling);
        TODRAW14.setMaxHeight(14*UIscaling);
        TODRAW14.setBaseColor(shadowcolor);
        TODRAW14.draw(shadowLoc);
        TODRAW14.setBaseColor(color);
        TODRAW14.draw(boxLoc);
        closeGL11ForText();

    }

    /**
     * Draw number at the right of the percent bar.
     * @param ship The player ship died or alive.
     * @param number The number displayed, bounded per the method to 0 at 999
     * 999.
     * @param offset Vector2f to offset the text by. Accepts null for no offset.
     */
    private static void addInterfaceStatusNumber(ShipAPI ship, int number, Vector2f offset) {
        if (ship != Global.getCombatEngine().getPlayerShip()) {
            return;
        }
        if (Global.getCombatEngine().getCombatUI().isShowingCommandUI() || !Global.getCombatEngine().isUIShowingHUD()) {
            return;
        }
        int numb = number;
        if (numb > 999999) {
            numb = 999999;
        }
        if (numb < 0) {
            numb = 0;
        }

        Color borderCol = GREENCOLOR;
        if (!ship.isAlive()) {
            borderCol = BLUCOLOR;
        }
        float alpha = 1;
        if (Global.getCombatEngine().isUIShowingDialog()) {
            alpha = 0.28f;
        }
        Color shadowcolor = new Color(Color.BLACK.getRed() / 255f, Color.BLACK.getGreen() / 255f, Color.BLACK.getBlue() / 255f,
                1f - Global.getCombatEngine().getCombatUI().getCommandUIOpacity());
        Color color = new Color(borderCol.getRed() / 255f, borderCol.getGreen() / 255f, borderCol.getBlue() / 255f,
                alpha * (borderCol.getAlpha() / 255f)
                        * (1f - Global.getCombatEngine().getCombatUI().getCommandUIOpacity()));

        Vector2f uiPos = getInterfaceOffsetFromStatusBars(ship, ship.getVariant());
        if(offset != null) {
            uiPos = Vector2f.add(uiPos, offset, null);
        }

        final Vector2f boxLoc = Vector2f.add(new Vector2f(355f, 131f),
                uiPos, null);
        final Vector2f shadowLoc = Vector2f.add(new Vector2f(356f, 130f),
                uiPos, null);

        if(UIscaling!=1){
            boxLoc.scale(UIscaling);
            shadowLoc.scale(UIscaling);
            TODRAW14.setFontSize(14*UIscaling);
        }

        openGL11ForText();
        TODRAW14.setText(numb + "");
        float width = TODRAW14.getWidth() - 1;
        TODRAW14.setBaseColor(shadowcolor);
        TODRAW14.draw(shadowLoc.x - width, shadowLoc.y);
        TODRAW14.setBaseColor(color);
        TODRAW14.draw(boxLoc.x - width, boxLoc.y);
        closeGL11ForText();
    }

    private static void OpenGLBar(ShipAPI ship, float alpha, Color borderCol, Color innerCol, int fboxWidth, int hfboxWidth, float boxHeight, float boxWidth, int pixelHardfill, Vector2f shadowLoc, Vector2f boxLoc) {
        final int width = (int) (Display.getWidth() * Display.getPixelScaleFactor());
        final int height = (int) (Display.getHeight() * Display.getPixelScaleFactor());

        // Set OpenGL flags
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, 0, height, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glTranslatef(0.01f, 0.01f, 0);

        if (ship.isAlive()) {
            // Render the drop shadow
            if (fboxWidth != 0) {
                GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
                GL11.glColor4f(Color.BLACK.getRed() / 255f, Color.BLACK.getGreen() / 255f, Color.BLACK.getBlue() / 255f,
                        1f - Global.getCombatEngine().getCombatUI().getCommandUIOpacity());
                GL11.glVertex2f(shadowLoc.x - 1, shadowLoc.y);
                GL11.glVertex2f(shadowLoc.x + fboxWidth, shadowLoc.y);
                GL11.glVertex2f(shadowLoc.x - 1, shadowLoc.y + boxHeight + 1);
                GL11.glVertex2f(shadowLoc.x + fboxWidth, shadowLoc.y + boxHeight + 1);
                GL11.glEnd();
            }
        }

        // Render the drop shadow of border.
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(Color.BLACK.getRed() / 255f, Color.BLACK.getGreen() / 255f, Color.BLACK.getBlue() / 255f,
                1f - Global.getCombatEngine().getCombatUI().getCommandUIOpacity());
        GL11.glVertex2f(shadowLoc.x + hfboxWidth + pixelHardfill, shadowLoc.y - 1);
        GL11.glVertex2f(shadowLoc.x + 3 + hfboxWidth + pixelHardfill, shadowLoc.y - 1);
        GL11.glVertex2f(shadowLoc.x + hfboxWidth + pixelHardfill, shadowLoc.y + boxHeight);
        GL11.glVertex2f(shadowLoc.x + 3 + hfboxWidth + pixelHardfill, shadowLoc.y + boxHeight);
        GL11.glVertex2f(shadowLoc.x + boxWidth, shadowLoc.y);
        GL11.glVertex2f(shadowLoc.x + boxWidth, shadowLoc.y + boxHeight);

        // Render the border transparency fix
        GL11.glColor4f(Color.BLACK.getRed() / 255f, Color.BLACK.getGreen() / 255f, Color.BLACK.getBlue() / 255f,
                1f - Global.getCombatEngine().getCombatUI().getCommandUIOpacity());
        GL11.glVertex2f(boxLoc.x + hfboxWidth + pixelHardfill, boxLoc.y - 1);
        GL11.glVertex2f(boxLoc.x + 3 + hfboxWidth + pixelHardfill, boxLoc.y - 1);
        GL11.glVertex2f(boxLoc.x + hfboxWidth + pixelHardfill, boxLoc.y + boxHeight);
        GL11.glVertex2f(boxLoc.x + 3 + hfboxWidth + pixelHardfill, boxLoc.y + boxHeight);
        GL11.glVertex2f(boxLoc.x + boxWidth, boxLoc.y);
        GL11.glVertex2f(boxLoc.x + boxWidth, boxLoc.y + boxHeight);

        // Render the border
        GL11.glColor4f(borderCol.getRed() / 255f, borderCol.getGreen() / 255f, borderCol.getBlue() / 255f,
                alpha * (1 - Global.getCombatEngine().getCombatUI().getCommandUIOpacity()));
        GL11.glVertex2f(boxLoc.x + hfboxWidth + pixelHardfill, boxLoc.y - 1);
        GL11.glVertex2f(boxLoc.x + 3 + hfboxWidth + pixelHardfill, boxLoc.y - 1);
        GL11.glVertex2f(boxLoc.x + hfboxWidth + pixelHardfill, boxLoc.y + boxHeight);
        GL11.glVertex2f(boxLoc.x + 3 + hfboxWidth + pixelHardfill, boxLoc.y + boxHeight);
        GL11.glVertex2f(boxLoc.x + boxWidth, boxLoc.y);
        GL11.glVertex2f(boxLoc.x + boxWidth, boxLoc.y + boxHeight);
        GL11.glEnd();

        // Render the fill element
        if (ship.isAlive()) {
            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            GL11.glColor4f(innerCol.getRed() / 255f, innerCol.getGreen() / 255f, innerCol.getBlue() / 255f,
                    alpha * (innerCol.getAlpha() / 255f)
                            * (1f - Global.getCombatEngine().getCombatUI().getCommandUIOpacity()));
            GL11.glVertex2f(boxLoc.x, boxLoc.y);
            GL11.glVertex2f(boxLoc.x + fboxWidth, boxLoc.y);
            GL11.glVertex2f(boxLoc.x, boxLoc.y + boxHeight);
            GL11.glVertex2f(boxLoc.x + fboxWidth, boxLoc.y + boxHeight);
            GL11.glEnd();
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glPopAttrib();

    }

    /**
     * GL11 to start, when you want render text of Lazyfont.
     */
    private static void openGL11ForText() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
        GL11.glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * GL11 to close, when you want render text of Lazyfont.
     */
    private static void closeGL11ForText() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}