package org.example.recruitment.job;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import job.JobServiceGrpc;
import job.JobServiceOuterClass.*;
import registry.ServiceRegistryGrpc;
import registry.ServiceRegistryOuterClass.*;
import screening.CandidateScreeningServiceGrpc;
import screening.CandidateScreeningServiceOuterClass.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JobServiceImpl extends JobServiceGrpc.JobServiceImplBase {

    private final List<Job> jobs = new ArrayList<>();
    private final List<ApplicationRecord> applications = new ArrayList<>();
    private final AtomicInteger jobIdGenerator = new AtomicInteger(1);

    private final ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub;

    public JobServiceImpl(ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub) {
        this.registryStub = registryStub;
    }

    @Override
    public void createJob(Job request, StreamObserver<JobResponse> responseObserver) {
        Job job = Job.newBuilder()
                .setId(jobIdGenerator.getAndIncrement())
                .setTitle(request.getTitle())
                .setDescription(request.getDescription())
                .setCompany(request.getCompany())
                .build();
        jobs.add(job);

        JobResponse response = JobResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Job created")
                .setJobId(job.getId())
                .build();

        responseObserver.onNext(response);
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
        Optional<Job> job = jobs.stream().filter(j -> j.getId() == request.getJobId()).findFirst();

        if (job.isEmpty()) {
            responseObserver.onNext(ApplicationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Job not found").build());
            responseObserver.onCompleted();
            return;
        }

        ApplicationRecord record = new ApplicationRecord(
                request.getJobId(),
                request.getCandidateName(),
                request.getCandidateEmail(),
                request.getResumeText()
        );

        try {
            ServiceInfo info = registryStub.discover(ServiceQuery.newBuilder().setName("CandidateScreeningService").build());

            if (info.getHost().isEmpty()) {
                responseObserver.onNext(ApplicationResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Screening service not found").build());
                responseObserver.onCompleted();
                return;
            }

            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(info.getHost(), info.getPort())
                    .usePlaintext()
                    .build();

            CandidateScreeningServiceGrpc.CandidateScreeningServiceStub screeningStub =
                    CandidateScreeningServiceGrpc.newStub(channel);

            CountDownLatch latch = new CountDownLatch(1);
            final ScreeningResult[] resultHolder = new ScreeningResult[1];

            StreamObserver<ScreeningResult> responseObs = new StreamObserver<>() {
                @Override public void onNext(ScreeningResult value) { resultHolder[0] = value; }
                @Override public void onError(Throwable t) { latch.countDown(); }
                @Override public void onCompleted() { latch.countDown(); }
            };

            StreamObserver<ResumeRequest> requestObs = screeningStub.submitResume(responseObs);
            for (String line : request.getResumeText().split("\n")) {
                requestObs.onNext(ResumeRequest.newBuilder()
                        .setCandidateEmail(request.getCandidateEmail())
                        .setContentChunk(line)
                        .build());
            }
            requestObs.onCompleted();

            latch.await(3, TimeUnit.SECONDS);
            channel.shutdown();

            if (resultHolder[0] != null) {
                record.screeningScore = resultHolder[0].getScore();
                record.screeningFeedback = resultHolder[0].getFeedback();
            }

            applications.add(record);

            responseObserver.onNext(ApplicationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Application submitted").build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onNext(ApplicationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error during application").build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listApplications(com.google.protobuf.Empty request, StreamObserver<ApplicationList> responseObserver) {
        List<Application> appList = new ArrayList<>();
        for (ApplicationRecord r : applications) {
            appList.add(Application.newBuilder()
                    .setCandidateName(r.candidateName)
                    .setCandidateEmail(r.candidateEmail)
                    .setJobId(r.jobId)
                    .setScreeningScore(r.screeningScore)
                    .setScreeningFeedback(r.screeningFeedback)
                    .build());
        }
        responseObserver.onNext(ApplicationList.newBuilder().addAllApplications(appList).build());
        responseObserver.onCompleted();
    }

    private static class ApplicationRecord {
        int jobId;
        String candidateName;
        String candidateEmail;
        String resumeText;
        int screeningScore;
        String screeningFeedback;

        public ApplicationRecord(int jobId, String name, String email, String resumeText) {
            this.jobId = jobId;
            this.candidateName = name;
            this.candidateEmail = email;
            this.resumeText = resumeText;
        }
    }

    public static void main(String[] args) throws Exception {
        ManagedChannel regChannel = ManagedChannelBuilder
                .forAddress("localhost", 9000)
                .usePlaintext()
                .build();

        ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub =
                ServiceRegistryGrpc.newBlockingStub(regChannel);

        Server server = ServerBuilder.forPort(9001)
                .addService(new JobServiceImpl(registryStub))
                .build()
                .start();

        System.out.println("Job Management Service running on port 9001");

        ServiceInfo info = ServiceInfo.newBuilder()
                .setName("JobService")
                .setHost("localhost")
                .setPort(9001)
                .build();

        registryStub.register(info);
        server.awaitTermination();
    }
}
