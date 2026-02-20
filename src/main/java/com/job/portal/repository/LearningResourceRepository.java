package com.job.portal.repository;

import com.job.portal.entity.LearningResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {
    Page<LearningResource> findByType(LearningResource.ResourceType type, Pageable pageable);

    Page<LearningResource> findByJobId(Long jobId, Pageable pageable);

    Page<LearningResource> findByTagsContaining(String tag, Pageable pageable);
}
