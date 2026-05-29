package com.example.ecolums;

import com.google.firebase.Timestamp;

/**
 * Represents a single CO2 conversion factor or impact-calculation metric
 * managed by the Campus Sustainability Office.
 *
 * <p>Stored in the Firestore collection {@code calculationMetrics}. Admins
 * update these via {@link AdminDashboard}; {@link ActivityLog} reads them
 * when converting raw inputs into CO2 equivalents.</p>
 *
 * <p>Example: metricKey = "BIKE_PER_KM", value = 0.21, unit = "kg CO2/km".</p>
 * <p>
 * Outstanding issues: Unit normalisation (imperial vs metric) not yet handled.
 */
public class CalculationMetric {

	// -------------------------------------------------------------------------
	// Well-known metric keys (used throughout the application)
	// -------------------------------------------------------------------------

	public static final String KEY_BIKE_PER_KM = "BIKE_PER_KM";
	public static final String KEY_BUS_PER_KM = "BUS_PER_KM";
	public static final String KEY_CAR_PER_KM = "CAR_PER_KM";
	public static final String KEY_TRAIN_PER_KM = "TRAIN_PER_KM";
	public static final String KEY_WALK_PER_KM = "WALK_PER_KM";
	public static final String KEY_ENERGY_PER_KWH = "ENERGY_PER_KWH";
	public static final String KEY_WASTE_LANDFILL_KG = "LANDFILL_KG";
	public static final String KEY_WASTE_RECYCLE_KG = "RECYCLE_KG";
	public static final String KEY_WASTE_COMPOST_KG = "COMPOST_KG";

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firestore document ID (mirrors {@link #metricKey} for convenience).
	 */
	private String metricId;

	/**
	 * Machine-readable key used when looking up this factor in code.
	 */
	private String metricKey;

	/**
	 * Human-readable label shown in the admin settings UI.
	 */
	private String displayName;

	/**
	 * The conversion factor value (e.g. 0.21 for kg CO2 saved per km biked).
	 */
	private double value;

	/**
	 * Human-readable unit description (e.g. "kg CO2 saved / km").
	 */
	private String unit;

	/**
	 * The environmental standard or reference this factor is based on,
	 * shown to students via the "Info" icon (e.g. "EPA 2024 Emission Factors").
	 */
	private String referenceStandard;

	/**
	 * Timestamp of the most recent update to this metric.
	 */
	private Timestamp lastUpdated;

	/**
	 * UID of the admin who last modified this metric.
	 */
	private String lastUpdatedByAdminId;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Required no-arg constructor for Firestore deserialization.
	 */
	public CalculationMetric() {
	}

	/**
	 * Creates a new CalculationMetric.
	 *
	 * @param metricId          Firestore document ID.
	 * @param metricKey         Machine-readable key (use KEY_* constants).
	 * @param displayName       Label shown in the admin UI.
	 * @param value             Conversion factor.
	 * @param unit              Human-readable unit string.
	 * @param referenceStandard Environmental standard reference.
	 */
	public CalculationMetric(
			String metricId, String metricKey, String displayName,
			double value, String unit, String referenceStandard
	) {
		this.metricId = metricId;
		this.metricKey = metricKey;
		this.displayName = displayName;
		this.value = value;
		this.unit = unit;
		this.referenceStandard = referenceStandard;
		this.lastUpdated = null;
		this.lastUpdatedByAdminId = null;
	}

	// -------------------------------------------------------------------------
	// Getters and Setters
	// -------------------------------------------------------------------------

	public String getMetricId() {
		return metricId;
	}

	public void setMetricId(String v) {
		metricId = v;
	}

	public String getMetricKey() {
		return metricKey;
	}

	public void setMetricKey(String v) {
		metricKey = v;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String v) {
		displayName = v;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double v) {
		value = v;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String v) {
		unit = v;
	}

	public String getReferenceStandard() {
		return referenceStandard;
	}

	public void setReferenceStandard(String v) {
		referenceStandard = v;
	}

	public Timestamp getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(Timestamp v) {
		lastUpdated = v;
	}

	public String getLastUpdatedByAdminId() {
		return lastUpdatedByAdminId;
	}

	public void setLastUpdatedByAdminId(String v) {
		lastUpdatedByAdminId = v;
	}
}
