import {
  S3Client,
  PutObjectCommand,
  DeleteObjectCommand,
  ListObjectsV2Command,
  GetObjectCommand,
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";

const s3 = new S3Client({
  region: process.env.AWS_REGION,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
  },
});

const BUCKET = process.env.S3_BUCKET_NAME;

/** Upload a buffer or stream to S3 */
export async function uploadToS3(key, body, contentType) {
  await s3.send(new PutObjectCommand({
    Bucket: BUCKET,
    Key: key,
    Body: body,
    ContentType: contentType,
  }));
  return `s3://${BUCKET}/${key}`;
}

/** Delete a single object */
export async function deleteFromS3(key) {
  await s3.send(new DeleteObjectCommand({ Bucket: BUCKET, Key: key }));
}

/** List objects under a prefix */
export async function listS3Objects(prefix) {
  const res = await s3.send(new ListObjectsV2Command({
    Bucket: BUCKET,
    Prefix: prefix,
  }));
  return res.Contents || [];
}

/** Generate a short-lived presigned GET URL (default 5 min) */
export async function getPresignedUrl(key, expiresIn = 300) {
  return getSignedUrl(
    s3,
    new GetObjectCommand({ Bucket: BUCKET, Key: key }),
    { expiresIn }
  );
}

/** Delete all objects older than maxAgeMs under a prefix */
export async function pruneOldSegments(prefix, maxAgeMs = 10 * 60 * 1000) {
  const objects = await listS3Objects(prefix);
  const cutoff = Date.now() - maxAgeMs;
  const toDelete = objects.filter(
    (o) => o.LastModified && o.LastModified.getTime() < cutoff
  );
  await Promise.all(toDelete.map((o) => deleteFromS3(o.Key)));
  return toDelete.length;
}

export { BUCKET, s3 };