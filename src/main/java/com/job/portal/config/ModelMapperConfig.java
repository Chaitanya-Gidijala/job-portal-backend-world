package com.job.portal.config;

import com.job.portal.dto.JobDTO;
import com.job.portal.entity.Job;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.spi.MappingContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        // Custom converter for String to LocalDate
        modelMapper.addConverter(new Converter<String, LocalDate>() {
            public LocalDate convert(MappingContext<String, LocalDate> context) {
                // Ensure that the date format used is in ISO format or any format you require
                return LocalDate.parse(context.getSource(), DateTimeFormatter.ISO_DATE);
            }
        });

        // Custom mapping from Job entity to JobDTO
        modelMapper.addMappings(new PropertyMap<Job, JobDTO>() {
            @Override
            protected void configure() {
                using(ctx -> {
                    Job source = (Job) ctx.getSource();
                    if (source.getCreatedDate() != null) {
                        LocalTime time = source.getCreatedTime() != null ? source.getCreatedTime() : LocalTime.MIDNIGHT;
                        return LocalDateTime.of(source.getCreatedDate(), time);
                    }
                    return null;
                }).map(source, destination.getCreatedAt());
            }
        });

        // Custom mapping from JobDTO to Job entity
        modelMapper.addMappings(new PropertyMap<JobDTO, Job>() {
            @Override
            protected void configure() {
                using(ctx -> {
                    LocalDateTime createdAt = (LocalDateTime) ctx.getSource();
                    return createdAt != null ? createdAt.toLocalDate() : null;
                }).map(source.getCreatedAt(), destination.getCreatedDate());

                using(ctx -> {
                    LocalDateTime createdAt = (LocalDateTime) ctx.getSource();
                    return createdAt != null ? createdAt.toLocalTime() : null;
                }).map(source.getCreatedAt(), destination.getCreatedTime());
            }
        });

        return modelMapper;
    }
}
