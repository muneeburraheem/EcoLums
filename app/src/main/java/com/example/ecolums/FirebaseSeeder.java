package com.example.ecolums;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Seeds Firestore with realistic hard-coded data so the prototype is usable
 * immediately after first login without manual data entry.
 * <p>
 * Call FirebaseSeeder.seedIfNeeded() once after the user's first sign-up.
 * Uses a sentinel document "seeded/status" to avoid re-seeding on every launch.
 */
public class FirebaseSeeder {

	private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

	public static void seedIfNeeded() {
		db.collection("seeded").document("status").get()
				.addOnSuccessListener(doc -> {
					if (!doc.exists()) {
						seedAll();
					}
				});
	}

	private static void seedAll() {
		seedCalculationMetrics();
		seedChallenges();
		seedCampusGoal();
		seedSustainabilityTips();
		seedPlatformConfig();

		// Mark as seeded
		Map<String, Object> sentinel = new HashMap<>();
		sentinel.put("done", true);
		db.collection("seeded").document("status").set(sentinel);
	}

	// -------------------------------------------------------------------------
	// Calculation Metrics
	// -------------------------------------------------------------------------
	private static void seedCalculationMetrics() {
		Object[][] metrics = {
				{ "BIKE_PER_KM", "Bike (CO₂ saved/km)", 0.21, "kg CO₂/km", "EPA 2024" },
				{ "WALK_PER_KM", "Walking (CO₂ saved/km)", 0.00, "kg CO₂/km", "EPA 2024" },
				{ "BUS_PER_KM", "Bus (CO₂ saved vs car/km)", 0.08, "kg CO₂/km", "EPA 2024" },
				{ "TRAIN_PER_KM", "Train (CO₂ saved vs car/km)", 0.10, "kg CO₂/km", "EPA 2024" },
				{ "CAR_PER_KM", "Car (CO₂ emitted/km)", -0.21, "kg CO₂/km", "EPA 2024" },
				{ "ENERGY_PER_KWH", "Energy saved (per kWh)", 0.45, "kg CO₂/kWh", "DOE 2024" },
				{ "LANDFILL_KG", "Landfill waste (per kg)", -0.50, "kg CO₂/kg", "IPCC 2021" },
				{ "RECYCLE_KG", "Recycled waste (per kg)", 0.30, "kg CO₂/kg", "IPCC 2021" },
				{ "COMPOST_KG", "Composted waste (per kg)", 0.10, "kg CO₂/kg", "IPCC 2021" },
		};

		for (Object[] m : metrics) {
			Map<String, Object> data = new HashMap<>();
			data.put("metricKey", m[0]);
			data.put("displayName", m[1]);
			data.put("value", m[2]);
			data.put("unit", m[3]);
			data.put("referenceStandard", m[4]);
			data.put("lastUpdated", Timestamp.now());
			data.put("lastUpdatedByAdminId", "seeder");
			db.collection("calculationMetrics").document((String) m[0]).set(data);
		}
	}

	// -------------------------------------------------------------------------
	// Challenges
	// -------------------------------------------------------------------------
	private static void seedChallenges() {
		Calendar cal = Calendar.getInstance();
		Timestamp now = Timestamp.now();

		// End date = 28 days from now
		cal.add(Calendar.DAY_OF_YEAR, 28);
		Timestamp endDate = new Timestamp(cal.getTimeInMillis() / 1000, 0);

		Object[][] challenges = {
				{
						"challenge_1",
						"Energy Saver Sprint",
						"⚡",
						"ENERGY",
						100.0,
						"kWh",
						"Reduce your energy consumption for 7 days straight."
				},
				{
						"challenge_2",
						"Zero Landfill March",
						"🌍",
						"WASTE",
						30.0,
						"kg",
						"Compost or recycle everything — nothing to landfill."
				},
				{
						"challenge_3",
						"Green Commute Challenge",
						"🚌",
						"TRANSPORT",
						500.0,
						"km",
						"Use public transit or active transport for a full month."
				},
		};

		for (Object[] c : challenges) {
			Map<String, Object> data = new HashMap<>();
			data.put("challengeId", c[0]);
			data.put("title", c[1]);
			data.put("description", c[6]);
			data.put("goalType", c[3]);
			data.put("targetValue", c[4]);
			data.put("targetUnit", c[5]);
			data.put("startDate", now);
			data.put("endDate", endDate);
			data.put("status", Challenge.STATUS_ACTIVE);
			data.put("creatorId", "seeder");
			data.put("participantIds", new java.util.ArrayList<>());
			data.put("participantScores", new HashMap<>());
			db.collection("challenges").document((String) c[0]).set(data);
		}
	}

