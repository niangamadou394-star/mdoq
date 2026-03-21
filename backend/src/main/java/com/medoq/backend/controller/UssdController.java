package com.medoq.backend.controller;

import com.medoq.backend.dto.ussd.UssdRequest;
import com.medoq.backend.service.ussd.UssdFlowService;
import com.medoq.backend.service.ussd.UssdSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Africa's Talking USSD gateway endpoint.
 *
 * AT sends a POST with application/x-www-form-urlencoded form fields on every
 * user interaction. The response must be plain text starting with:
 *   "CON " — session continues (user will be prompted again)
 *   "END " — session terminates
 *
 * This endpoint is PUBLIC — security is provided by AT's callback IP allowlist
 * configured at the network level (no JWT required).
 */
@RestController
@RequestMapping("/ussd")
@RequiredArgsConstructor
@Slf4j
public class UssdController {

    private final UssdFlowService    flowService;
    private final UssdSessionService sessionService;

    @PostMapping(
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String handle(UssdRequest req) {
        log.info("USSD [{}] phone={} text='{}'",
            req.getSessionId(), req.getPhoneNumber(), req.getText());

        try {
            String response = flowService.handle(
                req.getSessionId(),
                req.getPhoneNumber(),
                req.getText()
            );

            // If session ended, clean up Redis keys eagerly
            if (response.startsWith("END")) {
                sessionService.clear(req.getSessionId());
            }

            return response;

        } catch (Exception e) {
            log.error("USSD error for session {}: {}", req.getSessionId(), e.getMessage(), e);
            sessionService.clear(req.getSessionId());
            return "END Une erreur est survenue. Veuillez réessayer en composant *338#.";
        }
    }
}
