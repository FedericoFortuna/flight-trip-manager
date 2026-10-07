package com.flighttripmanager.flightsearch.infrastructure.providers;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import com.flighttripmanager.flightsearch.infrastructure.configuration.SearchProperties;
import com.flighttripmanager.flightsearch.application.port.out.SearchProviderFailure;
import static com.flighttripmanager.flightsearch.application.port.out.SearchProviderFailure.Reason.*;
public final class DuffelHttp {
    private final HttpClient client;private final URI endpoint;private final SearchProperties p;private final Clock clock;private final ProviderCallGuard guard;
    public DuffelHttp(URI endpoint,SearchProperties p,Clock clock){
        this.endpoint=endpoint;this.p=p;this.clock=clock;this.guard=new ProviderCallGuard(p,clock);
        client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(p.getConnectTimeoutMs())).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public String post(String body,Instant deadline){
        for(int attempt=1;attempt<=p.getMaxAttempts();attempt++){
            long remaining=Math.min(p.getRequestTimeoutMs(),Duration.between(clock.instant(),deadline).toMillis());
            if(remaining<=0)throw new SearchProviderFailure(TIMEOUT);
            guard.acquire();
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofMillis(remaining))
                .header("Authorization","Bearer "+p.getDuffelToken()).header("Duffel-Version","v2")
                .header("Accept","application/json").header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
            CompletableFuture<HttpResponse<byte[]>> future=client.sendAsync(request,info->new BoundedBodySubscriber(p.getMaxResponseBytes()));
            try{
                var response=future.get(remaining,TimeUnit.MILLISECONDS);int status=response.statusCode();
                LoggerFactory.getLogger(DuffelHttp.class).atInfo().addKeyValue("module","flightsearch")
                    .addKeyValue("provider","DUFFEL").addKeyValue("operation","offer_request").addKeyValue("status",status).log("Provider request completed");
                if(status>=200&&status<300){guard.success();return new String(response.body(),StandardCharsets.UTF_8);}
                if(status==401||status==403){guard.authenticationFailed();throw new SearchProviderFailure(AUTHENTICATION);}
                if(status==429){
                    Instant reset=reset(response);
                    guard.limitedUntil(reset);
                    long delay=Math.max(1,Duration.between(clock.instant(),reset).toMillis());
                    if(attempt<p.getMaxAttempts()&&delay<=p.getMaxRetryDelayMs()&&clock.instant().plusMillis(delay).isBefore(deadline)){sleep(delay);continue;}
                    throw new SearchProviderFailure(RATE_LIMITED);
                }
                if(status>=500||status==408)guard.failure();
                if((status==503||status==408)&&attempt<p.getMaxAttempts()){backoff(attempt,deadline);continue;}
                throw new SearchProviderFailure(status>=500||status==408?UNAVAILABLE:REJECTED);
            }catch(TimeoutException e){
                future.cancel(true);guard.failure();
                if(attempt==p.getMaxAttempts())throw new SearchProviderFailure(TIMEOUT);
                backoff(attempt,deadline);
            }catch(ExecutionException e){
                guard.failure();
                if(root(e) instanceof IllegalArgumentException)throw new SearchProviderFailure(INVALID_RESPONSE);
                if(attempt==p.getMaxAttempts())throw new SearchProviderFailure(root(e) instanceof HttpTimeoutException?TIMEOUT:UNAVAILABLE);
                backoff(attempt,deadline);
            }catch(InterruptedException e){
                future.cancel(true);Thread.currentThread().interrupt();throw new SearchProviderFailure(TIMEOUT);
            }
        }
        throw new SearchProviderFailure(UNAVAILABLE);
    }
    private void backoff(int attempt,Instant deadline){
        long delay=Math.min(p.getMaxRetryDelayMs(),(long)p.getRetryDelayMs()*(1L<<(attempt-1)));
        if(!clock.instant().plusMillis(delay).isBefore(deadline))throw new SearchProviderFailure(TIMEOUT);
        sleep(delay);
    }
    private void sleep(long millis){
        try{Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new SearchProviderFailure(TIMEOUT);}
    }
    private Instant reset(HttpResponse<?> response){
        String retry=response.headers().firstValue("Retry-After").orElse(null);
        if(retry!=null){
            try{return clock.instant().plusSeconds(Math.min(86400,Math.max(0,Long.parseLong(retry))));}
            catch(NumberFormatException e){try{return ZonedDateTime.parse(retry,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();}catch(java.time.format.DateTimeParseException ignored){}}
        }
        String reset=response.headers().firstValue("ratelimit-reset").orElse(null);
        if(reset!=null){
            try{return Instant.parse(reset);}catch(java.time.format.DateTimeParseException e){
                try{return Instant.ofEpochSecond(Long.parseLong(reset));}catch(NumberFormatException|DateTimeException ignored){}
            }
        }
        return clock.instant().plusSeconds(p.getWindowSeconds());
    }
    private Throwable root(Throwable error){while(error.getCause()!=null)error=error.getCause();return error;}
}
