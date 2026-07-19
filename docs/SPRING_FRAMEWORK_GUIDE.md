# 🌱 Spring Framework — Complete Learning Guide
## From Zero (5th Class Level) → Expert (4+ Years Experience)

> **Philosophy**: We build up like a house — first lay the foundation, then walls, then roof.
> Every concept is explained with a **real-world story**, then **code**, then **interview questions**.

---

## 📑 Table of Contents

| Phase | Topic | Level |
|-------|-------|-------|
| [Phase 1](#phase-1-why-does-spring-exist) | Why Spring Exists — The Problem It Solves | 🟢 Beginner |
| [Phase 2](#phase-2-ioc-container--dependency-injection) | IoC Container & Dependency Injection | 🟢 Beginner |
| [Phase 3](#phase-3-spring-boot--auto-configuration) | Spring Boot & Auto-Configuration | 🟡 Intermediate |
| [Phase 4](#phase-4-spring-web-mvc) | Spring Web MVC — REST APIs | 🟡 Intermediate |
| [Phase 5](#phase-5-spring-data-jpa--hibernate) | Spring Data JPA & Hibernate | 🟡 Intermediate |
| [Phase 6](#phase-6-spring-security) | Spring Security + JWT | 🔴 Advanced |
| [Phase 7](#phase-7-advanced-spring-concepts) | AOP, Caching, Async, Events, Internals | 🔴 Expert |
| [Interview Master Sheet](#-interview-master-sheet) | All Key Q&A for 4+ Years Level | 🔴 Expert |

---

# Phase 1: Why Does Spring Exist?

## 🍎 The Story (5th Class Level)

> Imagine you are building a **toy car**. The toy car needs:
> - 4 **Wheels**
> - 1 **Engine**
> - 1 **Steering Wheel**
>
> In the old Java way, the car **makes its own wheels**. If you want bigger wheels, you have to open the car and modify it from inside. Very difficult!
>
> **Spring says**: *"Don't build your own wheels. Tell me what you need, I will give it to you."*
> The car just says *"I need 4 wheels"* — Spring provides them. You can change wheel type without touching the car.
>
> This is called **Inversion of Control (IoC)**.

---

## 💻 The Problem — Tight Coupling (BAD)

```java
// ❌ BAD: NotificationService creates its own EmailSender
// If we want to switch to SMS, we must MODIFY NotificationService

public class NotificationService {

    // The service creates its OWN dependency
    private EmailSender emailSender = new EmailSender(); // TIGHT COUPLING!

    public void sendAlert(String message) {
        emailSender.send(message);
    }
}

// Problems:
// 1. Cannot test without sending a real email
// 2. Cannot switch to SMS without modifying this class
// 3. Cannot reuse EmailSender elsewhere easily
```

---

## ✅ The Solution — Loose Coupling (GOOD)

```java
// ✅ GOOD: NotificationService RECEIVES its dependency
// We can inject ANY MessageSender — Email, SMS, WhatsApp!

public interface MessageSender {
    void send(String message);
}

public class EmailSender implements MessageSender {
    public void send(String message) {
        System.out.println("Email sent: " + message);
    }
}

public class SmsSender implements MessageSender {
    public void send(String message) {
        System.out.println("SMS sent: " + message);
    }
}

// NotificationService does NOT know which sender it gets
// Spring injects it from OUTSIDE
public class NotificationService {

    private final MessageSender messageSender; // Just an interface

    // Constructor receives the dependency — NOT creates it
    public NotificationService(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public void sendAlert(String message) {
        messageSender.send(message); // Works for Email, SMS, anything!
    }
}
```

**This is Dependency Injection. Spring does this injection AUTOMATICALLY.**

---

## 🎯 Key Terms to Remember

| Term | Simple Meaning | Real Example |
|------|---------------|-------------|
| **IoC (Inversion of Control)** | "Don't call us, we'll call you" — Spring controls object creation | Spring creates `JobService` object for you |
| **Dependency Injection (DI)** | Spring gives you what you need | Spring "injects" `JobRepository` into `JobService` |
| **Bean** | Any Java object managed by Spring | Your `@Service`, `@Repository`, `@Controller` classes |
| **ApplicationContext** | The Spring container that holds all beans | Like a factory/warehouse that stores all objects |

---

# Phase 2: IoC Container & Dependency Injection

## 🏭 The Spring Container (ApplicationContext)

> Think of Spring as a **big factory**. When your app starts:
> 1. Spring reads your code
> 2. Creates ALL the objects (beans) you need
> 3. Connects them together
> 4. Gives them to you when asked

```
App Starts
    ↓
Spring scans for @Component, @Service, @Repository, @Controller
    ↓
Creates all objects and stores them in ApplicationContext
    ↓
Injects dependencies into each object
    ↓
Your app is ready!
```

---

## 2.1 Three Types of Dependency Injection

### Type 1: Constructor Injection ✅ (BEST — Always use this)

```java
@Service
public class JobService {

    // final = cannot change after construction (immutable — thread safe)
    private final JobRepository jobRepository;
    private final EmailService emailService;

    // Spring automatically calls this constructor
    // and passes the required beans
    public JobService(JobRepository jobRepository, EmailService emailService) {
        this.jobRepository = jobRepository;
        this.emailService = emailService;
    }

    // WHY BEST?
    // 1. Fields are final (immutable)
    // 2. Easy to test — just pass mock objects to constructor
    // 3. Fails fast — if bean missing, app won't start
    // 4. Makes dependencies explicit and visible
}

// Lombok shortcut — generates constructor automatically
@Service
@RequiredArgsConstructor  // Lombok: generates constructor for all final fields
public class JobService {
    private final JobRepository jobRepository;   // Injected automatically
    private final EmailService emailService;      // Injected automatically
}
```

### Type 2: Setter Injection ⚠️ (Use for Optional dependencies only)

```java
@Service
public class ReportService {

    private EmailService emailService;  // NOT final — can be null

    @Autowired(required = false)  // Optional dependency
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void generateReport() {
        if (emailService != null) {
            emailService.send("Report ready");
        }
    }

    // WHY USE: When dependency is optional (app works without it)
    // WHY NOT ALWAYS: Fields not final, can be changed after construction
}
```

### Type 3: Field Injection ❌ (AVOID in production code)

```java
@Service
public class JobService {

    @Autowired  // Spring injects directly into field via reflection
    private JobRepository jobRepository;  // NOT final, NOT testable easily

    // WHY BAD?
    // 1. Cannot test without Spring context
    // 2. Can inject null (no fail-fast on startup)
    // 3. Hides dependencies — hard to see what class needs
    // 4. Breaks with final fields
}

// Test problem:
// new JobService()  ← jobRepository will be NULL! Can't test without Spring!
```

---

## 2.2 Stereotype Annotations — Registering Beans

```java
// ── @Component: Generic bean ──────────────────────────────────────
@Component
public class FileUploadUtil {
    // Any utility class goes here
    // Spring manages lifecycle (creates, injects, destroys)
}

// ── @Service: Business Logic layer ───────────────────────────────
@Service  // Same as @Component but expresses "I am a service"
public class JobService {
    // Contains business rules, orchestrates repository calls
    // Typically @Transactional methods live here
}

// ── @Repository: Data Access layer ───────────────────────────────
@Repository  // Same as @Component BUT adds:
             // Exception translation — converts SQL exceptions to Spring exceptions
public interface JobRepository extends JpaRepository<Job, Long> {
    // Spring Data generates implementation automatically!
}

// ── @Controller / @RestController: Web layer ─────────────────────
@RestController  // = @Controller + @ResponseBody
                 // All return values automatically serialized to JSON
public class JobController {
    // Handles HTTP requests, calls service, returns response
}

// ── @Configuration + @Bean: Manual bean registration ─────────────
@Configuration  // Spring reads this class for @Bean definitions
public class AppConfig {

    @Bean  // Method return value becomes a Spring bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

## 2.3 @Qualifier and @Primary — Handling Multiple Beans

```java
// Scenario: Two implementations of MessageSender

@Component
public class EmailSender implements MessageSender { ... }

@Component
public class SmsSender implements MessageSender { ... }

// ── Problem: Spring doesn't know which one to inject! ────────────
@Service
public class NotificationService {
    @Autowired
    private MessageSender messageSender; // ERROR: 2 beans found!
}

// ── Solution 1: @Primary — Mark the default choice ────────────────
@Component
@Primary  // This is the default when multiple beans exist
public class EmailSender implements MessageSender { ... }

// ── Solution 2: @Qualifier — Name-based selection ─────────────────
@Service
public class NotificationService {
    @Autowired
    @Qualifier("smsSender")  // Exact bean name (lowercase class name by default)
    private MessageSender messageSender; // Gets SmsSender specifically
}

// ── Solution 3: Constructor Injection with @Qualifier ─────────────
@Service
@RequiredArgsConstructor
public class AlertService {

    @Qualifier("emailSender")
    private final MessageSender messageSender; // Gets EmailSender
}
```

---

## 2.4 Bean Scopes — How Many Instances?

```java
// ── SINGLETON (default): One instance per Spring container ─────────
@Service  // By default all beans are singleton
public class JobService {
    // Only ONE JobService object exists for entire app lifetime
    // Shared across all requests — must be stateless (no instance variables for request data!)
}

// ── PROTOTYPE: New instance every time ─────────────────────────────
@Component
@Scope("prototype")
public class ReportGenerator {
    private List<String> lines = new ArrayList<>(); // Stateful — each caller gets fresh list

    // New object created every time it's injected/requested
}

// ── REQUEST: One per HTTP request (Web only) ───────────────────────
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
    private String requestId = UUID.randomUUID().toString();
    // Unique per HTTP request
}

// ── SESSION: One per HTTP session (Web only) ───────────────────────
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserCart {
    private List<Item> items = new ArrayList<>();
    // Survives across multiple requests for same user session
}

// Interview Q: Can you inject a prototype bean into a singleton?
// Answer: YES, but you get the SAME prototype instance (Spring injects at startup time)
// Fix: Use @Lookup or ObjectProvider<MyPrototype>
@Service
public class SingletonService {
    @Autowired
    private ObjectProvider<ReportGenerator> reportGeneratorProvider;

    public void doWork() {
        ReportGenerator generator = reportGeneratorProvider.getObject(); // New each time!
    }
}
```

---

## 2.5 Bean Lifecycle

```java
@Component
public class MyBean {

    // Called AFTER constructor + DI completed
    @PostConstruct
    public void init() {
        System.out.println("Bean is ready! Initialize resources here.");
        // Load config from DB, open connections, warm up cache, etc.
    }

    // Called BEFORE bean is destroyed (app shutdown)
    @PreDestroy
    public void cleanup() {
        System.out.println("Bean shutting down. Clean up resources here.");
        // Close connections, flush cache, release files, etc.
    }
}

// BEAN LIFECYCLE ORDER:
// 1. Constructor called
// 2. Dependencies injected (@Autowired fields set)
// 3. @PostConstruct method called
// 4. Bean is ready and used by app
// 5. @PreDestroy called on app shutdown
// 6. Bean destroyed
```

---

# Phase 3: Spring Boot & Auto-Configuration

## 🎩 The Magic of Spring Boot

> Without Spring Boot: You configure EVERYTHING manually — web server, DB connection, JSON serializer...
> With Spring Boot: Add a dependency → Spring Boot configures it automatically!

```java
// @SpringBootApplication is actually 3 annotations in one:

@SpringBootApplication
// = @Configuration          : This class can define @Bean methods
// + @EnableAutoConfiguration : Enable auto-config based on classpath
// + @ComponentScan           : Scan current package + sub-packages for beans

public class JobPortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobPortalApplication.class, args);
        // Starts embedded Tomcat server (no external server needed!)
        // Creates ApplicationContext
        // Runs all auto-configurations
    }
}
```

---

## 3.1 How Auto-Configuration Works

```
You add: spring-boot-starter-data-jpa to pom.xml
    ↓
Spring Boot checks: Is HibernateJpa class on classpath? YES
    ↓
Spring Boot checks: Is spring.datasource.url configured? YES
    ↓
Spring Boot auto-creates:
    - DataSource bean (connection pool)
    - EntityManagerFactory bean
    - TransactionManager bean
    - JpaRepositories support
    ↓
You write ZERO configuration code!
```

```java
// How Spring Boot knows what to auto-configure:
// File inside spring-boot-autoconfigure jar:
// META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

// Example entries:
// org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
// org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
// org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
// org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration

// Each auto-config class uses @Conditional annotations:
@AutoConfiguration
@ConditionalOnClass(DataSource.class)       // Only if DataSource is on classpath
@ConditionalOnMissingBean(DataSource.class) // Only if YOU haven't already defined one
public class DataSourceAutoConfiguration {

    @Bean
    public DataSource dataSource() {
        // Creates HikariCP DataSource automatically
        return HikariDataSourceBuilder.create().build();
    }
}
```

---

## 3.2 @Conditional Annotations — Loading Beans Conditionally

```java
// ── @ConditionalOnProperty: Load bean only if property is set ────────
@Bean
@ConditionalOnProperty(name = "feature.email.enabled", havingValue = "true")
public EmailService emailService() {
    return new EmailService();
}
// In application.properties: feature.email.enabled=true → bean created
// Without property → bean NOT created

// ── @ConditionalOnBean: Load if another bean exists ──────────────────
@Bean
@ConditionalOnBean(EmailService.class)
public EmailTemplate emailTemplate() {
    return new EmailTemplate();
}

// ── @ConditionalOnMissingBean: Load as fallback ──────────────────────
@Bean
@ConditionalOnMissingBean(CacheManager.class) // If no custom cache manager
public CacheManager defaultCacheManager() {
    return new ConcurrentMapCacheManager(); // Use simple default
}

// ── @ConditionalOnClass: Load if class is on classpath ───────────────
@Bean
@ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
public CacheManager caffeineCacheManager() {
    return new CaffeineCacheManager();
}
```

---

## 3.3 @Value and @ConfigurationProperties — Reading Config

```java
// ── @Value: Inject single property ───────────────────────────────────
@Component
public class JwtTokenProvider {

    @Value("${app.jwt-secret}")         // Reads from application.properties
    private String jwtSecret;

    @Value("${app.jwt-expiration:3600}") // Default value 3600 if not set
    private long jwtExpiration;

    @Value("${APP_JWT_SECRET}")         // Also reads from env variables!
    private String secretFromEnv;

    @Value("#{systemProperties['user.home']}") // SpEL expression
    private String userHome;
}

// ── @ConfigurationProperties: Bind a GROUP of properties (PREFERRED) ─
// application.properties:
// app.mail.host=smtp.gmail.com
// app.mail.port=587
// app.mail.username=user@gmail.com
// app.mail.password=secret

@Component
@ConfigurationProperties(prefix = "app.mail")
@Data  // Lombok — generates getters/setters for binding
public class MailProperties {
    private String host;
    private int port;
    private String username;
    private String password;
}

// WHY @ConfigurationProperties is better than @Value:
// 1. Type-safe (converts string to int automatically)
// 2. Groups related properties
// 3. Validated with @Validated
// 4. IDE autocomplete in YAML/properties files
```

---

## 3.4 @Profile — Environment-Specific Beans

```java
// ── Load different beans for different environments ───────────────────

@Service
@Profile("dev")  // Only created when spring.profiles.active=dev
public class MockEmailService implements EmailService {
    public void send(String to, String body) {
        System.out.println("MOCK EMAIL to: " + to); // No real email in dev
    }
}

@Service
@Profile("prod")  // Only created when spring.profiles.active=prod
public class RealEmailService implements EmailService {
    public void send(String to, String body) {
        // Actually sends email via Brevo/SendGrid etc.
    }
}

// application.properties:
// spring.profiles.active=dev

// application-dev.properties  (loaded when profile=dev)
// spring.datasource.url=jdbc:mysql://localhost:3306/mydb_dev

// application-prod.properties (loaded when profile=prod)
// spring.datasource.url=jdbc:mysql://prod-server:3306/mydb_prod

// In code — check active profile:
@Autowired
private Environment environment;

public void checkProfile() {
    if (environment.acceptsProfiles(Profiles.of("dev"))) {
        System.out.println("Running in DEV mode");
    }
}
```

---

# Phase 4: Spring Web MVC

## 🌐 How a Request Travels Through Spring

```
Browser: GET http://localhost:8080/api/jobs/5
                    ↓
        Embedded Tomcat Server receives request
                    ↓
        DispatcherServlet (Front Controller — one servlet handles all)
                    ↓
        HandlerMapping: Which controller handles /api/jobs/5 ?
                    ↓
        Finds: JobController.getJob(@PathVariable Long id)
                    ↓
        HandlerAdapter: Call the method, resolve @PathVariable, @RequestBody
                    ↓
        JobController.getJob(5L) runs
                    ↓
        Returns: ResponseEntity<ApiResponse<JobDTO>>
                    ↓
        HttpMessageConverter: Converts JobDTO → JSON
                    ↓
        Response: 200 OK + JSON body sent to browser
```

---

## 4.1 Controller Annotations — Complete Guide

```java
@RestController             // @Controller + @ResponseBody (all methods return JSON)
@RequestMapping("/api/jobs") // Base path for all endpoints in this class
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // ── GET: Retrieve data ────────────────────────────────────────────
    @GetMapping                           // GET /api/jobs
    public ResponseEntity<List<JobDTO>> getAllJobs() {
        return ResponseEntity.ok(jobService.getAllJobs());
    }

    @GetMapping("/{id}")                  // GET /api/jobs/5
    public ResponseEntity<JobDTO> getJob(
        @PathVariable Long id) {          // Extracts "5" from URL
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    @GetMapping("/search")               // GET /api/jobs/search?keyword=java&page=0
    public ResponseEntity<Page<JobDTO>> search(
        @RequestParam(required = false) String keyword,           // Query param
        @RequestParam(defaultValue = "0") int page,              // Default value
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdDate") String sortBy,
        @PageableDefault(size = 10, sort = "createdDate") Pageable pageable) {
        // Pageable is auto-populated from page, size, sort params
        return ResponseEntity.ok(jobService.searchJobs(keyword, pageable));
    }

    // ── POST: Create data ─────────────────────────────────────────────
    @PostMapping                          // POST /api/jobs
    @PreAuthorize("hasRole('ADMIN')")    // Security check before method runs
    public ResponseEntity<JobDTO> createJob(
        @Valid @RequestBody JobDTO jobDTO) { // @Valid triggers Jakarta validation
        // @RequestBody: Deserializes request JSON → JobDTO
        JobDTO saved = jobService.saveJob(jobDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved); // 201 Created
    }

    // ── PUT: Full update ─────────────────────────────────────────────
    @PutMapping("/{id}")                  // PUT /api/jobs/5
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<JobDTO> updateJob(
        @PathVariable Long id,
        @Valid @RequestBody JobDTO jobDTO) {
        return ResponseEntity.ok(jobService.updateJob(id, jobDTO));
    }

    // ── PATCH: Partial update ────────────────────────────────────────
    @PatchMapping("/{id}/status")        // PATCH /api/jobs/5/status
    public ResponseEntity<Void> updateStatus(
        @PathVariable Long id,
        @RequestParam String status) {
        jobService.updateStatus(id, status);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // ── DELETE: Remove data ──────────────────────────────────────────
    @DeleteMapping("/{id}")               // DELETE /api/jobs/5
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
        return ResponseEntity.ok().build();
    }
}
```

---

## 4.2 Standard API Response Wrapper

```java
// ALWAYS use a consistent response structure in production!
// Your project already does this with ApiResponse<T>

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp = LocalDateTime.now();

    // Factory methods
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false, message, data, LocalDateTime.now());
    }
}

// Usage in Controller:
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<JobDTO>> getJob(@PathVariable Long id) {
    JobDTO job = jobService.getJobById(id);
    return ResponseEntity.ok(ApiResponse.success("Job fetched successfully", job));
}

// Response JSON:
// {
//   "success": true,
//   "message": "Job fetched successfully",
//   "data": { "id": 5, "jobTitle": "Java Developer", ... },
//   "timestamp": "2024-01-15T10:30:00"
// }
```

---

## 4.3 @ControllerAdvice — Global Exception Handling

```java
@ControllerAdvice  // Applies to ALL controllers in app
@Slf4j
public class GlobalExceptionHandler {

    // ── Handle @Valid validation failures ─────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        log.warn("Validation failed: {}", errors);
        return ApiResponse.error("Validation failed", errors);
    }

    // ── Handle resource not found ─────────────────────────────────────
    @ExceptionHandler(JobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(JobNotFoundException ex) {
        log.warn("Job not found: {}", ex.getMessage());
        return ApiResponse.error(ex.getMessage(), null);
    }

    // ── Handle access denied ──────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException ex) {
        return ApiResponse.error("Access Denied: Insufficient permissions", null);
    }

    // ── Catch-all handler ─────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGeneral(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ApiResponse.error("An unexpected error occurred", null);
    }
}

