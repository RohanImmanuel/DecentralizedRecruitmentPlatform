package org.example.recruitment.gateway;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class WebGateway {

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
            config.staticFiles.add(staticFileConfig -> {
                staticFileConfig.directory = "/web";              // Folder inside src/main/resources
                staticFileConfig.location = Location.CLASSPATH;   // Serve from classpath
            });
        }).start(8080);

        ServiceBridge.registerRoutes(app);

        System.out.println("✅ WebGateway running at http://localhost:8080");
    }
}
