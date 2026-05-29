package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unit tests for AuditLogEntry — covers US_08.02 (Admin Audit Trail).
 * <p>
 * AuditLogEntry is immutable after creation. Tests verify that all fields
 * are set via the constructor, that action constants are well-defined, and
 * that no public setter methods exist (enforcing immutability).
 * <p>
 * No Firebase services are needed; Timestamp can be constructed directly.
 */
public class AuditLogEntryTest {

	// -------------------------------------------------------------------------
	// US_08.02 — Constructor / field retrieval
	// -------------------------------------------------------------------------

	/**
	 * AC: Constructor persists all provided fields correctly.
	 */
	@Test
	public void constructor_setsAllFields() {
		Timestamp ts = new Timestamp(1_700_000_000L, 0);
		AuditLogEntry entry = new AuditLogEntry(
				"entry-1",
				"admin-uid",
				AuditLogEntry.ACTION_METRIC_UPDATED,
				"BIKE_PER_KM",
				"Admin updated bike metric",
				"{\"value\":0.21}",
				"{\"value\":0.25}",
				ts
		);

		assertEquals("entry-1", entry.getEntryId());
		assertEquals("admin-uid", entry.getAdminId());
		assertEquals(AuditLogEntry.ACTION_METRIC_UPDATED, entry.getActionType());
		assertEquals("BIKE_PER_KM", entry.getTargetEntityId());
		assertEquals("Admin updated bike metric", entry.getDescription());
		assertEquals("{\"value\":0.21}", entry.getPreviousValue());
		assertEquals("{\"value\":0.25}", entry.getNewValue());
		assertEquals(ts, entry.getTimestamp());
	}

	/**
	 * AC: targetEntityId may be null for system-wide actions.
	 */
	@Test
	public void constructor_nullTargetEntityId_isAllowed() {
		AuditLogEntry entry = new AuditLogEntry(
				"e1", "admin", AuditLogEntry.ACTION_GOAL_CREATED,
				null, "Goal created", null, null,
				new Timestamp(0L, 0)
		);
		assertNull(entry.getTargetEntityId());
	}

	/**
	 * AC: previousValue and newValue may be null when not applicable.
	 */
	@Test
	public void constructor_nullPreviousAndNewValue_areAllowed() {
		AuditLogEntry entry = new AuditLogEntry(
				"e1", "admin", AuditLogEntry.ACTION_CHALLENGE_CREATED,
				"c1", "Challenge created", null, null,
				new Timestamp(0L, 0)
		);
		assertNull(entry.getPreviousValue());
		assertNull(entry.getNewValue());
	}

	// -------------------------------------------------------------------------
	// US_08.02 — Action type constants
	// -------------------------------------------------------------------------

	@Test
	public void actionConstant_metricUpdated_hasCorrectValue() {
		assertEquals("METRIC_UPDATED", AuditLogEntry.ACTION_METRIC_UPDATED);
	}

	@Test
	public void actionConstant_userPrivacyChanged_hasCorrectValue() {
		assertEquals("USER_PRIVACY_CHANGED", AuditLogEntry.ACTION_USER_PRIVACY_CHANGED);
	}

	@Test
	public void actionConstant_userDataDeleted_hasCorrectValue() {
		assertEquals("USER_DATA_DELETED", AuditLogEntry.ACTION_USER_DATA_DELETED);
	}

	@Test
	public void actionConstant_userDataExported_hasCorrectValue() {
		assertEquals("USER_DATA_EXPORTED", AuditLogEntry.ACTION_USER_DATA_EXPORTED);
	}

	@Test
	public void actionConstant_passwordReset_hasCorrectValue() {
		assertEquals("PASSWORD_RESET", AuditLogEntry.ACTION_PASSWORD_RESET);
	}

	@Test
	public void actionConstant_goalCreated_hasCorrectValue() {
		assertEquals("GOAL_CREATED", AuditLogEntry.ACTION_GOAL_CREATED);
	}

	@Test
	public void actionConstant_goalArchived_hasCorrectValue() {
		assertEquals("GOAL_ARCHIVED", AuditLogEntry.ACTION_GOAL_ARCHIVED);
	}

	@Test
	public void actionConstant_tipPublished_hasCorrectValue() {
		assertEquals("TIP_PUBLISHED", AuditLogEntry.ACTION_TIP_PUBLISHED);
	}

	@Test
	public void actionConstant_tipDeleted_hasCorrectValue() {
		assertEquals("TIP_DELETED", AuditLogEntry.ACTION_TIP_DELETED);
	}

	@Test
	public void actionConstant_challengeCreated_hasCorrectValue() {
		assertEquals("CHALLENGE_CREATED", AuditLogEntry.ACTION_CHALLENGE_CREATED);
	}

	// -------------------------------------------------------------------------
	// US_08.02 — Immutability: no public setter methods
	// -------------------------------------------------------------------------

	/**
	 * AC: AuditLogEntry is documented as immutable once created.
	 * Verify that no public setter methods exist on the class.
	 */
	@Test
	public void auditLogEntry_hasNoPublicSetterMethods() {
		Set<String> setters = Arrays.stream(AuditLogEntry.class.getMethods())
				.filter(m -> Modifier.isPublic(m.getModifiers()))
				.filter(m -> m.getName().startsWith("set"))
				.map(Method::getName)
				.collect(Collectors.toSet());

		assertTrue(
				"AuditLogEntry should have no public setters but found: " + setters,
				setters.isEmpty()
		);
	}
}
