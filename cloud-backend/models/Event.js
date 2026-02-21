import mongoose from "mongoose";

const EventSchema = new mongoose.Schema({
  deviceId: {
    type: String,
    required: true,
    index: true,
  },

  filename: {
    type: String,
    required: true,
  },

  s3Key: {
    type: String,
    required: true,
  },

  thumbnailS3Key: {
    type: String,
  },

  status: {
    type: String,
    enum: ["uploading", "ready", "failed"],
    default: "ready",
  },

  createdAt: {
    type: Date,
    default: Date.now,
    index: true,
  },
});

export const Event = mongoose.model("Event", EventSchema);
