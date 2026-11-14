package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import com.job.portal.dto.DailyChallengeDtos.Stats;
import com.job.portal.dto.DailyChallengeDtos.Submission;
import com.job.portal.dto.DailyChallengeDtos.SubmissionResult;
import com.job.portal.service.DailyChallengeService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

/**
 * Traceflow Daily community endpoints (public, anonymous).
 *
 *   GET  /api/engagement/daily/{day}/stats     snapshot
 *   POST /api/engagement/daily/{day}/results   submit a finished run
 *   GET  /api/engagement/daily/{day}/live      Server-Sent Events stream of `stats`
 */
@RestController
@RequestMapping("/api/engagement/daily")
public class EngagementController {

    private final DailyChallengeService service;

    public EngagementController(DailyChallengeService service) {
        this.service = service;
    }

    @GetMapping("/{day}/stats")
    public ResponseEntity<ApiResponse<Stats>> stats(@PathVariable int day) {
        requireKnownDay(day);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.SECONDS).cachePublic())
                .body(ApiResponse.success("Daily stats", service.stats(day)));
    }

    @PostMapping("/{day}/results")
    public ResponseEntity<ApiResponse<SubmissionResult>> submit(@PathVariable int day,
                                                                @Valid @RequestBody Submission submission) {
        try {
            SubmissionResult result = service.submit(day, submission);
            HttpStatus status = result.alreadySubmitted() ? HttpStatus.OK : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(ApiResponse.success("Result recorded", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity().body(ApiResponse.error(e.getMessage(), null));
        }
    }

    @GetMapping(value = "/{day}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live(@PathVariable int day,
                           @RequestParam(required = false) String learnerId,
                           HttpServletResponse response) {
        requireKnownDay(day);
        String learner = learnerId != null && learnerId.length() <= 64 ? learnerId : null;
        SseEmitter emitter = service.subscribe(day, learner);
        if (emitter == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Live stream at capacity");
        }
        // Disable proxy buffering (nginx / Render) so frames are delivered immediately.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        return emitter;
    }

    private void requireKnownDay(int day) {
        if (day < 1 || day > service.currentDay() + 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown puzzle #" + day);
        }
    }
}
