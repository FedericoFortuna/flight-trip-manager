package com.flighttripmanager.flightsearch.infrastructure.providers;
import java.time.*;
import com.flighttripmanager.flightsearch.infrastructure.configuration.SearchProperties;
import com.flighttripmanager.flightsearch.application.port.out.SearchProviderFailure;
import static com.flighttripmanager.flightsearch.application.port.out.SearchProviderFailure.Reason.*;
public final class ProviderCallGuard {
    private final SearchProperties p;private final Clock clock;
    private Instant window=Instant.MIN,openUntil=Instant.MIN,limitedUntil=Instant.MIN,authBlockedUntil=Instant.MIN;
    private int requests,failures;
    public ProviderCallGuard(SearchProperties p,Clock clock){this.p=p;this.clock=clock;}
    public synchronized void acquire(){
        Instant now=clock.instant();
        if(now.isBefore(authBlockedUntil))throw new SearchProviderFailure(AUTHENTICATION);
        if(now.isBefore(limitedUntil))throw new SearchProviderFailure(RATE_LIMITED);
        if(now.isBefore(openUntil))throw new SearchProviderFailure(CIRCUIT_OPEN);
        if(window.equals(Instant.MIN)||!now.isBefore(window.plusSeconds(p.getWindowSeconds()))){window=now;requests=0;}
        if(requests>=p.getRequestsPerWindow())throw new SearchProviderFailure(RATE_LIMITED);
        requests++;
    }
    public synchronized void success(){failures=0;}
    public synchronized void authenticationFailed(){authBlockedUntil=clock.instant().plusSeconds(p.getCircuitSeconds());}
    public synchronized void failure(){
        if(++failures>=p.getCircuitThreshold()){openUntil=clock.instant().plusSeconds(p.getCircuitSeconds());failures=0;}
    }
    public synchronized void limitedUntil(Instant value){if(value.isAfter(limitedUntil))limitedUntil=value;}
}
