package com.example.ecolums;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the creation, retrieval, editing, and deletion of a user's daily
 * sustainability activity log entries, and converts raw inputs into CO2
 * equivalents using {@link CalculationMetric} factors.
 *
 * <p><b>Responsibilities (from CRC card):</b></p>
 * <ul>
 *   <li>Capture transport, energy, and waste data inputs.</li>
 *   <li>Convert raw activity data into CO2 (carbon equivalents).</li>
 *   <li>Provide data for personal visualizations.</li>
 * </ul>
 *
 * <p><b>Collaborators:</b> {@link User}, {@link ProgressTracker}.</p>
 *
 * <p>Each log entry is stored as a document under
 * {@code users/{userId}/activityLogs/{logId}} in Firestore.</p>
 * <p>
 * Outstanding issues: Firestore real-time listener for the daily-log list
 * view has not been wired to the UI yet.
 */
public class ActivityLog {

	// -------------------------------------------------------------------------
	// Constants — transport modes
	// -------------------------------------------------------------------------

	public static final String TRANSPORT_BUS = "BUS";
	public static final String TRANSPORT_BIKE = "BIKE";
	public static final String TRANSPORT_CAR = "CAR";
	public static final String TRANSPORT_WALK = "WALK";
	public static final String TRANSPORT_TRAIN = "TRAIN";

	// -------------------------------------------------------------------------
	// Constants — waste types
	// -------------------------------------------------------------------------

	public static final String WASTE_LANDFILL = "LANDFILL";
	public static final String WASTE_RECYCLE = "RECYCLE";
	public static final String WASTE_COMPOST = "COMPOST";

	// -------------------------------------------------------------------------
	// Constants — log categories
	// -------------------------------------------------------------------------

	public static final String CATEGORY_TRANSPORT = "TRANSPORT";
	public static final String CATEGORY_ENERGY = "ENERGY";
	public static final String CATEGORY_WASTE = "WASTE";

	// -------------------------------------------------------------------------
	// Fields (one document = one log entry)
	// -------------------------------------------------------------------------

	/**
	 * Firestore document ID for this log entry.
	 */
	private String logId;

	/**
	 * UID of the user who owns this log entry.
	 */
	private String userId;

	/**
	 * Date and time when the activity occurred or was logged.
	 */
	private Timestamp date;

	/**
	 * One of the CATEGORY_* constants — which sustainability area this covers.
	 */
	private String category;

	// --- Transport fields (populated only when category == CATEGORY_TRANSPORT) ---

	/**
	 * One of the TRANSPORT_* constants. Null for non-transport entries.
	 */
	private String transportMode;

	/**
	 * Distance traveled in kilometres. 0 for non-transport entries.
	 */
	private double distanceKm;

	// --- Energy fields (populated only when category == CATEGORY_ENERGY) ---

	/**
	 * Energy consumed or saved in kWh. 0 for non-energy entries.
	 */
	private double energyKwh;

	// --- Waste fields (populated only when category == CATEGORY_WASTE) ---

	/**
	 * One of the WASTE_* constants. Null for non-waste entries.
	 */
	private String wasteType;

	/**
	 * Weight of waste in kilograms. 0 for non-waste entries.
	 */
	private double wasteKg;

	// --- Computed fields ---

	/**
	 * CO2 equivalent (in kg) calculated from the raw input using the current
	 * {@link CalculationMetric} factors. Positive = CO2 saved; negative = CO2 emitted.
	 */
	private double co2EquivalentKg;

	/**
	 * Green points awarded for this log entry, derived from co2EquivalentKg.
	 */
	private double pointsEarned;

	/**
	 * Autoencoder reconstruction error produced by {@link AnomalyDetector}.
	 * 0.0 if anomaly detection was not run.
	 */
	private double anomalyScore;

	/**
	 * {@code true} if the anomaly score exceeds the detector threshold,
	 * indicating a potentially fraudulent or erroneous entry.
	 */
	private boolean isFlagged;

	// -------------------------------------------------------------------------
	// Transient (not persisted)
	// -------------------------------------------------------------------------

	/**
	 * Firestore instance.
	 */
	private transient FirebaseFirestore db;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Required no-arg constructor for Firestore deserialization.
	 */
	public ActivityLog() {
		this.db = FirebaseFirestore.getInstance();
	}

