package com.job.portal.service;

import com.job.portal.dto.JobDTO;
import com.job.portal.dto.JobSearchCriteria;
import com.job.portal.entity.Job;
import com.job.portal.exception.JobNotFoundException;
import com.job.portal.repository.JobRepository;
import com.job.portal.specification.JobSpecifications;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JobServiceImpl implements JobService {

    private static final Logger log = LoggerFactory.getLogger(JobServiceImpl.class);

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    @Cacheable(value = "jobs", key = "'all_jobs'")
    public List<JobDTO> getAllJobs() {
        log.debug("Fetching all jobs...");
        List<Job> jobs = jobRepository.findAll();
        log.info("Total jobs fetched: {}", jobs.size());
        return mapToDtoList(jobs);
    }

    @Override
    @Cacheable(value = "job", key = "#id")
    public JobDTO getJobById(Long id) {
        log.debug("Fetching job details for jobId: {}", id);
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found for jobId: " + id));
        log.info("Job details fetched for jobId: {}", id);
        JobDTO dto = modelMapper.map(job, JobDTO.class);
        // Manually set createdAt from createdDate and createdTime
        if (job.getCreatedDate() != null) {
            LocalTime time = job.getCreatedTime() != null ? job.getCreatedTime() : LocalTime.MIDNIGHT;
            dto.setCreatedAt(LocalDateTime.of(job.getCreatedDate(), time));
        }
        return dto;
    }

    @Override
    @Cacheable(value = "job", key = "#jobTitle.toLowerCase()")
    public JobDTO getJobByTitle(String jobTitle) {
        List<Job> jobs = jobRepository.findByJobTitle(jobTitle);
        if (jobs.isEmpty()) {
            throw new JobNotFoundException("Job not found for title: " + jobTitle);
        }
        if (jobs.size() > 1) {
            throw new JobNotFoundException("Multiple jobs found for title: " + jobTitle
                    + ". Please use job ID or the combined title/ID endpoint instead.");
        }
        Job job = jobs.get(0);
        JobDTO dto = modelMapper.map(job, JobDTO.class);
        // Manually set createdAt from createdDate and createdTime
        if (job.getCreatedDate() != null) {
            LocalTime time = job.getCreatedTime() != null ? job.getCreatedTime() : LocalTime.MIDNIGHT;
            dto.setCreatedAt(LocalDateTime.of(job.getCreatedDate(), time));
        }
        return dto;
    }

    @Override
    @Cacheable(value = "job", key = "#jobTitle.toLowerCase() + '_' + #id")
    public JobDTO getJobByTitleAndId(String jobTitle, Long id) {
        log.debug("Fetching job details for jobTitle: {} and jobId: {}", jobTitle, id);
        Job job = jobRepository.findByJobTitleAndId(jobTitle, id)
                .orElseThrow(() -> new JobNotFoundException("Job not found for title: " + jobTitle + " and id: " + id));
        log.info("Job details fetched for jobTitle: {} and jobId: {}", jobTitle, id);
        JobDTO dto = modelMapper.map(job, JobDTO.class);
        // Manually set createdAt from createdDate and createdTime
        if (job.getCreatedDate() != null) {
            LocalTime time = job.getCreatedTime() != null ? job.getCreatedTime() : LocalTime.MIDNIGHT;
            dto.setCreatedAt(LocalDateTime.of(job.getCreatedDate(), time));
        }
        return dto;
    }

    @Override
    @Cacheable(value = "jobs_by_experience", key = "#experience.toLowerCase()")
    public List<JobDTO> getJobsByExperience(String experience) {
        log.debug("Fetching jobs for experience: {}", experience);
        List<Job> jobs = jobRepository.findByExperienceOrderByCreatedDateDescCreatedTimeDesc(experience);
        if (jobs.isEmpty()) {
            log.warn("No jobs found for experience: {}", experience);
            return List.of();
        }
        log.info("Jobs fetched for experience: {}", experience);
        return mapToDtoList(jobs);
    }

    @Override
    @Cacheable(value = "jobs_latest", key = "'latest_jobs'")
    public List<JobDTO> getLatestJobs() {
        log.debug("Fetching latest jobs...");
        List<Job> jobs = jobRepository.findAllByOrderByCreatedDateAndCreatedTimeDesc();
        if (jobs.isEmpty()) {
            log.warn("No latest jobs found");
        } else {
            log.info("Fetched {} latest jobs", jobs.size());
        }
        return mapToDtoList(jobs);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "jobs", allEntries = true),

            @CacheEvict(value = "jobs_latest", allEntries = true),
            @CacheEvict(value = "jobs_search", allEntries = true),
            @CacheEvict(value = "jobs_by_company", allEntries = true),
            @CacheEvict(value = "job_counts", allEntries = true),
            @CacheEvict(value = "job", allEntries = true)
    })
    @Transactional
    public JobDTO saveJob(JobDTO jobDto) {
        log.debug("Saving job: {}", jobDto);
        if (jobDto.getCreatedAt() == null) {
            jobDto.setCreatedAt(LocalDateTime.now());
        }
        Job jobEntity = modelMapper.map(jobDto, Job.class);
        Job job = jobRepository.save(jobEntity);
        log.info("Job saved with id: {}", job.getId());
        return modelMapper.map(job, JobDTO.class);
    }

    @Override
    @Cacheable(value = "job_counts", key = "'total'")
    public long getJobCount() {
        log.debug("Fetching total job count...");
        long count = jobRepository.count();
        log.info("Total job count: {}", count);
        return count;
    }

    @Override
    @Cacheable(value = "jobs_search", key = "T(java.util.Objects).hash(#criteria?.getKeyword(),#criteria?.getLocation(),#criteria?.getJobType(),#criteria?.getCompany(),#criteria?.getMinSalary(),#criteria?.getPostedFrom(),#criteria?.getPostedTo(),#pageable.pageNumber,#pageable.pageSize,#pageable.sort.toString())")
    public Page<JobDTO> searchJobs(JobSearchCriteria criteria, Pageable pageable) {
        log.debug("Searching jobs with criteria: {}", criteria);
        Specification<Job> specification = JobSpecifications.build(criteria);
        Page<Job> page = jobRepository.findAll(specification, pageable);
        List<JobDTO> content = mapToDtoList(page.getContent());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "jobs", allEntries = true),

            @CacheEvict(value = "jobs_latest", allEntries = true),
            @CacheEvict(value = "jobs_search", allEntries = true),
            @CacheEvict(value = "jobs_by_company", allEntries = true),
            @CacheEvict(value = "job_counts", allEntries = true),
            @CacheEvict(value = "job", key = "#id")
    })
    @Transactional
    public JobDTO updateJob(Long id, JobDTO jobDTO) {
        log.debug("Updating job with id: {}", id);
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found for jobId: " + id));

        job.setJobTitle(jobDTO.getJobTitle());
        job.setCompany(jobDTO.getCompany());
        job.setLocation(jobDTO.getLocation());
        job.setJobDetails(jobDTO.getJobDetails());
        job.setExperienceRequired(jobDTO.getExperienceRequired());
        job.setExperience(jobDTO.getExperience());
        job.setApplyLink(jobDTO.getApplyLink());
        job.setSalary(jobDTO.getSalary());
        job.setCompanyLogo(jobDTO.getCompanyLogo());
        job.setJobType(jobDTO.getJobType());

        Job updated = jobRepository.save(job);
        return modelMapper.map(updated, JobDTO.class);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "jobs", allEntries = true),

            @CacheEvict(value = "jobs_latest", allEntries = true),
            @CacheEvict(value = "jobs_search", allEntries = true),
            @CacheEvict(value = "jobs_by_company", allEntries = true),
            @CacheEvict(value = "job_counts", allEntries = true),
            @CacheEvict(value = "job", key = "#id")
    })
    @Transactional
    public void deleteJob(Long id) {
        log.debug("Deleting job with id: {}", id);
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found for jobId: " + id));
        jobRepository.delete(job);
        log.info("Deleted job with id: {}", id);
    }

    @Override
    @Cacheable(value = "jobs_by_company", key = "#company.toLowerCase()")
    public List<JobDTO> getJobsByCompany(String company) {
        List<Job> jobs = jobRepository.findByCompanyIgnoreCase(company);
        return mapToDtoList(jobs);
    }

    @Override
    @Cacheable(value = "job_counts", key = "'byType'")
    public Map<String, Long> getJobStatsByType() {
        return jobRepository.countJobsByType().stream()
                .collect(Collectors.toMap(JobRepository.JobTypeCountProjection::getJobType,
                        JobRepository.JobTypeCountProjection::getCount));
    }

    private List<JobDTO> mapToDtoList(List<Job> jobs) {
        return jobs.stream()
                .map(job -> {
                    JobDTO dto = modelMapper.map(job, JobDTO.class);
                    // Manually set createdAt from createdDate and createdTime
                    if (job.getCreatedDate() != null) {
                        LocalTime time = job.getCreatedTime() != null ? job.getCreatedTime() : LocalTime.MIDNIGHT;
                        dto.setCreatedAt(LocalDateTime.of(job.getCreatedDate(), time));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "jobs", allEntries = true),

            @CacheEvict(value = "jobs_latest", allEntries = true),
            @CacheEvict(value = "jobs_search", allEntries = true),
            @CacheEvict(value = "jobs_by_company", allEntries = true),
            @CacheEvict(value = "job_counts", allEntries = true),
            @CacheEvict(value = "job", allEntries = true)
    })
    @Transactional
    public List<JobDTO> saveAllJobs(List<JobDTO> jobDTOs) {
        log.debug("Saving batch of {} jobs", jobDTOs.size());
        List<Job> jobs = jobDTOs.stream().map(jobDto -> {
            if (jobDto.getCreatedAt() == null) {
                jobDto.setCreatedAt(LocalDateTime.now());
            }
            return modelMapper.map(jobDto, Job.class);
        }).collect(Collectors.toList());

        List<Job> savedJobs = jobRepository.saveAll(jobs);
        log.info("Batch saved {} jobs", savedJobs.size());
        return mapToDtoList(savedJobs);
    }
}