// Custom exception class:
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String message) {
        super(message);
    }

    // Why RuntimeException and not Exception?
    // @Transactional only rolls back on RuntimeException by default
}
```

---

## 4.4 Pagination & Sorting — The Right Way

```java
// ── Service Method with Pageable ──────────────────────────────────────
@Service
public class JobService {

    public Page<JobDTO> getAllJobsPaged(Pageable pageable) {
        Page<Job> page = jobRepository.findAll(pageable);

        // Map entities to DTOs but preserve pagination metadata
        return page.map(job -> modelMapper.map(job, JobDTO.class));
    }
}

// ── Controller ────────────────────────────────────────────────────────
@GetMapping
public ResponseEntity<ApiResponse<Page<JobDTO>>> getJobs(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "createdDate") String sortBy,
    @RequestParam(defaultValue = "DESC") String direction) {

    Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
    Pageable pageable = PageRequest.of(page, size, sort);

    return ResponseEntity.ok(ApiResponse.success("Jobs fetched", jobService.getAllJobsPaged(pageable)));
}

// Client calls: GET /api/jobs?page=2&size=5&sortBy=salary&direction=ASC
// Response includes:
// {
//   "content": [...5 jobs...],
//   "totalElements": 150,
//   "totalPages": 30,
//   "number": 2,
//   "size": 5,
//   "first": false,
//   "last": false
// }
```

---

# Phase 5: Spring Data JPA & Hibernate

## 🗄 Understanding ORM — Object Relational Mapping

> **The Problem**: Java speaks "Objects". Database speaks "Tables". They're different languages!
> **ORM's Job**: Translate between them automatically.

```
Java Class: Job { String jobTitle; String company; }
                    ↕ ORM (Hibernate)
