package data.hullmods;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 机动兵器整备/起飞阶段状态跟踪器。
 */
public class RS_Moci_MobileSuitRepairTracker {

    public static final String DATA_KEY = "Moci_MobileSuitRepairTracker";

    public enum State {
        IDLE,
        RETURNING,
        APPROACHING,
        REPAIRING,
        TAKEOFF_PENDING,
        TAKING_OFF,
        EMERGENCY_RELEASE,
        COMPLETED,
        ABORTED
    }

    private State state = State.IDLE;
    private ShipAPI carrier;
    private Moci_RS_RepairBayScript.Moci_RepairBay bay;
    private Vector2f lastKnownLocation;
    private float stateElapsed = 0f;
    private float totalElapsed = 0f;

    public RS_Moci_MobileSuitRepairTracker(ShipAPI ship) {
    }

    public static RS_Moci_MobileSuitRepairTracker get(ShipAPI ship) {
        if (ship == null) {
            return null;
        }
        Object existing = ship.getCustomData().get(DATA_KEY);
        return existing instanceof RS_Moci_MobileSuitRepairTracker
                ? (RS_Moci_MobileSuitRepairTracker) existing
                : null;
    }

    public static RS_Moci_MobileSuitRepairTracker getOrCreate(ShipAPI ship) {
        RS_Moci_MobileSuitRepairTracker tracker = get(ship);
        if (tracker == null && ship != null) {
            tracker = new RS_Moci_MobileSuitRepairTracker(ship);
            ship.setCustomData(DATA_KEY, tracker);
        }
        return tracker;
    }

    public static void clear(ShipAPI ship) {
        if (ship != null) {
            ship.removeCustomData(DATA_KEY);
        }
    }

    public void advance(float amount) {
        stateElapsed += amount;
        totalElapsed += amount;
    }

    public void beginReturn(ShipAPI carrier, Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        updateState(State.RETURNING, carrier, bay);
    }

    public void beginApproach(ShipAPI carrier, Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        updateState(State.APPROACHING, carrier, bay);
    }

    public void beginRepair(ShipAPI carrier, Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        updateState(State.REPAIRING, carrier, bay);
    }

    public void beginTakeoff(ShipAPI carrier, Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        updateState(State.TAKEOFF_PENDING, carrier, bay);
    }

    public void markTakingOff() {
        updateState(State.TAKING_OFF, carrier, bay);
    }

    public void markEmergencyRelease() {
        updateState(State.EMERGENCY_RELEASE, carrier, bay);
    }

    public void markCompleted() {
        updateState(State.COMPLETED, carrier, bay);
    }

    public void markAborted() {
        updateState(State.ABORTED, carrier, bay);
    }

    public State getState() {
        return state;
    }

    public ShipAPI getCarrier() {
        return carrier;
    }

    public Moci_RS_RepairBayScript.Moci_RepairBay getBay() {
        return bay;
    }

    public Vector2f getLastKnownLocation() {
        return lastKnownLocation != null ? new Vector2f(lastKnownLocation) : null;
    }

    public float getStateElapsed() {
        return stateElapsed;
    }

    public float getTotalElapsed() {
        return totalElapsed;
    }

    private void updateState(State newState, ShipAPI carrier, Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        this.state = newState;
        this.stateElapsed = 0f;
        this.carrier = carrier;
        this.bay = bay;
        if (bay != null && bay.getLocation() != null) {
            this.lastKnownLocation = new Vector2f(bay.getLocation());
        } else if (carrier != null && carrier.getLocation() != null) {
            this.lastKnownLocation = new Vector2f(carrier.getLocation());
        }
    }
}
