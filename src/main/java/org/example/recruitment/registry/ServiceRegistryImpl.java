package org.example.recruitment.registry;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import registry.ServiceRegistryGrpc;
import registry.ServiceRegistryOuterClass.ServiceInfo;
import registry.ServiceRegistryOuterClass.ServiceQuery;
import registry.ServiceRegistryOuterClass.RegisterResponse;

public class ServiceRegistryImpl extends ServiceRegistryGrpc.ServiceRegistryImplBase {
    private final ConcurrentMap<String, ServiceInfo> services = new ConcurrentHashMap<>();

    @Override
    public void register(ServiceInfo request, StreamObserver<RegisterResponse> responseObserver) {
        services.put(request.getName(), request);
        RegisterResponse resp = RegisterResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Registered " + request.getName())
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
        System.out.println("Service registered: " + request.getName() +
                " -> " + request.getHost() + ":" + request.getPort());
    }

    @Override
    public void discover(ServiceQuery request, StreamObserver<ServiceInfo> responseObserver) {
        ServiceInfo info = services.get(request.getName());
        if (info != null) {
            responseObserver.onNext(info);
        } else {
            // Return an empty ServiceInfo if not found
            responseObserver.onNext(ServiceInfo.newBuilder().build());
        }
        responseObserver.onCompleted();
    }

    public static void main(String[] args) throws Exception {
        // Start the Service Registry server
        int port = 9000;
        Server server = ServerBuilder.forPort(port)
                .addService(new ServiceRegistryImpl())
                .build()
                .start();
        System.out.println("Service Registry started on port " + port);
        server.awaitTermination();
    }
}
