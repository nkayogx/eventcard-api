package com.kayogx.eventcard.invitation;

import com.kayogx.eventcard.common.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops people from guessing invitation codes: after too many wrong codes
 * from one internet address within a minute, that address must wait.
 *
 * Kept in memory - simple, and enough for one server.
 */
@Component
public class WrongCodeLimiter {

    static final int MAX_WRONG_CODES_PER_MINUTE = 30;

    /** For each internet address: when its current one-minute window started, and how many wrong codes so far. */
    private record Attempts(Instant windowStart, int wrongCodes) {
    }

    private final Map<String, Attempts> attemptsByAddress = new ConcurrentHashMap<>();

    /** Call before looking up a code. Refuses the request if this address has guessed too much. */
    public void checkNotBlocked(String internetAddress) {
        Attempts attempts = attemptsByAddress.get(internetAddress);
        if (attempts != null && isInCurrentWindow(attempts) && attempts.wrongCodes() >= MAX_WRONG_CODES_PER_MINUTE) {
            throw new TooManyRequestsException("Too many attempts. Please wait a minute and try again.");
        }
    }

    public void recordWrongCode(String internetAddress) {
        attemptsByAddress.compute(internetAddress, (address, attempts) ->
                attempts == null || !isInCurrentWindow(attempts)
                        ? new Attempts(Instant.now(), 1)
                        : new Attempts(attempts.windowStart(), attempts.wrongCodes() + 1));
    }

    private static boolean isInCurrentWindow(Attempts attempts) {
        return attempts.windowStart().isAfter(Instant.now().minusSeconds(60));
    }
}
