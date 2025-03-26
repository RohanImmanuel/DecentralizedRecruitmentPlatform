package org.example.recruitment.interview;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import interview.InterviewServiceGrpc;
import interview.InterviewServiceOuterClass.Slot;
import interview.InterviewServiceOuterClass.SlotList;
import interview.InterviewServiceOuterClass.InterviewRequest;
import interview.InterviewServiceOuterClass.InterviewConfirmation;
import interview.InterviewServiceOuterClass.ScheduleRequest;
import interview.InterviewServiceOuterClass.ScheduleUpdate;
import registry.ServiceRegistryGrpc;
import registry.ServiceRegistryOuterClass.ServiceInfo;

public class InterviewServiceImpl extends InterviewServiceGrpc.InterviewServiceImplBase {
    // Thread-safe list of available slots
    private final List<Slot> availableSlots = Collections.synchronizedList(new ArrayList<>());
    // List of active subscriber streams for updates
    private final List<StreamObserver<ScheduleUpdate>> subscribers = Collections.synchronizedList(new ArrayList<>());

    public InterviewServiceImpl() {
        // Initialize with some predefined time slots
        availableSlots.add(Slot.newBuilder().setSlotId("S1").setTime("2025-04-01 10:00 AM").setBooked(false).build());
        availableSlots.add(Slot.newBuilder().setSlotId("S2").setTime("2025-04-01 11:00 AM").setBooked(false).build());
        availableSlots.add(Slot.newBuilder().setSlotId("S3").setTime("2025-04-01 02:00 PM").setBooked(false).build());
    }

    @Override
    public void listAvailableSlots(com.google.protobuf.Empty request, StreamObserver<SlotList> responseObserver) {
        // Return all slots that are not booked (we remove booked slots from list when scheduling)
        SlotList slotList = SlotList.newBuilder().addAllSlots(availableSlots).build();
        responseObserver.onNext(slotList);
        responseObserver.onCompleted();
    }

    @Override
    public void scheduleInterview(InterviewRequest request, StreamObserver<InterviewConfirmation> responseObserver) {
        String slotId = request.getSlotId();
        Slot slotToBook = null;
        synchronized (availableSlots) {
            for (Slot slot : availableSlots) {
                if (slot.getSlotId().equals(slotId)) {
                    slotToBook = slot;
                    break;
                }
            }
            if (slotToBook != null) {
                availableSlots.remove(slotToBook);  // remove the slot from available list (mark as booked)
            }
        }
        if (slotToBook == null) {
            // Slot not found or already booked
            InterviewConfirmation confirmation = InterviewConfirmation.newBuilder()
                    .setSuccess(false)
                    .setMessage("Slot " + slotId + " is not available")
                    .build();
            responseObserver.onNext(confirmation);
            responseObserver.onCompleted();
        } else {
            String msg = "Interview scheduled for " + request.getCandidateName() + " at " + slotToBook.getTime();
            InterviewConfirmation confirmation = InterviewConfirmation.newBuilder()
                    .setSuccess(true)
                    .setMessage(msg)
                    .build();
            responseObserver.onNext(confirmation);
            responseObserver.onCompleted();
            // Broadcast update to all subscribers about this booking
            broadcastUpdate("Slot " + slotId + " booked by " + request.getCandidateName());
        }
    }

    @Override
    public StreamObserver<ScheduleRequest> scheduleUpdates(final StreamObserver<ScheduleUpdate> responseObserver) {
        // When a new streaming connection is established:
        subscribers.add(responseObserver);
        // Immediately send the current slots to this subscriber
        ScheduleUpdate initialUpdate = ScheduleUpdate.newBuilder()
                .setMessage("Connected to schedule updates")
                .addAllSlots(availableSlots)
                .build();
        responseObserver.onNext(initialUpdate);

        // Return an observer to handle incoming requests from this client
        return new StreamObserver<ScheduleRequest>() {
            @Override
            public void onNext(ScheduleRequest request) {
                if (request.getSubscribe()) {
                    // Client is (re)confirming subscription; we've already added them, so no action needed
                } else if (!request.getSlotId().isEmpty()) {
                    // A booking request via the stream
                    String slotId = request.getSlotId();
                    String candidateName = request.getCandidateName();
                    Slot slotToBook = null;
                    synchronized (availableSlots) {
                        for (Slot slot : availableSlots) {
                            if (slot.getSlotId().equals(slotId)) {
                                slotToBook = slot;
                                break;
                            }
                        }
                        if (slotToBook != null) {
                            availableSlots.remove(slotToBook);
                        }
                    }
                    if (slotToBook == null) {
                        // Slot already taken or invalid – send a failure update *only to this client*
                        ScheduleUpdate update = ScheduleUpdate.newBuilder()
                                .setMessage("Slot " + slotId + " not available")
                                .addAllSlots(availableSlots)
                                .build();
                        responseObserver.onNext(update);
                    } else {
                        // Successful booking – broadcast to all subscribers
                        broadcastUpdate("Slot " + slotId + " booked by " + candidateName);
                    }
                }
            }
            @Override
            public void onError(Throwable t) {
                System.err.println("ScheduleUpdates error: " + t.getMessage());
                // Remove this subscriber on error
                subscribers.remove(responseObserver);
            }
            @Override
            public void onCompleted() {
                // Client disconnected – remove from subscribers
                subscribers.remove(responseObserver);
            }
        };
    }

    private void broadcastUpdate(String message) {
        // Build an update with the latest available slots
        ScheduleUpdate update = ScheduleUpdate.newBuilder()
                .setMessage(message)
                .addAllSlots(availableSlots)
                .build();
        // Send this update to all connected subscribers
        synchronized (subscribers) {
            Iterator<StreamObserver<ScheduleUpdate>> it = subscribers.iterator();
            while (it.hasNext()) {
                StreamObserver<ScheduleUpdate> subscriber = it.next();
                try {
                    subscriber.onNext(update);
                } catch (Exception e) {
                    // If a subscriber is no longer active, remove it
                    it.remove();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Connect to Service Registry
        ManagedChannel regChannel = ManagedChannelBuilder.forAddress("localhost", 9000)
                .usePlaintext().build();
        ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub = ServiceRegistryGrpc.newBlockingStub(regChannel);

        // Start gRPC server for Interview Service
        int port = 9003;
        Server server = ServerBuilder.forPort(port)
                .addService(new InterviewServiceImpl())
                .build()
                .start();
        System.out.println("Interview Scheduling Service started on port " + port);

        // Register the service in the registry
        ServiceInfo serviceInfo = ServiceInfo.newBuilder()
                .setName("InterviewService")
                .setHost("localhost")
                .setPort(port)
                .build();
        registryStub.register(serviceInfo);

        server.awaitTermination();
    }
}
