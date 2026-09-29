package dev.example.grpc.server;

import org.springframework.security.access.annotation.Secured;

import dev.example.grpc.hello.DemoTokens;
import dev.example.grpc.hello.GreeterGrpc;
import dev.example.grpc.hello.HelloReply;
import dev.example.grpc.hello.HelloRequest;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class GreeterService extends GreeterGrpc.GreeterImplBase {

    @Override
    @Secured(DemoTokens.ROLE_GREETER)
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hola, " + request.getName())
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
