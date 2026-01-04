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

  driveFileId: {
    type: String,
    required: true,
  },

  driveLink: {
    type: String,
    required: true,
  },

  thumbnailDriveId: {
    type: String,
  },

  thumbnailUrl: {
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
