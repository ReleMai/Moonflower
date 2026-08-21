package haven.cookbook;

import haven.ClientData;
import haven.Defer;
import haven.GItem;
import haven.ItemInfo;
import haven.Loading;
import haven.Resource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/** Coordinates deferred tooltip parsing and serialized local database access. */
public final class CookbookService implements AutoCloseable {
    private final String worldId;
    private final String characterId;
    private final CookbookRepository repository;
    private final ExecutorService databaseExecutor;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean closed;
    private volatile String lastError;

    public CookbookService(String worldId, String characterId) {
        this.worldId = (worldId == null) ? "" : worldId;
        this.characterId = (characterId == null) ? "" : characterId;
        CookbookRepository repository = null;
        try {
            repository = new CookbookRepository(ClientData.sqlite("cookbook.db"));
        } catch(RuntimeException e) {
            recordFailure("Could not resolve cookbook data directory", e);
        }
        this.repository = repository;
        this.databaseExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Cookbook database");
            thread.setDaemon(true);
            return(thread);
        });
        if(repository != null) {
            CookbookRepository activeRepository = repository;
            databaseExecutor.execute(() -> {
                try {
                    activeRepository.initialize();
                } catch(SQLException e) {
                    recordFailure("Could not initialize cookbook database", e);
                }
            });
        }
    }

    public void observe(GItem item) {
        if(closed || repository == null)
            return;
        Defer.later(() -> {
            try {
                if(closed || item.checkForHempBuff())
                    return(null);
                List<ItemInfo> info = new ArrayList<>(item.cookbookInfo());
                Resource resource = item.getres();
                long observedAt = System.currentTimeMillis();
                CookbookItem observedItem = CookbookFoodParser.parseItem(info, resource, worldId,
                        observedAt);
                CookbookFood food = CookbookFoodParser.parse(info, resource, worldId, characterId,
                        observedAt);
                if(!closed && (observedItem != null || food != null))
                    databaseExecutor.execute(() -> save(observedItem, food));
            } catch(Loading loading) {
                throw(loading);
            } catch(RuntimeException e) {
                recordFailure("Could not read food tooltip", e);
            }
            return(null);
        });
    }

    public Future<List<CookbookEntry>> list(String attribute, String search) {
        if(closed || repository == null)
            return(CompletableFuture.completedFuture(Collections.emptyList()));
        return(databaseExecutor.submit(() -> {
            try {
                return(repository.list(worldId, attribute, search));
            } catch(SQLException e) {
                recordFailure("Could not load cookbook entries", e);
                throw(e);
            }
        }));
    }

    public Future<List<CookbookIngredientEntry>> listIngredients(
            CookbookIngredientCategory category, String search) {
        if(closed || repository == null)
            return(CompletableFuture.completedFuture(Collections.emptyList()));
        return(databaseExecutor.submit(() -> {
            try {
                return(repository.listIngredients(worldId, category, search));
            } catch(SQLException e) {
                recordFailure("Could not load cookbook ingredients", e);
                throw(e);
            }
        }));
    }

    public long generation() {
        return(generation.get());
    }

    public boolean available() {
        return(repository != null);
    }

    public String lastError() {
        return(lastError);
    }

    private void save(CookbookItem item, CookbookFood food) {
        try {
            boolean changed = false;
            if(item != null)
                changed = repository.saveItem(item);
            if(food != null)
                changed = repository.save(food) || changed;
            if(changed)
                generation.incrementAndGet();
        } catch(SQLException e) {
            recordFailure("Could not save food to cookbook", e);
        }
    }

    private void recordFailure(String message, Exception exception) {
        String detail = exception.getMessage();
        lastError = (detail == null || detail.isBlank()) ? message : message + ": " + detail;
    }

    @Override
    public void close() {
        closed = true;
        databaseExecutor.shutdown();
    }
}
