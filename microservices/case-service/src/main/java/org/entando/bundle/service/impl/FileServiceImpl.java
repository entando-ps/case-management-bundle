package org.entando.bundle.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.entando.bundle.config.BundleConfiguration;
import org.entando.bundle.service.FileService;
import org.entando.bundle.service.impl.utils.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FileServiceImpl implements FileService {

  private final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

  private final BundleConfiguration config;

  public FileServiceImpl(BundleConfiguration config) {
    this.config = config;
  }

  private String getBucketName() {
    String name = null;

    try {
      name = config.getS3().getBucket().getName();
    } catch (Throwable t) {
      log.error("Configuration error detected! No bucket name defined: " + t);
    }
    return name;
  }

  private String getBucketUrl() {
    String url = null;

    try {
      url = "https://" + getBucketName() + ".s3." + getBucketRegion() + ".amazonaws.com";
    } catch (Throwable t) {
      log.error("Configuration error detected! No bucket url defined: " + t);
    }
    return url;
  }

  private Region getBucketRegion() {
    Region region = Region.EU_WEST_1;

    try {
      region = Region.of(config.getS3().getRegion());
    } catch (Throwable t) {
      log.error("Configuration error detected! No AWS region defined: " + t);
    }
    return region;
  }

  /**
   * Create S3 client
   * @return the S£ client for the base calls
   */
  private S3Client getClient() {
    return S3Client.builder()
      .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
      .region(getBucketRegion())
      .build();
  }
  @Override
  public String fileUpload(String resourceToUpload, String key, HashMap<String, String> metadata) {
    S3Client s3 = getClient();

    try {
      PutObjectRequest putOb = PutObjectRequest.builder()
        .bucket(getBucketName())
        .key(key)
        .metadata(metadata)
        .build();
      PutObjectResponse response = s3.putObject(putOb,
        RequestBody.fromBytes(resourceToUpload.getBytes(StandardCharsets.UTF_8)));
      log.info("Uploaded file {}", key);
    } catch (S3Exception t) {
      log.error(t.awsErrorDetails().errorMessage());
      key = null;
    }
    return key;
  }

  @Override
  public String fileUpload(MultipartFile file, Map<String, String> metadata) {
    S3Client s3 = getClient();

    try {
      PutObjectRequest putOb = PutObjectRequest.builder()
        .bucket(getBucketName())
        .key(file.getOriginalFilename())
        .metadata(metadata)
        .build();
      PutObjectResponse response = s3.putObject(putOb, RequestBody.fromBytes(getObjectFile(file)));
      log.info("Uploaded file {}", file.getOriginalFilename());
      return response.eTag();
    } catch (S3Exception t) {
      log.error(t.awsErrorDetails().errorMessage());
    }
    return null;
  }

  private byte[] getObjectFile(MultipartFile file) {
    byte[] bytesArray = null;
    InputStream is = null;

    try {
      is = file.getInputStream();
      bytesArray = new byte[(int) file.getSize()];
      file.getInputStream().read(bytesArray);
    } catch (IOException e) {
      log.error("error building byte array of file", e);
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return bytesArray;
  }

  @Override
  @Deprecated
  public String fileUpload(File file, Map<String, String> metadata) {
    S3Client s3 = getClient();

    try {
      PutObjectRequest putOb = PutObjectRequest.builder()
        .bucket(getBucketName())
        .key(file.getName())
        .metadata(metadata)
        .build();
      PutObjectResponse response = s3.putObject(putOb, RequestBody.fromBytes(getObjectFile(file.getAbsolutePath())));
      return response.eTag();
    } catch (S3Exception t) {
      log.error(t.awsErrorDetails().errorMessage());
      t.printStackTrace();
    }
    return null;
  }

  @Deprecated
  private byte[] getObjectFile(String filePath) {
    FileInputStream fileInputStream = null;
    byte[] bytesArray = null;

    try {
      File file = new File(filePath);
      bytesArray = new byte[(int) file.length()];
      fileInputStream = new FileInputStream(file);
      fileInputStream.read(bytesArray);

    } catch (IOException e) {
      log.error("error building byte array of file", e);
    } finally {
      if (fileInputStream != null) {
        try {
          fileInputStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return bytesArray;
  }

  @Override
  public List<S3Object> fileList() {
    S3Client s3 = getClient();

    try {
      ListObjectsRequest listObjects = ListObjectsRequest
        .builder()
        .bucket(getBucketName())
        .build();

      ListObjectsResponse res = s3.listObjects(listObjects);
      log.debug("File list successfully downloaded from bucket");
      return res.contents();
    } catch (S3Exception t) {
      log.error("Error getting the file list of a bucket", t);
    }
    return null;
  }

  @Override
  public File downloadFile(String keyName) {
    S3Client s3 = getClient();
    File dest = null;

    try {
      GetObjectRequest objectRequest = GetObjectRequest
        .builder()
        .key(keyName)
        .bucket(getBucketName())
        .build();

      ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(objectRequest);
      byte[] data = objectBytes.asByteArray();

      // Write the data to a local file.
      dest = new File(FileHelper.getTmpPathForFile(keyName));
      try (OutputStream os = new FileOutputStream(dest)) {
        os.write(data);
        log.debug("{} successfully downloaded from bucket", dest.getAbsolutePath());
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } catch (S3Exception t) {
      log.error("Error getting the file list of a bucket", t);
    }
    return dest;
  }

  @Override
  public boolean deleteFile(String keyName) {
    ObjectIdentifier objectId = ObjectIdentifier.builder()
      .key(keyName)
      .build();
    return deleteFile(Collections.singletonList(objectId));
  }

  @Override
  public boolean deleteFile(List<ObjectIdentifier> objectIdentifiers) {
    S3Client s3 = getClient();
    DeleteObjectsResponse response = null;

    log.debug("{} element(s) to delete", objectIdentifiers.size());
    try {
      Delete del = Delete.builder()
        .objects(objectIdentifiers)
        .build();
      DeleteObjectsRequest multiObjectDeleteRequest = DeleteObjectsRequest.builder()
        .bucket(getBucketName())
        .delete(del)
        .build();

      response = s3.deleteObjects(multiObjectDeleteRequest);
      log.debug("delete overall result: {}\n {}", response.hasErrors(), response);
      log.debug(" has deleted? {}", response.hasDeleted());
    } catch (S3Exception e) {
      log.error("error deleting resource form S3", e.awsErrorDetails().errorMessage());
    }
    return (!response.hasErrors() && response.hasDeleted());
  }

  @Override
  public String getFilePublicUrl(String keyName) {
    try {
      if (StringUtils.isNotBlank(keyName)) {
        List<S3Object> files = fileList();
        Optional<S3Object> opt = files.stream().filter(f -> f.key().equals(keyName)).findFirst();
        if (opt.isPresent()) {
          return getFilePublicUrlNoCheck(keyName);
        }
      }
    } catch(Throwable t) {
      log.error("error getting public url of a resource in a basket", t);
    }
    return null;
  }

  @Override
  public String getFilePublicUrlNoCheck(String keyName) {
    String name = null;

    if (StringUtils.isNotBlank(keyName)) {
      try {
        URIBuilder builder = new URIBuilder(getBucketUrl());
        builder.setPath(keyName);
        name = builder.toString();
      } catch (Throwable t) {
        log.error("Error generating public url of a file", t);
      }
    }
    return name;
  }

}