	// -------------------------------------------------------------------------
	// Campus Goal
	// -------------------------------------------------------------------------
	private static void seedCampusGoal() {
		Map<String, Object> goal = new HashMap<>();
		goal.put("title", "Save 10,000 kg CO₂ this semester");
		goal.put("metricType", CampusGoal.METRIC_CO2);
		goal.put("targetValue", 10000.0);
		goal.put("currentProgress", 1115.0);
		goal.put("status", CampusGoal.STATUS_ACTIVE);
		goal.put("startDate", Timestamp.now());

		Calendar end = Calendar.getInstance();
		end.add(Calendar.MONTH, 4);
		goal.put("endDate", new Timestamp(end.getTimeInMillis() / 1000, 0));
		goal.put("notificationMilestones", Arrays.asList(25, 50, 75, 100));

		db.collection("campusGoals").document("goal_main").set(goal);
	}

	// -------------------------------------------------------------------------
	// Sustainability Tips
	// -------------------------------------------------------------------------
	private static void seedSustainabilityTips() {
		Object[][] tips = {
				{
						"tip_1",
						"10 Ways to Reduce Waste on Campus",
						"Simple daily habits to cut your campus footprint.",
						"Waste",
						"From reusable cups to composting bins, small swaps make a huge difference. " +
								"Bring your own bag, refuse plastic straws, and choose the recycling bin over the trash."
				},
				{
						"tip_2",
						"Why Biking Saves More Than You Think",
						"Every km on a bike saves 210g of CO₂ vs a car.",
						"Transport",
						"Cycling to campus not only cuts emissions but also improves mental health. " +
								"A 5 km bike ride saves over 1 kg of CO₂ compared to driving alone."
				},
				{
						"tip_3",
						"The Student Guide to Saving Energy",
						"Quick wins for slashing your electricity bill and footprint.",
						"Energy",
						"Turn off lights when leaving. Unplug chargers. Use cold water for laundry. " +
								"Each kWh you save keeps 450g of CO₂ out of the atmosphere."
				},
				{
						"tip_4", "Composting 101",
						"Turn your food scraps into campus gold.", "Waste",
						"Composting diverts organic waste from landfill and produces rich soil. " +
								"Most campuses have composting drop-off bins near cafeterias."
				},
		};

		for (Object[] t : tips) {
			Map<String, Object> data = new HashMap<>();
			data.put("tipId", t[0]);
			data.put("title", t[1]);
			data.put("summary", t[2]);
			data.put("category", t[3]);
			data.put("bodyContent", t[4]);
			data.put("publishedAt", Timestamp.now());
			data.put("viewCount", 0);
			data.put("likeCount", 0);
			data.put("isNew", true);
			data.put("authorAdminId", "seeder");
			db.collection("sustainabilityTips").document((String) t[0]).set(data);
		}
	}

	// -------------------------------------------------------------------------
	// Platform Config (Privacy Defaults)
	// -------------------------------------------------------------------------
	private static void seedPlatformConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("shareDataWithClubsByDefault", false);
		config.put("showOnLeaderboardByDefault", true);
		config.put("lastUpdated", Timestamp.now());
		db.collection("platformConfig").document("privacySettings").set(config);
	}
}
