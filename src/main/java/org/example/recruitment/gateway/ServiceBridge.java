package org.example.recruitment.gateway;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.javalin.Javalin;

import job.JobServiceGrpc;
import job.JobServiceOuterClass;
import screening.CandidateScreeningServiceGrpc;
import screening.CandidateScreeningServiceOuterClass;
import interview.InterviewServiceGrpc;
import interview.InterviewServiceOuterClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServiceBridge {

    public static class JobCreateRequest {
        public String title;
        public String company;
        public String description;
    }

    public static class ApplicationRequestDTO {
        public int jobId;
        public String candidateName;
        public String candidateEmail;
        public String resumeText;
    }

    public static class ScheduleRequestDTO {
        public String slotId;
        public String candidateName;
        public String candidateEmail;
        public int jobId;
    }

    public static void registerRoutes(Javalin app) {
        app.post("/jobs", ctx -> {
            var req = ctx.bodyAsClass(JobCreateRequest.class);
            var channel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
            var stub = JobServiceGrpc.newBlockingStub(channel);

            var response = stub.createJob(JobServiceOuterClass.Job.newBuilder()
                    .setTitle(req.title)
                    .setCompany(req.company)
                    .setDescription(req.description)
                    .build());

            ctx.json(Map.of("success", response.getSuccess(), "jobId", response.getJobId()));
            channel.shutdown();
        });

        app.get("/jobs", ctx -> {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
            var stub = JobServiceGrpc.newBlockingStub(channel);
            var jobs = stub.listJobs(Empty.getDefaultInstance());
            List<Map<String, Object>> jobList = jobs.getJobsList().stream().map(job -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", job.getId());
                map.put("title", job.getTitle());
                map.put("description", job.getDescription());
                map.put("company", job.getCompany());
                return map;
            }).collect(Collectors.toList());
            ctx.json(jobList);
            channel.shutdown();
        });

        app.post("/apply", ctx -> {
            var req = ctx.bodyAsClass(ApplicationRequestDTO.class);
            var channel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
            var stub = JobServiceGrpc.newBlockingStub(channel);

            var resp = stub.applyForJob(JobServiceOuterClass.ApplicationRequest.newBuilder()
                    .setJobId(req.jobId)
                    .setCandidateName(req.candidateName)
                    .setCandidateEmail(req.candidateEmail)
                    .setResumeText(req.resumeText)
                    .build());

            ctx.json(Map.of("success", resp.getSuccess(), "message", resp.getMessage()));
            channel.shutdown();
        });

        app.get("/screening", ctx -> {
            String email = ctx.queryParam("email");
            var channel = ManagedChannelBuilder.forAddress("localhost", 9002).usePlaintext().build();
            var stub = CandidateScreeningServiceGrpc.newBlockingStub(channel);

            var result = stub.getScreeningResult(CandidateScreeningServiceOuterClass.ScreeningQuery.newBuilder()
                    .setCandidateEmail(email)
                    .build());

            Map<String, Object> map = new HashMap<>();
            map.put("email", result.getCandidateEmail());
            map.put("score", result.getScore());
            map.put("feedback", result.getFeedback());

            ctx.json(map);
            channel.shutdown();
        });

        app.get("/slots", ctx -> {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9003).usePlaintext().build();
            var stub = InterviewServiceGrpc.newBlockingStub(channel);
            var slots = stub.listAvailableSlots(Empty.getDefaultInstance());
            List<Map<String, Object>> slotList = slots.getSlotsList().stream().map(slot -> {
                Map<String, Object> map = new HashMap<>();
                map.put("slotId", slot.getSlotId());
                map.put("time", slot.getTime());
                map.put("booked", slot.getBooked());
                return map;
            }).collect(Collectors.toList());
            ctx.json(slotList);
            channel.shutdown();
        });

        app.post("/schedule", ctx -> {
            var req = ctx.bodyAsClass(ScheduleRequestDTO.class);
            var channel = ManagedChannelBuilder.forAddress("localhost", 9003).usePlaintext().build();
            var stub = InterviewServiceGrpc.newBlockingStub(channel);

            var result = stub.scheduleInterview(InterviewServiceOuterClass.InterviewRequest.newBuilder()
                    .setCandidateName(req.candidateName)
                    .setCandidateEmail(req.candidateEmail)
                    .setJobId(req.jobId)
                    .setSlotId(req.slotId)
                    .build());

            ctx.json(Map.of("success", result.getSuccess(), "message", result.getMessage()));
            channel.shutdown();
        });

        app.get("/applications", ctx -> {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
            var stub = JobServiceGrpc.newBlockingStub(channel);
            var apps = stub.listApplications(Empty.getDefaultInstance());
            List<Map<String, Object>> list = apps.getApplicationsList().stream().map(a -> {
                Map<String, Object> map = new HashMap<>();
                map.put("candidateName", a.getCandidateName());
                map.put("candidateEmail", a.getCandidateEmail());
                map.put("jobId", a.getJobId());
                map.put("screeningScore", a.getScreeningScore());
                map.put("screeningFeedback", a.getScreeningFeedback());
                return map;
            }).collect(Collectors.toList());
            ctx.json(list);
            channel.shutdown();
        });

        app.get("/interviews", ctx -> {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9003).usePlaintext().build();
            var stub = InterviewServiceGrpc.newBlockingStub(channel);
            var interviews = stub.listScheduledInterviews(Empty.getDefaultInstance());
            List<Map<String, Object>> list = interviews.getInterviewsList().stream().map(i -> {
                Map<String, Object> map = new HashMap<>();
                map.put("candidateName", i.getCandidateName());
                map.put("candidateEmail", i.getCandidateEmail());
                map.put("jobId", i.getJobId());
                map.put("slotId", i.getSlotId());
                map.put("time", i.getTime());
                return map;
            }).collect(Collectors.toList());
            ctx.json(list);
            channel.shutdown();
        });
    }
}
