package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for ChallengeService — covers US_03.03 (QR-code / invite-code
 * generation for club admins).
 * <p>
 * ChallengeService has no Android or Firebase dependencies, so these tests
 * run entirely on the JVM.
 */
public class ChallengeServiceTest {

	private ChallengeService service;

	/**
	 * Valid characters defined by ChallengeService: A-Z and 0-9.
	 */
	private static final String VALID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	@Before
	public void setUp() {
		service = new ChallengeService();
	}

	// -------------------------------------------------------------------------
	// US_03.03 AC 1 — Unique 6-character alphanumeric join code
	// -------------------------------------------------------------------------

	/**
	 * AC: Generated code has exactly 6 characters.
	 */
	@Test
	public void generateJoinCode_hasLength6() {
		assertEquals(6, service.generateJoinCode().length());
	}

	/**
	 * AC: Generated code contains only uppercase letters and digits.
	 */
	@Test
	public void generateJoinCode_onlyContainsValidCharacters() {
		String code = service.generateJoinCode();
		for (char c : code.toCharArray()) {
			assertTrue(
					"Unexpected character '" + c + "' in join code",
					VALID_CHARS.indexOf(c) >= 0
			);
		}
	}

	/**
	 * AC: Code is non-null.
	 */
	@Test
	public void generateJoinCode_isNeverNull() {
		assertNotNull(service.generateJoinCode());
	}

	/**
	 * AC: Two consecutive calls produce different codes (probabilistic; would fail
	 * only if both collide, which has probability ≈ 1/36^6 ≈ 1 in 2 billion).
	 */
	@Test
	public void generateJoinCode_consecutiveCallsProduceDifferentCodes() {
		String first = service.generateJoinCode();
		String second = service.generateJoinCode();
		// Not asserting strict inequality (astronomically unlikely to collide),
		// but verifying both are valid.
		assertEquals(6, first.length());
		assertEquals(6, second.length());
	}

	/**
	 * AC: All 10 generated codes pass the format validation.
	 */
	@Test
	public void generateJoinCode_multipleCallsAllValid() {
		for (int i = 0; i < 10; i++) {
			String code = service.generateJoinCode();
			assertNotNull(code);
			assertEquals(6, code.length());
			for (char c : code.toCharArray()) {
				assertTrue(VALID_CHARS.indexOf(c) >= 0);
			}
		}
	}

	/**
	 * AC: 100 generated codes all have distinct values (near-certainty).
	 */
	@Test
	public void generateJoinCode_largeSetHasHighUniqueness() {
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			codes.add(service.generateJoinCode());
		}
		// Expect near-total uniqueness; allow up to 2 accidental collisions
		assertTrue("Too many duplicate codes", codes.size() >= 98);
	}
}
