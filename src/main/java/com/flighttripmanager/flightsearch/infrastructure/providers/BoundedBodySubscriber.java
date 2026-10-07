package com.flighttripmanager.flightsearch.infrastructure.providers;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result=new CompletableFuture<>();
    private Flow.Subscription subscription;
    BoundedBodySubscriber(int limit){this.limit=limit;}
    @Override public CompletionStage<byte[]> getBody(){return result;}
    @Override public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
    @Override public void onNext(List<ByteBuffer> items){
        for(var buffer:items){
            if(buffer.remaining()>limit-bytes.size()){
                subscription.cancel();result.completeExceptionally(new IllegalArgumentException("Response limit"));return;
            }
            byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }
    @Override public void onError(Throwable error){result.completeExceptionally(error);}
    @Override public void onComplete(){result.complete(bytes.toByteArray());}
}
