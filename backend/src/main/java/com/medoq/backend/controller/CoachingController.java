package com.medoq.backend.controller;

import com.medoq.backend.service.CoachingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/coaching")
@RequiredArgsConstructor
public class CoachingController {

    private final CoachingService coachingService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@Valid @RequestBody ChatRequest req) {
        String reply = coachingService.chat(req.systemPrompt(), req.messages(), req.isInit());
        return ResponseEntity.ok(Map.of("message", reply));
    }

    public record ChatRequest(
        @NotNull @Size(max = 4000) String systemPrompt,
        @NotNull @Size(max = 20)   List<Message> messages,
        boolean isInit
    ) {}

    public record Message(
        @NotNull String role,
        @NotNull @Size(max = 2000) String content
    ) {}
}
