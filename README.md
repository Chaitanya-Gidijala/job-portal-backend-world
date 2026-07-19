<div align="center">

# 🚀 Job Portal Backend

### A Production-Grade Spring Boot 3 REST API

[![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.1-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-JWT-6DB33F?style=for-the-badge&logo=spring-security&logoColor=white)](https://spring.io/projects/spring-security)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)](https://maven.apache.org/)
[![Caffeine](https://img.shields.io/badge/Cache-Caffeine-FFAA00?style=for-the-badge&logo=java&logoColor=white)](https://github.com/ben-manes/caffeine)
[![JaCoCo](https://img.shields.io/badge/Coverage-JaCoCo_80%25-brightgreen?style=for-the-badge)](https://www.jacoco.org/)

*A fully-featured Job Portal REST API — job listings, JWT auth, interview prep, quizzes, newsletters, Razorpay payments, and async email notifications.*

</div>

---

## 📑 Table of Contents

1. [Project Overview & Business Use Case](#-1-project-overview--business-use-case)
2. [Tech Stack & Dependencies](#-2-tech-stack--dependencies)
3. [Architecture Diagram & Layer Explanation](#-3-architecture-diagram--layer-explanation)
4. [Spring Boot Concepts — Complete Annotation Reference](#-4-spring-boot-concepts--complete-annotation-reference)
5. [Jakarta Bean Validation — DTO Validation Reference](#-5-jakarta-bean-validation--dto-validation-reference)
6. [Caching Strategy — Caffeine Cache Deep Dive](#-6-caching-strategy--caffeine-cache-deep-dive)
7. [Security Flow — JWT Authentication Lifecycle](#-7-security-flow--jwt-authentication-lifecycle)
8. [Testing Strategy](#-8-testing-strategy)
9. [Prerequisites & Local Setup Instructions](#-9-prerequisites--local-setup-instructions)
10. [Environment Variables Reference](#-10-environment-variables-reference)
11. [API Endpoints Reference](#-11-api-endpoints-reference)
12. [Database Design & JPA Concepts](#-12-database-design--jpa-concepts)
13. [Exception Handling Strategy](#-13-exception-handling-strategy)
14. [Async & Event-Driven Design](#-14-async--event-driven-design)
15. [DevOps — Docker & Containerization](#-15-devops--docker--containerization)
16. [Senior Java Interview Quick Reference](#-16-senior-java-interview-quick-reference)

---

## 🏢 1. Project Overview & Business Use Case

### What is this?
The **Job Portal Backend** is a production-ready, stateless RESTful API backend that powers [Chaitanya Tech World](https://www.chaitanyatechworld.com) — an independent technical education and job notification platform for software professionals.

### Business Domain Features

| Domain | Features |
|--------|----------|
| 💼 **Job Management** | CRUD for job listings, batch import, dynamic search with filters (keyword, location, type, salary, date range), pagination & sorting |
| 🔐 **Authentication** | JWT-based stateless authentication, CORS configuration, role-based access control (ADMIN, RECRUITER) |
| 📚 **Learning Resources** | Categorised learning materials for tech topics |
| 🎯 **Interview Prep** | Interview questions & answers, topic-wise Quiz system |
| 💰 **Payments** | Razorpay payment integration with HMAC signature verification, idempotent payment handling (replay attack prevention) |
| 📧 **Email Notifications** | Async email delivery via Brevo HTTP API for payment confirmations, contact messages, newsletters |
| 📊 **Analytics** | Daily analytics tracking, job stats by type |
| 🔔 **Newsletter** | Email newsletter subscription management |

### Why This Architecture?
- **Stateless**: No server-side sessions; JWT tokens carry all authentication state
- **Horizontally Scalable**: Any instance can serve any request without sticky sessions
- **Cache-First**: Caffeine cache drastically reduces DB load on frequently read job listings
- **Async I/O**: Email sending is fully non-blocking — never blocks the HTTP response thread
- **Clean Architecture**: Strict separation of Controller → Service Interface → Service Impl → Repository

---

## 🛠 2. Tech Stack & Dependencies

### Core Framework

| Library | Version | Purpose |
|---------|---------|---------|
| **Spring Boot** | 3.4.1 | Auto-configuration, embedded Tomcat server |
| **Java** | 17 (LTS) | Language runtime |
| **Maven** | 3.9+ | Build & dependency management |

### Data Layer

| Library | Version | Purpose |
|---------|---------|---------|
| **Spring Data JPA** | 3.4.1 | Repository abstraction over Hibernate |
| **Hibernate** | 6.x | ORM — maps Java entities to DB tables |
| **MySQL Connector/J** | 8.x | JDBC driver for MySQL |
| **PostgreSQL Driver** | 42.x | JDBC driver for PostgreSQL |
| **HikariCP** | Bundled | High-performance JDBC connection pool |

### Security

| Library | Version | Purpose |
|---------|---------|---------|
| **Spring Security** | 6.x | Filter chain, authentication, authorization |
| **JJWT** | 0.12.3 | JWT creation, parsing, and validation |
| **BCrypt** | Bundled | One-way password hashing |

### Caching

| Library | Version | Purpose |
|---------|---------|---------|
| **Spring Cache Abstraction** | 3.4.1 | `@Cacheable`, `@CacheEvict`, `@Caching` annotations |
| **Caffeine** | Bundled | In-process, high-performance cache backend |

### Other Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| **ModelMapper** | 3.2.1 | Entity ↔ DTO object mapping |
| **Lombok** | Latest | Boilerplate reduction (`@Data`, `@Builder`, `@Slf4j`) |
| **Spring Boot Actuator** | 3.4.1 | Health checks, metrics endpoints |
| **Spring Boot AOP** | 3.4.1 | Aspect-Oriented Programming support |
| **spring-dotenv** | 4.0.0 | Load `.env` files as Spring properties |
| **Razorpay Java SDK** | 1.4.3 | Payment gateway integration |

### Testing Libraries

| Library | Purpose |
|---------|---------|
| **JUnit 5** | Unit testing framework |
| **Mockito** | Mocking framework for unit tests |
| **Spring Boot Test** | Integration testing with full Spring context |
| **H2 Database** | In-memory DB for fast integration tests |
| **REST Assured** | Fluent DSL for API contract testing |
| **Testcontainers** | Real Docker containers for integration tests |
| **JaCoCo** | Code coverage reporting (80% minimum threshold) |
| **Spring Security Test** | Testing secured endpoints (`@WithMockUser`) |

---

## 🏗 3. Architecture Diagram & Layer Explanation

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT (React / Browser)                    │
│                    HTTP Requests with Bearer JWT Token               │
└──────────────────────────────┬──────────────────────────────────────┘
                               │  HTTPS
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    SPRING SECURITY FILTER CHAIN                      │
│  JwtAuthenticationFilter (OncePerRequestFilter)                     │
│  ① Extract JWT from Authorization header                            │
│  ② Validate token signature + expiry via JwtTokenProvider           │
│  ③ Extract username + roles from JWT claims                          │
│  ④ Set Authentication in SecurityContextHolder                       │
│  SecurityFilterChain (SecurityConfig.java)                          │
│  ⑤ Route Authorization: public vs authenticated                      │
│  ⑥ CORS: allow configured origins with credentials                  │
│  ⑦ CSRF: disabled (stateless JWT app)                               │
│  ⑧ Session: STATELESS (no HttpSession created)                      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         CONTROLLER LAYER                            │
│                   (@RestController, @RequestMapping)                │
│  JobController │ QuizController │ TopicController                  │
│  PromptController │ ContactController │ RazorpayController          │
│                                                                     │
│  Responsibilities:                                                  │
│  • Receive HTTP requests, deserialize @RequestBody to DTO           │
│  • Trigger @Valid for Jakarta Bean Validation                       │
│  • Call Service layer                                               │
│  • Return ResponseEntity<ApiResponse<T>> with proper HTTP status    │
│  • Method-level security with @PreAuthorize                         │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          SERVICE LAYER                              │
│               (@Service, Interface + Impl pattern)                  │
│  JobService → JobServiceImpl                                        │
│  QuizService → QuizServiceImpl                                      │
│  TopicService → TopicServiceImpl                                    │
│                                                                     │
│  Responsibilities:                                                  │
│  • Business logic, Entity ↔ DTO transformation via ModelMapper      │
│  • @Transactional: ACID guarantees on data mutation                 │
│  • @Cacheable / @CacheEvict: Caffeine cache interaction             │
│  • @Async: fire-and-forget email dispatching                        │
│  • Custom exceptions (JobNotFoundException, ResourceNotFoundException)│
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        REPOSITORY LAYER                             │
│    (@Repository, JpaRepository + JpaSpecificationExecutor)          │
│  JobRepository │ QuizRepository │ TopicRepository                  │
│  ContactRepository │ UserPaymentRepository │ NewsletterRepository   │
│                                                                     │
│  Responsibilities:                                                  │
│  • CRUD via JpaRepository<Entity, ID>                               │
│  • Dynamic queries via JpaSpecificationExecutor                     │
│  • Named method queries (findByExperience..., findByCompany...)     │
│  • @Query JPQL for custom aggregations (countJobsByType)            │
│  • Interface Projections (JobTypeCountProjection)                   │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        DATABASE LAYER                               │
│                   MySQL 8.0 / PostgreSQL 16                         │
│                   HikariCP Connection Pool                          │
└─────────────────────────────────────────────────────────────────────┘

    CROSS-CUTTING CONCERNS:
    • GlobalExceptionHandler (@ControllerAdvice)
    • Caffeine Cache (jobs, jobs_latest, jobs_search, job_counts...)
    • EmailAsyncService (@Async — Brevo HTTP API)
    • Spring Boot Actuator (/actuator/health public, /actuator/** secured)
```

---

## 📘 4. Spring Boot Concepts — Complete Annotation Reference

> Every Spring Boot annotation used in this project — explained with interview context.

---

### 4.1 🚀 Application Bootstrap Annotations

| Annotation | Where Used | Why / Interview Explanation |
|-----------|-----------|----------------------------|
| `@SpringBootApplication` | `JobPortalApplication.java` | Meta-annotation = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`. Triggers Spring Boot auto-config based on classpath. **Interview Q**: *"What does @SpringBootApplication do internally?"* → Scans beans in current package + sub-packages, enables conditional auto-configuration. |
| `@EnableWebSecurity` | `SecurityConfig.java` | Disables Spring Boot's default security auto-configuration and enables custom `SecurityFilterChain`. |
| `@EnableMethodSecurity` | `SecurityConfig.java` | Activates `@PreAuthorize`, `@PostAuthorize`, `@Secured` annotations on controller/service methods. **Interview Q**: *"Difference between @EnableMethodSecurity and @EnableGlobalMethodSecurity?"* → The former is the modern Spring Security 6 replacement. |
| `@EnableCaching` | Config class | Activates Spring's proxy-based caching infrastructure. Without this, `@Cacheable` does nothing. |
| `@EnableAsync` | Config class | Enables `@Async` support. Spring creates a thread pool executor for background task execution. |

---

### 4.2 🧩 Stereotype & Component Annotations

| Annotation | Used On | Purpose |
|-----------|---------|---------|
| `@Component` | Generic beans | Generic stereotype; Spring creates and manages lifecycle |
| `@Service` | `JobServiceImpl`, `EmailAsyncService`, etc. | Service-layer bean; carries transactional semantics |
| `@Repository` | `JobRepository`, `QuizRepository`, etc. | Data-access bean; enables JPA exception translation (`PersistenceException` → `DataAccessException`) |
| `@RestController` | `JobController`, etc. | = `@Controller` + `@ResponseBody`. All return values serialized to JSON. |
| `@Configuration` | `SecurityConfig` | Source of `@Bean` definitions. Processed at startup before component scan. |

---

### 4.3 🔗 Dependency Injection Annotations

| Annotation | Purpose | Best Practice |
|-----------|---------|---------------|
| `@Autowired` | Field/constructor injection | **Avoid field injection** in production. Constructor injection is preferred (testable, immutable). |
| `@RequiredArgsConstructor` (Lombok) | Auto-generates constructor for `final` fields | **Industry-preferred DI style** — generates constructor injection without boilerplate |
| `@Bean` | Factory method in `@Configuration` | Manually defines a Spring-managed bean (e.g., `PasswordEncoder`, `ModelMapper`, `CorsConfigurationSource`) |
| `@Qualifier("name")` | Disambiguates when multiple beans of same type exist | Used with `@Autowired` |
| `@Primary` | Marks the default bean when multiple exist | Without `@Qualifier`, Spring picks `@Primary` one |
| `@Value("${property.key}")` | `SecurityConfig`, `JwtTokenProvider` | Injects values from `application.properties` / `.env`. Supports SpEL defaults: `@Value("${key:default}")` |

---

### 4.4 🌐 Web / REST Controller Annotations

| Annotation | Purpose |
|-----------|---------|
| `@RequestMapping("/api/jobs")` | Class-level base path for all endpoints |
| `@GetMapping` | Maps HTTP GET request |
| `@PostMapping` | Maps HTTP POST request |
| `@PutMapping` | Maps HTTP PUT request (full update) |
| `@DeleteMapping` | Maps HTTP DELETE request |
| `@PatchMapping` | Maps HTTP PATCH request (partial update) |
| `@PathVariable` | Extracts value from URI path segment: `GET /jobs/{id}` |
| `@RequestParam` | Extracts query parameter: `GET /jobs/search?keyword=java` |
| `@RequestBody` | Deserializes HTTP request body JSON → Java DTO |
| `@ResponseStatus(HttpStatus.CREATED)` | Sets default HTTP status for a method/exception |
| `@CrossOrigin` | Per-controller CORS configuration |
| `@PageableDefault(size=10)` | Sets default pagination parameters for Pageable arguments |
| `@DateTimeFormat(iso = ISO.DATE)` | Parses date strings from `@RequestParam` into `LocalDate` |

---

### 4.5 🔒 Security Annotations

| Annotation | Used In | Purpose |
|-----------|---------|---------|
| `@PreAuthorize("hasRole('ADMIN')")` | `JobController.deleteJob()` | Evaluated **before** method execution; throws `AccessDeniedException` if false |
| `@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")` | `JobController.createJob()` | Allows multiple roles |
| `@PostAuthorize("returnObject.username == principal.username")` | (Interview knowledge) | Evaluated **after** method; can inspect return value |
| `@Secured({"ROLE_ADMIN"})` | (Alternative) | Simple role check; no SpEL support |
| `@WithMockUser(roles="ADMIN")` | Test classes | Spring Security Test — simulates authenticated user |

---

### 4.6 💾 JPA / Persistence Annotations

| Annotation | Used On | Purpose |
|-----------|---------|---------|
| `@Entity` | `Job`, `Quiz`, `Topic`, etc. | Marks class as JPA entity mapped to a DB table |
| `@Table(name="jobs")` | Entity class | Customizes table name, schema, unique constraints |
| `@Id` | Entity field | Marks the primary key |
| `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Entity `id` field | Auto-increment — DB assigns ID on insert |
| `@Column(name="job_details", columnDefinition="TEXT")` | Entity fields | Customizes column name, type, nullable, updatable |
| `@MappedSuperclass` | `BaseEntity` | Fields inherited by child entities; `BaseEntity` itself is NOT a table |
| `@EntityListeners(AuditingEntityListener.class)` | `BaseEntity` | Plugs in Spring Data's auditing listener for auto-fill |
| `@CreatedDate` | `BaseEntity.createdAt` | Auto-populated with current timestamp on entity creation |
| `@LastModifiedDate` | `BaseEntity.updatedAt` | Auto-populated on every entity update |
| `@CreatedBy` | `BaseEntity.createdBy` | Auto-populated with current user (requires `AuditorAware` bean) |
| `@LastModifiedBy` | `BaseEntity.updatedBy` | Auto-populated on every entity update |
| `@OneToMany(mappedBy = "topic")` | Relationship fields | One-to-many JPA association |
| `@ManyToOne(fetch = FetchType.LAZY)` | Relationship fields | Many-to-one; **always use LAZY** to avoid N+1 problem |
| `@JoinColumn(name = "topic_id")` | Owning side of relationship | Defines the FK column |
| `@ElementCollection` | Collection of non-entities | Maps a `List<String>` to a separate table |
| `@CollectionTable` | Used with `@ElementCollection` | Configures the joined collection table |
| `@Enumerated(EnumType.STRING)` | Enum fields | Stores enum as VARCHAR, not as index number |
| `@Transactional` | Service methods | **Critical!** Wraps method in a DB transaction |

---

### 4.7 ⚡ Caching Annotations

| Annotation | Purpose |
|-----------|---------|
| `@EnableCaching` | Activates cache proxy |
| `@Cacheable(value, key)` | Read-through cache — returns cached value or executes method and caches result |
| `@CacheEvict(value, key)` | Invalidate cache entry on mutation |
| `@CachePut(value, key)` | Update cache without skipping method |
| `@Caching` | Combine multiple cache operations on a single method |

---

### 4.8 ⚙️ Advanced Configuration Annotations

| Annotation | Purpose |
|-----------|---------|
| `@ConditionalOnProperty("feature.enabled")` | Load a bean only if a property is set |
| `@ConditionalOnBean(MyService.class)` | Load a bean only if another bean exists |
| `@ConditionalOnMissingBean` | Fallback bean if no other exists |
| `@Profile("dev")` | Load bean only in specific profile |
| `@Scope("prototype")` | New instance per injection point (default is singleton) |
| `@Lazy` | Delay bean initialization until first use |
| `@Async` | Run method in separate thread pool |
| `@Scheduled(cron="0 0 * * * ?")` | Schedule a task on a cron expression |
| `@EventListener` | React to Spring application events |
| `@TransactionEventListener` | Execute logic after a transaction commits |

---

## ✅ 5. Jakarta Bean Validation — DTO Validation Reference

> Uses `spring-boot-starter-validation` which bundles **Hibernate Validator 8** (Jakarta EE reference implementation).

### How Validation is Triggered

```
HTTP Request → Controller Method
    @Valid @RequestBody JobDTO jobDTO       ← triggers validation cascade
         ↓
    If violations found → MethodArgumentNotValidException
         ↓
    @ControllerAdvice GlobalExceptionHandler catches it
         ↓
    Returns HTTP 400 with {field: message} map
```

---

### 5.1 Annotations Used in This Project

| Annotation | Used On (DTO) | Validation Rule |
|-----------|--------------|----------------|
| `@NotBlank` | `jobTitle`, `company`, `location`, `salary`, `jobType` | String must not be null AND must contain at least one non-whitespace character |
| `@NotNull` | Any mandatory non-string field | Value must not be null |
| `@Size(min=3, max=100)` | `jobTitle`, `company`, `location`, `jobType` | String length must be within bounds |
| `@URL` | `applyLink` | String must be a valid URL format (Hibernate-specific) |
| `@Email` | Contact/user DTOs | Validates well-formed email address format |
| `@Valid` | Controller `@RequestBody` param | Triggers cascaded validation on the annotated object |

---

### 5.2 Complete Jakarta Validation Annotations Reference

```java
// ─── NULL / PRESENCE CHECKS ───────────────────────────────────────
@NotNull                         // Value != null (works for any type)
@NotEmpty                        // != null && size > 0 (String, Collection, Array, Map)
@NotBlank                        // != null && trimmed length > 0 (Strings only)

// ─── STRING CONSTRAINTS ───────────────────────────────────────────
@Size(min = 2, max = 50)         // Length bounds for String, Collection, Array
@Pattern(regexp = "^[A-Za-z ]+$", message = "Only letters allowed")
@Email(message = "Invalid email")
@URL                             // Hibernate Validator — valid URL

// ─── NUMERIC CONSTRAINTS ──────────────────────────────────────────
@Min(value = 0)                  // Number must be >= value
@Max(value = 100)                // Number must be <= value
@Positive                        // Number must be > 0
@PositiveOrZero                  // Number must be >= 0
@Negative                        // Number must be < 0
@NegativeOrZero                  // Number must be <= 0
@Digits(integer = 5, fraction = 2) // Max 5 integer digits, 2 decimal digits

// ─── DATE / TIME CONSTRAINTS ──────────────────────────────────────
@Past                            // LocalDate/LocalDateTime must be in the past
@PastOrPresent                   // Can be today or in the past
@Future                          // Must be in the future
@FutureOrPresent                 // Can be today or in the future

// ─── BOOLEAN CONSTRAINTS ──────────────────────────────────────────
@AssertTrue                      // boolean must be true
@AssertFalse                     // boolean must be false

// ─── CASCADED VALIDATION ──────────────────────────────────────────
@Valid  // Cascades validation into nested objects
// Example:
public class OrderDTO {
    @Valid
    @NotNull
    private AddressDTO shippingAddress; // AddressDTO fields also get validated
}

// ─── GROUPS — Conditional Validation ──────────────────────────────
interface OnCreate {}
interface OnUpdate {}

@NotBlank(groups = OnCreate.class)             // Required only on POST
@Size(min = 3, groups = {OnCreate.class, OnUpdate.class})

// Controller usage:
@Validated(OnCreate.class) @RequestBody JobDTO dto   // POST
@Validated(OnUpdate.class) @RequestBody JobDTO dto   // PUT
```

---

### 5.3 Custom Constraint — Senior Interview Must-Know

```java
// Step 1: Create the annotation
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "Email already registered";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Step 2: Implement ConstraintValidator
@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return email != null && !userRepository.existsByEmail(email);
    }
}

// Step 3: Use in DTO
public class RegisterRequest {
    @NotBlank @Email @UniqueEmail  // Custom constraint hits DB!
    private String email;
}
```

---

## ⚡ 6. Caching Strategy — Caffeine Cache Deep Dive

### Why Caffeine?
Caffeine is a **high-performance, near-optimal in-process caching library** for Java. Uses W-TinyLFU eviction algorithm that approximates optimal cache replacement with very low overhead.

### Cache Topology in This Project

| Cache Name | Key | Evicted When |
|-----------|-----|-------------|
| `jobs` | `'all'` | Save / Update / Delete job |
| `jobs_latest` | `'latest_jobs'` | Save / Update / Delete job |
| `jobs_search` | Hash of all search criteria | Save / Update / Delete job |
| `jobs_by_company` | `company.toLowerCase()` | Save / Update / Delete job |
| `jobs_by_experience` | `experience.toLowerCase()` | TTL-based |
| `job` | `id` | Update or Delete specific job |
| `job_counts` | `'total'` / `'byType'` | Save / Update / Delete job |

---

### 6.1 Annotation Patterns Used

```java
// ── @Cacheable: Cache-read pattern ─────────────────────────────────
@Cacheable(value = "jobs_latest", key = "'latest_jobs'")
public List<JobDTO> getLatestJobs() {
    // DB query runs ONLY on cache miss
    return jobRepository.findAllByOrderByCreatedDateAndCreatedTimeDesc();
}

// ── @Cacheable with dynamic SpEL key ───────────────────────────────
@Cacheable(value = "jobs_by_experience", key = "#experience.toLowerCase()")
public List<JobDTO> getJobsByExperience(String experience) { ... }

// ── @Cacheable with complex hash key for paginated search ──────────
@Cacheable(
    value = "jobs_search",
    key = "T(java.util.Objects).hash(#criteria?.getKeyword(), "
        + "#criteria?.getLocation(), #pageable.pageNumber, #pageable.sort.toString())"
)
public Page<JobDTO> searchJobs(JobSearchCriteria criteria, Pageable pageable) { ... }

// ── @CacheEvict: Invalidate single entry ───────────────────────────
@CacheEvict(value = "job", key = "#id")
public void deleteJob(Long id) { ... }

// ── @Caching: Multi-cache eviction on mutation ─────────────────────
@Caching(evict = {
    @CacheEvict(value = "jobs",            allEntries = true),
    @CacheEvict(value = "jobs_latest",     allEntries = true),
    @CacheEvict(value = "jobs_search",     allEntries = true),
    @CacheEvict(value = "jobs_by_company", allEntries = true),
    @CacheEvict(value = "job_counts",      allEntries = true),
    @CacheEvict(value = "job",             key = "#id")
})
@Transactional
public JobDTO updateJob(Long id, JobDTO jobDTO) { ... }

// ── @CachePut: Update cache without skipping method ────────────────
// Interview Q: What is the difference between @Cacheable and @CachePut?
// @Cacheable: Skips method if cache hit. @CachePut: ALWAYS runs method, then stores result.
@CachePut(value = "job", key = "#id")
public JobDTO updateJobAndRefreshCache(Long id, JobDTO dto) { ... }
```

---

### 6.2 How Spring Cache Abstraction Works Internally

```
Method Call: getJobById(5L)
       │
   Spring AOP Proxy intercepts
       │
   Is key "5" in cache "job"?
   ├── YES → Return cached value immediately (DB not touched)
   └── NO  → Execute actual method → Store result in cache → Return result
```

**Interview Q**: *"What are the limitations of Caffeine cache in a distributed system?"*

**Answer**: Caffeine is a **local/in-process cache** — it lives in JVM heap. In a horizontally scaled deployment with 3 application instances, each instance has its own independent cache. A write on Instance A evicts from Instance A's cache only — Instances B and C still serve stale data. **Solution**: Transition to a **distributed cache like Redis** with Spring Cache abstraction (just swap the `CacheManager` bean).

---

### 6.3 Caffeine Configuration — Eviction Policies

```java
@Bean
public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(1_000)                        // Evict when cache exceeds 1000 entries
        .expireAfterWrite(10, TimeUnit.MINUTES)    // TTL: evict 10 min after write
        .expireAfterAccess(5, TimeUnit.MINUTES)    // Evict if not accessed for 5 min
        .recordStats()                             // Enable hit/miss statistics
    );
    return manager;
}
```

| Policy | Description | Use Case |
|--------|-------------|---------|
| `expireAfterWrite` | TTL from last write | Static/infrequently changing data |
| `expireAfterAccess` | TTL from last access | Session-like data |
| `maximumSize` | Size-based eviction (W-TinyLFU) | Bounded memory usage |
| `maximumWeight` | Weighted size eviction | Variable-size entries |
| `refreshAfterWrite` | Async stale-while-revalidate | High-traffic, slightly stale OK |

---

## 🔐 7. Security Flow — JWT Authentication Lifecycle

### 7.1 Login Flow

```
Client               Spring Security         JwtTokenProvider
  │                       │                        │
  │ POST /api/auth/login   │                        │
  │ {username, password}   │                        │
  ├──────────────────────►│                        │
  │                        │ AuthenticationManager  │
  │                        │ .authenticate()        │
  │                        │ BCrypt.matches()       │
  │                        │                        │
  │                        │ generateToken(auth)   │
  │                        ├───────────────────────►
  │                        │◄───────────────────────
  │                        │ JWT (HS512 signed)     │
  │ 200 OK {token: "eyJ..."│                        │
  │◄──────────────────────┤                        │
```

### 7.2 Authorization Flow (Subsequent Requests)

```
Client          JwtAuthenticationFilter    JwtTokenProvider   Controller
  │                     │                      │                │
  │ GET /api/jobs        │                      │                │
  │ Authorization: Bearer│                      │                │
  ├────────────────────►│                      │                │
  │                      │ getTokenFromRequest()│                │
  │                      │ validateToken(token) │                │
  │                      ├─────────────────────►               │
  │                      │ Jwts.parser()        │                │
  │                      │ .verifyWith(key)     │                │
  │                      │◄─────────────────────               │
  │                      │                      │                │
  │                      │ SecurityContextHolder.setAuthentication()
  │                      │ (UsernamePasswordAuthenticationToken) │
  │                      │ filterChain.doFilter()               │
  │                      │ @PreAuthorize SpEL evaluated         │
  │ 200 OK               │                                      │
  │◄─────────────────────────────────────────────────────────►│
```

---

### 7.3 JWT Token Structure

```
eyJhbGciOiJIUzUxMiJ9                      ← HEADER: {"alg":"HS512"}
.
eyJzdWIiOiJhZG1pbiIsImlhdCI6MTcwMDAsImV4cCI6MTcwMDYwNH0
                                           ← PAYLOAD: {"sub":"admin","iat":...,"exp":...}
.
HMACSHA512(header+payload, SECRET_KEY)     ← SIGNATURE
```

### 7.4 Security Configuration Breakdown

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // ① Disable CSRF — safe for stateless REST APIs (no cookies)
        .csrf(AbstractHttpConfigurer::disable)

        // ② Enable CORS from CorsConfigurationSource bean
        .cors(Customizer.withDefaults())

        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(HttpMethod.GET, "/api/jobs/**").permitAll()  // Public
            .requestMatchers("/api/auth/**").permitAll()                  // Public
            .requestMatchers("/actuator/health").permitAll()              // Health check
            .requestMatchers("/actuator/**").authenticated()              // Metrics secured
            .anyRequest().authenticated()                                 // Everything else
        )
        // ③ STATELESS — Spring never creates/uses HttpSession
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    // ④ Add JWT filter BEFORE UsernamePasswordAuthenticationFilter
    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

### 7.5 Password Encoding

```java
// Registration: hash the password before storing
String hashedPassword = passwordEncoder.encode(rawPassword);

// Login: verify (BCrypt handles salt automatically)
boolean matches = passwordEncoder.matches(rawPassword, storedHashedPassword);

// BCrypt always generates a new salt — same input produces different hashes:
BCrypt.hashpw("password", BCrypt.gensalt(12));  // 12 = cost factor (2^12 rounds)
```

---

## 🧪 8. Testing Strategy

### 8.1 Testing Pyramid

```
             ┌─────────────┐
             │   E2E Tests │   (Future: Selenium)
             └──────┬───────┘
       ┌────────────┴────────────┐
       │  Integration Tests      │  Testcontainers + H2
       └────────────┬────────────┘
    ┌───────────────┴────────────────┐
    │  Unit Tests                   │  JUnit 5 + Mockito
    └────────────────────────────────┘
```

---

### 8.2 Unit Tests (JUnit 5 + Mockito)

```java
@ExtendWith(MockitoExtension.class)           // JUnit 5 + Mockito integration
class JobServiceImplTest {

    @Mock
    private JobRepository jobRepository;      // Mock — no real DB call

    @Spy
    private ModelMapper modelMapper = new ModelMapper(); // Spy — real object, can verify calls

    @InjectMocks
    private JobServiceImpl jobService;        // System Under Test — mocks injected

    @Test
    void getJobById_notFoundThrowsException() {
        when(jobRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(JobNotFoundException.class, () -> jobService.getJobById(99L));
    }

    @Test
    void searchJobs_returnsPagedResults() {
        Page<Job> page = new PageImpl<>(List.of(createJob(3L)));
        when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<JobDTO> result = jobService.searchJobs(new JobSearchCriteria(), PageRequest.of(0, 5));

        assertEquals(1, result.getTotalElements());
        verify(jobRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
```

| Mockito Concept | Annotation/Method | Purpose |
|----------------|------------------|---------|
| Mock object | `@Mock` | Fake implementation — no real behavior |
| Spy object | `@Spy` | Real object — can stub/verify |
| Inject Mocks | `@InjectMocks` | Creates SUT and injects mocks |
| Stubbing | `when(...).thenReturn(...)` | Define mock behavior |
| Verification | `verify(mock).method()` | Assert interaction occurred |
| Argument matchers | `any()`, `anyLong()` | Flexible matching |
| Argument capture | `@Captor ArgumentCaptor<T>` | Capture args passed to mocks |

---

### 8.3 Testcontainers — Real DB Integration Tests

```java
@SpringBootTest
@Testcontainers
class JobRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("job_portal_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

**Why Testcontainers?**
- Tests run against the **same DB engine** used in production (MySQL 8.0), not H2
- Catches DB-specific issues: charset, collation, index behavior, SQL dialect differences
- Fully isolated: each test run gets a fresh container

---

### 8.4 REST Assured — API Contract Testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobApiTest {

    @Test
    void getJobs_returns200() {
        given().contentType(ContentType.JSON)
        .when().get("/api/jobs")
        .then()
            .statusCode(200)
            .body("data", not(empty()))
            .body("message", equalTo("Jobs fetched successfully"));
    }

    @Test
    void createJob_withoutToken_returns403() {
        given().contentType(ContentType.JSON).body(new JobDTO(...))
        .when().post("/api/jobs")
        .then().statusCode(403);
    }
}
```

---

### 8.5 JaCoCo — Code Coverage Enforcement

```xml
<!-- pom.xml: Enforces 80% minimum line coverage per package -->
<limit>
    <counter>LINE</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.80</minimum>   <!-- Build fails if < 80% coverage -->
</limit>
```

**Excluded from coverage:**
- `**/config/**` — Configuration classes (wiring only)
- `**/dto/**` — Data Transfer Objects (plain POJOs)
- `**/entity/**` — JPA Entities (data containers)

```bash
# Generate coverage report
mvn test jacoco:report
# View report at: target/site/jacoco/index.html
```

---

## ⚙️ 9. Prerequisites & Local Setup Instructions

### System Requirements

| Tool | Version |
|------|---------|
| **JDK** | 17+ |
| **Maven** | 3.9+ |
| **Docker Desktop** | Latest |
| **Docker Compose** | v2+ |

---

### Option A: Run with Docker Compose (Recommended)

```bash
# 1. Clone the repository
git clone https://github.com/chaitanyatechworld/Job-Portal-Backend-master.git
cd Job-Portal-Backend-master

# 2. Configure environment
cp .env.example .env
# Edit .env with your credentials

# 3. Start entire stack (MySQL + Spring Boot)
docker compose up -d

# 4. Check health
curl http://localhost:8080/actuator/health

# 5. View logs
docker compose logs -f app

# 6. Stop all services
docker compose down

# 7. Stop and remove volumes
docker compose down -v
```

---

### Option B: Run Locally (Maven)

```bash
# 1. Ensure MySQL is running on port 3306
# 2. Configure .env
# 3. Run with Maven wrapper (auto-loads .env via spring-dotenv)
./mvnw spring-boot:run

# Windows:
mvnw.cmd spring-boot:run

# Run tests
mvn test

# Run tests with coverage report
mvn verify

# Build production JAR
mvn clean package -DskipTests
java -jar target/JobPortalBackend-0.0.1-SNAPSHOT.jar
```

---

## 🔑 10. Environment Variables Reference

```bash
# ─── DATABASE ──────────────────────────────────────────────────────
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/chaitanyatechworld?createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_db_password

# ─── JWT ───────────────────────────────────────────────────────────
APP_JWT_SECRET=your_256bit_base64_encoded_secret_key
APP_JWT_EXPIRATION_MILLISECONDS=604800000   # 7 days

# ─── CORS ──────────────────────────────────────────────────────────
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000

# ─── EMAIL (Brevo) ─────────────────────────────────────────────────
BREVO_API_KEY=your_brevo_api_key
APP_ADMIN_EMAIL=admin@yourdomain.com

# ─── PAYMENTS (Razorpay) ───────────────────────────────────────────
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxx
RAZORPAY_KEY_SECRET=your_razorpay_secret

# ─── SPRING PROFILE ────────────────────────────────────────────────
SPRING_PROFILES_ACTIVE=dev    # Options: dev | prod
```

### Profile-Based Configuration

```
application.properties          ← Common settings (loaded always)
application-dev.properties      ← MySQL local, DEBUG logging, show-sql
application-prod.properties     ← Production DB, WARN logging, pool tuning
```

---

## 📋 11. API Endpoints Reference

### 🔐 Auth Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/login` | ❌ | Login and receive JWT token |
| `POST` | `/api/auth/register` | ❌ | Register new user |
| `GET` | `/api/auth/validate` | ✅ | Validate current token |

### 💼 Job Endpoints

| Method | Path | Auth | Role | Description |
|--------|------|------|------|-------------|
| `GET` | `/api/jobs` | ❌ | — | Get all jobs |
| `GET` | `/api/jobs/latest` | ❌ | — | Latest jobs sorted by date |
| `GET` | `/api/jobs/{id}` | ❌ | — | Get job by ID |
| `GET` | `/api/jobs/title/{jobTitle}` | ❌ | — | Get job by title |
| `GET` | `/api/jobs/title/{title}/id/{id}` | ❌ | — | Get job by title + ID |
| `GET` | `/api/jobs/experience/{exp}` | ❌ | — | Filter by experience |
| `GET` | `/api/jobs/company/{company}` | ❌ | — | Filter by company |
| `GET` | `/api/jobs/count` | ❌ | — | Total job count |
| `GET` | `/api/jobs/stats/type` | ❌ | — | Job count grouped by type |
| `GET` | `/api/jobs/search` | ❌ | — | Dynamic search with pagination |
| `POST` | `/api/jobs` | ✅ | ADMIN/RECRUITER | Create job |
| `POST` | `/api/jobs/batch` | ✅ | ADMIN/RECRUITER | Bulk create jobs |
| `PUT` | `/api/jobs/{id}` | ✅ | ADMIN/RECRUITER | Update job |
| `DELETE` | `/api/jobs/{id}` | ✅ | ADMIN only | Delete job |

### 🔍 Search Query Parameters

```
GET /api/jobs/search
  ?keyword=java                     ← Searches title, company, details (LIKE)
  &location=bangalore               ← Location filter
  &jobType=Full-Time                ← Exact match (case-insensitive)
  &company=google                   ← Exact match (case-insensitive)
  &minSalary=50000                  ← Minimum salary filter
  &postedFrom=2024-01-01            ← Date range start (ISO date)
  &postedTo=2024-12-31              ← Date range end
  &page=0 &size=10                  ← Pagination
  &sortBy=createdDate &direction=DESC ← Sorting
```

### 💰 Payment Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/payments/create-order` | Create Razorpay order |
| `POST` | `/api/payments/verify-payment` | Verify HMAC signature, store payment, send emails |

### 📊 Other Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET/POST` | `/api/topics/**` | Learning topics |
| `GET/POST` | `/api/learning-resources/**` | Learning resources |
| `GET/POST` | `/api/interview-questions/**` | Interview questions |
| `GET/POST` | `/api/quizzes/**` | Quiz management |
| `POST` | `/api/contact/**` | Contact form |
| `POST` | `/api/newsletter/**` | Newsletter subscription |
| `GET` | `/api/analytics/**` | Usage analytics |
| `GET` | `/actuator/health` | Service health (public) |
| `GET` | `/actuator/**` | Full metrics (auth required) |

---

## 🗄 12. Database Design & JPA Concepts

### Entity Relationship Overview

```
                    ┌──────────────┐
                    │   BaseEntity │  @MappedSuperclass
                    │  createdAt   │  @EntityListeners(AuditingEntityListener)
                    │  updatedAt   │  @CreatedDate, @LastModifiedDate
                    │  createdBy   │  @CreatedBy, @LastModifiedBy
                    └──────┬───────┘
                           │ extends
          ┌────────────────┼──────────────────┐
          ▼                ▼                  ▼
    ┌──────────┐    ┌─────────────┐    ┌──────────────┐
    │   Topic  │    │    Quiz     │    │  LearningRes │
    │  id, name│◄───│  topic(FK)  │    │  id, title   │
    └──────────┘    └──────┬──────┘    └──────────────┘
                           │ @OneToMany
                           ▼
                    ┌──────────────┐
                    │ QuizQuestion │
                    │ id, question │
                    │ options[]    │
                    └──────────────┘

    ┌──────────┐    ┌──────────────┐    ┌───────────────────┐
    │   Job    │    │ UserPayment  │    │ NewsletterSubscriber│
    │ (no base)│    │ txnId, amount│    │ email, subscribedAt│
    └──────────┘    └──────────────┘    └───────────────────┘
```

### Spring Data JPA Query Methods

```java
// Spring generates SQL from method names automatically:
List<Job> findByExperienceOrderByCreatedDateDescCreatedTimeDesc(String experience);
// → SELECT * FROM jobs WHERE experience = ? ORDER BY created_date DESC, created_time DESC

Optional<Job> findByJobTitleAndId(String jobTitle, Long id);
// → SELECT * FROM jobs WHERE job_title = ? AND id = ?

List<Job> findByCompanyIgnoreCase(String company);
// → SELECT * FROM jobs WHERE LOWER(company) = LOWER(?)

// JPQL @Query for custom logic:
@Query("SELECT j FROM Job j ORDER BY j.createdDate DESC, j.createdTime DESC")
List<Job> findAllByOrderByCreatedDateAndCreatedTimeDesc();

// Interface Projection — returns only needed fields (no entity overhead):
interface JobTypeCountProjection {
    String getJobType();
    long getCount();
}

@Query("SELECT j.jobType as jobType, COUNT(j) as count FROM Job j GROUP BY j.jobType")
List<JobTypeCountProjection> countJobsByType();
```

### JPA Specifications — Dynamic Queries

```java
// Type-safe, composable dynamic query builder
public static Specification<Job> build(JobSearchCriteria criteria) {
    Specification<Job> spec = Specification.where(null);

    if (criteria.getKeyword() != null) {
        String keyword = "%" + criteria.getKeyword().toLowerCase() + "%";
        spec = spec.and((root, query, cb) ->
            cb.or(
                cb.like(cb.lower(root.get("jobTitle")), keyword),
                cb.like(cb.lower(root.get("company")), keyword),
                cb.like(cb.lower(root.get("jobDetails")), keyword)
            )
        );
    }
    // More predicates added conditionally...
    return spec;
}
```

### @Transactional Deep Dive

```java
// Propagation Types:
@Transactional(propagation = Propagation.REQUIRED)      // Default: joins existing or creates new
@Transactional(propagation = Propagation.REQUIRES_NEW)  // Always creates new transaction
@Transactional(propagation = Propagation.NESTED)        // Savepoint inside existing
@Transactional(propagation = Propagation.SUPPORTS)      // Joins if exists; non-tx otherwise
@Transactional(propagation = Propagation.NEVER)         // Must NOT run in a transaction

// Isolation Levels:
@Transactional(isolation = Isolation.READ_COMMITTED)    // Default: no dirty reads
@Transactional(isolation = Isolation.REPEATABLE_READ)  // Same data each re-read
@Transactional(isolation = Isolation.SERIALIZABLE)     // Strictest — full isolation

// Read-only optimization (skips Hibernate dirty checking):
@Transactional(readOnly = true)
public List<JobDTO> getAllJobs() { ... }

// Rollback rules:
@Transactional(rollbackFor = Exception.class)              // Rollback on any exception
@Transactional(noRollbackFor = BusinessException.class)    // Don't rollback for this
```

---

## ⚠️ 13. Exception Handling Strategy

### GlobalExceptionHandler

```java
@ControllerAdvice          // Applied to ALL controllers globally
public class GlobalExceptionHandler {

    // Triggered by @Valid/@Validated failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);     // HTTP 400
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<String> handleJobNotFound(JobNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage()); // HTTP 404
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied"); // HTTP 403
    }

    @ExceptionHandler(Exception.class)   // Catch-all fallback
    public ResponseEntity<String> handleGenericException(Exception ex) {
        return ResponseEntity.internalServerError().body(ex.getMessage()); // HTTP 500
    }
}
```

### Custom Exceptions

| Exception Class | HTTP Status | When Thrown |
|----------------|------------|-------------|
| `JobNotFoundException` | `404 Not Found` | `findById()` returns empty |
| `ResourceNotFoundException` | `404 Not Found` | Generic resource lookup fails |
| `JobAlreadyExistsException` | `400 Bad Request` | Duplicate job creation attempt |
| `AccessDeniedException` | `403 Forbidden` | Role check fails via `@PreAuthorize` |

---

## 📬 14. Async & Event-Driven Design

### @Async — Non-Blocking Email

```java
@Service
@Slf4j
public class EmailAsyncService {

    @Async   // Runs in Spring's async thread pool, NOT the HTTP thread
    public void sendSupportEmails(String name, String email, ...) {
        log.info("Sending email asynchronously to {} and admin", email);
        sendEmailViaBrevo(email, "Thank you!", userHtml);
        sendEmailViaBrevo(adminEmail, "New support received!", adminHtml);
    }
}
```

**Lifecycle**:
1. HTTP thread calls `emailService.sendSupportEmails(...)` and **immediately returns**
2. Spring AOP proxy submits method to `TaskExecutor` thread pool
3. HTTP response returned to client instantly (no email delay)
4. Email sent in background thread

### Production-Grade Thread Pool Configuration

```java
// For production: configure ThreadPoolTaskExecutor instead of default SimpleAsyncTaskExecutor
@Bean("emailTaskExecutor")
public TaskExecutor emailTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("EmailAsync-");
    executor.initialize();
    return executor;
}

// Then use: @Async("emailTaskExecutor")
```

---

## 🐳 15. DevOps — Docker & Containerization

### Multi-Stage Dockerfile Analysis

```dockerfile
# ── Stage 1: BUILD (large image, has Maven + JDK) ──────────────────
FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B     # Cache this layer — reruns only if pom.xml changes
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: RUNTIME (small image, only JRE) ───────────────────────
FROM eclipse-temurin:21-jre-alpine-3.23   # JRE only — ~80MB vs ~600MB for Stage 1
WORKDIR /app

# Security: run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar   # Only the JAR, not build tools

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
```

### Docker Compose Architecture

```yaml
services:
  mysql:            # DB container with health check
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]

  app:              # Spring Boot container
    depends_on:
      mysql:
        condition: service_healthy   # Wait until MySQL is HEALTHY, not just started
    healthcheck:
      test: ["CMD", "wget", "http://localhost:8080/actuator/health"]
```

### Useful Docker Commands

```bash
# Build Docker image
docker build -t job-portal-backend:latest .

# Run with specific env file
docker run --env-file .env.prod -p 8080:8080 job-portal-backend:latest

# Check logs live
docker compose logs -f app

# Connect to DB
docker exec -it job-portal-mysql mysql -uroot -proot job_portal

# Inspect running container
docker exec -it job-portal-backend sh
```

---

## 🎯 16. Senior Java Interview Quick Reference

### Spring Boot Auto-Configuration — How It Works

```
JAR: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    ↓
Spring Boot reads this file at startup
    ↓
Each class has @ConditionalOn... annotations
    ↓
If conditions met (class on classpath, property set) → Bean is created
    ↓
Example: DataSourceAutoConfiguration creates DataSource bean
         IF spring-boot-starter-data-jpa is on classpath
         AND spring.datasource.url is configured
```

### @Transactional Propagation Cheatsheet

| Propagation | Existing Transaction | No Existing Transaction |
|-------------|---------------------|------------------------|
| `REQUIRED` (default) | Joins it | Creates new |
| `REQUIRES_NEW` | Suspends it, creates new | Creates new |
| `NESTED` | Creates savepoint | Creates new |
| `SUPPORTS` | Joins it | Runs without transaction |
| `NOT_SUPPORTED` | Suspends it | Runs without transaction |
| `MANDATORY` | Joins it | **Throws exception** |
| `NEVER` | **Throws exception** | Runs without transaction |

### Bean Scopes Cheatsheet

| Scope | Use Case | One Instance Per |
|-------|---------|-----------------|
| `singleton` (default) | Stateless services | Spring ApplicationContext |
| `prototype` | Stateful beans | Each `getBean()` call |
| `request` | Web request-scoped data | HTTP Request |
| `session` | User session data | HTTP Session |
| `application` | ServletContext-scoped | Web ApplicationContext |

### N+1 Problem & Solutions

```java
// BAD: 1 query for topics + N queries for each topic's questions
List<Topic> topics = topicRepository.findAll();  // 1 query
topics.forEach(t -> t.getQuestions().size());     // N queries (LAZY loading)

// Solution 1: JOIN FETCH in JPQL
@Query("SELECT t FROM Topic t JOIN FETCH t.questions")
List<Topic> findAllWithQuestions();              // 1 query with JOIN

// Solution 2: Entity Graph
@EntityGraph(attributePaths = {"questions"})
List<Topic> findAll();

// Solution 3: Batch fetching
@OneToMany
@BatchSize(size = 10)  // Hibernate fetches 10 collections in one IN query
private List<QuizQuestion> questions;
```

### Common Interview Questions & Answers

| Question | Key Points |
|---------|-----------|
| *What is Spring AOP?* | Aspect Oriented Programming — cross-cutting concerns (logging, security, caching) implemented as Aspects that wrap method execution via proxies |
| *What is the difference between @Component, @Service, @Repository?* | All register beans. @Repository adds exception translation. @Service has no extra behavior but documents intent. Semantically important for readability. |
| *How does @Transactional work internally?* | Spring creates a proxy around the bean. The proxy intercepts method calls, starts a transaction, calls the actual method, then commits or rolls back. |
| *What is HikariCP?* | High-performance JDBC connection pool. Pre-creates DB connections and reuses them. Configured via `spring.datasource.hikari.*`. |
| *What is the difference between LAZY and EAGER fetching?* | LAZY: association loaded only when accessed (default for @OneToMany, @ManyToMany). EAGER: loaded immediately with parent (default for @ManyToOne, @OneToOne). Always use LAZY to avoid N+1. |
| *How does Spring Cache work with Caffeine?* | `@EnableCaching` activates AOP proxies. `@Cacheable` intercepts method calls, checks cache, returns cached value or executes method. Caffeine is the in-memory store. |

---

<div align="center">

### 📫 Built by [Chaitanya Gidijala](https://www.chaitanyatechworld.com)

*Production-grade Spring Boot 3 backend: JWT Security • Caffeine Cache • Testcontainers • JPA Specifications • Async Email • Razorpay Payments*

[![Website](https://img.shields.io/badge/Website-chaitanyatechworld.com-6366f1?style=for-the-badge)](https://www.chaitanyatechworld.com)

</div>
