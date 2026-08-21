package haven.fishing;

import haven.ClientData;
import haven.MCache;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Serializes local fishing database work away from the client UI and bot loop. */
public final class FishingJournalService implements AutoCloseable {
    private final String worldId;
    private final FishingRepository repository;
    private final ExecutorService databaseExecutor;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean closed;
    private volatile String lastError;

    public FishingJournalService(String worldId) {
        this.worldId = worldId == null ? "" : worldId;
        FishingRepository repository = null;
        try {
            repository = new FishingRepository(ClientData.sqlite("fishing.db"));
        } catch(RuntimeException e) {
            recordFailure("Could not resolve fishing data directory", e);
        }
        this.repository = repository;
        databaseExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Fishing journal database");
            thread.setDaemon(true);
            return(thread);
        });
        if(repository != null) {
            FishingRepository active = repository;
            databaseExecutor.execute(() -> {
                try {
                    active.initialize();
                } catch(SQLException e) {
                    recordFailure("Could not initialize fishing journal", e);
                }
            });
        }
    }

    public boolean available() {
        return(repository != null && lastError == null && !closed);
    }

    public String lastError() {
        return(lastError);
    }

    public long generation() {
        return(generation.get());
    }

    public void record(FishingObservation observation) {
        if(closed || repository == null || observation == null)
            return;
        try {
            databaseExecutor.execute(() -> {
                try {
                    repository.save(observation);
                    generation.incrementAndGet();
                } catch(SQLException e) {
                    recordFailure("Could not save fishing observation", e);
                }
            });
        } catch(RejectedExecutionException e) {
            if(!closed)
                recordFailure("Could not queue fishing observation", e);
        }
    }

    public Future<List<FishingObservation>> recent(int limit) {
        if(closed || repository == null)
            return(CompletableFuture.completedFuture(Collections.emptyList()));
        try {
            return(databaseExecutor.submit(() -> {
                try {
                    return(repository.recent(worldId, limit));
                } catch(SQLException e) {
                    recordFailure("Could not read fishing journal", e);
                    return(Collections.emptyList());
                }
            }));
        } catch(RejectedExecutionException e) {
            if(!closed)
                recordFailure("Could not queue fishing journal read", e);
            return(CompletableFuture.completedFuture(Collections.emptyList()));
        }
    }

    public Future<List<FishingObservation>> spot(long gridId, int gridTileX, int gridTileY) {
        if(closed || repository == null)
            return(CompletableFuture.completedFuture(Collections.emptyList()));
        double minX = gridTileX * MCache.tilesz.x;
        double maxX = minX + MCache.tilesz.x;
        double minY = gridTileY * MCache.tilesz.y;
        double maxY = minY + MCache.tilesz.y;
        try {
            return(databaseExecutor.submit(() -> {
                try {
                    return(repository.spot(worldId, gridId, minX, maxX, minY, maxY));
                } catch(SQLException e) {
                    recordFailure("Could not read fishing spot", e);
                    return(Collections.emptyList());
                }
            }));
        } catch(RejectedExecutionException e) {
            if(!closed)
                recordFailure("Could not queue fishing spot read", e);
            return(CompletableFuture.completedFuture(Collections.emptyList()));
        }
    }

    private void recordFailure(String message, Exception exception) {
        String detail = exception.getMessage();
        lastError = (detail == null || detail.isBlank()) ? message : message + ": " + detail;
        new haven.Warning(exception, lastError).level(haven.Warning.ERROR).issue();
    }

    @Override
    public void close() {
        closed = true;
        databaseExecutor.shutdown();
        try {
            if(!databaseExecutor.awaitTermination(2, TimeUnit.SECONDS))
                databaseExecutor.shutdownNow();
        } catch(InterruptedException e) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
