package com.job.portal.service;

import com.job.portal.dto.JobDTO;
import com.job.portal.dto.JobSearchCriteria;
import com.job.portal.entity.Job;
import com.job.portal.exception.JobNotFoundException;
import com.job.portal.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    @Mock
    private JobRepository jobRepository;

    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private JobServiceImpl jobService;

    @Test
    void getAllJobs_returnsDtos() {
        Job job = createJob(1L);
        when(jobRepository.findAll()).thenReturn(List.of(job));

        List<JobDTO> result = jobService.getAllJobs();

        assertEquals(1, result.size());
        assertEquals(job.getJobTitle(), result.get(0).getJobTitle());
        verify(jobRepository).findAll();
    }

    @Test
    void getJobById_foundReturnsDto() {
        Job job = createJob(2L);
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job));

        JobDTO dto = jobService.getJobById(2L);

        assertEquals(job.getCompany(), dto.getCompany());
        verify(jobRepository).findById(2L);
    }

    @Test
    void getJobById_notFoundThrowsException() {
        when(jobRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> jobService.getJobById(99L));
    }

    @Test
    void searchJobs_returnsPagedResults() {
        Job job = createJob(3L);
        Page<Job> page = new PageImpl<>(List.of(job));
        when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword("engineer");

        Page<JobDTO> result = jobService.searchJobs(criteria, PageRequest.of(0, 5));

        assertEquals(1, result.getTotalElements());
        assertEquals(job.getJobTitle(), result.getContent().get(0).getJobTitle());
    }

    @Test
    void updateJob_overwritesMutableFields() {
        Job existing = createJob(5L);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(jobRepository.save(existing)).thenReturn(existing);

        JobDTO updateDto = new JobDTO();
        updateDto.setJobTitle("Updated Title");
        updateDto.setCompany("New Company");
        updateDto.setLocation("Remote");
        updateDto.setJobDetails("Updated details");
        updateDto.setExperienceRequired("5+ years");
        updateDto.setApplyLink("http://example.com/apply");
        updateDto.setSalary("100k");
        updateDto.setCompanyLogo("logo.png");
        updateDto.setJobType("Full-Time");

        JobDTO result = jobService.updateJob(5L, updateDto);

        assertEquals("Updated Title", result.getJobTitle());
        assertEquals("New Company", result.getCompany());
        verify(jobRepository).save(existing);
    }

    @Test
    void deleteJob_removesEntity() {
        Job job = createJob(7L);
        when(jobRepository.findById(7L)).thenReturn(Optional.of(job));

        jobService.deleteJob(7L);

        verify(jobRepository).delete(job);
    }

    private Job createJob(Long id) {
        Job job = new Job();
        job.setId(id);
        job.setJobTitle("Software Engineer " + id);
        job.setCompany("Company " + id);
        job.setLocation("City " + id);
        job.setJobDetails("Details");
        job.setExperienceRequired("3 years");
        job.setApplyLink("http://example.com");
        job.setSalary("80k");
        job.setCompanyLogo("logo.png");
        job.setJobType("Full-Time");
        job.setCreatedDate(LocalDate.now());
        job.setCreatedTime(LocalTime.now());
        return job;
    }
}

