package org.example.recruitment.gateway;

import com.google.protobuf.Empty;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class WebGateway {

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/web";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.hostedPath = "/";
            });
        }).start(8080);

        System.out.println("Web UI running on http://localhost:8080");

        ServiceBridge.registerRoutes(app);
    }
}
