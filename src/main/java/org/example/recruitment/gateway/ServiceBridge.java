package org.example.recruitment.gateway;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.javalin.Javalin;

import job.JobServiceGrpc;
import job.JobServiceOuterClass;
import screening.CandidateScreeningServiceGrpc;
import screening.CandidateScreeningServiceOuterClass;
import interview.InterviewServiceGrpc;
import interview.InterviewServiceOuterClass;
import registry.ServiceRegistryGrpc;
import registry.ServiceRegistryOuterClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServiceBridge {

    private static ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub;

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
        var registryChannel = ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build();
        registryStub = ServiceRegistryGrpc.newBlockingStub(registryChannel);

        // REST endpoints
        app.post("/jobs", ctx -> {
            var req = ctx.bodyAsClass(JobCreateRequest.class);
            var stub = JobServiceGrpc.newBlockingStub(getChannel("JobService"));
            var response = stub.createJob(JobServiceOuterClass.Job.newBuilder()
                    .setTitle(req.title)
                    .setCompany(req.company)
                    .setDescription(req.description)
                    .build());
            ctx.json(Map.of("success", response.getSuccess(), "jobId", response.getJobId()));
        });

        app.get("/jobs", ctx -> {
            var stub = JobServiceGrpc.newBlockingStub(getChannel("JobService"));
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
        });

        app.post("/apply", ctx -> {
            var req = ctx.bodyAsClass(ApplicationRequestDTO.class);
            var stub = JobServiceGrpc.newBlockingStub(getChannel("JobService"));
            var resp = stub.applyForJob(JobServiceOuterClass.ApplicationRequest.newBuilder()
                    .setJobId(req.jobId)
                    .setCandidateName(req.candidateName)
                    .setCandidateEmail(req.candidateEmail)
                    .setResumeText(req.resumeText)
                    .build());
            ctx.json(Map.of("success", resp.getSuccess(), "message", resp.getMessage()));
        });

        app.get("/screening", ctx -> {
            String email = ctx.queryParam("email");
            var stub = CandidateScreeningServiceGrpc.newBlockingStub(getChannel("CandidateScreeningService"));
            var result = stub.getScreeningResult(CandidateScreeningServiceOuterClass.ScreeningQuery.newBuilder()
                    .setCandidateEmail(email)
                    .build());
            Map<String, Object> map = new HashMap<>();
            map.put("email", result.getCandidateEmail());
            map.put("score", result.getScore());
            map.put("feedback", result.getFeedback());
            ctx.json(map);
        });

        app.get("/slots", ctx -> {
            var stub = InterviewServiceGrpc.newBlockingStub(getChannel("InterviewService"));
            var slots = stub.listAvailableSlots(Empty.getDefaultInstance());
            List<Map<String, Object>> slotList = slots.getSlotsList().stream().map(slot -> {
                Map<String, Object> map = new HashMap<>();
                map.put("slotId", slot.getSlotId());
                map.put("time", slot.getTime());
                map.put("booked", slot.getBooked());
                return map;
            }).collect(Collectors.toList());
            ctx.json(slotList);
        });

        app.post("/schedule", ctx -> {
            var req = ctx.bodyAsClass(ScheduleRequestDTO.class);
            var stub = InterviewServiceGrpc.newBlockingStub(getChannel("InterviewService"));
            var result = stub.scheduleInterview(InterviewServiceOuterClass.InterviewRequest.newBuilder()
                    .setCandidateName(req.candidateName)
                    .setCandidateEmail(req.candidateEmail)
                    .setJobId(req.jobId)
                    .setSlotId(req.slotId)
                    .build());
            ctx.json(Map.of("success", result.getSuccess(), "message", result.getMessage()));
        });

        app.get("/applications", ctx -> {
            var stub = JobServiceGrpc.newBlockingStub(getChannel("JobService"));
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
        });

        app.get("/interviews", ctx -> {
            var stub = InterviewServiceGrpc.newBlockingStub(getChannel("InterviewService"));
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
        });

        // WebSocket: Resume Submission (Client Streaming)
        app.ws("/ws/screening/submit", ws -> {
            ws.onConnect(ctx -> {
                var stub = CandidateScreeningServiceGrpc.newStub(getChannel("CandidateScreeningService"));
                StreamObserver<CandidateScreeningServiceOuterClass.ResumeRequest> reqStream = stub.submitResume(
                        new StreamObserver<CandidateScreeningServiceOuterClass.ScreeningResult>() {
                            public void onNext(CandidateScreeningServiceOuterClass.ScreeningResult value) {
                                if (ctx.session.isOpen()) {
                                    ctx.send("SCORE: " + value.getScore() + ", FEEDBACK: " + value.getFeedback());
                                }
                            }
                            public void onError(Throwable t) {
                                if (ctx.session.isOpen()) {
                                    ctx.send("ERROR: " + t.getMessage());
                                }
                            }
                            public void onCompleted() {
                                if (ctx.session.isOpen()) {
                                    ctx.send("DONE");
                                }
                            }
                        }
                );
                ctx.attribute("stream", reqStream);
            });

            ws.onMessage(ctx -> {
                var stream = ctx.attribute("stream");
                if (stream != null) {
                    ((StreamObserver<CandidateScreeningServiceOuterClass.ResumeRequest>) stream).onNext(
                            CandidateScreeningServiceOuterClass.ResumeRequest.newBuilder()
                                    .setCandidateEmail("stream@user.com")
                                    .setContentChunk(ctx.message())
                                    .build()
                    );
                }
            });

            ws.onClose(ctx -> {
                var stream = ctx.attribute("stream");
                if (stream != null) ((StreamObserver<?>) stream).onCompleted();
            });
        });

        // WebSocket: Interview Scheduling (Bidirectional Streaming)
        app.ws("/ws/interviews/schedule", ws -> {
            ws.onConnect(ctx -> {
                var stub = InterviewServiceGrpc.newStub(getChannel("InterviewService"));
                StreamObserver<InterviewServiceOuterClass.InterviewRequest> reqStream =
                        stub.scheduleInterviewStream(new StreamObserver<InterviewServiceOuterClass.InterviewResponse>() {
                            @Override
                            public void onNext(InterviewServiceOuterClass.InterviewResponse response) {
                                if (ctx.session.isOpen()) {
                                    ctx.send("CONFIRM: " + response.getMessage());
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                if (ctx.session.isOpen()) {
                                    ctx.send("ERROR: " + t.getMessage());
                                }
                            }

                            @Override
                            public void onCompleted() {
                                if (ctx.session.isOpen()) {
                                    ctx.send("DONE");
                                }
                            }
                        });
                ctx.attribute("stream", reqStream);
            });

            ws.onMessage(ctx -> {
                var stream = ctx.attribute("stream");
                if (stream != null) {
                    String[] parts = ctx.message().split(",");
                    ((StreamObserver<InterviewServiceOuterClass.InterviewRequest>) stream).onNext(
                            InterviewServiceOuterClass.InterviewRequest.newBuilder()
                                    .setCandidateName(parts[0])
                                    .setCandidateEmail(parts[1])
                                    .setJobId(Integer.parseInt(parts[2]))
                                    .setSlotId(parts[3])
                                    .build()
                    );
                }
            });

            ws.onClose(ctx -> {
                var stream = ctx.attribute("stream");
                if (stream != null) ((StreamObserver<?>) stream).onCompleted();
            });
        });
    }

    private static ManagedChannel getChannel(String serviceName) {
        var info = registryStub.discover(ServiceRegistryOuterClass.ServiceQuery.newBuilder()
                .setName(serviceName)
                .build());
        return ManagedChannelBuilder.forAddress(info.getHost(), info.getPort()).usePlaintext().build();
    }
}
