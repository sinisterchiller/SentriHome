#!/bin/bash

# Test script for UDP motion trigger with cooldown

echo "🧪 Testing UDP Motion Trigger"
echo "================================"
echo ""

echo "Test 1: Valid trigger (should be accepted)"
echo "INTRUDER INTRUDER" | nc -u -w1 127.0.0.1 5005
sleep 1

echo ""
echo "Test 2: Immediate second trigger (should be rejected - cooldown)"
echo "INTRUDER INTRUDER" | nc -u -w1 127.0.0.1 5005
sleep 1

echo ""
echo "Test 3: Invalid message (should be ignored)"
echo "HELLO WORLD" | nc -u -w1 127.0.0.1 5005
sleep 1

echo ""
echo "Test 4: Waiting for cooldown to expire..."
echo "Sleeping for 10 seconds..."
sleep 10

echo ""
echo "Test 5: Trigger after cooldown (should be accepted)"
echo "INTRUDER INTRUDER" | nc -u -w1 127.0.0.1 5005

echo ""
echo "================================"
echo "✅ Test complete! Check Pi backend logs for results."
