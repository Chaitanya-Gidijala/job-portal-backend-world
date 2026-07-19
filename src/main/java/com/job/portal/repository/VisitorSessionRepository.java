package com.job.portal.repository;

import com.job.portal.entity.VisitorSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VisitorSessionRepository extends JpaRepository<VisitorSession, Long> {
    List<VisitorSession> findTop50ByOrderByTimestampDesc();
}