DB Table:   jobs (job_title VARCHAR, company VARCHAR)
```

---

## 5.1 Entity Mapping — Complete Reference

```java
@Entity                          // This class maps to a DB table
@Table(
    name = "jobs",               // Custom table name
    schema = "job_portal",       // Schema name
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"job_title", "company"}) // Composite unique
    },
    indexes = {
        @Index(name = "idx_company", columnList = "company")      // DB index
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // AUTO_INCREMENT
    private Long id;

    // GenerationType options:
    // IDENTITY: DB auto-increment (MySQL, PostgreSQL SERIAL) — MOST COMMON
    // SEQUENCE: Uses DB sequence (PostgreSQL recommended)
    // TABLE:    Uses separate table for IDs (AVOID — slow)
    // AUTO:     Spring picks best strategy for current DB

    @Column(
        name = "job_title",       // Custom column name
        nullable = false,         // NOT NULL constraint
        length = 100,             // VARCHAR(100)
        updatable = false         // Cannot be updated after insert
    )
    private String jobTitle;

    @Column(columnDefinition = "TEXT")  // For long text — no length limit
    private String jobDetails;

    @Column(name = "is_active")
    private boolean active = true;

    @Enumerated(EnumType.STRING)  // Store "FULL_TIME" not 0 in DB
    // NEVER use EnumType.ORDINAL — adding enum values breaks existing data!
    private JobType jobType;

    @CreationTimestamp   // Hibernate: auto-set on INSERT
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp     // Hibernate: auto-set on INSERT + UPDATE
    private LocalDateTime updatedAt;

    // ── Relationships ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)    // LAZY = don't load company until accessed
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @OneToMany(
        mappedBy = "job",           // "job" = field name in Application class
        cascade = CascadeType.ALL,  // Operations cascade to applications
        orphanRemoval = true,       // Delete Application if removed from list
        fetch = FetchType.LAZY      // ALWAYS lazy for collections!
    )
    private List<Application> applications = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "job_skills",
        joinColumns = @JoinColumn(name = "job_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> skills = new HashSet<>();

    @ElementCollection  // List of non-entity values (no separate entity needed)
    @CollectionTable(name = "job_tags", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();
}
```

---

## 5.2 Repository — All Query Methods

```java
@Repository
public interface JobRepository extends JpaRepository<Job, Long>,
                                       JpaSpecificationExecutor<Job> {

    // ── Method name queries (Spring generates SQL automatically) ────────
    List<Job> findByCompany(String company);
    // → SELECT * FROM jobs WHERE company = ?

    List<Job> findByCompanyIgnoreCase(String company);
    // → SELECT * FROM jobs WHERE LOWER(company) = LOWER(?)

    List<Job> findByJobTypeAndActive(JobType type, boolean active);
    // → SELECT * FROM jobs WHERE job_type = ? AND is_active = ?

    Optional<Job> findByIdAndActive(Long id, boolean active);
    // Returns Optional — safe way to handle not found

    List<Job> findByJobTitleContainingIgnoreCase(String keyword);
    // → SELECT * FROM jobs WHERE LOWER(job_title) LIKE LOWER('%keyword%')

    List<Job> findByCreatedAtAfter(LocalDateTime date);
    // → SELECT * FROM jobs WHERE created_at > ?

    List<Job> findByCompanyOrderByCreatedAtDesc(String company);
    // → ORDER BY created_at DESC

    Page<Job> findByActive(boolean active, Pageable pageable);
    // Returns paginated result

    long countByActive(boolean active);
    // → SELECT COUNT(*) FROM jobs WHERE is_active = ?

    boolean existsByJobTitleAndCompany(String title, String company);
    // → SELECT COUNT(*) > 0 WHERE job_title = ? AND company = ?

    void deleteByIdAndActive(Long id, boolean active);
    // → DELETE FROM jobs WHERE id = ? AND is_active = ?

    // ── JPQL @Query (Java-based, database-independent) ─────────────────
    @Query("SELECT j FROM Job j WHERE j.salary > :minSalary ORDER BY j.createdAt DESC")
    List<Job> findHighPayingJobs(@Param("minSalary") double minSalary);

    @Query("SELECT j FROM Job j WHERE " +
           "LOWER(j.jobTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.company) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Job> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT j.jobType as jobType, COUNT(j) as count FROM Job j GROUP BY j.jobType")
    List<JobTypeCountProjection> countJobsByType();

    // ── Native SQL @Query (database-specific, use sparingly) ───────────
    @Query(value = "SELECT * FROM jobs WHERE MATCH(job_title, job_details) AGAINST (:keyword IN BOOLEAN MODE)",
           nativeQuery = true)
    List<Job> fullTextSearch(@Param("keyword") String keyword);

    // ── @Modifying: For UPDATE/DELETE queries ───────────────────────────
    @Modifying
    @Transactional
    @Query("UPDATE Job j SET j.active = false WHERE j.createdAt < :cutoff")
    int deactivateOldJobs(@Param("cutoff") LocalDateTime cutoff);
    // Returns number of affected rows

    // ── Interface Projection: Return only needed columns ────────────────
    interface JobSummary {
        Long getId();
        String getJobTitle();
        String getCompany();
    }

    List<JobSummary> findAllProjectedBy();
    // Only fetches id, job_title, company — NOT all columns (performance!)
}
```

---

## 5.3 @Transactional — The Most Important Concept for Senior Interviews

```java
@Service
@Slf4j
public class JobService {

    // ── Basic @Transactional ──────────────────────────────────────────
    @Transactional
    public JobDTO createJob(JobDTO dto) {
        // Everything in this method runs in ONE transaction
        Job job = modelMapper.map(dto, Job.class);
        Job saved = jobRepository.save(job);       // INSERT

        // If ANY exception occurs here, BOTH inserts are rolled back!
        auditRepository.save(new AuditLog("JOB_CREATED", saved.getId())); // INSERT

        return modelMapper.map(saved, JobDTO.class);
    }

    // ── readOnly = true: Performance optimization ─────────────────────
    @Transactional(readOnly = true)
    public List<JobDTO> getAllJobs() {
        // readOnly=true tells Hibernate:
        // 1. Don't track entity changes (dirty checking disabled)
        // 2. Hibernate can use read replicas
        // 3. Faster — no flush needed
        return jobRepository.findAll().stream()
            .map(j -> modelMapper.map(j, JobDTO.class))
            .toList();
    }

    // ── rollbackFor: Control which exceptions cause rollback ───────────
    @Transactional(rollbackFor = Exception.class)
    // Default: rollback only on RuntimeException and Error
    // With rollbackFor = Exception.class: also rolls back on checked exceptions
    public void processPayment(PaymentDTO dto) throws PaymentException {
        // ...
    }

    // ── PROPAGATION DEEP DIVE ─────────────────────────────────────────

    // REQUIRED (default): Join existing TX or create new one
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA() {
        jobRepository.save(job1);
        methodB(); // methodB joins methodA's transaction
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void methodB() {
        jobRepository.save(job2);
        // job1 and job2 in SAME transaction!
        // If methodB fails → BOTH roll back
    }

    // REQUIRES_NEW: Always create new independent transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAuditLog(String event) {
        // This runs in its OWN transaction
        // Even if caller's transaction fails, audit log is still saved!
        auditRepository.save(new AuditLog(event));
    }

    // SUPPORTS: Use existing TX if available, else run without TX
    @Transactional(propagation = Propagation.SUPPORTS)
    public JobDTO getJobById(Long id) {
        return jobRepository.findById(id).orElseThrow();
        // Fine without TX for simple reads
    }

    // MANDATORY: MUST run inside an existing transaction
    @Transactional(propagation = Propagation.MANDATORY)
    public void validateJob(Job job) {
        // Throws TransactionRequiredException if called without TX
        // Ensures this is only called from within a transactional context
    }

    // NEVER: Must NOT run in a transaction
    @Transactional(propagation = Propagation.NEVER)
    public void exportToFile(List<Job> jobs) {
        // File export should NOT be in a DB transaction
        // Throws IllegalTransactionStateException if called within TX
    }
}

// ── CRITICAL: @Transactional PITFALLS ────────────────────────────────

// ❌ PITFALL 1: Self-invocation — @Transactional doesn't work on self-calls!
@Service
public class JobService {

    @Transactional
    public void createJob(JobDTO dto) {
        // Spring wraps this in a proxy
    }

    public void processJobs(List<JobDTO> dtos) {
        for (JobDTO dto : dtos) {
            createJob(dto); // ❌ WRONG: Called on 'this', not the proxy!
                            // @Transactional NOT applied here!
        }
    }
}

// ✅ FIX: Inject self or move to separate class
@Service
public class JobBatchService {
    private final JobService jobService; // Inject the PROXY, not this

    public void processJobs(List<JobDTO> dtos) {
        for (JobDTO dto : dtos) {
            jobService.createJob(dto); // ✅ Called on proxy — @Transactional works!
        }
    }
}

// ❌ PITFALL 2: @Transactional on private methods — DOESN'T WORK!
@Service
public class JobService {
    @Transactional  // ❌ Ignored! Spring proxy only wraps PUBLIC methods
    private void internalSave(Job job) { ... }
}

// ❌ PITFALL 3: Catching exceptions prevents rollback
@Transactional
public void createJob(JobDTO dto) {
    try {
        jobRepository.save(job);
        throw new RuntimeException("Error!");
    } catch (RuntimeException e) {
        // ❌ Exception caught — Spring doesn't know about it — NO ROLLBACK!
        log.error("Error: ", e);
    }
}

// ✅ FIX: Let exception propagate OR use TransactionAspectSupport
@Transactional
public void createJob(JobDTO dto) {
    try {
        jobRepository.save(job);
    } catch (RuntimeException e) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        throw e; // Re-throw for proper rollback
    }
}
```

---

## 5.4 JPA Specifications — Dynamic Queries

```java
// PROBLEM: User can filter by keyword, location, jobType, salary...
// Any combination! You can't write a separate query for each combination.

// SOLUTION: JPA Specifications — composable predicates

public final class JobSpecifications {

    public static Specification<Job> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return null; // No filter
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("jobTitle")), pattern),
                cb.like(cb.lower(root.get("company")), pattern)
            );
        };
    }

    public static Specification<Job> hasJobType(String jobType) {
        return (root, query, cb) ->
            jobType == null ? null : cb.equal(root.get("jobType"), jobType);
    }

    public static Specification<Job> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Job> postedAfter(LocalDate date) {
        return (root, query, cb) ->
            date == null ? null : cb.greaterThanOrEqualTo(root.get("createdDate"), date);
    }
}

