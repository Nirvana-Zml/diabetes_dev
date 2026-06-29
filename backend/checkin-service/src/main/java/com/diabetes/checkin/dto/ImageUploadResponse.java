package com.diabetes.checkin.dto;

public class ImageUploadResponse {

    private String imageId;
    private String objectKey;
    private String imageUrl;

    public ImageUploadResponse() {
    }

    public ImageUploadResponse(String imageId, String objectKey, String imageUrl) {
        this.imageId = imageId;
        this.objectKey = objectKey;
        this.imageUrl = imageUrl;
    }

    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
