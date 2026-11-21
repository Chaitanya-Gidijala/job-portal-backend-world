package com.job.portal.growth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Cross-device progress for signed-in learners, and the certificates it unlocks.
 *
 * Progress is merged, never overwritten: streak days keep the highest XP, roadmap
 * ticks / puzzles / achievements are unions — so playing on a phone and a laptop
 * adds up instead of one device clobbering the other.
 */
@Service
public class ProgressService {

    public static final int MAX_BYTES = 100_000;

    /**
     * Certificate catalogue. Roadmap totals must match the frontend roadmap data
     * (src/features/roadmap/data) — the server checks the synced ticks against them.
     */
    public record Kind(String id, String title, String description) {
    }

    static final Map<String, Integer> ROADMAP_TOPICS = Map.of("java", 154, "frontend", 122);

    static final List<Kind> KINDS = List.of(
            new Kind("ROADMAP_JAVA", "Java Backend Developer Roadmap", "Completed all 154 topics of the Java Backend Developer roadmap."),
            new Kind("ROADMAP_FRONTEND", "Frontend Developer Roadmap", "Completed all 122 topics of the Frontend Developer roadmap."),
            new Kind("PUZZLES_10", "Traceflow Daily · 10 puzzles", "Solved 10 daily algorithm puzzles."),
            new Kind("PUZZLES_50", "Traceflow Daily · 50 puzzles", "Solved 50 daily algorithm puzzles."),
            new Kind("PUZZLE_STREAK_7", "Traceflow Daily · 7-day streak", "Played the daily algorithm puzzle 7 days in a row."),
            new Kind("LEARNING_STREAK_30", "30-day learning streak", "Learned something on Chaitanya Tech World 30 days in a row."));

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserProgressRepository progress;
    private final CertificateRepository certificates;
    private final ObjectMapper mapper;

    public ProgressService(UserProgressRepository progress, CertificateRepository certificates, ObjectMapper mapper) {
        this.progress = progress;
        this.certificates = certificates;
        this.mapper = mapper;
    }

    /* ── Sync ──────────────────────────────────────────────────────────── */

    public JsonNode get(String username) {
        return progress.findById(username).map(p -> read(p.getData())).orElse(null);
    }

    /** Merge the device's progress into the stored copy and return the merged result. */
    public JsonNode sync(String username, String email, JsonNode incoming) {
        if (incoming == null || !incoming.isObject()) throw new IllegalArgumentException("Progress must be an object");
        UserProgress row = progress.findById(username).orElseGet(() -> {
            UserProgress p = new UserProgress();
            p.setUsername(username);
            return p;
        });
        ObjectNode merged = merge(read(row.getData()), (ObjectNode) incoming.deepCopy());
        String json;
        try {
            json = mapper.writeValueAsString(merged);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unreadable progress");
        }
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("Progress too large");
        row.setData(json);
        row.setEmail(email);
        row.setUpdatedAt(Instant.now());
        progress.save(row);
        return merged;
    }

