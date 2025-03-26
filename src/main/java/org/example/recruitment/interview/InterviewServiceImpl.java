package org.example.recruitment.interview;

import interview.InterviewServiceGrpc;
import interview.InterviewServiceOuterClass.*;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class InterviewServiceImpl extends InterviewServiceGrpc.InterviewServiceImplBase {

    private final List<Slot> availableSlots = new ArrayList<>();
    private final List<ScheduledInterview> scheduledInterviews = new ArrayList<>();

    public InterviewServiceImpl() {
        generateSlotsForTomorrow();
    }

    private void generateSlotsForTomorrow() {
        LocalDateTime base = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = 0; i < 10; i++) {
            String time = base.plusMinutes(i * 30).format(formatter);
            Slot slot = Slot.newBuilder()
                    .setSlotId("SLOT_" + i)
                    .setTime(time)
                    .setBooked(false)
                    .build();
            availableSlots.add(slot);
        }
    }

    @Override
    public void listAvailableSlots(Empty request, StreamObserver<SlotList> responseObserver) {
        SlotList.Builder listBuilder = SlotList.newBuilder();
        listBuilder.addAllSlots(availableSlots);
        responseObserver.onNext(listBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void scheduleInterview(InterviewRequest request, StreamObserver<InterviewResponse> responseObserver) {
        Optional<Slot> optionalSlot = availableSlots.stream()
                .filter(s -> s.getSlotId().equals(request.getSlotId()) && !s.getBooked())
                .findFirst();

        if (optionalSlot.isEmpty()) {
            responseObserver.onNext(InterviewResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Slot unavailable or already booked")
                    .build());
        } else {
            Slot original = optionalSlot.get();
            Slot updated = original.toBuilder().setBooked(true).build();
            availableSlots.set(availableSlots.indexOf(original), updated);

            ScheduledInterview interview = ScheduledInterview.newBuilder()
                    .setCandidateName(request.getCandidateName())
                    .setCandidateEmail(request.getCandidateEmail())
                    .setJobId(request.getJobId())
                    .setSlotId(request.getSlotId())
                    .setTime(original.getTime())
                    .build();
            scheduledInterviews.add(interview);

            responseObserver.onNext(InterviewResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Interview scheduled at " + original.getTime())
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void listScheduledInterviews(Empty request, StreamObserver<ScheduledInterviewList> responseObserver) {
        ScheduledInterviewList.Builder listBuilder = ScheduledInterviewList.newBuilder();
        listBuilder.addAllInterviews(scheduledInterviews);
        responseObserver.onNext(listBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<InterviewRequest> scheduleInterviewStream(StreamObserver<InterviewResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(InterviewRequest request) {
                Optional<Slot> optionalSlot = availableSlots.stream()
                        .filter(s -> s.getSlotId().equals(request.getSlotId()) && !s.getBooked())
                        .findFirst();

                if (optionalSlot.isEmpty()) {
                    responseObserver.onNext(InterviewResponse.newBuilder()
                            .setSuccess(false)
                            .setMessage("Slot unavailable or already booked")
                            .build());
                } else {
                    Slot original = optionalSlot.get();
                    Slot updated = original.toBuilder().setBooked(true).build();
                    availableSlots.set(availableSlots.indexOf(original), updated);

                    ScheduledInterview interview = ScheduledInterview.newBuilder()
                            .setCandidateName(request.getCandidateName())
                            .setCandidateEmail(request.getCandidateEmail())
                            .setJobId(request.getJobId())
                            .setSlotId(request.getSlotId())
                            .setTime(original.getTime())
                            .build();
                    scheduledInterviews.add(interview);

                    responseObserver.onNext(InterviewResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage("Interview scheduled at " + original.getTime())
                            .build());
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Bidirectional stream error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