// Compose in service:
@Service
public class JobService {

    public Page<JobDTO> searchJobs(JobSearchCriteria criteria, Pageable pageable) {
        Specification<Job> spec = Specification.where(isActive())
            .and(hasKeyword(criteria.getKeyword()))
            .and(hasJobType(criteria.getJobType()))
            .and(postedAfter(criteria.getPostedFrom()));
        // Null predicates are automatically ignored!

        return jobRepository.findAll(spec, pageable)
            .map(j -> modelMapper.map(j, JobDTO.class));
    }
}
```

---

## 5.5 The N+1 Problem — Critical Interview Topic

```java
// SETUP: Topic has many Questions
@Entity
public class Topic {
    @OneToMany(mappedBy = "topic", fetch = FetchType.LAZY)
    private List<Question> questions;
}

// ─────────────────────────────────────────────────────────────────
// ❌ THE N+1 PROBLEM:
// ─────────────────────────────────────────────────────────────────
List<Topic> topics = topicRepository.findAll();  // Query 1: SELECT all topics
for (Topic t : topics) {
    t.getQuestions().size(); // Query 2,3,4...N: SELECT questions WHERE topic_id = ?
}
// If 100 topics → 101 queries! BAD for performance.

// ─────────────────────────────────────────────────────────────────
// ✅ SOLUTION 1: JOIN FETCH in JPQL
// ─────────────────────────────────────────────────────────────────
@Query("SELECT t FROM Topic t JOIN FETCH t.questions")
List<Topic> findAllWithQuestions();
// → 1 query with SQL JOIN: SELECT t.*, q.* FROM topics t LEFT JOIN questions q ON q.topic_id = t.id

