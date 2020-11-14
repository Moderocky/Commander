package mx.kenzie.commander.future;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public record SafeFuture<V>(Future<V> future, V value) implements Future<V> {
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future().cancel(mayInterruptIfRunning);
    }
    
    @Override
    public boolean isCancelled() {
        return future().isCancelled();
    }
    
    @Override
    public boolean isDone() {
        return future().isDone();
    }
    
    @Override
    public V get() {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            if (value != null)
                return value;
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public V get(long timeout, @NotNull TimeUnit unit) {
        try {
            return future().get(timeout, unit);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (value != null)
                return value;
            throw new RuntimeException(e);
        }
    }
}