	/**
	 * Creates a new ActivityLog entry (before it has been saved to Firestore).
	 *
	 * @param userId   UID of the owning user.
	 * @param date     When the activity took place.
	 * @param category One of the CATEGORY_* constants.
	 */
	public ActivityLog(String userId, Timestamp date, String category) {
		this.userId = userId;
		this.date = date;
		this.category = category;
		this.db = FirebaseFirestore.getInstance();
	}

	// -------------------------------------------------------------------------
	// CO2 conversion
	// -------------------------------------------------------------------------

	/**
	 * Converts the raw input fields of this log entry into a CO2 equivalent
	 * (kg) by looking up the relevant {@link CalculationMetric} from Firestore.
	 *
	 * <p>For transport: {@code co2EquivalentKg = distanceKm × factor}.
	 * For energy: {@code co2EquivalentKg = energyKwh × factor}.
	 * For waste: factor depends on {@link #wasteType}.</p>
	 *
	 * <p>After computing, calls {@link #calculatePoints()} to set
	 * {@link #pointsEarned}.</p>
	 *
	 * @param metrics Map of metricKey → {@link CalculationMetric}, pre-fetched
	 *                to avoid an extra Firestore read per log.
	 */
	public void convertToCO2(Map<String, CalculationMetric> metrics) {
		double factor = 0.0;

		switch (category) {
		case CATEGORY_TRANSPORT:
			if (transportMode != null && metrics.containsKey(transportMode + "_PER_KM")) {
				factor = metrics.get(transportMode + "_PER_KM").getValue();
			}
			// Positive for green modes (bike/walk/bus), negative for car
			co2EquivalentKg = distanceKm * factor;
			break;

		case CATEGORY_ENERGY:
			if (metrics.containsKey(CalculationMetric.KEY_ENERGY_PER_KWH)) {
				factor = metrics.get(CalculationMetric.KEY_ENERGY_PER_KWH).getValue();
			}
			co2EquivalentKg = energyKwh * factor;
			break;

		case CATEGORY_WASTE:
			String wasteKey = wasteType + "_KG"; // e.g. "LANDFILL_KG"
			if (metrics.containsKey(wasteKey)) {
				factor = metrics.get(wasteKey).getValue();
			}
			co2EquivalentKg = wasteKg * factor;
			break;

		default:
			co2EquivalentKg = 0.0;
		}

		calculatePoints();
	}

	/**
	 * Derives green points from {@link #co2EquivalentKg}.
	 *
	 * <p>Current rule: 10 points per kg of CO2 equivalent saved.
	 * Negative CO2 (e.g. car travel) yields 0 points.</p>
	 */
	public void calculatePoints() {
		// TODO: Refine the point formula if the game-design spec changes.
		pointsEarned = Math.max(0.0, co2EquivalentKg * 10.0);
	}

	/**
	 * Estimates CO2 using the on-device TFLite model ({@link Co2Estimator}) when
	 * available, otherwise falls back to the static metric-factor approach.
	 *
	 * <p>The ML model factors in time-of-day grid intensity, peak-hour bus
	 * occupancy, and seasonal variation — things the fixed factors cannot
	 * capture. The fallback ensures the app always produces a result even if
	 * the model asset is missing.</p>
	 *
	 * @param metrics Pre-fetched metric map, used only by the fallback path.
	 */
	public void convertToCO2WithML(Map<String, CalculationMetric> metrics) {
		try {
			Co2Estimator estimator = EcoLumsApp.getCo2Estimator();
			if (estimator != null && estimator.isAvailable()) {
				int   subType  = resolveSubType();
				float quantity = resolveQuantity();
				Calendar cal   = Calendar.getInstance();
				if (date != null) {
					cal.setTimeInMillis(date.toDate().getTime());
				}
				float mlResult = estimator.estimate(subType, quantity, cal);
				if (Float.isFinite(mlResult)) {
					co2EquivalentKg = mlResult;
					calculatePoints();
					return;
				}
			}
		} catch (Exception e) {
			// TFLite inference can throw; fall through to static formula
		}
		convertToCO2(metrics); // fallback: static metric factors
	}

	/**
	 * Public alias for {@link #resolveSubType()} — used by
	 * {@code AddActivityBottomSheet} for anomaly detection.
	 */
	public int resolveSubTypePublic() {
		return resolveSubType();
	}