// ─────────────────────────────────────────────────────────────────
// ✅ SOLUTION 2: @EntityGraph (preferred — declarative)
// ─────────────────────────────────────────────────────────────────
@EntityGraph(attributePaths = {"questions", "questions.options"})
List<Topic> findAll();
// Generates the same JOIN but without writing JPQL

// ─────────────────────────────────────────────────────────────────
// ✅ SOLUTION 3: @BatchSize (Hibernate-specific)
// ─────────────────────────────────────────────────────────────────
@OneToMany
@BatchSize(size = 20)   // Loads 20 collections in one IN query
private List<Question> questions;
// Still 2 queries but uses IN clause: SELECT * FROM questions WHERE topic_id IN (1,2,...20)
```

---

# Phase 6: Spring Security

## 🔒 How Spring Security Works — The Filter Chain

```
HTTP Request
    ↓
Filter 1: SecurityContextPersistenceFilter (loads security context)
    ↓
Filter 2: UsernamePasswordAuthenticationFilter (form login)
    ↓
Filter 3: JwtAuthenticationFilter (YOUR CUSTOM filter — checks JWT)
    ↓
Filter 4: ExceptionTranslationFilter (handles auth exceptions)
    ↓
Filter 5: FilterSecurityInterceptor (final authorization check)
    ↓
