package com.job.portal.growth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {
    List<Certificate> findByUsernameOrderByIssuedAtDesc(String username);

    Optional<Certificate> findFirstByUsernameAndKind(String username, String kind);

    List<Certificate> findTop50ByOrderByIssuedAtDesc();
}
