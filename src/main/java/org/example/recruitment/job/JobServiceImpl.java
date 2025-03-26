package org.example.recruitment.job;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
// Import generated gRPC classes and messages from protos
import job.JobServiceGrpc;
import job.JobServiceOuterClass.Job;
import job.JobServiceOuterClass.JobList;
import job.JobServiceOuterClass.JobResponse;
import job.JobServiceOuterClass.ApplicationRequest;
import job.JobServiceOuterClass.ApplicationResponse;
import registry.ServiceRegistryGrpc;
import registry.ServiceRegistryOuterClass.ServiceInfo;
import registry.ServiceRegistryOuterClass.ServiceQuery;
import screening.CandidateScreeningServiceGrpc;
import screening.CandidateScreeningServiceGrpc.CandidateScreeningServiceStub;
import screening.CandidateScreeningServiceOuterClass.ResumeRequest;
import screening.CandidateScreeningServiceOuterClass.ScreeningResult;

public class JobServiceImpl extends JobServiceGrpc.JobServiceImplBase {
    private final ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub;
    private final List<Job> jobs = new ArrayList<>();
    private final AtomicInteger jobIdGenerator = new AtomicInteger(1);
    private final List<ApplicationRecord> applications = new ArrayList<>();

    // Constructor accepts a registry stub for service discovery
    public JobServiceImpl(ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub) {
        this.registryStub = registryStub;
    }

    @Override
    public void createJob(Job request, StreamObserver<JobResponse> responseObserver) {
        // Create a new Job with a generated ID
        Job newJob = Job.newBuilder()
                .setId(jobIdGenerator.getAndIncrement())
                .setTitle(request.getTitle())
                .setDescription(request.getDescription())
                .setCompany(request.getCompany())
                .build();
        jobs.add(newJob);
        JobResponse resp = JobResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Job created successfully")
                .setJobId(newJob.getId())
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void listJobs(com.google.protobuf.Empty request, StreamObserver<JobList> responseObserver) {
        JobList list = JobList.newBuilder().addAllJobs(jobs).build();
        responseObserver.onNext(list);
        responseObserver.onCompleted();
    }

    @Override
    public void applyForJob(ApplicationRequest request, StreamObserver<ApplicationResponse> responseObserver) {
        // Find the job the candidate is applying to
        Job jobFound = null;
        for (Job j : jobs) {
            if (j.getId() == request.getJobId()) {
                jobFound = j;
                break;
            }
        }
        if (jobFound == null) {
            // Job ID not found
            ApplicationResponse resp = ApplicationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Job not found")
                    .build();
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
            return;
        }

        // Create an internal record for this application
        ApplicationRecord app = new ApplicationRecord(
                request.getJobId(),
                request.getCandidateName(),
                request.getCandidateEmail(),
                request.getResumeText()
        );

        // Call the Candidate Screening Service to get a screening score for the resume
        try {
            // Discover the Screening Service's address via the registry
            ServiceQuery query = ServiceQuery.newBuilder().setName("CandidateScreeningService").build();
            ServiceInfo info = registryStub.discover(query);
            if (info.getHost().isEmpty()) {
                // Screening service not found or not available
                ApplicationResponse resp = ApplicationResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Screening service unavailable; application stored without screening")
                        .build();
                applications.add(app);
                responseObserver.onNext(resp);
                responseObserver.onCompleted();
            } else {
                // Connect to the Screening Service at the discovered host/port
                ManagedChannel screeningChannel = ManagedChannelBuilder
                        .forAddress(info.getHost(), info.getPort())
                        .usePlaintext()
                        .build();
                CandidateScreeningServiceStub screeningStub = CandidateScreeningServiceGrpc.newStub(screeningChannel);

                // Prepare to receive the screening result asynchronously
                final ScreeningResult[] screeningResultHolder = new ScreeningResult[1];
                final CountDownLatch finishLatch = new CountDownLatch(1);
                StreamObserver<ScreeningResult> screeningResponseObserver = new StreamObserver<ScreeningResult>() {
                    @Override
                    public void onNext(ScreeningResult result) {
                        screeningResultHolder[0] = result;
                    }
                    @Override
                    public void onError(Throwable t) {
                        System.err.println("Screening error: " + t.getMessage());
                        finishLatch.countDown();
                    }
                    @Override
                    public void onCompleted() {
                        finishLatch.countDown();
                    }
                };

                // Stream the resume text to the Screening service (client-streaming RPC)
                StreamObserver<ResumeRequest> requestObserver = screeningStub.submitResume(screeningResponseObserver);
                String resumeText = request.getResumeText();
                // Split resume into lines and send as chunks (simulating streaming of large data)
                for (String line : resumeText.split("\\n")) {
                    ResumeRequest resumeReq = ResumeRequest.newBuilder()
                            .setCandidateEmail(request.getCandidateEmail())
                            .setContentChunk(line)
                            .build();
                    requestObserver.onNext(resumeReq);
                }
                requestObserver.onCompleted();

                // Wait for screening service to respond with a score (up to a timeout)
                finishLatch.await(5, TimeUnit.SECONDS);
                ScreeningResult screeningRes = screeningResultHolder[0];
                screeningChannel.shutdown();

                if (screeningRes != null) {
                    // Store the screening score and feedback with the application record
                    app.screeningScore = screeningRes.getScore();
                    app.screeningFeedback = screeningRes.getFeedback();
                    System.out.println("Screening completed for " + app.candidateEmail +
                            ": score=" + app.screeningScore);
                } else {
                    System.err.println("Screening result not available for " + app.candidateEmail);
                }
                applications.add(app);

                // Respond to the applicant (candidate) that application was submitted successfully
                ApplicationResponse resp = ApplicationResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Application submitted successfully")
                        .build();
                responseObserver.onNext(resp);
                responseObserver.onCompleted();
            }
        } catch (Exception e) {
            e.printStackTrace();
            ApplicationResponse resp = ApplicationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error during application: " + e.getMessage())
                    .build();
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        }
    }

    // Internal class to store application details and screening outcome
    private static class ApplicationRecord {
        int jobId;
        String candidateName;
        String candidateEmail;
        String resumeText;
        int screeningScore = 0;
        String screeningFeedback = "";
        ApplicationRecord(int jobId, String name, String email, String resumeText) {
            this.jobId = jobId;
            this.candidateName = name;
            this.candidateEmail = email;
            this.resumeText = resumeText;
        }
    }

    public static void main(String[] args) throws Exception {
        // Connect to the Service Registry
        ManagedChannel regChannel = ManagedChannelBuilder.forAddress("localhost", 9000)
                .usePlaintext().build();
        ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub = ServiceRegistryGrpc.newBlockingStub(regChannel);

        // Start gRPC server for JobService
        int port = 9001;
        Server server = ServerBuilder.forPort(port)
                .addService(new JobServiceImpl(registryStub))
                .build()
                .start();
        System.out.println("Job Management Service started on port " + port);

        // Register this service with the registry
        ServiceInfo serviceInfo = ServiceInfo.newBuilder()
                .setName("JobService")
                .setHost("localhost")
                .setPort(port)
                .build();
        registryStub.register(serviceInfo);

        server.awaitTermination();
    }
}
