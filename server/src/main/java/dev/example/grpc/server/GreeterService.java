package dev.example.grpc.server;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import dev.example.grpc.hello.GreeterGrpc;
import dev.example.grpc.hello.HelloReply;
import dev.example.grpc.hello.HelloRequest;
import io.grpc.stub.StreamObserver;

@Service
public class GreeterService extends GreeterGrpc.GreeterImplBase {

    @Override
    @PreAuthorize("hasRole('GREETER')")
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        var reply = HelloReply.newBuilder()
                .setMessage("Hola, " + request.getName())
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
