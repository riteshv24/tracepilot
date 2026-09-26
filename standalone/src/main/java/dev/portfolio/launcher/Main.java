package dev.portfolio.launcher;

import dev.portfolio.foundation.*;
import dev.portfolio.tracepilot.Product;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private Main() {
    }
    public static void main(String[] args) throws Exception {
        Config config=Config.fromEnvironment(8311);
        Application app=new Product(config.createStore(), config.createModel());
        app.start();
        HttpRuntime server=new HttpRuntime(app, config);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(()-> {
            server.close();
            app.close();
        }, "graceful-shutdown"));
        System.out.println("TracePilot is running at http://localhost:"+config.port());
        System.out.println("Storage: "+app.store().mode()+" | Auth: "+(config.token().isBlank()?"loopback development":"workspace key"));
        new CountDownLatch(1).await();
    }
}
