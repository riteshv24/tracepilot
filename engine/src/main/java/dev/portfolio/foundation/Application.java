package dev.portfolio.foundation;

import java.util.*;

/** Composition root implemented separately by each product. */
public interface Application extends AutoCloseable {
    Router router();
    Store store();
    Jobs jobs();
    String name();
    void seed();
    default void start() {
        seed();
        jobs().start();
    }
    default void close() {
        jobs().close();
        store().close();
    }
    default void commonRoutes(ModelClient model) {
        Router r=router();
        r.on("GET", "/api/meta", req->Router.Response.json(Json.map("name", name(), "storage", store().mode(), "runtime", System.getProperty("java.version"), "generationConfigured", model.available(), "generationModel", model.model(), "embeddingConfigured", model.embeddingsAvailable(), "scope", "single-workspace / single-instance")));
        r.on("GET", "/api/jobs", req->Router.Response.json(jobs().list()));
        r.on("GET", "/api/jobs/{id}", req->Router.Response.json(jobs().get(req.param("id"))));
        r.on("POST", "/api/jobs/{id}/cancel", req->Router.Response.json(jobs().cancel(req.param("id"))));
        r.on("GET", "/api/audit", req->Router.Response.json(Audit.recent(store())));
        r.on("GET", "/api/routes", req->Router.Response.json(r.contract()));
    }
}
