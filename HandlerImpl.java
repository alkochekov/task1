import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HandlerImpl implements Handler {

    private final Client client;

    public HandlerImpl(Client client) {
        this.client = client;
    }

    @Override
    public ApplicationStatusResponse performOperation(String id) {
        final long startTime = System.currentTimeMillis();
        int retriesCount = 0;

        while (true) {
            CompletableFuture<Response> service1 = CompletableFuture.supplyAsync(() -> client.getApplicationStatus1(id));
            CompletableFuture<Response> service2 = CompletableFuture.supplyAsync(() -> client.getApplicationStatus2(id));

            try {
                Response response = CompletableFuture.anyOf(service1, service2)
                        .completeOnTimeout(null, 15, TimeUnit.SECONDS)
                        .get();

                if (response == null) {
                    return new ApplicationStatusResponse.Failure(null, retriesCount);
                } else if (response instanceof Response.Success) {
                    Response.Success success = (Response.Success) response;
                    return new ApplicationStatusResponse.Success(success.applicationId(), success.applicationStatus());
                } else if (response instanceof Response.RetryAfter) {
                    Response.RetryAfter retryAfter = (Response.RetryAfter) response;
                    long waitTime = Math.min(retryAfter.delay().toMillis(), TimeUnit.SECONDS.toMillis(15) - (System.currentTimeMillis() - startTime));
                    if (waitTime > 0) {
                        retriesCount++;
                        Thread.sleep(waitTime);
                        continue;
                    } else {
                        return new ApplicationStatusResponse.Failure(null, retriesCount);
                    }
                } else if (response instanceof Response.Failure) {
                    return new ApplicationStatusResponse.Failure(Duration.ofMillis(System.currentTimeMillis() - startTime), retriesCount);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                return new ApplicationStatusResponse.Failure(Duration.ofMillis(System.currentTimeMillis() - startTime), retriesCount);
            }
        }
    }
}