    private ObjectNode read(String json) {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        try {
            JsonNode n = mapper.readTree(json);
            return n instanceof ObjectNode o ? o : mapper.createObjectNode();
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    static ObjectNode merge(ObjectNode stored, ObjectNode incoming) {
        ObjectNode out = stored.deepCopy();
        out.remove("learnerId");
        out.put("v", 1);

        // days: highest XP per day, action counts take the max
        ObjectNode days = obj(out, "days");
        incoming.path("days").fields().forEachRemaining(e -> {
            JsonNode mine = days.get(e.getKey());
            if (mine == null || e.getValue().path("xp").asInt() > mine.path("xp").asInt()) days.set(e.getKey(), e.getValue());
        });

        // roadmaps: union of ticked topics (keep the earliest date)
        ObjectNode roadmaps = obj(out, "roadmaps");
        incoming.path("roadmaps").fields().forEachRemaining(rm -> {
            ObjectNode target = obj(roadmaps, rm.getKey());
            rm.getValue().fields().forEachRemaining(t -> {
                JsonNode mine = target.get(t.getKey());
                if (mine == null || t.getValue().asText().compareTo(mine.asText()) < 0) target.set(t.getKey(), t.getValue());
            });
        });

        // unions keyed by id: first value wins (stable history)
        for (String key : List.of("topicXp", "challenges", "achievements")) {
            ObjectNode target = obj(out, key);
            incoming.path(key).fields().forEachRemaining(e -> {
                if (!target.has(e.getKey())) target.set(e.getKey(), e.getValue());
            });
        }

        // visualized: most recent day per algorithm
        ObjectNode visualized = obj(out, "visualized");
        incoming.path("visualized").fields().forEachRemaining(e -> {
            JsonNode mine = visualized.get(e.getKey());
            if (mine == null || e.getValue().asText().compareTo(mine.asText()) > 0) visualized.set(e.getKey(), e.getValue());
        });

        String created = incoming.path("createdAt").asText("");
        if (!created.isEmpty() && (out.path("createdAt").asText("").isEmpty() || created.compareTo(out.path("createdAt").asText()) < 0)) {
            out.put("createdAt", created);
        }
        return out;
    }

    private static ObjectNode obj(ObjectNode parent, String field) {
        JsonNode n = parent.get(field);
        if (n instanceof ObjectNode o) return o;
        return parent.putObject(field);
    }

    /* ── Certificates ──────────────────────────────────────────────────── */

    /** Which certificate kinds this learner has earned (verified from synced progress). */
    public List<Map<String, Object>> eligibility(String username) {
        JsonNode p = get(username);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Kind k : KINDS) {
            int[] prog = progressFor(k.id(), p);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", k.id());
            m.put("title", k.title());
            m.put("description", k.description());
            m.put("current", prog[0]);
            m.put("target", prog[1]);
            m.put("eligible", prog[0] >= prog[1]);
            certificates.findFirstByUsernameAndKind(username, k.id()).ifPresent(c -> m.put("code", c.getCode()));
            out.add(m);
        }
        return out;
    }

    /** [current, target] for one certificate kind. */
    static int[] progressFor(String kind, JsonNode p) {
        if (p == null) return new int[]{0, target(kind)};
        return switch (kind) {
            case "ROADMAP_JAVA" -> new int[]{p.path("roadmaps").path("java").size(), ROADMAP_TOPICS.get("java")};
            case "ROADMAP_FRONTEND" -> new int[]{p.path("roadmaps").path("frontend").size(), ROADMAP_TOPICS.get("frontend")};
            case "PUZZLES_10" -> new int[]{p.path("challenges").size(), 10};
            case "PUZZLES_50" -> new int[]{p.path("challenges").size(), 50};
            case "PUZZLE_STREAK_7" -> {
                Set<Integer> nums = new HashSet<>();
                p.path("challenges").fieldNames().forEachRemaining(n -> {
                    try {
                        nums.add(Integer.parseInt(n));
                    } catch (NumberFormatException ignored) {
                        // not a puzzle number
                    }
                });
                yield new int[]{longestRun(nums), 7};
            }
            case "LEARNING_STREAK_30" -> {
                List<LocalDate> days = new ArrayList<>();
                p.path("days").fields().forEachRemaining(e -> {
                    if (e.getValue().path("xp").asInt() > 0) {
                        try {
                            days.add(LocalDate.parse(e.getKey()));
                        } catch (RuntimeException ignored) {
                            // malformed day key
                        }
                    }
                });
                Set<Integer> epochDays = new HashSet<>();
                days.forEach(d -> epochDays.add((int) d.toEpochDay()));
                yield new int[]{longestRun(epochDays), 30};
            }
            default -> new int[]{0, 1};
        };
    }

    private static int target(String kind) {
        return progressFor(kind, com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())[1];
    }

    static int longestRun(Set<Integer> values) {
        int best = 0;
        for (int v : values) {
            if (values.contains(v - 1)) continue;
            int len = 1;
            while (values.contains(v + len)) len++;
            best = Math.max(best, len);
        }
        return best;
    }

    /** Issue (or return the existing) certificate when the learner qualifies. */
    public Certificate issue(String username, String email, String recipientName, String kindId) {
        Kind kind = KINDS.stream().filter(k -> k.id().equals(kindId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown certificate"));
        Optional<Certificate> existing = certificates.findFirstByUsernameAndKind(username, kindId);
        if (existing.isPresent()) return existing.get();
        int[] prog = progressFor(kindId, get(username));
        if (prog[0] < prog[1]) throw new IllegalStateException("Not earned yet: " + prog[0] + " of " + prog[1]);
        if (recipientName == null || recipientName.isBlank()) throw new IllegalArgumentException("A name is required for the certificate");

        Certificate c = new Certificate();
        c.setCode(newCode());
        c.setKind(kindId);
        c.setTitle(kind.title());
        c.setDescription(kind.description());
        c.setRecipientName(recipientName.trim().length() > 120 ? recipientName.trim().substring(0, 120) : recipientName.trim());
        c.setUsername(username);
        c.setEmail(email);
        c.setIssuedAt(Instant.now());
        return certificates.save(c);
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("CTW-");
            for (int i = 0; i < 8; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            if (!certificates.existsById(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Could not allocate a certificate code");
    }

    public Optional<Certificate> find(String code) {
        return certificates.findById(code.toUpperCase(Locale.ROOT));
    }

    public List<Certificate> mine(String username) {
        return certificates.findByUsernameOrderByIssuedAtDesc(username);
    }
}
