package org.example.recruitment.interview;

import interview.InterviewServiceGrpc;
import interview.InterviewServiceOuterClass.*;

import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import registry.ServiceRegistryGrpc;
import registry.ServiceRegistryOuterClass;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class InterviewServiceImpl extends InterviewServiceGrpc.InterviewServiceImplBase {

    private final List<Slot> slots = new ArrayList<>();
    private final List<ScheduledInterview> scheduledInterviews = new ArrayList<>();

    public InterviewServiceImpl() {
        generateSlots();
    }

    private void generateSlots() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = 0; i < 10; i++) {
            slots.add(Slot.newBuilder()
                    .setSlotId(UUID.randomUUID().toString())
                    .setTime(tomorrow.plusMinutes(i * 30).format(formatter))
                    .setBooked(false)
                    .build());
        }
    }

    @Override
    public void listAvailableSlots(Empty request, StreamObserver<SlotList> responseObserver) {
        responseObserver.onNext(SlotList.newBuilder().addAllSlots(slots).build());
        responseObserver.onCompleted();
    }

    @Override
    public void scheduleInterview(InterviewRequest request, StreamObserver<InterviewResponse> responseObserver) {
        Optional<Slot> slotOpt = slots.stream()
                .filter(s -> s.getSlotId().equals(request.getSlotId()) && !s.getBooked())
                .findFirst();

        if (slotOpt.isEmpty()) {
            responseObserver.onNext(InterviewResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Slot not available")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        Slot original = slotOpt.get();
        Slot updated = Slot.newBuilder(original).setBooked(true).build();
        slots.set(slots.indexOf(original), updated);

        scheduledInterviews.add(ScheduledInterview.newBuilder()
                .setCandidateName(request.getCandidateName())
                .setCandidateEmail(request.getCandidateEmail())
                .setJobId(request.getJobId())
                .setSlotId(request.getSlotId())
                .setTime(original.getTime())
                .build());

        responseObserver.onNext(InterviewResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Interview scheduled successfully")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listScheduledInterviews(Empty request, StreamObserver<ScheduledInterviewList> responseObserver) {
        responseObserver.onNext(ScheduledInterviewList.newBuilder()
                .addAllInterviews(scheduledInterviews)
                .build());
        responseObserver.onCompleted();
    }

    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(9003)
                .addService(new InterviewServiceImpl())
                .build()
                .start();

        System.out.println("Interview Scheduling Service running on port 9003");

        var regChannel = io.grpc.ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build();
        var stub = ServiceRegistryGrpc.newBlockingStub(regChannel);
        stub.register(ServiceRegistryOuterClass.ServiceInfo.newBuilder()
                .setName("InterviewService")
                .setHost("localhost")
                .setPort(9003)
                .build());

        server.awaitTermination();
    }
}
