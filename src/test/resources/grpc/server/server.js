const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

// Load the proto file
const PROTO_PATH = path.join(__dirname, 'test.proto');
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true
});

const testProto = grpc.loadPackageDefinition(packageDefinition).testservice;

// Implement the service methods
function unaryCall(call, callback) {
  console.log('Received unary call:', call.request.message);
  callback(null, {reply: `Echo: ${call.request.message}`});
}

function serverStreamingCall(call) {
  const count = call.request.count || 5;
  console.log(`Received server streaming call: ${call.request.message}, count: ${count}`);

  for (let i = 0; i < count; i++) {
    call.write({
      reply: `Stream response ${i + 1}: ${call.request.message}`,
      index: i
    });
  }
  call.end();
}

function bidirectionalStreamingCall(call) {
  console.log('Received bidirectional streaming call');

  call.on('data', (request) => {
    console.log('Received bidi message:', request.message);
    call.write({
      reply: `Bidi echo: ${request.message}`
    });
  });

  call.on('end', () => {
    console.log('Bidi stream ended');
    call.end();
  });

  call.on('error', (err) => {
    console.error('Bidi stream error:', err);
  });
}

// Start the server
function main() {
  const server = new grpc.Server();

  server.addService(testProto.TestService.service, {
    UnaryCall: unaryCall,
    ServerStreamingCall: serverStreamingCall,
    BidirectionalStreamingCall: bidirectionalStreamingCall
  });

  const port = process.env.GRPC_PORT || '50051';
  const host = process.env.GRPC_HOST || '0.0.0.0';
  const address = `${host}:${port}`;

  server.bindAsync(
    address,
    grpc.ServerCredentials.createInsecure(),
    (err, port) => {
      if (err) {
        console.error('Failed to bind server:', err);
        process.exit(1);
      }
      console.log(`gRPC server listening on ${address}`);
    }
  );
}

main();
