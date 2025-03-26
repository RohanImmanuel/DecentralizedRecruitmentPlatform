package org.example.recruitment;

import org.example.recruitment.gateway.WebGateway;
import org.example.recruitment.job.JobServiceImpl;
import org.example.recruitment.registry.ServiceRegistryImpl;
import org.example.recruitment.screening.CandidateScreeningServiceImpl;
import org.example.recruitment.interview.InterviewServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ManagedChannelBuilder;
import registry.ServiceRegistryGrpc;
import registry.ServiceRegistryOuterClass;

import java.util.ArrayList;
import java.util.List;

public class MainLauncher {

    public static void main(String[] args) throws Exception {
        List<Server> servers = new ArrayList<>();

        // 1. Start Registry
        Server registryServer = ServerBuilder.forPort(9000)
                .addService(new ServiceRegistryImpl())
                .build()
                .start();
        System.out.println("✅ Service Registry started on port 9000");
        servers.add(registryServer);

        // Prepare stub to register others
        var registryStub = ServiceRegistryGrpc.newBlockingStub(
                ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build()
        );

        // 2. Start Job Service
        Server jobServer = ServerBuilder.forPort(9001)
                .addService(new JobServiceImpl(registryStub))
                .build()
                .start();
        System.out.println("✅ Job Service started on port 9001");
        registryStub.register(ServiceRegistryOuterClass.ServiceInfo.newBuilder()
                .setName("JobService").setHost("localhost").setPort(9001).build());
        servers.add(jobServer);

        // 3. Start Screening Service
        Server screeningServer = ServerBuilder.forPort(9002)
                .addService(new CandidateScreeningServiceImpl())
                .build()
                .start();
        System.out.println("✅ Screening Service started on port 9002");
        registryStub.register(ServiceRegistryOuterClass.ServiceInfo.newBuilder()
                .setName("CandidateScreeningService").setHost("localhost").setPort(9002).build());
        servers.add(screeningServer);

        // 4. Start Interview Service
        Server interviewServer = ServerBuilder.forPort(9003)
                .addService(new InterviewServiceImpl())
                .build()
                .start();
        System.out.println("✅ Interview Service started on port 9003");
        registryStub.register(ServiceRegistryOuterClass.ServiceInfo.newBuilder()
                .setName("InterviewService").setHost("localhost").setPort(9003).build());
        servers.add(interviewServer);

        // 5. Start Web Gateway
        System.out.println("🌐 Starting Web Gateway on http://localhost:8080");
        WebGateway.main(new String[0]);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down services...");
            servers.forEach(s -> {
                try { s.shutdown(); } catch (Exception e) { e.printStackTrace(); }
            });
        }));

        // Block main thread until all services terminated
        for (Server s : servers) {
            s.awaitTermination();
        }
    }
}
