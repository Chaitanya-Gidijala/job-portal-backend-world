package com.job.portal.growth;

import com.job.portal.entity.DailyChallengeResult;
import com.job.portal.monitoring.VisitorProfile;
import com.job.portal.monitoring.VisitorProfileRepository;
import com.job.portal.repository.DailyChallengeResultRepository;
import com.job.portal.service.DailyChallengeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Daily puzzle leaderboards: today, this week (last 7 puzzles) and streaks.
 *
 * Players are shown as "First L." when signed in (never full names or emails),
 * otherwise as "Learner ABCD". The requesting learner is flagged so the UI can
 * highlight them and show their rank even outside the top list.
 */
@Service
public class LeaderboardService {

    public record Entry(int rank, String name, boolean you, boolean signedIn, int score, long durationMs, int played, int streak) {
    }

    private final DailyChallengeResultRepository results;
    private final VisitorProfileRepository profiles;
    private final DailyChallengeService daily;

    public LeaderboardService(DailyChallengeResultRepository results, VisitorProfileRepository profiles, DailyChallengeService daily) {
        this.results = results;
        this.profiles = profiles;
        this.daily = daily;
    }

    /** "Chaitanya Gidijala" → "Chaitanya G." ; anonymous → "Learner 3FA2". */
    static String displayName(VisitorProfile p, String learnerId) {
        if (p != null && p.getName() != null && !p.getName().isBlank()) {
            String[] parts = p.getName().trim().split("\\s+");
            String first = parts[0].length() > 18 ? parts[0].substring(0, 18) : parts[0];
            return parts.length > 1 ? first + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + "." : first;
        }
        return "Learner " + learnerId.substring(0, Math.min(4, learnerId.length())).toUpperCase();
    }

    private Map<String, VisitorProfile> profilesFor(Collection<String> learnerIds) {
        if (learnerIds.isEmpty()) return Map.of();
        return profiles.findByVisitorIdIn(learnerIds).stream()
                .collect(Collectors.toMap(VisitorProfile::getVisitorId, p -> p, (a, b) -> a));
    }

    public Map<String, Object> today(String learnerId, int limit) {
        int day = daily.currentDay();
        List<DailyChallengeResult> all = results.findByChallengeDayBetween(day, day);
        all.sort(Comparator.comparingInt(DailyChallengeResult::getScore).reversed().thenComparingLong(DailyChallengeResult::getDurationMs));
        Map<String, VisitorProfile> names = profilesFor(all.stream().map(DailyChallengeResult::getLearnerId).toList());
        List<Entry> entries = new ArrayList<>();
        Entry me = null;
        for (int i = 0; i < all.size(); i++) {
            DailyChallengeResult r = all.get(i);
            VisitorProfile p = names.get(r.getLearnerId());
            Entry e = new Entry(i + 1, displayName(p, r.getLearnerId()), r.getLearnerId().equals(learnerId),
                    p != null && p.getName() != null, r.getScore(), r.getDurationMs(), 1, 0);
            if (e.you()) me = e;
            if (i < limit) entries.add(e);
        }
        return Map.of("day", day, "players", all.size(), "entries", entries, "you", me == null ? Map.of() : me);
    }

    /** Last 7 puzzles: total score, then total time. */
    public Map<String, Object> week(String learnerId, int limit) {
        int day = daily.currentDay();
        List<DailyChallengeResult> all = results.findByChallengeDayBetween(Math.max(1, day - 6), day);
        record Agg(String id, int score, long time, int played) {
        }
        Map<String, Agg> by = new HashMap<>();
        for (DailyChallengeResult r : all) {
            by.merge(r.getLearnerId(), new Agg(r.getLearnerId(), r.getScore(), r.getDurationMs(), 1),
                    (a, b) -> new Agg(a.id(), a.score() + b.score(), a.time() + b.time(), a.played() + b.played()));
        }
        List<Agg> sorted = by.values().stream()
                .sorted(Comparator.comparingInt(Agg::score).reversed()
                        .thenComparing(Comparator.comparingInt(Agg::played).reversed())
                        .thenComparingLong(Agg::time))
                .toList();
        Map<String, VisitorProfile> names = profilesFor(by.keySet());
        List<Entry> entries = new ArrayList<>();
        Entry me = null;
        for (int i = 0; i < sorted.size(); i++) {
            Agg a = sorted.get(i);
            VisitorProfile p = names.get(a.id());
            Entry e = new Entry(i + 1, displayName(p, a.id()), a.id().equals(learnerId), p != null && p.getName() != null,
                    a.score(), a.time(), a.played(), 0);
            if (e.you()) me = e;
            if (i < limit) entries.add(e);
        }
        return Map.of("from", Math.max(1, day - 6), "to", day, "players", sorted.size(), "entries", entries, "you", me == null ? Map.of() : me);
    }

    /** Consecutive puzzle days ending today or yesterday (same rule as the browser streak). */
    public Map<String, Object> streaks(String learnerId, int limit) {
        int day = daily.currentDay();
        List<DailyChallengeResult> all = results.findByChallengeDayBetween(Math.max(1, day - 120), day);
        Map<String, Set<Integer>> days = new HashMap<>();
        for (DailyChallengeResult r : all) days.computeIfAbsent(r.getLearnerId(), k -> new HashSet<>()).add(r.getChallengeDay());
        record S(String id, int streak, int played) {
        }
        List<S> sorted = days.entrySet().stream()
                .map(e -> new S(e.getKey(), currentStreak(e.getValue(), day), e.getValue().size()))
                .filter(s -> s.streak() > 0)
                .sorted(Comparator.comparingInt(S::streak).reversed().thenComparing(Comparator.comparingInt(S::played).reversed()))
                .toList();
        Map<String, VisitorProfile> names = profilesFor(sorted.stream().map(S::id).toList());
        List<Entry> entries = new ArrayList<>();
        Entry me = null;
        for (int i = 0; i < sorted.size(); i++) {
            S s = sorted.get(i);
            VisitorProfile p = names.get(s.id());
            Entry e = new Entry(i + 1, displayName(p, s.id()), s.id().equals(learnerId), p != null && p.getName() != null,
                    0, 0, s.played(), s.streak());
            if (e.you()) me = e;
            if (i < limit) entries.add(e);
        }
        return Map.of("players", sorted.size(), "entries", entries, "you", me == null ? Map.of() : me);
    }

    static int currentStreak(Set<Integer> days, int today) {
        int cursor = days.contains(today) ? today : days.contains(today - 1) ? today - 1 : -1;
        int streak = 0;
        while (cursor > 0 && days.contains(cursor)) {
            streak++;
            cursor--;
        }
        return streak;
    }

    /** Top N of today (used in emails and the admin page). */
    public List<Entry> topToday(int n) {
        int day = daily.currentDay();
        List<DailyChallengeResult> top = results.findByChallengeDayOrderByScoreDescDurationMsAsc(day, PageRequest.of(0, n));
        Map<String, VisitorProfile> names = profilesFor(top.stream().map(DailyChallengeResult::getLearnerId).toList());
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            DailyChallengeResult r = top.get(i);
            VisitorProfile p = names.get(r.getLearnerId());
            out.add(new Entry(i + 1, displayName(p, r.getLearnerId()), false, p != null && p.getName() != null, r.getScore(), r.getDurationMs(), 1, 0));
        }
        return out;
    }
}
