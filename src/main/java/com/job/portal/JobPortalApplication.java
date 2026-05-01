package com.job.portal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
@EnableCaching
@EnableAsync
@org.springframework.data.jpa.repository.config.EnableJpaAuditing
public class JobPortalApplication implements CommandLineRunner {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    public static void main(String[] args) {
        SpringApplication.run(JobPortalApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("====================================================");
        System.out.println("🚀 APPLICATION STARTING");
        System.out.println("🔗 CONNECTED TO DATABASE: " + dbUrl);
        System.out.println("====================================================");
    }
}
