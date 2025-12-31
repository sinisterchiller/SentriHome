#!/bin/bash

mkdir -p buffer

ffmpeg \
  -f avfoundation \
  -framerate 30 \
  -i "0" \
  -c:v libx264 \
  -preset ultrafast \
  -pix_fmt yuv420p \
  -f segment \
  -segment_time 1 \
  -segment_format mpegts \
  -reset_timestamps 1 \
  buffer/chunk_%05d.ts