	/** Maps category + mode/type to a {@link Co2Estimator} SUBTYPE_* constant. */
	private int resolveSubType() {
		switch (category) {
		case CATEGORY_TRANSPORT:
			if (transportMode == null) return Co2Estimator.SUBTYPE_WALK;
			switch (transportMode) {
			case TRANSPORT_BUS:   return Co2Estimator.SUBTYPE_BUS;
			case TRANSPORT_BIKE:  return Co2Estimator.SUBTYPE_BIKE;
			case TRANSPORT_CAR:   return Co2Estimator.SUBTYPE_CAR;
			case TRANSPORT_TRAIN: return Co2Estimator.SUBTYPE_TRAIN;
			default:              return Co2Estimator.SUBTYPE_WALK;
			}
		case CATEGORY_ENERGY:
			return Co2Estimator.SUBTYPE_ENERGY;
		case CATEGORY_WASTE:
			if (wasteType == null) return Co2Estimator.SUBTYPE_LANDFILL;
			switch (wasteType) {
			case WASTE_RECYCLE: return Co2Estimator.SUBTYPE_RECYCLE;
			case WASTE_COMPOST: return Co2Estimator.SUBTYPE_COMPOST;
			default:            return Co2Estimator.SUBTYPE_LANDFILL;
			}
		default:
			return Co2Estimator.SUBTYPE_WALK;
		}
	}

	/** Returns the quantity in the unit the model expects (km / kWh / kg). */
	private float resolveQuantity() {
		switch (category) {
		case CATEGORY_TRANSPORT: return (float) distanceKm;
		case CATEGORY_ENERGY:    return (float) energyKwh;
		case CATEGORY_WASTE:     return (float) wasteKg;
		default:                 return 0.0f;
		}
	}

	// -------------------------------------------------------------------------
	// CRUD operations
	// -------------------------------------------------------------------------

