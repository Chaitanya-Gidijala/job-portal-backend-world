package com.job.portal.growth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface UserProgressRepository extends JpaRepository<UserProgress, String> {
    long countByUpdatedAtAfter(Instant since);
}
