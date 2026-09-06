package io.vykronis.incident;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically closes the verification window for incidents that have been
 * VERIFYING past their deadline (see {@link VerificationService#sweepExpired}).
 * The sweep interval is independent of the window length so the demo can keep a
 * short window and still resolve promptly.
 */
@Component
public class VerificationSweeper {

    private final VerificationService verificationService;

    public VerificationSweeper(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Scheduled(fixedDelayString = "${vykronis.verify-sweep-ms:10000}")
    public void sweep() {
        verificationService.sweepExpired();
    }
}