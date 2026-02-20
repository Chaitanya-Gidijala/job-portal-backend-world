package com.job.portal.specification;

import com.job.portal.dto.JobSearchCriteria;
import com.job.portal.entity.Job;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class JobSpecifications {

    private JobSpecifications() {
    }

    public static Specification<Job> build(JobSearchCriteria criteria) {
        Specification<Job> spec = Specification.where(null);

        if (criteria == null) {
            return spec;
        }

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String keyword = "%" + criteria.getKeyword().toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("jobTitle")), keyword),
                            cb.like(cb.lower(root.get("company")), keyword),
                            cb.like(cb.lower(root.get("jobDetails")), keyword)
                    )
            );
        }

        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
            String location = "%" + criteria.getLocation().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("location")), location));
        }

        if (criteria.getJobType() != null && !criteria.getJobType().isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("jobType")), criteria.getJobType().toLowerCase()));
        }

        if (criteria.getCompany() != null && !criteria.getCompany().isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("company")), criteria.getCompany().toLowerCase()));
        }

        if (criteria.getMinSalary() != null && !criteria.getMinSalary().isBlank()) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("salary"), criteria.getMinSalary()));
        }

        LocalDate postedFrom = criteria.getPostedFrom();
        if (postedFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdDate"), postedFrom));
        }

        LocalDate postedTo = criteria.getPostedTo();
        if (postedTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdDate"), postedTo));
        }

        return spec;
    }
}

