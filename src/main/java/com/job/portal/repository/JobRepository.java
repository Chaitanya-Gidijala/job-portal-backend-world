package com.job.portal.repository;

import com.job.portal.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    long count();

    List<Job> findByExperienceOrderByCreatedDateDescCreatedTimeDesc(String experience);

    List<Job> findByJobTypeOrderByCreatedDateDescCreatedTimeDesc(String jobType);

    @Query("SELECT j FROM Job j ORDER BY j.createdDate DESC, j.createdTime DESC")
    List<Job> findAllByOrderByCreatedDateAndCreatedTimeDesc();

    // Changed to List to handle duplicate job titles
    List<Job> findByJobTitle(String jobTitle);

    // New method: Find by both title and ID for uniqueness
    Optional<Job> findByJobTitleAndId(String jobTitle, Long id);

    List<Job> findByCompanyIgnoreCase(String company);

    interface JobTypeCountProjection {
        String getJobType();

        long getCount();
    }

    @Query("SELECT j.jobType as jobType, COUNT(j) as count FROM Job j GROUP BY j.jobType")
    List<JobTypeCountProjection> countJobsByType();
}
