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

// Get server address from environment or use default
const serverAddress = process.env.GRPC_SERVER || 'localhost:50051';
const client = new testProto.TestService(
  serverAddress,
  grpc.credentials.createInsecure()
);

let testsPassed = 0;
let testsFailed = 0;

// Test 1: Unary call
function testUnaryCall() {
  return new Promise((resolve, reject) => {
    console.log('Testing unary call...');
    client.UnaryCall({message: 'Hello from client'}, (err, response) => {
      if (err) {
        console.error('Unary call failed:', err);
        testsFailed++;
        reject(err);
      } else {
        console.log('Unary call response:', response.reply);
        if (response.reply.includes('Hello from client')) {
          console.log('✓ Unary call test PASSED');
          testsPassed++;
          resolve();
        } else {
          console.error('✗ Unary call test FAILED: unexpected response');
          testsFailed++;
          reject(new Error('Unexpected response'));
        }
      }
    });
  });
}

// Test 2: Server streaming call
function testServerStreamingCall() {
  return new Promise((resolve, reject) => {
    console.log('Testing server streaming call...');
    const call = client.ServerStreamingCall({message: 'Stream test', count: 3});
    let receivedCount = 0;

    call.on('data', (response) => {
      console.log(`Received stream response ${response.index}:`, response.reply);
      receivedCount++;
    });

    call.on('end', () => {
      if (receivedCount === 3) {
        console.log('✓ Server streaming call test PASSED');
        testsPassed++;
        resolve();
      } else {
        console.error(`✗ Server streaming call test FAILED: expected 3 responses, got ${receivedCount}`);
        testsFailed++;
        reject(new Error(`Expected 3 responses, got ${receivedCount}`));
      }
    });

    call.on('error', (err) => {
      console.error('Server streaming call failed:', err);
      testsFailed++;
      reject(err);
    });
  });
}

// Test 3: Bidirectional streaming call
function testBidirectionalStreamingCall() {
  return new Promise((resolve, reject) => {
    console.log('Testing bidirectional streaming call...');
    const call = client.BidirectionalStreamingCall();
    let receivedCount = 0;
    const messagesToSend = ['Message 1', 'Message 2', 'Message 3'];

    call.on('data', (response) => {
      console.log('Received bidi response:', response.reply);
      receivedCount++;

      if (receivedCount === messagesToSend.length) {
        call.end();
      }
    });

    call.on('end', () => {
      if (receivedCount === messagesToSend.length) {
        console.log('✓ Bidirectional streaming call test PASSED');
        testsPassed++;
        resolve();
      } else {
        console.error(`✗ Bidirectional streaming call test FAILED: expected ${messagesToSend.length} responses, got ${receivedCount}`);
        testsFailed++;
        reject(new Error(`Expected ${messagesToSend.length} responses, got ${receivedCount}`));
      }
    });

    call.on('error', (err) => {
      console.error('Bidirectional streaming call failed:', err);
      testsFailed++;
      reject(err);
    });

    // Send messages
    messagesToSend.forEach((msg, index) => {
      setTimeout(() => {
        console.log(`Sending bidi message ${index + 1}:`, msg);
        call.write({message: msg});
      }, index * 100);
    });
  });
}

// Run all tests
async function runTests() {
  console.log('Starting gRPC client tests...');
  console.log(`Connecting to server at: ${serverAddress}`);

  try {
    await testUnaryCall();
    await testServerStreamingCall();
    await testBidirectionalStreamingCall();

    console.log('\n=== Test Results ===');
    console.log(`Tests passed: ${testsPassed}`);
    console.log(`Tests failed: ${testsFailed}`);

    if (testsFailed === 0) {
      console.log('All tests PASSED!');
      process.exit(0);
    } else {
      console.log('Some tests FAILED!');
      process.exit(1);
    }
  } catch (err) {
    console.error('Test execution failed:', err);
    console.log('\n=== Test Results ===');
    console.log(`Tests passed: ${testsPassed}`);
    console.log(`Tests failed: ${testsFailed}`);
    process.exit(1);
  }
}

runTests();