Your Controller Method
```

---

## 6.1 Complete Security Configuration

```java
@Configuration
@EnableWebSecurity          // Disables Spring Boot default security, enables custom
@EnableMethodSecurity       // Enables @PreAuthorize, @PostAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // ── CSRF: Disable for REST APIs ──────────────────────────────
            // CSRF protects against browser-based attacks using cookies
            // REST APIs use JWT tokens in Authorization header — CSRF not needed
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS: Allow frontend origins ─────────────────────────────
            .cors(Customizer.withDefaults()) // Uses CorsConfigurationSource bean

            // ── URL Authorization Rules ───────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Permit public endpoints (no JWT needed)
                .requestMatchers(HttpMethod.GET, "/api/jobs/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Preflight

                // Actuator needs authentication
                .requestMatchers("/actuator/**").authenticated()

                // Everything else needs JWT
                .anyRequest().authenticated()
            )

            // ── Session: Stateless (no server-side session) ───────────────
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Add JWT filter before Spring's default auth filter ─────────
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // 12 = strength/cost factor
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "https://yourdomain.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

---

## 6.2 JWT Filter — How It Works

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
// OncePerRequestFilter: Guaranteed to run exactly ONCE per request
// (Prevents double-invocation in forward/include scenarios)

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: Extract token from Authorization header
        String token = extractToken(request);

        // Step 2: Validate token
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // Step 3: Extract user details from token
            String username = jwtTokenProvider.getUsername(token);
            List<String> roles = jwtTokenProvider.getRoles(token);

            // Step 4: Build authorities list from roles
            List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(
                    role.startsWith("ROLE_") ? role : "ROLE_" + role))
                .toList();

            // Step 5: Create Authentication object
            UserDetails userDetails = User.withUsername(username)
                .password("")
                .authorities(authorities)
                .build();

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Step 6: Set in SecurityContext — Spring now knows user is authenticated
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Step 7: Always continue filter chain
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }
}
```

---

## 6.3 Method-Level Security

```java
// @PreAuthorize: Checked BEFORE method executes
@PreAuthorize("hasRole('ADMIN')")
public void deleteJob(Long id) { ... }

@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
public void createJob(JobDTO dto) { ... }

// SpEL expressions in @PreAuthorize:
@PreAuthorize("hasRole('ADMIN') or #userId == principal.id")
// User can only access their own data, unless they're ADMIN

@PreAuthorize("@jobService.isOwner(#jobId, principal.username)")
// Call a custom method for complex authorization logic

// @PostAuthorize: Checked AFTER method executes (can inspect return value)
@PostAuthorize("returnObject.createdBy == principal.username")
public JobDTO getJob(Long id) { ... }
// Method runs, then checks if returned job was created by the requesting user

// @Secured: Simple role check (no SpEL)
@Secured("ROLE_ADMIN")
public void adminAction() { ... }

// Getting current user in service:
public String getCurrentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth.getName(); // username from JWT subject
}

public boolean isAdmin() {
    return SecurityContextHolder.getContext().getAuthentication()
        .getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
}
```

---

# Phase 7: Advanced Spring Concepts

## 7.1 AOP — Aspect Oriented Programming

> **Simple Analogy**: You want to **log every method call** and **measure time** across your app.
> Without AOP: Add logging code to EVERY method (hundreds of places).
> With AOP: Write the logging ONCE, Spring applies it to all methods automatically.

```java
// ── Terminology ───────────────────────────────────────────────────────
// Aspect   : The class containing the cross-cutting concern (e.g., LoggingAspect)
// Advice   : The action to take (@Before, @After, @Around)
// Pointcut : Which methods to intercept (expression)
// JoinPoint: The specific method call being intercepted

@Aspect          // This class is an Aspect
@Component       // Must be a Spring bean
@Slf4j
public class LoggingAspect {

    // ── @Before: Run BEFORE the method ────────────────────────────────
    @Before("execution(* com.job.portal.service.*.*(..))") // Any method in service package
    public void logBefore(JoinPoint joinPoint) {
        log.info("Calling: {}.{}() with args: {}",
            joinPoint.getTarget().getClass().getSimpleName(),
            joinPoint.getSignature().getName(),
            Arrays.toString(joinPoint.getArgs())
        );
    }

    // ── @AfterReturning: Run AFTER successful return ───────────────────
    @AfterReturning(
        pointcut = "execution(* com.job.portal.service.*.*(..))",
        returning = "result"
    )
    public void logAfterReturn(JoinPoint joinPoint, Object result) {
        log.info("Method {} returned: {}", joinPoint.getSignature().getName(), result);
    }

    // ── @AfterThrowing: Run AFTER exception thrown ─────────────────────
    @AfterThrowing(
        pointcut = "execution(* com.job.portal.service.*.*(..))",
        throwing = "ex"
    )
    public void logException(JoinPoint joinPoint, Exception ex) {
        log.error("Method {} threw: {}", joinPoint.getSignature().getName(), ex.getMessage());
    }

    // ── @Around: Wrap the method completely (most powerful) ───────────
    @Around("@annotation(Timed)") // Apply to methods with @Timed annotation
    public Object measureTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed(); // Executes the actual method
            long duration = System.currentTimeMillis() - start;
            log.info("Method {} took {}ms", joinPoint.getSignature().getName(), duration);
            return result;
        } catch (Throwable ex) {
            log.error("Method {} failed after {}ms",
                joinPoint.getSignature().getName(),
                System.currentTimeMillis() - start);
            throw ex;
        }
    }
}

// Custom annotation to mark methods for timing:
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed {}

// Usage:
@Service
public class JobService {
    @Timed  // ← LoggingAspect.measureTime() will wrap this
    public List<JobDTO> getAllJobs() { ... }
}

// Pointcut expressions:
// execution(* com.portal.service.*.*(..))      ← Any method in service package
// execution(public * *(..))                    ← Any public method
// @annotation(Transactional)                   ← Methods with @Transactional
// within(com.portal.service.*)                 ← All methods within service package
// args(Long, ..)                               ← Methods whose first arg is Long
```

---

## 7.2 @Async — Non-Blocking Operations

```java
@Configuration
@EnableAsync   // MUST be present to enable @Async
public class AsyncConfig {

