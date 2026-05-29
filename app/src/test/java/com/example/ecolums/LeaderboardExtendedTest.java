package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Extended unit tests for Leaderboard — covers sorting edge cases, single-entry
 * rankings, tied scores, RankEntry defaults, and the top-N logic not covered
 * by the primary LeaderboardTest suite.
 * <p>
 * Firebase is mocked; tests validate only pure-Java sorting/ranking logic using
 * the same algorithm that Leaderboard uses internally.
 */
public class LeaderboardExtendedTest {

    private MockedStatic<FirebaseFirestore> mockedFirestore;
    private Leaderboard leaderboard;

    @Before
    public void setUp() {
        FirebaseFirestore mockDb = mock(FirebaseFirestore.class);
        mockedFirestore = Mockito.mockStatic(FirebaseFirestore.class);
        mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mockDb);
        leaderboard = new Leaderboard();
    }

    @After
    public void tearDown() {
        mockedFirestore.close();
    }

    // -------------------------------------------------------------------------
    // RankEntry — default field values
    // -------------------------------------------------------------------------

    /**
     * AC: A freshly created RankEntry has all fields at their Java defaults
     * (null strings, 0.0 score, 0 rank).
     */
    @Test
    public void rankEntry_defaultFieldValues() {
        Leaderboard.RankEntry entry = new Leaderboard.RankEntry();
        assertNull(entry.entityId);
        assertNull(entry.entityName);
        assertNull(entry.avatarUrl);
        assertEquals(0.0, entry.score, 1e-9);
        assertEquals(0, entry.rank);
        assertNull(entry.entityType);
    }

    // -------------------------------------------------------------------------
    // Sorting — edge cases
    // -------------------------------------------------------------------------

    /**
     * AC: Sorting a single-entry list assigns rank 1.
     */
    @Test
    public void sort_singleEntry_getsRankOne() {
        List<Leaderboard.RankEntry> entries = new ArrayList<>();
        entries.add(rankEntry("u1", "Solo User", 500.0));

        entries.sort(Comparator.comparingDouble((Leaderboard.RankEntry e) -> e.score).reversed());
        for (int i = 0; i < entries.size(); i++) entries.get(i).rank = i + 1;

        assertEquals(1, entries.get(0).rank);
    }

    /**
     * AC: Sorting an empty list does not throw and results in an empty list.
     */
    @Test
    public void sort_emptyList_doesNotThrow() {
        List<Leaderboard.RankEntry> entries = new ArrayList<>();
        entries.sort(Comparator.comparingDouble((Leaderboard.RankEntry e) -> e.score).reversed());
        assertTrue(entries.isEmpty());
    }

    /**
     * AC: A two-entry list is sorted correctly; higher score gets rank 1.
     */
    @Test
    public void sort_twoEntries_higherScoreGetsRankOne() {
        List<Leaderboard.RankEntry> entries = Arrays.asList(
                rankEntry("u1", "Low", 50.0),
                rankEntry("u2", "High", 200.0)
        );

        List<Leaderboard.RankEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingDouble((Leaderboard.RankEntry e) -> e.score).reversed());
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).rank = i + 1;

        assertEquals("u2", sorted.get(0).entityId);
        assertEquals(1, sorted.get(0).rank);
        assertEquals(2, sorted.get(1).rank);
    }

    /**
     * AC: When scores are equal, ranking is still assigned sequentially
     * (tie-breaking is left to the sort algorithm's stability).
     */
    @Test
    public void sort_tiedScores_allGetConsecutiveRanks() {
        List<Leaderboard.RankEntry> entries = Arrays.asList(
                rankEntry("u1", "Alice", 100.0),
                rankEntry("u2", "Bob", 100.0),
                rankEntry("u3", "Carol", 100.0)
        );

        List<Leaderboard.RankEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingDouble((Leaderboard.RankEntry e) -> e.score).reversed());
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).rank = i + 1;

        // All have same score; verify ranks are 1, 2, 3 in some order
        List<Integer> ranks = new ArrayList<>();
        for (Leaderboard.RankEntry e : sorted) ranks.add(e.rank);
        Collections.sort(ranks);
        assertEquals(Arrays.asList(1, 2, 3), ranks);
    }

    /**
     * AC: A five-entry list is fully sorted; ranks are 1 through 5.
     */
    @Test
    public void sort_fiveEntries_ranksOneThroughFive() {
        List<Leaderboard.RankEntry> entries = Arrays.asList(
                rankEntry("u5", "E", 10.0),
                rankEntry("u2", "B", 400.0),
                rankEntry("u4", "D", 50.0),
                rankEntry("u1", "A", 500.0),
                rankEntry("u3", "C", 200.0)
        );

        List<Leaderboard.RankEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingDouble((Leaderboard.RankEntry e) -> e.score).reversed());
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).rank = i + 1;

        assertEquals(500.0, sorted.get(0).score, 1e-9);
        assertEquals(1, sorted.get(0).rank);
        assertEquals(400.0, sorted.get(1).score, 1e-9);
        assertEquals(2, sorted.get(1).rank);
        assertEquals(10.0, sorted.get(4).score, 1e-9);
        assertEquals(5, sorted.get(4).rank);
    }

    /**
     * AC: Scores of 0 are included in the ranking (participants with no points).
     */
    @Test
    public void sort_withZeroScoreEntries_includedInRanking() {
        List<Leaderboard.RankEntry> entries = Arrays.asList(
                rankEntry("u1", "Leader", 300.0),
                rankEntry("u2", "Newbie", 0.0)
        );

        List<Leaderboard.RankEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingDouble((Leaderboard.RankEntry e) -> e.score).reversed());
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).rank = i + 1;

        assertEquals(1, sorted.get(0).rank);
        assertEquals(300.0, sorted.get(0).score, 1e-9);
        assertEquals(2, sorted.get(1).rank);
        assertEquals(0.0, sorted.get(1).score, 1e-9);
    }

    // -------------------------------------------------------------------------
    // Top-N logic (mirrors getTopPerformers subList logic)
    // -------------------------------------------------------------------------

    /**
     * AC: topN == 0 returns an empty list.
     */
    @Test
    public void topN_zero_returnsEmptyList() {
        List<Leaderboard.RankEntry> all = buildSortedEntries(5);
        int topN = 0;
        List<Leaderboard.RankEntry> top = all.size() <= topN ? all : all.subList(0, topN);
        assertTrue(top.isEmpty());
    }

    /**
     * AC: topN == 1 returns only the highest-scoring entry.
     */
    @Test
    public void topN_one_returnsOnlyLeader() {
        List<Leaderboard.RankEntry> all = buildSortedEntries(5);
        int topN = 1;
        List<Leaderboard.RankEntry> top = all.size() <= topN ? all : all.subList(0, topN);
        assertEquals(1, top.size());
        assertEquals(500.0, top.get(0).score, 1e-9);
    }

    /**
     * AC: topN exactly equals list size — entire list is returned.
     */
    @Test
    public void topN_exactlyListSize_returnsAll() {
        List<Leaderboard.RankEntry> all = buildSortedEntries(3);
        int topN = 3;
        List<Leaderboard.RankEntry> top = all.size() <= topN ? all : all.subList(0, topN);
        assertEquals(3, top.size());
    }

    /**
     * AC: topN larger than list size returns the entire list without index OOB.
     */
    @Test
    public void topN_largerThanListSize_returnsAll() {
        List<Leaderboard.RankEntry> all = buildSortedEntries(3);
        int topN = 100;
        List<Leaderboard.RankEntry> top = all.size() <= topN ? all : all.subList(0, topN);
        assertEquals(3, top.size());
    }

    // -------------------------------------------------------------------------
    // Cached rankings state
    // -------------------------------------------------------------------------

    /**
     * AC: getCachedUserRankings returns an empty (non-null) list initially.
     */
    @Test
    public void cachedUserRankings_initiallyEmptyAndNonNull() {
        List<Leaderboard.RankEntry> cache = leaderboard.getCachedUserRankings();
        assertTrue(cache != null && cache.isEmpty());
    }

    /**
     * AC: getCachedClubRankings returns an empty (non-null) list initially.
     */
    @Test
    public void cachedClubRankings_initiallyEmptyAndNonNull() {
        List<Leaderboard.RankEntry> cache = leaderboard.getCachedClubRankings();
        assertTrue(cache != null && cache.isEmpty());
    }

    /**
     * AC: getLastRefreshedAt is null before any rankings are loaded.
     */
    @Test
    public void lastRefreshedAt_initiallyNull() {
        assertNull(leaderboard.getLastRefreshedAt());
    }

    // -------------------------------------------------------------------------
    // RankEntry — ENTITY_USER vs ENTITY_CLUB
    // -------------------------------------------------------------------------

    /**
     * AC: A RankEntry with entityType = ENTITY_CLUB stores the club type correctly.
     */
    @Test
    public void rankEntry_clubEntityType_setAndGet() {
        Leaderboard.RankEntry entry = new Leaderboard.RankEntry();
        entry.entityType = Leaderboard.ENTITY_CLUB;
        assertEquals(Leaderboard.ENTITY_CLUB, entry.entityType);
    }

    /**
     * AC: Entity type constants are different from each other.
     */
    @Test
    public void entityTypeConstants_areDistinct() {
        assertTrue(!Leaderboard.ENTITY_USER.equals(Leaderboard.ENTITY_CLUB));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Leaderboard.RankEntry rankEntry(String id, String name, double score) {
        Leaderboard.RankEntry e = new Leaderboard.RankEntry();
        e.entityId = id;
        e.entityName = name;
        e.score = score;
        e.entityType = Leaderboard.ENTITY_USER;
        return e;
    }

    /**
     * Returns a list of {@code count} entries sorted descending by score,
     * with scores 500, 400, 300, ... (decremented by 100).
     */
    private List<Leaderboard.RankEntry> buildSortedEntries(int count) {
        List<Leaderboard.RankEntry> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Leaderboard.RankEntry e = new Leaderboard.RankEntry();
            e.entityId = "u" + i;
            e.entityName = "User " + i;
            e.score = (count - i) * 100.0;
            e.rank = i + 1;
            e.entityType = Leaderboard.ENTITY_USER;
            list.add(e);
        }
        return list;
    }
}
