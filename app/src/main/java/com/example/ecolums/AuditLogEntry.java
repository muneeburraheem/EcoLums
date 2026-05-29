package com.example.ecolums;

import com.google.firebase.Timestamp;

/**
 * Immutable record of an administrative action performed on the system.
 *
 * <p>Stored in the Firestore collection {@code auditLog}. Entries are written
 * by {@link AdminDashboard} whenever a privileged action is taken, such as
 * changing a calculation metric, modifying user privacy settings, or deleting
 * user data. Entries are read-only once created.</p>
 * <p>
 * Outstanding issues: None at this time.
 */
public class AuditLogEntry {

	// -------------------------------------------------------------------------
	// Constants — action types
	// -------------------------------------------------------------------------

	public static final String ACTION_METRIC_UPDATED = "METRIC_UPDATED";
	public static final String ACTION_USER_PRIVACY_CHANGED = "USER_PRIVACY_CHANGED";
	public static final String ACTION_USER_DATA_DELETED = "USER_DATA_DELETED";
	public static final String ACTION_USER_DATA_EXPORTED = "USER_DATA_EXPORTED";
	public static final String ACTION_PASSWORD_RESET = "PASSWORD_RESET";
	public static final String ACTION_GOAL_CREATED = "GOAL_CREATED";
	public static final String ACTION_GOAL_ARCHIVED = "GOAL_ARCHIVED";
	public static final String ACTION_TIP_PUBLISHED = "TIP_PUBLISHED";
	public static final String ACTION_TIP_DELETED = "TIP_DELETED";
	public static final String ACTION_CHALLENGE_CREATED = "CHALLENGE_CREATED";

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firestore document ID.
	 */
	private String entryId;

	/**
	 * UID of the admin who performed the action.
	 */
	private String adminId;

	/**
	 * One of the ACTION_* constants describing what was done.
	 */
	private String actionType;

	/**
	 * ID of the entity that was acted upon (e.g. a userId, metricKey, goalId).
	 * Null if the action is system-wide.
	 */
	private String targetEntityId;

	/**
	 * Human-readable description of the action for display in the audit log UI.
	 */
	private String description;

	/**
	 * Serialized previous value before the change (JSON string). Null if not applicable.
	 */
	private String previousValue;

	/**
	 * Serialized new value after the change (JSON string). Null if not applicable.
	 */
	private String newValue;

	/**
	 * When the action occurred.
	 */
	private Timestamp timestamp;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Required no-arg constructor for Firestore deserialization.
	 */
	public AuditLogEntry() {
	}

	/**
	 * Creates a new AuditLogEntry.
	 *
	 * @param entryId        Firestore document ID.
	 * @param adminId        UID of the acting admin.
	 * @param actionType     One of the ACTION_* constants.
	 * @param targetEntityId ID of the affected entity (may be null).
	 * @param description    Human-readable summary.
	 * @param previousValue  JSON-serialized old value (may be null).
	 * @param newValue       JSON-serialized new value (may be null).
	 * @param timestamp      When the action occurred.
	 */
	public AuditLogEntry(
			String entryId, String adminId, String actionType,
			String targetEntityId, String description,
			String previousValue, String newValue,
			Timestamp timestamp
	) {
		this.entryId = entryId;
		this.adminId = adminId;
		this.actionType = actionType;
		this.targetEntityId = targetEntityId;
		this.description = description;
		this.previousValue = previousValue;
		this.newValue = newValue;
		this.timestamp = timestamp;
	}

	// -------------------------------------------------------------------------
	// Getters (no setters — audit entries are immutable after creation)
	// -------------------------------------------------------------------------

	public String getEntryId() {
		return entryId;
	}

	public String getAdminId() {
		return adminId;
	}

	public String getActionType() {
		return actionType;
	}

	public String getTargetEntityId() {
		return targetEntityId;
	}

	public String getDescription() {
		return description;
	}

	public String getPreviousValue() {
		return previousValue;
	}

	public String getNewValue() {
		return newValue;
	}

	public Timestamp getTimestamp() {
		return timestamp;
	}
}
