package org.example.recruitment.screening;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import java.util.Map;
import screening.CandidateScreeningServiceGrpc;
import screening.CandidateScreeningServiceOuterClass.ResumeRequest;
import screening.CandidateScreeningServiceOuterClass.ScreeningResult;
import screening.CandidateScreeningServiceOuterClass.ScreeningQuery;
import registry.ServiceRegistryGrpc;
import registry.ServiceRegistryOuterClass.ServiceInfo;
import registry.ServiceRegistryOuterClass.RegisterResponse;

public class CandidateScreeningServiceImpl extends CandidateScreeningServiceGrpc.CandidateScreeningServiceImplBase {
    // Map from candidate email to their ScreeningResult
    private final Map<String, ScreeningResult> results = new HashMap<>();

    @Override
    public StreamObserver<ResumeRequest> submitResume(StreamObserver<ScreeningResult> responseObserver) {
        return new StreamObserver<ResumeRequest>() {
            String candidateEmail = "";
            StringBuilder resumeBuilder = new StringBuilder();

            @Override
            public void onNext(ResumeRequest req) {
                // Accumulate resume content
                if (candidateEmail.isEmpty()) {
                    candidateEmail = req.getCandidateEmail();  // capture candidate's email from first message
                }
                resumeBuilder.append(req.getContentChunk());
                resumeBuilder.append("\n");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error receiving resume: " + t.getMessage());
                // We could abort or simply complete
            }

            @Override
            public void onCompleted() {
                // Resume fully received, now process it
                String fullResumeText = resumeBuilder.toString();
                int score = calculateScore(fullResumeText);
                String feedback;
                if (score > 80) {
                    feedback = "Strong candidate";
                } else if (score > 50) {
                    feedback = "Average candidate";
                } else {
                    feedback = "Needs improvement";
                }
                ScreeningResult result = ScreeningResult.newBuilder()
                        .setCandidateEmail(candidateEmail)
                        .setScore(score)
                        .setFeedback(feedback)
                        .build();
                // Store the result for later retrieval
                results.put(candidateEmail, result);
                // Return the result to the caller
                responseObserver.onNext(result);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void getScreeningResult(ScreeningQuery request, StreamObserver<ScreeningResult> responseObserver) {
        String email = request.getCandidateEmail();
        ScreeningResult result = results.get(email);
        if (result == null) {
            // No result found for this email – respond with score 0 and a message
            result = ScreeningResult.newBuilder()
                    .setCandidateEmail(email)
                    .setScore(0)
                    .setFeedback("No screening result available")
                    .build();
        }
        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }

    // Simple resume scoring logic: for example, score = number of words (capped at 100)
    private int calculateScore(String resumeText) {
        if (resumeText == null || resumeText.isEmpty()) return 0;
        String[] words = resumeText.trim().split("\\s+");
        int score = Math.min(100, words.length);
        return score;
    }

    public static void main(String[] args) throws Exception {
        // Connect to Service Registry
        ManagedChannel regChannel = ManagedChannelBuilder.forAddress("localhost", 9000)
                .usePlaintext().build();
        ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub = ServiceRegistryGrpc.newBlockingStub(regChannel);

        // Start gRPC server for Screening Service
        int port = 9002;
        Server server = ServerBuilder.forPort(port)
                .addService(new CandidateScreeningServiceImpl())
                .build()
                .start();
        System.out.println("Candidate Screening Service started on port " + port);

        // Register this service with the registry
        ServiceInfo serviceInfo = ServiceInfo.newBuilder()
                .setName("CandidateScreeningService")
                .setHost("localhost")
                .setPort(port)
                .build();
        RegisterResponse regResp = registryStub.register(serviceInfo);
        System.out.println("Service Registry response: " + regResp.getMessage());

        server.awaitTermination();
    }
}
