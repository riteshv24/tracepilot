package dev.portfolio.spring;

import dev.portfolio.foundation.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** H2 transaction-contract tests are useful, but are not substitutes for an actual PostgreSQL integration run. */
class JdbcStoreTest {
    private JdbcStore store;
    @BeforeEach void setup() {
        store=new JdbcStore("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
    @AfterEach void close() {
        store.close();
    }
    @Test void insertUpdateReadAndDelete() {
        store.write(tx-> {
            tx.put("items", "first", Json.map("n", 1));
            return null;
        });
        store.write(tx-> {
            tx.put("items", "first", Json.map("n", 2));
            return null;
        });
        assertEquals(2, store.read(tx->Json.integer(tx.require("items", "first"), "n", 0)).longValue());
        store.write(tx-> {
            tx.delete("items", "first");
            return null;
        });
        assertTrue(store.read(tx->tx.get("items", "first")).isEmpty());
    }
    @Test void failedTransactionRollsBack() {
        assertThrows(IllegalStateException.class, ()->store.write(tx-> {
            tx.put("items", "first", Json.map("n", 1));
            throw new IllegalStateException("rollback");
        }));
        assertTrue(store.read(tx->tx.scan("items")).isEmpty());
    }
    @Test void valuesAreBoundThroughPreparedStatements() {
        String text="Robert'); DROP TABLE workspace_records; --";
        store.write(tx-> {
            tx.put("items", "first", Json.map("text", text));
            return null;
        });
        assertEquals(text, store.read(tx->tx.require("items", "first").get("text")));
    }
    @Test void readTransactionsRejectMutation() {
        assertThrows(IllegalStateException.class, ()->store.read(tx-> {
            tx.put("items", "first", Map.of());
            return null;
        }));
    }
    @Test void mutexSerializesConcurrentIncrements() throws Exception {
        store.write(tx-> {
            tx.put("counts", "one", Json.map("n", 0));
            return null;
        });
        try(var pool=Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks=new ArrayList<>();
            for(int i=0; i<4; i++) tasks.add(pool.submit(()-> {
                for(int j=0; j<10; j++) store.write(tx-> {
                    var item=tx.require("counts", "one");
                    item.put("n", Json.integer(item, "n", 0)+1);
                    tx.put("counts", "one", item);
                    return null;
                });
            }));
            for(var task:tasks) task.get();
        }
        assertEquals(40, store.read(tx->Json.integer(tx.require("counts", "one"), "n", 0)).longValue());
    }
}
