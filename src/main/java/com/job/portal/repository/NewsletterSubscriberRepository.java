package com.job.portal.repository;

import com.job.portal.entity.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, Long> {
    Optional<NewsletterSubscriber> findByEmail(String email);

    Optional<NewsletterSubscriber> findFirstByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    Optional<NewsletterSubscriber> findByToken(String token);

    @Query("select s from NewsletterSubscriber s where (s.active is null or s.active = true) and s.wantsDaily = true")
    List<NewsletterSubscriber> findDailyRecipients();

    /** Weekly summary: explicit opt-in, or legacy subscribers from before preferences existed. */
    @Query("select s from NewsletterSubscriber s where (s.active is null or s.active = true) and (s.wantsWeekly = true or s.wantsWeekly is null)")
    List<NewsletterSubscriber> findWeeklyRecipients();

    @Query("select count(s) from NewsletterSubscriber s where s.active is null or s.active = true")
    long countActive();

    long countBySubscribedAtAfter(LocalDateTime since);
}