    // Configure thread pool for async operations
    @Bean("emailThreadPool")
    public ThreadPoolTaskExecutor emailThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);       // Always 3 threads ready
        executor.setMaxPoolSize(10);       // Max grow to 10 under load
        executor.setQueueCapacity(200);    // Queue 200 tasks if all threads busy
        executor.setKeepAliveSeconds(60);  // Idle threads die after 60s
        executor.setThreadNamePrefix("Email-"); // Thread name in logs
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // CallerRunsPolicy: If queue full, run in the caller's thread (backpressure)
        executor.initialize();
        return executor;
    }
}

@Service
@Slf4j
public class EmailService {

    // ── @Async: Runs in emailThreadPool thread, not HTTP thread ──────────
    @Async("emailThreadPool")
    public CompletableFuture<Void> sendWelcomeEmail(String to, String name) {
        log.info("Sending email in thread: {}", Thread.currentThread().getName());
        // This runs in Email-1, Email-2... thread
        // HTTP thread returned response ALREADY

        try {
            // Simulate email sending
            brevoClient.send(to, "Welcome " + name);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // ── Return CompletableFuture to chain async operations ────────────
    @Async
    public CompletableFuture<String> generateReport() {
        // Long-running task in background
        return CompletableFuture.completedFuture("Report ready");
    }
}

// Calling async methods:
@Service
public class UserRegistrationService {
    private final EmailService emailService;

    public void registerUser(UserDTO dto) {
        User user = userRepository.save(new User(dto)); // Sync — save to DB
        emailService.sendWelcomeEmail(dto.getEmail(), dto.getName()); // Async — fire and forget
        // Response returned IMMEDIATELY — user doesn't wait for email
    }

    // Waiting for multiple async tasks:
    public void sendBulkNotifications(List<String> emails) {
        List<CompletableFuture<Void>> futures = emails.stream()
            .map(email -> emailService.sendWelcomeEmail(email, "User"))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // Wait for ALL emails sent (or timeout)
    }
}
```

---

## 7.3 Spring Cache — Complete Mastery

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Different TTL for different caches:
        Map<String, CaffeineCache> caches = new HashMap<>();

        // Jobs cache: expires 10 min after write, max 1000 entries
        caches.put("jobs", buildCache(Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000)));

        // Users cache: expires 1 hour, max 500 entries
        caches.put("users", buildCache(Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(500)));

        manager.setCaches(caches.values());
        return manager;
    }

    private CaffeineCache buildCache(Caffeine<Object, Object> caffeine) {
        return new CaffeineCache("cache", caffeine.build(), false);
    }
}

// ── All 4 Spring Cache Annotations ───────────────────────────────────
@Service
public class JobService {

    // @Cacheable: Method result stored in cache
    // If key exists in cache → return it WITHOUT running method
    // If key doesn't exist → run method, store result, return it
    @Cacheable(value = "jobs", key = "#id")
    public JobDTO getJobById(Long id) {
        log.info("Cache miss! Hitting database for id: {}", id); // Only prints on cache miss
        return jobRepository.findById(id).map(j -> modelMapper.map(j, JobDTO.class))
            .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
    }

    // Custom SpEL key expressions:
    @Cacheable(value = "jobs_search", key = "T(java.util.Objects).hash(#keyword, #pageable.pageNumber)")
    public Page<JobDTO> searchJobs(String keyword, Pageable pageable) { ... }

    // Conditional caching:
    @Cacheable(value = "jobs", key = "#id", condition = "#id > 0")
    // Only cache if id is positive

    @Cacheable(value = "jobs", key = "#id", unless = "#result == null")
    // Don't cache null results

    // @CacheEvict: Remove from cache (on mutations)
    @CacheEvict(value = "jobs", key = "#id")
    @Transactional
    public void deleteJob(Long id) {
        jobRepository.deleteById(id);
    }

    @CacheEvict(value = "jobs", allEntries = true)
    // Remove ALL entries from "jobs" cache — use carefully!

    // @CachePut: Always run method AND update cache
    // DIFFERENCE from @Cacheable: @CachePut always runs; @Cacheable skips on hit
    @CachePut(value = "jobs", key = "#result.id")
    @Transactional
    public JobDTO updateJob(Long id, JobDTO dto) {
        Job job = jobRepository.findById(id).orElseThrow();
        // ... update fields ...
        return modelMapper.map(jobRepository.save(job), JobDTO.class);
        // Return value stored in cache with key=result.id
    }

    // @Caching: Multiple cache operations on one method
    @Caching(
        evict = {
            @CacheEvict(value = "jobs", key = "#id"),
            @CacheEvict(value = "jobs_latest", allEntries = true),
            @CacheEvict(value = "jobs_search", allEntries = true)
        },
        put = {
            // Can also add @CachePut here
        }
    )
    @Transactional
    public JobDTO saveJob(JobDTO dto) { ... }
}
```

---

## 7.4 @Scheduled — Scheduled Tasks

```java
@Configuration
@EnableScheduling   // Enable scheduled tasks
public class SchedulingConfig { }

@Component
@Slf4j
public class ScheduledTasks {

    // ── Fixed Rate: Every 5 seconds (regardless of completion time) ───────
    @Scheduled(fixedRate = 5000)
    public void syncData() {
        log.info("Syncing data every 5 seconds...");
    }

    // ── Fixed Delay: Wait 5 seconds AFTER completion ─────────────────────
    @Scheduled(fixedDelay = 5000)
    public void cleanupTemp() {
        log.info("Cleanup runs, then waits 5 seconds before next run");
    }

    // ── Cron expression ───────────────────────────────────────────────────
    @Scheduled(cron = "0 0 2 * * ?")  // Every day at 2:00 AM
    public void dailyReport() {
        log.info("Generating daily report...");
    }

    @Scheduled(cron = "0 */30 9-17 * * MON-FRI") // Every 30 min, 9am-5pm, weekdays
    public void businessHourTask() { ... }

    // Cron format: second minute hour day-of-month month day-of-week
    // 0 0 12 * * ?         → Every day at noon
    // 0 0 * * * ?          → Every hour
    // 0 */5 * * * ?        → Every 5 minutes
    // 0 0 8-17 ? * MON-FRI → Every hour 8am-5pm weekdays

    // ── Dynamic scheduling with property ─────────────────────────────────
    @Scheduled(cron = "${app.jobs.sync.cron:0 0 3 * * ?}")
    public void syncJobsFromExternalApi() {
        log.info("Syncing jobs from external API...");
    }
}
```

---

## 7.5 Spring Events — Decoupled Communication

```java
// ── Custom Event ──────────────────────────────────────────────────────
public class JobCreatedEvent {
    private final JobDTO job;
    private final String createdBy;

    public JobCreatedEvent(JobDTO job, String createdBy) {
        this.job = job;
        this.createdBy = createdBy;
    }
    // Getters...
}

// ── Publisher: Publishes event when job is created ─────────────────────
@Service
@RequiredArgsConstructor
public class JobService {

    private final ApplicationEventPublisher eventPublisher; // Spring provides this

    @Transactional
    public JobDTO createJob(JobDTO dto, String createdBy) {
        Job job = jobRepository.save(new Job(dto));
        JobDTO saved = modelMapper.map(job, JobDTO.class);

        // Publish event — DECOUPLED from email/notification logic
        eventPublisher.publishEvent(new JobCreatedEvent(saved, createdBy));

        return saved;
    }
}

// ── Listener: Reacts to event ──────────────────────────────────────────
@Component
@Slf4j
public class JobEventListener {

    @EventListener                          // Listens for JobCreatedEvent
    public void handleJobCreated(JobCreatedEvent event) {
        log.info("New job created: {} by {}", event.getJob().getJobTitle(), event.getCreatedBy());
        // Send notifications, update analytics, etc.
    }

    @EventListener
    @Async                                  // Handle in separate thread
    public void sendJobAlertEmails(JobCreatedEvent event) {
        // Send emails to subscribed users
        // Runs asynchronously — doesn't block the job creation transaction
    }

    // @TransactionEventListener: Only fires AFTER transaction commits!
    @TransactionEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void updateSearchIndex(JobCreatedEvent event) {
        // Safe to update Elasticsearch here — DB save is guaranteed committed
        // If TX rolled back, this listener is NOT called
    }
}

// Benefits of Events:
// 1. Decoupled: JobService doesn't know about EmailService
// 2. Easy to test — just verify event was published
// 3. Multiple listeners can react to one event
// 4. @TransactionEventListener prevents notifying on rollback
```

---

# 🎯 Interview Master Sheet

## Quick-Fire Q&A for 4+ Years Experience Level

### Spring Core

| Question | Answer |
|---------|--------|
| **What is IoC?** | Inversion of Control — the Spring container creates and manages bean lifecycles, not the developer |
| **3 types of DI?** | Constructor (best — immutable), Setter (optional deps), Field (avoid — not testable) |
| **Singleton vs Prototype scope?** | Singleton: 1 instance per context (default). Prototype: New instance each injection |
| **@Primary vs @Qualifier?** | @Primary is default winner. @Qualifier specifies exact bean name to inject |
| **What is BeanFactory vs ApplicationContext?** | BeanFactory: basic IoC container. ApplicationContext: extends with events, AOP, i18n |
| **What does @PostConstruct do?** | Runs after DI completed — use for initialization |
| **Circular dependency?** | A → B → A. Fix: Use @Lazy, or restructure design |

### Spring Boot

| Question | Answer |
|---------|--------|
| **How does auto-configuration work?** | Reads `AutoConfiguration.imports`, evaluates `@Conditional` annotations, creates beans if conditions met |
| **@SpringBootApplication = ?** | `@Configuration + @EnableAutoConfiguration + @ComponentScan` |
| **How to disable specific auto-config?** | `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` |
| **What is spring-dotenv?** | Library that loads `.env` file values as Spring properties |
| **What is Actuator?** | Provides monitoring endpoints: `/actuator/health`, `/actuator/metrics`, `/actuator/env` |

### Spring Web MVC

| Question | Answer |
|---------|--------|
| **DispatcherServlet role?** | Front Controller — routes ALL requests to correct controllers |
| **@Controller vs @RestController?** | @RestController = @Controller + @ResponseBody (auto JSON serialization) |
| **How does @RequestBody work?** | `HttpMessageConverter` (Jackson) deserializes JSON body → Java object |
| **Difference @PathVariable vs @RequestParam?** | PathVariable: `/jobs/{id}` → from URL path. RequestParam: `/jobs?id=5` → query string |
| **How to handle global exceptions?** | `@ControllerAdvice` class with `@ExceptionHandler` methods |

### Spring Data JPA

| Question | Answer |
|---------|--------|
| **@Transactional default propagation?** | REQUIRED — joins existing TX or creates new |
| **What is dirty checking?** | Hibernate tracks loaded entities; auto-generates UPDATE if they change within TX |
| **@Transactional on private method works?** | NO — Spring proxy only intercepts public methods |
| **How to fix N+1?** | JOIN FETCH, @EntityGraph, or @BatchSize |
| **FetchType.LAZY vs EAGER?** | LAZY: load on access (default collections). EAGER: always load with parent |
| **What is @MappedSuperclass?** | Parent class whose fields are mapped to child entity tables — no own table |

### Spring Security

| Question | Answer |
|---------|--------|
| **Why disable CSRF for REST APIs?** | CSRF needs cookies/sessions. Stateless JWT APIs don't use cookies |
| **What is SecurityContextHolder?** | ThreadLocal storage holding current user's Authentication object |
| **@PreAuthorize vs @Secured?** | @PreAuthorize supports SpEL expressions; @Secured only simple role names |
| **Why STATELESS session?** | No HttpSession created — each request is independently authenticated via JWT |
| **BCrypt advantage?** | Automatically adds salt; configurable cost factor; same password → different hashes |

### Advanced

| Question | Answer |
|---------|--------|
| **What is AOP proxy?** | Spring wraps beans in JDK Proxy or CGLIB proxy to intercept method calls |
| **Why @Transactional self-invocation fails?** | Self-calls bypass the proxy — Spring never intercepts internal `this.method()` calls |
| **@Cacheable vs @CachePut?** | @Cacheable: skips method on cache hit. @CachePut: always runs method, updates cache |
| **@EventListener vs @TransactionEventListener?** | TransactionEventListener only fires after successful transaction commit |
| **What is @Async limitation?** | Same self-invocation problem as @Transactional — async on self-call doesn't work |

---

## 📅 30-Day Practice Plan

| Week | Focus | Daily Practice |
|------|-------|----------------|
| **Week 1** | Core + Boot | Build a simple REST API from scratch, configure beans manually |
| **Week 2** | JPA + Queries | Add entities, relationships, write 5+ query methods per day |
| **Week 3** | Security | Add JWT auth, protect endpoints, write security tests |
| **Week 4** | Advanced | Add caching, async email, AOP logging, write integration tests |

---

## ✅ Daily Coding Exercises

### Beginner Exercises
```
Day 1: Create a Spring Boot app with @Service and @Repository (in-memory List)
Day 2: Add @RestController with CRUD endpoints for a simple entity
Day 3: Add Jakarta Validation to DTOs, test error responses
Day 4: Add @Profile for dev/prod configurations
Day 5: Add @ControllerAdvice for global exception handling
```

### Intermediate Exercises
```
Day 6:  Connect to MySQL, create Entity, test with Spring Data JPA
Day 7:  Write 10 different JpaRepository method names, verify generated SQL
Day 8:  Implement search with @Query JPQL + pagination
Day 9:  Implement @Transactional — test rollback behavior
Day 10: Build JPA Specifications for dynamic search
```

### Advanced Exercises
```
Day 11: Add Spring Security with JWT from scratch
Day 12: Add @PreAuthorize to endpoints, test role-based access
Day 13: Add @Cacheable with Caffeine — verify cache hit/miss in logs
Day 14: Add @Async email — verify non-blocking behavior
Day 15: Write AOP @Around aspect to measure all service method times
Day 16: Write unit tests with Mockito for every service
Day 17: Write integration test with Testcontainers
Day 18: Configure multi-profile application with different DBs
Day 19: Build @Scheduled task for data cleanup
Day 20: Implement custom Spring Events for decoupled notifications
```

---

<div align="center">

### 🚀 You are now ready for Senior Java Interviews!

*Remember: The key to mastery is BUILDING, not just reading.*
*Every day, write code. Every week, build a feature. Every month, complete a project.*

</div>