	/**
	 * Saves this log entry as a new Firestore document under
	 * {@code users/{userId}/activityLogs/}.
	 *
	 * <p>On success, notifies {@link ProgressTracker} to re-aggregate the
	 * user's totals and award any newly unlocked badges.</p>
	 *
	 * @param callback Invoked with {@code true} on success.
	 */
	public void submitLog(User.OnCompleteCallback callback) {
		db.collection("users")
				.document(userId)
				.collection("activityLogs")
				.add(this)
				.addOnSuccessListener(documentReference -> {
					this.logId = documentReference.getId();
					// TODO: Notify ProgressTracker to update user totals and
					//       check for badge milestones. Also update the active
					//       challenge participantScores if the user is enrolled.
					callback.onComplete(true);
				})
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	/**
	 * Updates an existing log entry in Firestore with the current field values.
	 *
	 * <p>Only entries from the current day may be edited.</p>
	 *
	 * @param callback Invoked with {@code true} on success.
	 */
	public void editLog(User.OnCompleteCallback callback) {
		if (logId == null || userId == null) {
			callback.onComplete(false);
			return;
		}

		Map<String, Object> updates = new HashMap<>();
		updates.put("category", category);
		updates.put("transportMode", transportMode);
		updates.put("distanceKm", distanceKm);
		updates.put("energyKwh", energyKwh);
		updates.put("wasteType", wasteType);
		updates.put("wasteKg", wasteKg);
		updates.put("co2EquivalentKg", co2EquivalentKg);
		updates.put("pointsEarned", pointsEarned);

		db.collection("users").document(userId)
				.collection("activityLogs").document(logId)
				.update(updates)
				.addOnSuccessListener(aVoid -> callback.onComplete(true))
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	/**
	 * Deletes this log entry from Firestore.
	 *
	 * <p>On success, triggers a recalculation of the user's total points
	 * via {@link ProgressTracker}.</p>
	 *
	 * @param callback Invoked with {@code true} on success.
	 */
	public void deleteLog(User.OnCompleteCallback callback) {
		if (logId == null || userId == null) {
			callback.onComplete(false);
			return;
		}

		db.collection("users")
				.document(userId)
				.collection("activityLogs")
				.document(logId)
				.delete()
				.addOnSuccessListener(aVoid -> {
					// TODO: Trigger ProgressTracker to subtract this log's
					//       contribution from the user's running totals.
					callback.onComplete(true);
				})
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	// -------------------------------------------------------------------------
	// Retrieval helpers
	// -------------------------------------------------------------------------

	/**
	 * Fetches all log entries for the specified user within a date range.
	 *
	 * <p>Results are ordered by {@code date} descending (most recent first).</p>
	 *
	 * @param targetUserId UID of the user whose logs to fetch.
	 * @param from         Start of the date range (inclusive).
	 * @param to           End of the date range (inclusive).
	 * @param callback     Receives the list of matching log entries.
	 */
	public static void getLogsForUser(
			String targetUserId, Timestamp from, Timestamp to,
			OnLogsLoadedCallback callback
	) {
		FirebaseFirestore db = FirebaseFirestore.getInstance();

		db.collection("users")
				.document(targetUserId)
				.collection("activityLogs")
				.whereGreaterThanOrEqualTo("date", from)
				.whereLessThanOrEqualTo("date", to)
				.orderBy("date", Query.Direction.DESCENDING)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					List<ActivityLog> logs = new ArrayList<>();
					for (var doc : querySnapshot.getDocuments()) {
						ActivityLog log = doc.toObject(ActivityLog.class);
						if (log != null) {
							log.setLogId(doc.getId());
							logs.add(log);
						}
					}
					callback.onLogsLoaded(logs);
				})
				.addOnFailureListener(e -> callback.onLogsLoaded(new ArrayList<>()));
	}

	/**
	 * Fetches today's log entries for the specified user, used to populate
	 * the "Daily History" view and enforce the daily-edit window.
	 *
	 * @param targetUserId UID of the user.
	 * @param callback     Receives today's log entries.
	 */
	public static void getTodaysLogs(String targetUserId, OnLogsLoadedCallback callback) {
		Calendar startOfDay = Calendar.getInstance();
		startOfDay.set(Calendar.HOUR_OF_DAY, 0);
		startOfDay.set(Calendar.MINUTE, 0);
		startOfDay.set(Calendar.SECOND, 0);
		startOfDay.set(Calendar.MILLISECOND, 0);

		Calendar endOfDay = (Calendar) startOfDay.clone();
		endOfDay.add(Calendar.DAY_OF_MONTH, 1);

		Timestamp from = new Timestamp(startOfDay.getTimeInMillis() / 1000, 0);
		Timestamp to = new Timestamp(endOfDay.getTimeInMillis() / 1000, 0);

		getLogsForUser(targetUserId, from, to, callback);
	}

	// -------------------------------------------------------------------------
	// Callback interfaces
	// -------------------------------------------------------------------------

	/**
	 * Callback for async log retrieval operations.
	 */
	public interface OnLogsLoadedCallback {
		/**
		 * @param logs List of loaded {@link ActivityLog} entries; empty on failure.
		 */
		void onLogsLoaded(List<ActivityLog> logs);
	}

	// -------------------------------------------------------------------------
	// Getters and Setters
	// -------------------------------------------------------------------------

	public String getLogId() {
		return logId;
	}

	public void setLogId(String v) {
		logId = v;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String v) {
		userId = v;
	}

	public Timestamp getDate() {
		return date;
	}

	public void setDate(Timestamp v) {
		date = v;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String v) {
		category = v;
	}

	public String getTransportMode() {
		return transportMode;
	}

	public void setTransportMode(String v) {
		transportMode = v;
	}

	public double getDistanceKm() {
		return distanceKm;
	}

	public void setDistanceKm(double v) {
		distanceKm = v;
	}

	public double getEnergyKwh() {
		return energyKwh;
	}

	public void setEnergyKwh(double v) {
		energyKwh = v;
	}

	public String getWasteType() {
		return wasteType;
	}

	public void setWasteType(String v) {
		wasteType = v;
	}

	public double getWasteKg() {
		return wasteKg;
	}

	public void setWasteKg(double v) {
		wasteKg = v;
	}

	public double getCo2EquivalentKg() {
		return co2EquivalentKg;
	}

	public void setCo2EquivalentKg(double v) {
		co2EquivalentKg = v;
	}

	public double getPointsEarned() {
		return pointsEarned;
	}

	public void setPointsEarned(double v) {
		pointsEarned = v;
	}

	public double getAnomalyScore() {
		return anomalyScore;
	}

	public void setAnomalyScore(double v) {
		anomalyScore = v;
	}

	public boolean isFlagged() {
		return isFlagged;
	}

	public void setFlagged(boolean v) {
		isFlagged = v;
	}
}
