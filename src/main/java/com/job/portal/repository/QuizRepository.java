package com.job.portal.repository;

import com.job.portal.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Page<Quiz> findByJobId(Long jobId, Pageable pageable);

    Page<Quiz> findByTagsContaining(String tag, Pageable pageable);
}
