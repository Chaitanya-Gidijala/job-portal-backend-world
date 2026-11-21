package com.job.portal.repository;

import com.job.portal.entity.DailyChallengeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DailyChallengeResultRepository extends JpaRepository<DailyChallengeResult, Long> {

    Optional<DailyChallengeResult> findByChallengeDayAndLearnerId(int challengeDay, String learnerId);

    List<DailyChallengeResult> findByChallengeDayOrderByScoreDescDurationMsAsc(int challengeDay, org.springframework.data.domain.Pageable pageable);

    List<DailyChallengeResult> findByChallengeDayBetween(int fromDay, int toDay);

    List<DailyChallengeResult> findByLearnerIdAndChallengeDayBetween(String learnerId, int fromDay, int toDay);

    /** Rows of [score, count, sum(durationMs)] for one puzzle. */
    @Query("select r.score, count(r), sum(r.durationMs) from DailyChallengeResult r "
            + "where r.challengeDay = :day group by r.score")
    List<Object[]> aggregateByScore(@Param("day") int day);

    /** Players who did strictly worse: lower score, or same score but slower. */
    @Query("select count(r) from DailyChallengeResult r where r.challengeDay = :day "
            + "and (r.score < :score or (r.score = :score and r.durationMs > :durationMs))")
    long countBeaten(@Param("day") int day, @Param("score") int score, @Param("durationMs") long durationMs);
}
